/**
 * In-memory sliding-window rate limiter.
 *
 * Single-process only: this guards against SMS cost blowouts and credential
 * stuffing from one host, not a distributed attack. Move to Redis if the API
 * is ever load balanced across machines.
 */

const buckets = new Map()
const SWEEP_EVERY_MS = 5 * 60 * 1000
let lastSweep = Date.now()

function sweep(now) {
  if (now - lastSweep < SWEEP_EVERY_MS) return
  lastSweep = now
  for (const [key, hits] of buckets) {
    if (!hits.length || now - hits[hits.length - 1] > SWEEP_EVERY_MS) buckets.delete(key)
  }
}

/**
 * Records one attempt for [key]. Returns { ok, retryAfterSec }.
 * Call only when the attempt should count against the quota.
 */
export function hit(key, { limit, windowMs }) {
  const now = Date.now()
  sweep(now)
  const hits = (buckets.get(key) || []).filter((at) => now - at < windowMs)
  if (hits.length >= limit) {
    buckets.set(key, hits)
    const retryAfterMs = windowMs - (now - hits[0])
    return { ok: false, retryAfterSec: Math.max(1, Math.ceil(retryAfterMs / 1000)) }
  }
  hits.push(now)
  buckets.set(key, hits)
  return { ok: true, retryAfterSec: 0 }
}

/** Forgets the quota for [key], e.g. after a successful login. */
export function reset(key) {
  buckets.delete(key)
}

/** Replies 429 and returns true when [key] is over quota. */
export function limited(res, key, { limit, windowMs, message }) {
  const result = hit(key, { limit, windowMs })
  if (result.ok) return false
  res.set('Retry-After', String(result.retryAfterSec))
  res.status(429).json({ error: message, retryAfterSec: result.retryAfterSec })
  return true
}
