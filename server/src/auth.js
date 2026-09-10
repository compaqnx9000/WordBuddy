import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto'
import jwt from 'jsonwebtoken'
import { query } from './db.js'
import { extractDeviceInfo, normalizeIp, resolveIpLocation } from './device.js'

const TOKEN_TTL = '30d'

export function signToken(user) {
  return jwt.sign(
    { sub: String(user.id), phone: user.phone },
    process.env.JWT_SECRET,
    { expiresIn: TOKEN_TTL },
  )
}

export function authRequired(req, res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) {
    res.status(401).json({ error: '未登录' })
    return
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET)
    req.user = { id: Number(payload.sub), phone: payload.phone }
    next()
  } catch {
    res.status(401).json({ error: '登录已过期' })
  }
}

/** Attach req.user when token is present; otherwise continue as guest. */
export function optionalAuth(req, _res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) {
    req.user = null
    next()
    return
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET)
    req.user = { id: Number(payload.sub), phone: payload.phone }
  } catch {
    req.user = null
  }
  next()
}

export async function ensureUserNotebook(userId) {
  const existing = await query(
    `SELECT id FROM notebooks
     WHERE kind = 'user' AND owner_user_id = $1
     ORDER BY sort_order ASC, id ASC
     LIMIT 1`,
    [userId],
  )
  if (existing.rowCount > 0) return existing.rows[0].id
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
    .split(',')[0]
    .trim()
  return normalizeIp(forwarded || req.socket?.remoteAddress || '')
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
