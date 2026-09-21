import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto'
import jwt from 'jsonwebtoken'
import { query } from './db.js'
import { extractDeviceInfo, isPrivateIp, normalizeIp, resolveIpLocation } from './device.js'
import {
  ACCOUNT_DELETED_CODE,
  ACCOUNT_DELETED_MESSAGE,
  finalizeDueAccountById,
} from './deletion.js'

const TOKEN_TTL = '30d'
export const SESSION_REPLACED_CODE = 'SESSION_REPLACED'
export const SESSION_REPLACED_MESSAGE = '账号已在其他设备登录'
export { ACCOUNT_DELETED_CODE, ACCOUNT_DELETED_MESSAGE }

export function signToken(user, sessionVersion = 0) {
  return jwt.sign(
    { sub: String(user.id), phone: user.phone, sv: Number(sessionVersion) || 0 },
    process.env.JWT_SECRET,
    { expiresIn: TOKEN_TTL },
  )
}

/** Bump session so any previously issued JWT becomes invalid. */
export async function rotateSessionVersion(userId) {
  const result = await query(
    `UPDATE users
     SET session_version = COALESCE(session_version, 0) + 1
     WHERE id = $1
     RETURNING session_version`,
    [userId],
  )
  return Number(result.rows[0]?.session_version || 1)
}

async function assertActiveSession(payload) {
  const userId = Number(payload.sub)
  if (!Number.isFinite(userId) || userId <= 0) {
    const err = new Error(SESSION_REPLACED_MESSAGE)
    err.code = SESSION_REPLACED_CODE
    throw err
  }
  const row = (
    await query('SELECT session_version, deletion_due_at FROM users WHERE id = $1', [userId])
  ).rows[0]
  if (!row) {
    const err = new Error(ACCOUNT_DELETED_MESSAGE)
    err.code = ACCOUNT_DELETED_CODE
    throw err
  }
  if (row.deletion_due_at && new Date(row.deletion_due_at).getTime() <= Date.now()) {
    await finalizeDueAccountById(userId)
    const err = new Error(ACCOUNT_DELETED_MESSAGE)
    err.code = ACCOUNT_DELETED_CODE
    throw err
  }
  const tokenSv = Number(payload.sv ?? 0)
  const dbSv = Number(row.session_version ?? 0)
  if (tokenSv !== dbSv) {
    const err = new Error(SESSION_REPLACED_MESSAGE)
    err.code = SESSION_REPLACED_CODE
    throw err
  }
  return { id: userId, phone: payload.phone }
}

export async function authRequired(req, res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) {
    res.status(401).json({ error: '未登录' })
    return
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET)
    req.user = await assertActiveSession(payload)
    next()
  } catch (error) {
    if (error?.code === SESSION_REPLACED_CODE) {
      res.status(401).json({ error: SESSION_REPLACED_MESSAGE, code: SESSION_REPLACED_CODE })
      return
    }
    if (error?.code === ACCOUNT_DELETED_CODE) {
      res.status(401).json({ error: ACCOUNT_DELETED_MESSAGE, code: ACCOUNT_DELETED_CODE })
      return
    }
    res.status(401).json({ error: '登录已过期' })
  }
}

/** Attach req.user when token is present and still the active session; otherwise guest. */
export async function optionalAuth(req, _res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) {
    req.user = null
    next()
    return
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET)
    req.user = await assertActiveSession(payload)
  } catch {
    req.user = null
  }
  next()
}

export async function ensureUserNotebook(userId) {
  // Always prefer the notebook literally named 生词本 — never the newest user book
  // (createNotebook inserts with min(sort_order)-1, which would otherwise steal this role).
  const named = await query(
    `SELECT id FROM notebooks
     WHERE kind = 'user' AND owner_user_id = $1 AND name = '生词本'
     ORDER BY id ASC
     LIMIT 1`,
    [userId],
  )
  if (named.rowCount > 0) return named.rows[0].id

  const created = await query(
    `INSERT INTO notebooks (kind, owner_user_id, name, sort_order)
     VALUES ('user', $1, '生词本', 0)
     RETURNING id`,
    [userId],
  )
  return created.rows[0].id
}

export function normalizePhone(raw) {
  const digits = String(raw || '').replace(/\D/g, '')
  if (!/^1[3-9]\d{9}$/.test(digits)) return null
  return digits
}

export function normalizePassword(raw) {
  const password = String(raw || '')
  if (password.length < 6 || password.length > 32) return null
  if (!password.trim()) return null
  return password
}

