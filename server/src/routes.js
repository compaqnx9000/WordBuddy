import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { Router } from 'express'
import { query } from './db.js'
import {
  authRequired,
  optionalAuth,
  ensureUserNotebook,
  hashPassword,
  normalizePassword,
  normalizePhone,
  recordLoginEvent,
  recordPasswordEvent,
  rotateSessionVersion,
  signToken,
  verifyPassword,
  clientIp,
} from './auth.js'
import { newLoginCode, sendCode, skipVerify } from './sms.js'
import { getUserCheckIn, performUserCheckIn, performMakeupCheckIn } from './checkin.js'
import { resolveIpLocation } from './device.js'
import {
  GIFT_CATEGORIES,
  getGift,
  listPublishedGifts,
  mapOrder,
  redeemGift,
} from './gifts.js'
import {
  createWithdrawal,
  listWithdrawals,
  withdrawConfig,
} from './withdrawals.js'
import {
  INVITE_REWARD_INVITEE,
  INVITE_REWARD_INVITER,
  bindInviteCode,
  findUserByBuddyId,
  getInviteStats,
  normalizeInviteCode,
} from './invite.js'

export const router = Router()

const uploadsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../uploads')

const PAGE_SIZE_MAX = 200

const USER_PROFILE_SQL = `id, phone, avatar_url, user_level, nickname,
              shipping_name, shipping_phone, shipping_detail,
              gender, region, buddy_id, signature, email,
              alipay_account, wechat_account,
              last_ip, last_ip_location`

async function loadUserProfileRow(userId) {
  let row = (await query(`SELECT ${USER_PROFILE_SQL} FROM users WHERE id = $1`, [userId])).rows[0]
  if (!row) return null
  const current = String(row.buddy_id || '').trim()
  if (!current || isLegacyAutoBuddyId(current)) {
    try {
      row = (await assignRandomBuddyId(userId, current)) || row
    } catch (err) {
      console.error('assignRandomBuddyId failed', userId, err)
      throw err
    }
  }
  return row
}

/** Random 8-char a-z0-9 → ~2.8e12 combinations, enough for 10M+ users. */
function randomBuddyId() {
  const alphabet = 'abcdefghijklmnopqrstuvwxyz0123456789'
  const bytes = crypto.randomBytes(8)
  let out = ''
  for (let i = 0; i < 8; i += 1) out += alphabet[bytes[i] % alphabet.length]
  return out
}

function isLegacyAutoBuddyId(buddyId) {
  const id = String(buddyId || '').trim().toLowerCase()
  if (!id) return true
  // Previous auto formats: dz1 / dz0000000001
  return /^dz\d+$/.test(id)
}

async function assignRandomBuddyId(userId, current) {
  const legacyPhoneKey = `dz${userId}`
  for (let attempt = 0; attempt < 32; attempt += 1) {
    const buddyId = randomBuddyId()
    const taken = (
      await query('SELECT id FROM users WHERE buddy_id = $1 LIMIT 1', [buddyId])
    ).rows[0]
    if (taken) continue
    try {
      const assigned = (
        await query(
          `UPDATE users SET buddy_id = $2
           WHERE id = $1
             AND (
               buddy_id IS NULL
               OR btrim(buddy_id) = ''
               OR lower(btrim(buddy_id)) = $3
               OR buddy_id ~ '^dz[0-9]+$'
             )
           RETURNING ${USER_PROFILE_SQL}`,
          [userId, buddyId, legacyPhoneKey],
        )
      ).rows[0]
      if (assigned) return assigned
      // Another request already assigned a non-legacy id.
      const fresh = (await query(`SELECT ${USER_PROFILE_SQL} FROM users WHERE id = $1`, [userId])).rows[0]
      if (fresh && String(fresh.buddy_id || '').trim() && !isLegacyAutoBuddyId(fresh.buddy_id)) {
        return fresh
      }
    } catch (err) {
      // Unique index collision under race — retry with a new id.
      if (err?.code === '23505') continue
      throw err
    }
  }
  throw new Error('无法分配唯一搭子号，请稍后重试')
}

function wordInitialLetterSql(column = 'word') {
  return `
    CASE
      WHEN upper(substr(trim(${column}), 1, 1)) ~ '^[A-Z]$'
        THEN upper(substr(trim(${column}), 1, 1))
      ELSE '#'
    END`
}

function asArray(value) {
  return Array.isArray(value) ? value : []
}

function mapWord(row) {
  return {
    id: Number(row.id),
    notebookId: Number(row.notebook_id),
    text: row.word,
    isPhrase: Boolean(row.is_phrase),
    ipaUk: row.ipa_uk,
    ipaUs: row.ipa_us,
    definitions: asArray(row.definitions),
    examples: asArray(row.examples).map((e) => ({
      english: e?.en ?? e?.english ?? '',
      chinese: e?.zh ?? e?.chinese ?? '',
    })),
    nearWords: asArray(row.near_words),
    synonyms: asArray(row.synonyms),
    antonyms: asArray(row.antonyms),
    sortOrder: row.sort_order,
    addedAtMillis: row.added_at ? new Date(row.added_at).getTime() : Date.now(),
  }
}

function parseCursor(raw) {
  if (!raw) return null
  const [sort, id] = String(raw).split(':')
  const sortOrder = Number(sort)
  const wordId = Number(id)
  if (!Number.isFinite(sortOrder) || !Number.isFinite(wordId)) return null
  return { sortOrder, wordId }
}

router.get('/', (_req, res) => {
  res.type('html').send('<h1>hello</h1><p>HotWords API is reachable.</p>')
})

router.get('/hello', (_req, res) => {
  res.type('text').send('hello')
})

router.get('/health', (_req, res) => {
  res.json({ ok: true })
})

function readAppVersion() {
  const file = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../public/app/version.json')
  if (!fs.existsSync(file)) return null
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch {
    return null
  }
}

/** Compatibility alias for older clients that probe /version */
router.get('/version', (_req, res) => {
  const data = readAppVersion()
  if (!data) {
    res.status(404).json({ error: '尚未配置应用版本' })
    return
  }
  res.json(data)
})

router.get('/app/version', (_req, res) => {
  const data = readAppVersion()
  if (!data) {
    res.status(404).json({ error: '尚未配置应用版本' })
    return
  }
  res.json(data)
})

