import crypto from 'node:crypto'
import jwt from 'jsonwebtoken'

const APPLE_ISSUER = 'https://appleid.apple.com'
const APPLE_KEYS_URL = 'https://appleid.apple.com/auth/keys'
const APPLE_AUDIENCE = 'com.hotgis.wordbuddy.ios'

let keyCache = { at: 0, keys: [] }

async function fetchKeys(force = false) {
  if (!force && keyCache.keys.length && Date.now() - keyCache.at < 60 * 60 * 1000) {
    return keyCache.keys
  }
  const response = await fetch(APPLE_KEYS_URL)
  if (!response.ok) {
    throw new Error(`apple keys ${response.status}`)
  }
  const body = await response.json()
  const keys = Array.isArray(body.keys) ? body.keys : []
  keyCache = { at: Date.now(), keys }
  return keys
}

export async function verifyAppleIdentityToken(identityToken, rawNonce) {
  const token = String(identityToken || '').trim()
  const nonce = String(rawNonce || '').trim()
  if (!token || nonce.length < 8 || nonce.length > 128) {
    return { ok: false, error: 'Apple 登录凭证无效' }
  }
  const decoded = jwt.decode(token, { complete: true })
  const kid = decoded?.header?.kid
  if (!kid || decoded.header.alg !== 'RS256') {
    return { ok: false, error: 'Apple 登录凭证无效' }
  }
  let keys = await fetchKeys()
  let jwk = keys.find((item) => item.kid === kid)
  if (!jwk) {
    keys = await fetchKeys(true)
    jwk = keys.find((item) => item.kid === kid)
  }
  if (!jwk) {
    return { ok: false, error: 'Apple 登录凭证无效' }
  }
  const key = crypto.createPublicKey({ key: jwk, format: 'jwk' })
  let payload
  try {
    payload = jwt.verify(token, key, {
      algorithms: ['RS256'],
      issuer: APPLE_ISSUER,
      audience: APPLE_AUDIENCE,
    })
  } catch {
    return { ok: false, error: 'Apple 登录已失效，请重试' }
  }
  const expected = crypto.createHash('sha256').update(nonce).digest('hex')
  if (payload.nonce !== expected) {
    return { ok: false, error: 'Apple 登录凭证无效' }
  }
  const sub = String(payload.sub || '').trim()
  if (!sub) {
    return { ok: false, error: 'Apple 登录凭证无效' }
  }
  const email = typeof payload.email === 'string' ? payload.email.trim() : ''
  return { ok: true, sub, email }
}