export function hashPassword(password) {
  const salt = randomBytes(16).toString('hex')
  const hash = scryptSync(password, salt, 64).toString('hex')
  return `${salt}:${hash}`
}

export function verifyPassword(password, stored) {
  if (!stored || !stored.includes(':')) return false
  const [salt, hash] = stored.split(':')
  const next = scryptSync(password, salt, 64)
  const prev = Buffer.from(hash, 'hex')
  if (next.length !== prev.length) return false
  return timingSafeEqual(next, prev)
}

export function clientIp(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '')
    .split(',')
    .map((part) => normalizeIp(part.trim()))
    .filter(Boolean)
  const realIp = normalizeIp(req.headers['x-real-ip'])
  const remote = normalizeIp(req.socket?.remoteAddress || '')
  const candidates = [...forwarded, realIp, remote]
  const publicIp = candidates.find((ip) => ip && !isPrivateIp(ip))
  return publicIp || remote || ''
}

export async function recordLoginEvent(req, { userId = null, phone, method, success }) {
  const device = extractDeviceInfo(req)
  const ip = clientIp(req)
  const ipLocation = await resolveIpLocation(ip)
  await query(
    `INSERT INTO login_events (
       user_id, phone, method, success, ip, user_agent,
       device_platform, device_brand, device_model, device_label, ip_location
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
    [
      userId,
      phone || null,
      method,
      Boolean(success),
      ip || null,
      device.userAgent || null,
      device.platform,
      device.brand,
      device.model,
      device.label,
      ipLocation,
    ],
  )
  if (success && userId) {
    await query(
      `UPDATE users
       SET last_login_at = now(),
           last_login_method = $2,
           login_count = login_count + 1,
           last_device_label = $3,
           last_device_platform = $4,
           last_ip = $5,
           last_ip_location = $6
       WHERE id = $1`,
      [userId, method, device.label, device.platform, ip || null, ipLocation],
    )
    await query(
      `INSERT INTO user_devices (
         user_id, device_key, platform, brand, model, label,
         os_version, app_version, last_ip, last_ip_location,
         first_seen_at, last_seen_at, login_count
       ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10, now(), now(), 1)
       ON CONFLICT (user_id, device_key) DO UPDATE SET
         platform = EXCLUDED.platform,
         brand = EXCLUDED.brand,
         model = EXCLUDED.model,
         label = EXCLUDED.label,
         os_version = COALESCE(EXCLUDED.os_version, user_devices.os_version),
         app_version = COALESCE(EXCLUDED.app_version, user_devices.app_version),
         last_ip = EXCLUDED.last_ip,
         last_ip_location = EXCLUDED.last_ip_location,
         last_seen_at = now(),
         login_count = user_devices.login_count + 1`,
      [
        userId,
        device.deviceKey,
        device.platform,
        device.brand,
        device.model,
        device.label,
        device.osVersion,
        device.appVersion,
        ip || null,
        ipLocation,
      ],
    )
  }
}

export async function recordPasswordEvent(req, userId, reason) {
  await query(
    `INSERT INTO password_events (user_id, reason, ip) VALUES ($1, $2, $3)`,
    [userId, reason, clientIp(req)],
  )
  await query('UPDATE users SET password_changed_at = now() WHERE id = $1', [userId])
}

export function signAdminToken(admin) {
  return jwt.sign(
    { sub: String(admin.id), username: admin.username, role: 'admin' },
    process.env.JWT_SECRET,
    { expiresIn: TOKEN_TTL },
  )
}

export function adminRequired(req, res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) {
    res.status(401).json({ error: '未登录管理后台' })
    return
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET)
    if (payload.role !== 'admin') {
      res.status(403).json({ error: '需要超级管理员权限' })
      return
    }
    req.admin = { id: Number(payload.sub), username: payload.username }
    next()
  } catch {
    res.status(401).json({ error: '管理登录已过期' })
  }
}

export async function ensureSuperAdmin() {
  const username = (process.env.ADMIN_USERNAME || 'admin').trim()
  const password = process.env.ADMIN_PASSWORD || 'changeme123'
  const existing = await query('SELECT id FROM admins WHERE username = $1', [username])
  if (existing.rowCount === 0) {
    await query('INSERT INTO admins (username, password_hash) VALUES ($1, $2)', [
      username,
      hashPassword(password),
    ])
    console.log(`[admin] seeded super admin "${username}"`)
  }
}