router.post('/auth/send-code', async (req, res) => {
  const phone = normalizePhone(req.body?.phone)
  if (!phone) {
    res.status(400).json({ error: '请输入正确的手机号' })
    return
  }
  const code = newLoginCode()
  try {
    await query(
      `INSERT INTO sms_codes (phone, code, expires_at)
       VALUES ($1, $2, now() + interval '10 minutes')`,
      [phone, code],
    )
    await sendCode(phone, code)
  } catch (error) {
    console.error('[auth/send-code]', error)
    res.status(502).json({ error: error.message || '短信发送失败，请稍后重试' })
    return
  }
  const payload = { ok: true, expiresInSec: 600 }
  if (skipVerify()) payload.debugCode = code
  res.json(payload)
})

async function verifySmsCode(phone, code) {
  if (!/^\d{6}$/.test(code)) return { ok: false, error: '请输入6位验证码' }
  if (skipVerify()) return { ok: true }
  const found = await query(
    `SELECT id FROM sms_codes
     WHERE phone = $1 AND code = $2 AND consumed_at IS NULL AND expires_at > now()
     ORDER BY id DESC
     LIMIT 1`,
    [phone, code],
  )
  if (found.rowCount === 0) return { ok: false, error: '验证码无效或已过期' }
  return { ok: true, smsId: found.rows[0].id }
}

async function consumeSms(smsId) {
  if (smsId) await query('UPDATE sms_codes SET consumed_at = now() WHERE id = $1', [smsId])
}

async function finishLogin(req, res, user, method, invite = null) {
  const vocabNotebookId = await ensureUserNotebook(user.id)
  // Single-device policy: each successful login invalidates older JWTs.
  const sessionVersion = await rotateSessionVersion(user.id)
  await recordLoginEvent(req, {
    userId: user.id,
    phone: user.phone,
    method,
    success: true,
  })
  const profileRow = await loadUserProfileRow(user.id)
  const profile = mapUserProfile(profileRow, user)
  const payload = {
    isNewUser: false,
    token: signToken(user, sessionVersion),
    user: profile,
    vocabNotebookId: Number(vocabNotebookId),
    avatarUrl: profile.avatarUrl,
    level: profile.level,
  }
  if (invite?.ok) {
    payload.invite = {
      bound: true,
      inviteeReward: invite.inviteeReward,
      inviterReward: invite.inviterReward,
      inviterBuddyId: invite.inviterBuddyId,
    }
  }
  res.json(payload)
}

router.post('/auth/login', async (req, res) => {
  const phone = normalizePhone(req.body?.phone)
  if (!phone) {
    res.status(400).json({ error: '请输入正确的手机号' })
    return
  }

  const password = String(req.body?.password || '')
  const code = String(req.body?.code || '').trim()

  // Phone + password login (existing accounts that already set a password).
  if (password && !code) {
    const normalized = normalizePassword(password)
    if (!normalized) {
      res.status(400).json({ error: '密码需要 6 到 32 位' })
      return
    }
    const user = (
      await query('SELECT id, phone, password_hash, avatar_url, user_level FROM users WHERE phone = $1', [phone])
    ).rows[0]
    if (!user) {
      await recordLoginEvent(req, { phone, method: 'password', success: false })
      res.status(400).json({ error: '账号不存在，请先用验证码登录' })
      return
    }
    if (!user.password_hash) {
      await recordLoginEvent(req, { userId: user.id, phone, method: 'password', success: false })
      res.status(400).json({ error: '尚未设置密码，请使用验证码登录' })
      return
    }
    if (!verifyPassword(normalized, user.password_hash)) {
      await recordLoginEvent(req, { userId: user.id, phone, method: 'password', success: false })
      res.status(400).json({ error: '手机号或密码错误' })
      return
    }
    await finishLogin(req, res, user, 'password')
    return
  }

  // Phone + SMS: existing users log in; new users must set a password via /auth/register.
  const checked = await verifySmsCode(phone, code)
  if (!checked.ok) {
    res.status(400).json({ error: checked.error })
    return
  }

  const user = (
    await query('SELECT id, phone, password_hash, avatar_url, user_level FROM users WHERE phone = $1', [phone])
  ).rows[0]
  if (!user || !user.password_hash) {
    // Keep the SMS code unconsumed so /auth/register can verify it again.
    res.json({ isNewUser: true })
    return
  }
  await consumeSms(checked.smsId)
  await finishLogin(req, res, user, 'sms')
})

router.post('/auth/register', async (req, res) => {
  const phone = normalizePhone(req.body?.phone)
  const code = String(req.body?.code || '').trim()
  const password = normalizePassword(req.body?.password)
  const inviteCode = normalizeInviteCode(req.body?.inviteCode || req.body?.invite || '')
  if (!phone) {
    res.status(400).json({ error: '请输入正确的手机号' })
    return
  }
  if (!password) {
    res.status(400).json({ error: '密码需要 6 到 32 位' })
    return
  }
  const checked = await verifySmsCode(phone, code)
  if (!checked.ok) {
    res.status(400).json({ error: checked.error })
    return
  }

  const passwordHash = hashPassword(password)
  const existing = (
    await query('SELECT id, phone FROM users WHERE phone = $1', [phone])
  ).rows[0]
  if (existing) {
    await query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, existing.id])
    await recordPasswordEvent(req, existing.id, 'set_after_sms')
    await consumeSms(checked.smsId)
    await finishLogin(req, res, existing, 'register')
    return
  }
  const user = (
    await query('INSERT INTO users (phone, password_hash) VALUES ($1, $2) RETURNING id, phone', [
      phone,
      passwordHash,
    ])
  ).rows[0]
  await recordPasswordEvent(req, user.id, 'register')
  await consumeSms(checked.smsId)

  let invite = null
  if (inviteCode) {
    try {
      invite = await bindInviteCode(user.id, inviteCode)
      if (invite?.ok) {
        console.log(
          `[invite] user ${user.id} bound to ${invite.inviterId} (+${invite.inviteeReward}/+${invite.inviterReward})`,
        )
      }
    } catch (err) {
      console.error('[invite] bind failed', err)
    }
  }

  // Ensure buddy id before responding so share works immediately.
  await loadUserProfileRow(user.id)
  await finishLogin(req, res, user, 'register', invite?.ok ? invite : null)
})

/** Public invite preview for landing page. */
router.get('/invite/:code', async (req, res) => {
  const code = normalizeInviteCode(req.params.code)
  if (!code) {
    res.status(400).json({ error: '邀请码无效' })
    return
  }
  const inviter = await findUserByBuddyId(code)
  if (!inviter) {
    res.status(404).json({ error: '邀请人不存在' })
    return
  }
  const displayName =
    String(inviter.nickname || '').trim() ||
    `搭子 ${String(inviter.buddy_id || code).slice(0, 4)}…`
  res.json({
    inviteCode: String(inviter.buddy_id || code).toLowerCase(),
    displayName,
    avatarUrl: inviter.avatar_url || null,
    rewards: {
      inviteePoints: INVITE_REWARD_INVITEE,
      inviterPoints: INVITE_REWARD_INVITER,
    },
    downloadUrl: '/app/WordBuddy-release.apk',
  })
})

router.get('/me/invite', authRequired, async (req, res) => {
  const row = await loadUserProfileRow(req.user.id)
  if (!row) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  const buddyId = String(row.buddy_id || '').trim()
  const stats = await getInviteStats(req.user.id)
  const base =
    String(process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '') ||
    `${req.protocol}://${req.get('host')}`
  res.json({
    buddyId,
    inviteCode: buddyId,
    inviteUrl: buddyId ? `${base}/i/${encodeURIComponent(buddyId)}` : null,
    invitedCount: stats.invitedCount,
    invitePointsEarned: stats.invitePointsEarned,
    canBindInvite: Boolean(stats.canBindInvite),
    invitedByBuddyId: stats.invitedByBuddyId,
    rewards: {
      inviteePoints: INVITE_REWARD_INVITEE,
      inviterPoints: INVITE_REWARD_INVITER,
    },
  })
})

/** Bind invite code once after registration (makeup). */
router.post('/me/invite/bind', authRequired, async (req, res) => {
  try {
    const result = await bindInviteCode(req.user.id, req.body?.inviteCode || req.body?.invite)
    if (!result.ok) {
      res.status(400).json({ error: result.error || '绑定失败' })
      return
    }
    const checkIn = await getUserCheckIn(req.user.id)
    const stats = await getInviteStats(req.user.id)
    res.json({
      ok: true,
      message: `邀请码已填写，获得 ${result.inviteeReward} 积分`,
      inviteeReward: result.inviteeReward,
      inviterReward: result.inviterReward,
      inviterBuddyId: result.inviterBuddyId,
      canBindInvite: false,
      invitedByBuddyId: result.inviterBuddyId,
      invitedCount: stats.invitedCount,
      checkIn,
      totalPoints: checkIn.totalPoints,
    })
  } catch (error) {
    console.error('[me/invite/bind]', error)
    res.status(500).json({ error: '绑定失败，请稍后重试' })
  }
})

router.get('/me', authRequired, async (req, res) => {
  const vocabNotebookId = await ensureUserNotebook(req.user.id)
  const row = await loadUserProfileRow(req.user.id)
  if (!row) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  const level = Math.min(7, Math.max(0, Number.isFinite(Number(row.user_level)) ? Number(row.user_level) : 0))
  const checkIn = await getUserCheckIn(req.user.id)
  const profile = mapUserProfile(row, req.user)
  res.json({
    user: profile,
    vocabNotebookId: Number(vocabNotebookId),
    avatarUrl: profile.avatarUrl,
    level,
    checkIn,
  })
})

router.patch('/me', authRequired, async (req, res) => {
  const row = await loadUserProfileRow(req.user.id)
  if (!row) {
    res.status(404).json({ error: '用户不存在' })
    return
  }

  let nickname = row.nickname
  if (req.body?.nickname != null) {
    nickname = String(req.body.nickname).trim().slice(0, 24)
    if (!nickname) {
      res.status(400).json({ error: '昵称不能为空' })
      return
    }
  }

  let shippingName = row.shipping_name
  let shippingPhone = row.shipping_phone
  let shippingDetail = row.shipping_detail
  if (req.body?.shipping != null && typeof req.body.shipping === 'object') {
    const s = req.body.shipping
    shippingName = String(s.name ?? s.shippingName ?? '').trim().slice(0, 40) || null
    shippingPhone = String(s.phone ?? s.shippingPhone ?? '').trim().slice(0, 20) || null
    shippingDetail = String(s.detail ?? s.shippingDetail ?? '').trim().slice(0, 200) || null
    if (shippingName || shippingPhone || shippingDetail) {
      if (!shippingName || !shippingPhone || !shippingDetail) {
        res.status(400).json({ error: '请完整填写收货姓名、手机号和地址' })
        return
      }
    }
  } else {
    if (req.body?.shippingName != null) {
      shippingName = String(req.body.shippingName).trim().slice(0, 40) || null
    }
    if (req.body?.shippingPhone != null) {
      shippingPhone = String(req.body.shippingPhone).trim().slice(0, 20) || null
    }
    if (req.body?.shippingDetail != null) {
      shippingDetail = String(req.body.shippingDetail).trim().slice(0, 200) || null
    }
  }

  let gender = row.gender
  if (req.body?.gender != null) {
    const next = String(req.body.gender).trim()
    if (next && !['男', '女', '未知'].includes(next)) {
      res.status(400).json({ error: '请选择男、女或未知' })
      return
    }
    gender = next || null
  }

  let region = row.region
  if (req.body?.region != null) {
    region = String(req.body.region).trim().slice(0, 40) || null
  }

  let signature = row.signature
  if (req.body?.signature != null) {
    signature = String(req.body.signature).trim().slice(0, 40) || null
  }

  let email = row.email
  if (req.body?.email != null) {
    const next = String(req.body.email).trim().slice(0, 80)
    if (next) {
      // Basic RFC-ish check; empty string clears email.
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(next)) {
        res.status(400).json({ error: '请输入有效的邮箱地址' })
        return
      }
      email = next.toLowerCase()
    } else {
      email = null
    }
  }

  let alipayAccount = row.alipay_account
  if (req.body?.alipayAccount != null) {
    const next = String(req.body.alipayAccount).trim().slice(0, 64)
    if (next && next.length < 3) {
      res.status(400).json({ error: '支付宝账号至少 3 位' })
      return
    }
    alipayAccount = next || null
  }

  let wechatAccount = row.wechat_account
  if (req.body?.wechatAccount != null) {
    const next = String(req.body.wechatAccount).trim().slice(0, 64)
    if (next && next.length < 3) {
      res.status(400).json({ error: '微信收款标识至少 3 位' })
      return
    }
    wechatAccount = next || null
  }

  // 搭子号为系统唯一识别码，禁止用户修改
  const buddyId = row.buddy_id
  if (req.body?.buddyId != null) {
    res.status(400).json({ error: '搭子号由系统分配，不可修改' })
    return
  }

  const updated = (
    await query(
      `UPDATE users SET
         nickname = $2,
         shipping_name = $3,
         shipping_phone = $4,
         shipping_detail = $5,
         gender = $6,
         region = $7,
         buddy_id = $8,
         signature = $9,
         email = $10,
         alipay_account = $11,
         wechat_account = $12
       WHERE id = $1
       RETURNING ${USER_PROFILE_SQL}`,
      [
        req.user.id,
        nickname,
        shippingName,
        shippingPhone,
        shippingDetail,
        gender,
        region,
        buddyId,
        signature,
        email,
        alipayAccount,
        wechatAccount,
      ],
    )
  ).rows[0]
  const profile = mapUserProfile(updated, req.user)
  res.json({
    user: profile,
    vocabNotebookId: Number(await ensureUserNotebook(req.user.id)),
    avatarUrl: profile.avatarUrl,
    level: profile.level,
  })
})

router.post('/me/network-region/refresh', authRequired, async (req, res) => {
  const ip = clientIp(req)
  const location = await resolveIpLocation(ip, { force: true })
  const updated = (
    await query(
      `UPDATE users SET last_ip = $2, last_ip_location = $3
       WHERE id = $1
       RETURNING ${USER_PROFILE_SQL}`,
      [req.user.id, ip || null, location],
    )
  ).rows[0]
  res.json({
    ok: true,
    user: mapUserProfile(updated, req.user),
    message: location ? `已更新为「${shortNetworkRegion(location)}」` : '暂时无法识别网络属地',
  })
})

function shortNetworkRegion(location) {
  if (!location) return null
  const text = String(location).trim()
  if (!text) return null
  if (text === '本地/内网' || text === '归属地未知') return text
  const main = text.split('·')[0].trim()
  const parts = main.split(/\s+/).filter(Boolean)
  if (parts[0] === '中国' || parts[0] === '中华人民共和国') {
    if (parts.length >= 3) return parts[2]
    if (parts.length >= 2) return parts[1]
  }
  return parts[parts.length - 1] || text
}

function mapUserProfile(row, fallbackUser = {}) {
  const level = Math.min(7, Math.max(0, Number.isFinite(Number(row?.user_level)) ? Number(row.user_level) : 0))
  const location = row?.last_ip_location || null
  return {
    id: Number(row?.id || fallbackUser.id),
    phone: row?.phone || fallbackUser.phone,
    avatarUrl: row?.avatar_url || null,
    level,
    nickname: row?.nickname || null,
    shippingName: row?.shipping_name || null,
    shippingPhone: row?.shipping_phone || null,
    shippingDetail: row?.shipping_detail || null,
    gender: row?.gender || null,
    region: row?.region || null,
    buddyId: row?.buddy_id || null,
    signature: row?.signature || null,
    email: row?.email || null,
    alipayAccount: row?.alipay_account || null,
    wechatAccount: row?.wechat_account || null,
    networkRegion: shortNetworkRegion(location),
    networkRegionDetail: location,
  }
}

router.get('/me/checkin', authRequired, async (req, res) => {
  const checkIn = await getUserCheckIn(req.user.id)
  res.json({ checkIn })
})

router.post('/me/checkin', authRequired, async (req, res) => {
  try {
    const result = await performUserCheckIn(req.user.id)
    if (result.already) {
      res.json({ ok: false, already: true, checkIn: result.state, error: '今天已经签到过了' })
      return
    }
    res.json({
      ok: true,
      already: false,
      pointsEarned: result.pointsEarned,
      streakDays: result.streakDays,
      totalPoints: result.totalPoints,
      checkIn: result.state,
    })
  } catch (error) {
    console.error('[me/checkin]', error)
    res.status(500).json({ error: '签到失败，请稍后重试' })
  }
})

router.post('/me/checkin/makeup', authRequired, async (req, res) => {
  try {
    const date = String(req.body?.date || '').trim()
    const result = await performMakeupCheckIn(req.user.id, date)
    if (!result.ok) {
      res.json({
        ok: false,
        already: !!result.already,
        error: result.error || '补签失败',
        checkIn: result.state,
      })
      return
    }
    res.json({
      ok: true,
      pointsEarned: result.pointsEarned,
      streakDays: result.streakDays,
      totalPoints: result.totalPoints,
      checkIn: result.state,
    })
  } catch (error) {
    console.error('[me/checkin/makeup]', error)
    res.status(500).json({ error: '补签失败，请稍后重试' })
  }
})

router.get('/gifts/categories', (_req, res) => {
  res.json({ items: GIFT_CATEGORIES })
})

router.get('/gifts', async (req, res) => {
  const page = Math.max(1, Number(req.query.page) || 1)
  const pageSize = Math.min(60, Math.max(1, Number(req.query.pageSize) || 40))
  const category = String(req.query.category || '').trim()
  const q = String(req.query.q || '').trim()
  const data = await listPublishedGifts({ category, q, page, pageSize })
  res.json(data)
})

router.get('/gifts/:id', async (req, res) => {
  const gift = await getGift(Number(req.params.id))
  if (!gift || !gift.published) {
    res.status(404).json({ error: '礼品不存在' })
    return
  }
  res.json({ item: gift })
})

router.post('/gifts/:id/redeem', authRequired, async (req, res) => {
  try {
    const result = await redeemGift(req.user.id, Number(req.params.id), {
      name: req.body?.name,
      phone: req.body?.phone,
      detail: req.body?.detail,
    })
    if (!result.ok) {
      res.status(400).json({ error: result.error })
      return
    }
    const checkIn = await getUserCheckIn(req.user.id)
    res.json({
      ok: true,
      message: result.message,
      order: result.order,
      totalPoints: result.totalPoints,
      checkIn,
    })
  } catch (error) {
    console.error('[gifts/redeem]', error)
    res.status(500).json({ error: '兑换失败，请稍后重试' })
  }
})

router.get('/me/gift-orders', authRequired, async (req, res) => {
  const page = Math.max(1, Number(req.query.page) || 1)
  const pageSize = Math.min(50, Math.max(1, Number(req.query.pageSize) || 20))
  const offset = (page - 1) * pageSize
  const total = (
    await query('SELECT count(*)::int AS n FROM gift_orders WHERE user_id = $1', [req.user.id])
  ).rows[0].n
  const result = await query(
    `SELECT * FROM gift_orders WHERE user_id = $1
     ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
    [req.user.id, pageSize, offset],
  )
  res.json({ items: result.rows.map(mapOrder), total, page, pageSize })
})

router.get('/me/withdrawals/config', authRequired, async (_req, res) => {
  res.json({ config: withdrawConfig() })
})

router.get('/me/withdrawals', authRequired, async (req, res) => {
  try {
    const data = await listWithdrawals(req.user.id, {
      page: req.query.page,
      pageSize: req.query.pageSize,
    })
    res.json(data)
  } catch (error) {
    console.error('[withdrawals/list]', error)
    res.status(500).json({ error: '加载提现记录失败' })
  }
})

router.post('/me/withdrawals', authRequired, async (req, res) => {
  try {
    const result = await createWithdrawal(req.user.id, {
      channel: req.body?.channel,
      account: req.body?.account,
    })
    if (!result.ok) {
      res.status(400).json({
        error: result.error,
        item: result.item || null,
        totalPoints: result.totalPoints,
      })
      return
    }
    const checkIn = await getUserCheckIn(req.user.id)
    res.json({
      ok: true,
      message: result.message,
      item: result.item,
      totalPoints: result.totalPoints,
      checkIn,
      config: result.config,
    })
  } catch (error) {
    console.error('[withdrawals/create]', error)
    res.status(500).json({ error: '提现失败，请稍后重试' })
  }
})

router.post('/me/avatar', authRequired, async (req, res) => {
  const raw = String(req.body?.imageBase64 || '').replace(/^data:image\/\w+;base64,/, '')
  if (!raw) {
    res.status(400).json({ error: '请选择一张图片' })
    return
  }
  let buf
  try {
    buf = Buffer.from(raw, 'base64')
  } catch {
    res.status(400).json({ error: '图片数据无效' })
    return
  }
  if (buf.length < 80 || buf.length > 1_800_000) {
    res.status(400).json({ error: '图片太大或已损坏' })
    return
  }
  const dir = path.join(uploadsRoot, 'avatars')
  fs.mkdirSync(dir, { recursive: true })
  const fileName = `${req.user.id}.jpg`
  fs.writeFileSync(path.join(dir, fileName), buf)
  const avatarUrl = `/uploads/avatars/${fileName}`
  await query('UPDATE users SET avatar_url = $1 WHERE id = $2', [avatarUrl, req.user.id])
  res.json({ avatarUrl, updatedAtMillis: Date.now() })
})

router.post('/auth/change-password', authRequired, async (req, res) => {
  const oldPassword = normalizePassword(req.body?.oldPassword)
  const newPassword = normalizePassword(req.body?.newPassword)
  if (!oldPassword) {
    res.status(400).json({ error: '请输入当前密码' })
    return
  }
  if (!newPassword) {
    res.status(400).json({ error: '新密码需要 6 到 32 位' })
    return
  }
  if (oldPassword === newPassword) {
    res.status(400).json({ error: '新密码不能与当前密码相同' })
    return
  }
  const user = (
    await query('SELECT id, phone, password_hash FROM users WHERE id = $1', [req.user.id])
  ).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  if (!user.password_hash || !verifyPassword(oldPassword, user.password_hash)) {
    res.status(400).json({ error: '当前密码不正确' })
    return
  }
  await query('UPDATE users SET password_hash = $1 WHERE id = $2', [hashPassword(newPassword), user.id])
  await recordPasswordEvent(req, user.id, 'change')
  res.json({ ok: true })
})

/** Re-check login password without rotating the session (used before change-phone). */
router.post('/auth/verify-password', authRequired, async (req, res) => {
  const password = normalizePassword(req.body?.password)
  if (!password) {
    res.status(400).json({ error: '请输入当前密码' })
    return
  }
  const user = (
    await query('SELECT id, password_hash FROM users WHERE id = $1', [req.user.id])
  ).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  if (!user.password_hash) {
    res.status(400).json({ error: '尚未设置密码，请先设置登录密码' })
    return
  }
  if (!verifyPassword(password, user.password_hash)) {
    res.status(400).json({ error: '当前密码不正确' })
    return
  }
  res.json({ ok: true })
})

/**
 * Change login phone: current password + SMS code sent to the new number.
 * Rotates session and returns a fresh JWT (phone claim updates).
 */
router.post('/auth/change-phone', authRequired, async (req, res) => {
  const password = normalizePassword(req.body?.password)
  const newPhone = normalizePhone(req.body?.newPhone ?? req.body?.phone)
  const code = String(req.body?.code || '').trim()
  if (!password) {
    res.status(400).json({ error: '请输入当前密码' })
    return
  }
  if (!newPhone) {
    res.status(400).json({ error: '请输入正确的新手机号' })
    return
  }
  const user = (
    await query('SELECT id, phone, password_hash FROM users WHERE id = $1', [req.user.id])
  ).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  if (!user.password_hash || !verifyPassword(password, user.password_hash)) {
    res.status(400).json({ error: '当前密码不正确' })
    return
  }
  if (newPhone === user.phone) {
    res.status(400).json({ error: '新手机号不能与当前号码相同' })
    return
  }
  const taken = (
    await query('SELECT id FROM users WHERE phone = $1 AND id <> $2 LIMIT 1', [newPhone, user.id])
  ).rows[0]
  if (taken) {
    res.status(400).json({ error: '该手机号已被其他账号使用' })
    return
  }
  const checked = await verifySmsCode(newPhone, code)
  if (!checked.ok) {
    res.status(400).json({ error: checked.error })
    return
  }
  try {
    await query('UPDATE users SET phone = $1 WHERE id = $2', [newPhone, user.id])
  } catch (error) {
    if (error?.code === '23505') {
      res.status(400).json({ error: '该手机号已被其他账号使用' })
      return
    }
    throw error
  }
  await consumeSms(checked.smsId)
  const sessionVersion = await rotateSessionVersion(user.id)
  const profileRow = await loadUserProfileRow(user.id)
  const profile = mapUserProfile(profileRow, { id: user.id, phone: newPhone })
  res.json({
    ok: true,
    token: signToken({ id: user.id, phone: newPhone }, sessionVersion),
    user: profile,
    vocabNotebookId: Number(await ensureUserNotebook(user.id)),
    avatarUrl: profile.avatarUrl,
    level: profile.level,
  })
})

/** Public catalog list (中考 / 高考 / CET-4 / CET-6) — no login required. */
router.get('/catalogs', async (_req, res) => {
  const result = await query(
    `
    SELECT n.id, n.kind, n.slug, n.name, n.sort_order, n.created_at,
           (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
    FROM notebooks n
    WHERE n.kind = 'catalog' AND n.published = TRUE
    ORDER BY n.sort_order ASC, n.id ASC
    `,
  )
  res.json({
    items: result.rows.map(mapNotebook),
  })
})

router.get('/notebooks', authRequired, async (req, res) => {
  await ensureUserNotebook(req.user.id)
  const result = await query(
    `
    SELECT n.id, n.kind, n.slug, n.name, n.sort_order, n.created_at,
           (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
    FROM notebooks n
    WHERE
      (n.kind = 'catalog' AND n.published = TRUE)
      OR
      (n.kind = 'user' AND n.owner_user_id = $1)
    ORDER BY n.kind DESC, n.sort_order ASC, n.id ASC
    `,
    [req.user.id],
  )
  res.json({
    items: result.rows.map(mapNotebook),
  })
})

function mapNotebook(row) {
  return {
    id: Number(row.id),
    kind: row.kind,
    slug: row.slug,
    name: row.name,
    sortOrder: row.sort_order,
    wordCount: row.word_count ?? 0,
    createdAtMillis: new Date(row.created_at).getTime(),
    isSystem: row.kind === 'catalog',
  }
}

router.post('/notebooks', authRequired, async (req, res) => {
  const name = String(req.body?.name || '').trim()
  if (!name) {
    res.status(400).json({ error: '请输入生词本名称' })
    return
  }
  if (name.length > 20) {
    res.status(400).json({ error: '名称最多 20 个字' })
    return
  }
  const dup = await query(
    `SELECT id FROM notebooks
     WHERE kind = 'user' AND owner_user_id = $1 AND lower(name) = lower($2)`,
    [req.user.id, name],
  )
  if (dup.rowCount > 0) {
    res.status(400).json({ error: '已有同名生词本' })
    return
  }
  const nextSort = (
    await query(
      `SELECT coalesce(min(sort_order), 0) - 1 AS n
       FROM notebooks WHERE kind = 'user' AND owner_user_id = $1`,
      [req.user.id],
    )
  ).rows[0].n
  const inserted = await query(
    `INSERT INTO notebooks (kind, owner_user_id, name, sort_order)
     VALUES ('user', $1, $2, $3)
     RETURNING id, kind, slug, name, sort_order, created_at`,
    [req.user.id, name, nextSort],
  )
  res.status(201).json({ item: mapNotebook({ ...inserted.rows[0], word_count: 0 }) })
})

router.delete('/notebooks/:id', authRequired, async (req, res) => {
  const notebook = await loadNotebook(Number(req.params.id))
  if (notebook?.kind === 'catalog') {
    res.status(403).json({ error: '系统词书不能删除' })
    return
  }
  if (!canWriteNotebook(notebook, req.user.id)) {
    res.status(403).json({ error: '不能删除该词本' })
    return
  }
  // Default「生词本」is a normal user notebook and may be deleted.
  // ensureUserNotebook recreates one when the user next needs a personal book.
  await query('DELETE FROM notebooks WHERE id = $1', [notebook.id])
  res.json({ ok: true })
})

async function loadNotebook(id) {
  const result = await query(
    `SELECT id, kind, slug, owner_user_id, name, published
     FROM notebooks WHERE id = $1`,
    [id],
  )
  return result.rows[0] || null
}

function canReadNotebook(notebook, userId) {
  if (!notebook) return false
  if (notebook.kind === 'catalog') return Boolean(notebook.published)
  if (userId == null) return false
  return Number(notebook.owner_user_id) === Number(userId)
}

function canWriteNotebook(notebook, userId) {
  return notebook?.kind === 'user' && Number(notebook.owner_user_id) === Number(userId)
}

/**
 * Precomputed A–Z / # → absolute list index (0-based) for the notebook.
 * Used by the client alphabet scrubber so seeks do not guess from a partial page.
 */
router.get('/notebooks/:id/letter-index', optionalAuth, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canReadNotebook(notebook, req.user?.id)) {
    res.status(notebook?.kind === 'catalog' ? 404 : 401).json({
      error: notebook?.kind === 'catalog' ? '词本不存在' : '未登录',
    })
    return
  }
  const total = (
    await query('SELECT count(*)::int AS n FROM words WHERE notebook_id = $1', [notebookId])
  ).rows[0].n
  const letterExpr = wordInitialLetterSql('word')
  const result = await query(
    `
    WITH ordered AS (
      SELECT word,
             (row_number() OVER (ORDER BY sort_order ASC, id ASC) - 1)::int AS idx
      FROM words
      WHERE notebook_id = $1
    )
    SELECT ${letterExpr} AS letter, MIN(idx)::int AS idx
    FROM ordered
    GROUP BY 1
    `,
    [notebookId],
  )
  const index = {}
  for (const row of result.rows) {
    index[row.letter] = row.idx
  }
  res.json({ index, total })
})

/**
 * Lightweight id+word list for in-memory alphabet / slider seeks.
 * Much smaller than full word pages (no definitions / examples).
 */
router.get('/notebooks/:id/heads', optionalAuth, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canReadNotebook(notebook, req.user?.id)) {
    res.status(notebook?.kind === 'catalog' ? 404 : 401).json({
      error: notebook?.kind === 'catalog' ? '词本不存在' : '未登录',
    })
    return
  }
  const result = await query(
    `SELECT id, word, is_phrase, ipa_uk, ipa_us, sort_order
     FROM words
     WHERE notebook_id = $1
     ORDER BY sort_order ASC, id ASC`,
    [notebookId],
  )
  const letterIndex = {}
  const items = result.rows.map((row, idx) => {
    const text = String(row.word || '')
    const ch = text.trim().charAt(0).toUpperCase()
    const letter = ch >= 'A' && ch <= 'Z' ? ch : '#'
    if (letterIndex[letter] === undefined) letterIndex[letter] = idx
    return {
      id: Number(row.id),
      text,
      isPhrase: Boolean(row.is_phrase),
      ipaUk: row.ipa_uk,
      ipaUs: row.ipa_us,
      sortOrder: row.sort_order,
    }
  })
  res.json({ items, total: items.length, index: letterIndex })
})

router.get('/notebooks/:id/words', optionalAuth, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canReadNotebook(notebook, req.user?.id)) {
    res.status(notebook?.kind === 'catalog' ? 404 : 401).json({
      error: notebook?.kind === 'catalog' ? '词本不存在' : '未登录',
    })
    return
  }
  const limit = Math.min(PAGE_SIZE_MAX, Math.max(1, Number(req.query.limit) || 50))
  const cursor = parseCursor(req.query.cursor)
  const fromIndexRaw = Number(req.query.fromIndex)
  const fromIndex =
    !cursor && Number.isFinite(fromIndexRaw) && fromIndexRaw > 0
      ? Math.floor(fromIndexRaw)
      : 0
  const total = cursor
    ? 0
    : (
        await query('SELECT count(*)::int AS n FROM words WHERE notebook_id = $1', [notebookId])
      ).rows[0].n

  let result
  if (fromIndex > 0) {
    result = await query(
      `SELECT * FROM words
       WHERE notebook_id = $1
       ORDER BY sort_order ASC, id ASC
       OFFSET $2 LIMIT $3`,
      [notebookId, fromIndex, limit + 1],
    )
  } else {
    const params = [notebookId]
    let where = 'notebook_id = $1'
    if (cursor) {
      params.push(cursor.sortOrder, cursor.wordId)
      where += ` AND (sort_order, id) > ($${params.length - 1}, $${params.length})`
    }
    params.push(limit + 1)
    result = await query(
      `SELECT * FROM words
       WHERE ${where}
       ORDER BY sort_order ASC, id ASC
       LIMIT $${params.length}`,
      params,
    )
  }
  const extra = result.rows.length > limit
  const rows = extra ? result.rows.slice(0, limit) : result.rows
  const last = rows[rows.length - 1]
  res.json({
    items: rows.map(mapWord),
    total,
    fromIndex,
    nextCursor: extra && last ? `${last.sort_order}:${last.id}` : null,
  })
})

router.post('/notebooks/:id/words', authRequired, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canWriteNotebook(notebook, req.user.id)) {
    res.status(403).json({ error: '系统词书不能修改' })
    return
  }
  const body = req.body || {}
  const text = String(body.text || '').trim()
  if (!text) {
    res.status(400).json({ error: '单词不能为空' })
    return
  }
  const existing = await query(
    'SELECT id FROM words WHERE notebook_id = $1 AND lower(word) = lower($2)',
    [notebookId, text],
  )
  if (existing.rowCount > 0) {
    const row = (
      await query('SELECT * FROM words WHERE id = $1', [existing.rows[0].id])
    ).rows[0]
    res.json({ item: mapWord(row), created: false })
    return
  }
  const maxSort = (
    await query('SELECT coalesce(min(sort_order), 0) - 1 AS n FROM words WHERE notebook_id = $1', [
      notebookId,
    ])
  ).rows[0].n
  const inserted = await query(
    `INSERT INTO words (
       notebook_id, word, is_phrase, ipa_uk, ipa_us,
       definitions, examples, near_words, synonyms, antonyms, sort_order
     ) VALUES (
       $1, $2, $3, $4, $5,
       $6::jsonb, $7::jsonb, $8::jsonb, $9::jsonb, $10::jsonb, $11
     ) RETURNING *`,
    [
      notebookId,
      text,
      Boolean(body.isPhrase || text.includes(' ')),
      body.ipaUk || null,
      body.ipaUs || null,
      JSON.stringify(body.definitions || []),
      JSON.stringify(
        (body.examples || []).map((e) => ({
          en: e.english ?? e.en ?? '',
          zh: e.chinese ?? e.zh ?? '',
        })),
      ),
      JSON.stringify(body.nearWords || []),
      JSON.stringify(body.synonyms || []),
      JSON.stringify(body.antonyms || []),
      maxSort,
    ],
  )
  res.status(201).json({ item: mapWord(inserted.rows[0]), created: true })
})

router.patch('/words/:id', authRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = await query(
    `SELECT w.*, n.kind, n.owner_user_id
     FROM words w JOIN notebooks n ON n.id = w.notebook_id
     WHERE w.id = $1`,
    [id],
  )
  const row = current.rows[0]
  if (!row || row.kind !== 'user' || Number(row.owner_user_id) !== req.user.id) {
    res.status(404).json({ error: '词条不存在' })
    return
  }
  const body = req.body || {}
  const updated = await query(
    `UPDATE words SET
       definitions = COALESCE($2::jsonb, definitions),
       examples = COALESCE($3::jsonb, examples),
       near_words = COALESCE($4::jsonb, near_words),
       synonyms = COALESCE($5::jsonb, synonyms),
       antonyms = COALESCE($6::jsonb, antonyms),
       ipa_uk = COALESCE($7, ipa_uk),
       ipa_us = COALESCE($8, ipa_us)
     WHERE id = $1
     RETURNING *`,
    [
      id,
      body.definitions ? JSON.stringify(body.definitions) : null,
      body.examples
        ? JSON.stringify(
            body.examples.map((e) => ({
              en: e.english ?? e.en ?? '',
              zh: e.chinese ?? e.zh ?? '',
            })),
          )
        : null,
      body.nearWords ? JSON.stringify(body.nearWords) : null,
      body.synonyms ? JSON.stringify(body.synonyms) : null,
      body.antonyms ? JSON.stringify(body.antonyms) : null,
      body.ipaUk ?? null,
      body.ipaUs ?? null,
    ],
  )
  res.json({ item: mapWord(updated.rows[0]) })
})

router.delete('/words/:id', authRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = await query(
    `SELECT w.id, n.kind, n.owner_user_id
     FROM words w JOIN notebooks n ON n.id = w.notebook_id
     WHERE w.id = $1`,
    [id],
  )
  const row = current.rows[0]
  if (row?.kind === 'catalog') {
    res.status(403).json({ error: '系统词书不能删除词条' })
    return
  }
  if (!row || row.kind !== 'user' || Number(row.owner_user_id) !== req.user.id) {
    res.status(404).json({ error: '词条不存在' })
    return
  }
  await query('DELETE FROM words WHERE id = $1', [id])
  res.json({ ok: true })
})

function normalizeWordKey(raw) {
  return String(raw ?? '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, ' ')
}

function mapHomophone(row, likedByMe = false, extras = {}) {
  return {
    id: Number(row.id),
    word: row.word_key,
    body: row.body,
    likeCount: Number(row.like_count) || 0,
    likedByMe: Boolean(likedByMe),
    authorUserId: Number(row.author_user_id),
    isMine: Boolean(extras.isMine),
    likers: Array.isArray(extras.likers) ? extras.likers : [],
  }
}

function maskPhone(phone) {
  const p = String(phone || '').trim()
  if (p.length >= 7) return `${p.slice(0, 3)}****${p.slice(-4)}`
  if (p) return p
  return '用户'
}

async function likersForHomophones(homophoneIds, authorUserId, perTipLimit = 3) {
  if (!authorUserId || !homophoneIds.length) return new Map()
  const rows = await query(
    `SELECT l.homophone_id, u.id AS user_id, u.phone, u.avatar_url, l.created_at
     FROM word_homophone_likes l
     JOIN users u ON u.id = l.user_id
     WHERE l.homophone_id = ANY($1::bigint[])
     ORDER BY l.created_at DESC`,
    [homophoneIds],
  )
  const map = new Map()
  for (const row of rows.rows) {
    const id = Number(row.homophone_id)
    const list = map.get(id) || []
    if (list.length >= perTipLimit) continue
    list.push({
      userId: Number(row.user_id),
      label: maskPhone(row.phone),
      avatarUrl: row.avatar_url || null,
    })
    map.set(id, list)
  }
  return map
}

/** Top liked 谐音助记 for a headword (shared across all notebooks). */
router.get('/homophones', optionalAuth, async (req, res) => {
  const wordKey = normalizeWordKey(req.query.word)
  if (!wordKey) {
    res.status(400).json({ error: '缺少单词' })
    return
  }
  const limit = Math.min(Math.max(Number(req.query.limit) || 3, 1), 10)
  const rows = await query(
    `SELECT id, word_key, body, author_user_id, like_count, created_at
     FROM word_homophones
     WHERE word_key = $1
     ORDER BY like_count DESC, id DESC
     LIMIT $2`,
    [wordKey, limit],
  )
  let liked = new Set()
  if (req.user?.id && rows.rows.length) {
    const ids = rows.rows.map((r) => r.id)
    const likedRows = await query(
      `SELECT homophone_id FROM word_homophone_likes
       WHERE user_id = $1 AND homophone_id = ANY($2::bigint[])`,
      [req.user.id, ids],
    )
    liked = new Set(likedRows.rows.map((r) => Number(r.homophone_id)))
  }
  const mineIds = req.user?.id
    ? rows.rows.filter((r) => Number(r.author_user_id) === req.user.id).map((r) => r.id)
    : []
  const likersMap = await likersForHomophones(mineIds, req.user?.id)
  res.json({
    word: wordKey,
    items: rows.rows.map((row) => {
      const id = Number(row.id)
      const isMine = req.user?.id != null && Number(row.author_user_id) === req.user.id
      return mapHomophone(row, liked.has(id), {
        isMine,
        likers: isMine ? likersMap.get(id) || [] : [],
      })
    }),
  })
})

/** Upsert current user's 谐音助记 for a word (one tip per user per word). */
router.post('/homophones', authRequired, async (req, res) => {
  const wordKey = normalizeWordKey(req.body?.word)
  const body = String(req.body?.body ?? '').trim()
  if (!wordKey) {
    res.status(400).json({ error: '缺少单词' })
    return
  }
  if (!body) {
    res.status(400).json({ error: '请输入谐音助记' })
    return
  }
  if (body.length > 120) {
    res.status(400).json({ error: '谐音助记太长（最多120字）' })
    return
  }
  const upserted = await query(
    `INSERT INTO word_homophones (word_key, body, author_user_id)
     VALUES ($1, $2, $3)
     ON CONFLICT (word_key, author_user_id)
     DO UPDATE SET body = EXCLUDED.body
     RETURNING id, word_key, body, author_user_id, like_count, created_at`,
    [wordKey, body, req.user.id],
  )
  const row = upserted.rows[0]
  const liked = await query(
    `SELECT 1 FROM word_homophone_likes WHERE homophone_id = $1 AND user_id = $2`,
    [row.id, req.user.id],
  )
  const likersMap = await likersForHomophones([row.id], req.user.id)
  res.status(201).json({
    item: mapHomophone(row, liked.rowCount > 0, {
      isMine: true,
      likers: likersMap.get(Number(row.id)) || [],
    }),
  })
})

/** Toggle like on a 谐音助记 tip. */
router.post('/homophones/:id/like', authRequired, async (req, res) => {
  const id = Number(req.params.id)
  if (!Number.isFinite(id) || id <= 0) {
    res.status(400).json({ error: '无效的助记' })
    return
  }
  const existing = await query(`SELECT id, word_key, body, author_user_id, like_count FROM word_homophones WHERE id = $1`, [id])
  if (!existing.rows[0]) {
    res.status(404).json({ error: '助记不存在' })
    return
  }
  const liked = await query(
    `SELECT 1 FROM word_homophone_likes WHERE homophone_id = $1 AND user_id = $2`,
    [id, req.user.id],
  )
  if (liked.rowCount > 0) {
    await query(`DELETE FROM word_homophone_likes WHERE homophone_id = $1 AND user_id = $2`, [id, req.user.id])
    await query(
      `UPDATE word_homophones SET like_count = GREATEST(like_count - 1, 0) WHERE id = $1`,
      [id],
    )
  } else {
    await query(
      `INSERT INTO word_homophone_likes (homophone_id, user_id) VALUES ($1, $2)
       ON CONFLICT DO NOTHING`,
      [id, req.user.id],
    )
    await query(`UPDATE word_homophones SET like_count = like_count + 1 WHERE id = $1`, [id])
  }
  const updated = await query(
    `SELECT id, word_key, body, author_user_id, like_count FROM word_homophones WHERE id = $1`,
    [id],
  )
  const nowLiked = await query(
    `SELECT 1 FROM word_homophone_likes WHERE homophone_id = $1 AND user_id = $2`,
    [id, req.user.id],
  )
  const row = updated.rows[0]
  const isMine = Number(row.author_user_id) === req.user.id
  const likersMap = isMine ? await likersForHomophones([id], req.user.id) : new Map()
  res.json({
    item: mapHomophone(row, nowLiked.rowCount > 0, {
      isMine,
      likers: isMine ? likersMap.get(id) || [] : [],
    }),
  })
})

/**
 * Paginated likers for a tip — only the author can browse the full list.
 * Preview in GET /homophones stays tiny (a few recent names); this is the detail view.
 */
router.get('/homophones/:id/likes', authRequired, async (req, res) => {
  const id = Number(req.params.id)
  if (!Number.isFinite(id) || id <= 0) {
    res.status(400).json({ error: '无效的助记' })
    return
  }
  const tip = await query(
    `SELECT id, author_user_id, like_count FROM word_homophones WHERE id = $1`,
    [id],
  )
  const row = tip.rows[0]
  if (!row) {
    res.status(404).json({ error: '助记不存在' })
    return
  }
  if (Number(row.author_user_id) !== req.user.id) {
    res.status(403).json({ error: '只能查看自己助记的点赞名单' })
    return
  }
  const limit = Math.min(Math.max(Number(req.query.limit) || 20, 1), 50)
  const offset = Math.max(Number(req.query.offset) || 0, 0)
  const total = Number(row.like_count) || 0
  const likes = await query(
    `SELECT u.id AS user_id, u.phone, u.avatar_url, l.created_at
     FROM word_homophone_likes l
     JOIN users u ON u.id = l.user_id
     WHERE l.homophone_id = $1
     ORDER BY l.created_at DESC
     LIMIT $2 OFFSET $3`,
    [id, limit, offset],
  )
  const items = likes.rows.map((r) => ({
    userId: Number(r.user_id),
    label: maskPhone(r.phone),
    avatarUrl: r.avatar_url || null,
  }))
  const nextOffset = offset + items.length < total ? offset + items.length : null
  res.json({ total, items, nextOffset })
})
