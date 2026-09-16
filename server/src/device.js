import { createHash } from 'node:crypto'

const geoCache = new Map()
const GEO_TTL_MS = 24 * 60 * 60 * 1000
const GEO_FAIL_TTL_MS = 30 * 1000

function clean(value, max = 80) {
  const text = String(value || '')
    .replace(/[\r\n\t]+/g, ' ')
    .trim()
  return text.slice(0, max)
}

function header(req, name) {
  const raw = req.headers[name.toLowerCase()]
  if (Array.isArray(raw)) return clean(raw[0])
  return clean(raw)
}

export function normalizeIp(ip) {
  let value = clean(ip, 64)
  if (value.startsWith('::ffff:')) value = value.slice(7)
  if (value === '::1') return '127.0.0.1'
  return value
}

export function isPrivateIp(ip) {
  const value = normalizeIp(ip)
  if (!value) return true
  if (value === '127.0.0.1' || value === 'localhost') return true
  if (value.startsWith('10.')) return true
  if (value.startsWith('192.168.')) return true
  if (/^172\.(1[6-9]|2\d|3[0-1])\./.test(value)) return true
  if (value.startsWith('fc') || value.startsWith('fd') || value.startsWith('fe80:')) return true
  return false
}

function parseUserAgentFallback(ua) {
  const text = clean(ua, 400)
  if (!text) return { platform: null, brand: null, model: null }
  if (/iPhone|iPad|iPod/i.test(text)) {
    const ios = text.match(/OS (\d+[_\.]\d+)/i)
    return {
      platform: 'iOS',
      brand: 'Apple',
      model: /iPad/i.test(text) ? 'iPad' : /iPod/i.test(text) ? 'iPod' : 'iPhone',
      osVersion: ios ? ios[1].replace('_', '.') : null,
    }
  }
  if (/Android/i.test(text)) {
    const android = text.match(/Android[ /]([\d.]+)/i)
    // HotWords/0.1.1 (Android 14; HUAWEI ELE-AL00; ...)
    const custom = text.match(/Android[^;]*;\s*([^;)]+)/i)
    let brand = null
    let model = null
    if (custom) {
      const parts = custom[1].trim().split(/\s+/)
      if (parts.length >= 2) {
        brand = parts[0]
        model = parts.slice(1).join(' ')
      } else {
        model = custom[1].trim()
      }
    }
    const brandHeader = text.match(/brand\/([^;\s)]+)/i)
    const modelHeader = text.match(/model\/([^;\s)]+)/i)
    const mfrHeader = text.match(/manufacturer\/([^;\s)]+)/i)
    return {
      platform: 'Android',
      brand: clean(brandHeader?.[1] || mfrHeader?.[1] || brand),
      model: clean(modelHeader?.[1] || model),
      osVersion: android ? android[1] : null,
    }
  }
  if (/Windows/i.test(text)) return { platform: 'Windows', brand: null, model: 'PC' }
  if (/Macintosh|Mac OS/i.test(text)) return { platform: 'macOS', brand: 'Apple', model: 'Mac' }
  if (/Linux/i.test(text)) return { platform: 'Linux', brand: null, model: null }
  return { platform: null, brand: null, model: null }
}

export function buildDeviceLabel({ platform, brand, manufacturer, model, osVersion }) {
  const parts = []
  const plat = clean(platform)
  const brandName = clean(brand || manufacturer)
  const modelName = clean(model, 120)
  if (plat) parts.push(plat)
  if (brandName && (!modelName || !modelName.toLowerCase().includes(brandName.toLowerCase()))) {
    parts.push(brandName)
  }
  if (modelName) parts.push(modelName)
  let label = parts.join(' · ')
  if (!label) label = '未知设备'
  if (osVersion && plat) {
    const osBit = `${plat} ${clean(osVersion, 20)}`
    if (!label.includes(osBit)) label = `${label} (${osBit})`
  }
  return label.slice(0, 160)
}

export function deviceKeyOf({ platform, brand, manufacturer, model }) {
  const raw = [
    clean(platform).toLowerCase(),
    clean(brand || manufacturer).toLowerCase(),
    clean(model).toLowerCase(),
  ].join('|')
  return createHash('sha1').update(raw || 'unknown').digest('hex').slice(0, 24)
}

export function extractDeviceInfo(req) {
  const ua = clean(req.headers['user-agent'], 400)
  const fallback = parseUserAgentFallback(ua)
  const platform = header(req, 'x-device-platform') || fallback.platform || null
  const brand = header(req, 'x-device-brand') || fallback.brand || null
  const manufacturer = header(req, 'x-device-manufacturer') || brand
  const model =
    header(req, 'x-device-model') ||
    header(req, 'x-device-product') ||
    fallback.model ||
    null
  const osVersion = header(req, 'x-device-os-version') || fallback.osVersion || null
  const appVersion = header(req, 'x-app-version') || null
  const label = buildDeviceLabel({ platform, brand, manufacturer, model, osVersion })
  return {
    platform,
    brand,
    manufacturer,
    model,
    osVersion,
    appVersion,
    label,
    userAgent: ua,
    deviceKey: deviceKeyOf({ platform, brand, manufacturer, model }),
  }
}

async function fetchJson(url, { timeoutMs = 2500, encoding } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'WordBuddy/1.0' },
    })
    if (!res.ok) return null
    if (encoding) {
      const buf = Buffer.from(await res.arrayBuffer())
      let text
      try {
        text = new TextDecoder(encoding).decode(buf)
      } catch {
        text = buf.toString('utf8')
      }
      const jsonStart = text.indexOf('{')
      return JSON.parse(jsonStart >= 0 ? text.slice(jsonStart) : text)
    }
    return await res.json()
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}

function formatLocation({ country, province, city, isp }) {
  const place = [...new Set([country, province, city].filter(Boolean))].join(' ')
  const ispBit = isp ? ` · ${isp}` : ''
  return clean(`${place}${ispBit}`, 160) || null
}

async function lookupIp9(ip) {
  const data = await fetchJson(`https://ip9.com.cn/get?ip=${encodeURIComponent(ip)}`)
  if (!data || Number(data.ret) !== 200 || !data.data) return null
  const row = data.data
  return formatLocation({
    country: row.country,
    province: row.prov || row.province,
    city: row.city,
    isp: row.isp,
  })
}

async function lookupPconline(ip) {
  const data = await fetchJson(
    `https://whois.pconline.com.cn/ipJson.jsp?ip=${encodeURIComponent(ip)}&json=true`,
    { encoding: 'gb18030' },
  )
  if (!data || data.err) return null
  const province = clean(data.pro)
  const city = clean(data.city)
  const addr = clean(data.addr, 120)
  const isp = addr && province && addr.includes(province)
    ? clean(addr.replace(province, '').replace(city, '').replace(/\s+/g, ' '))
    : ''
  return formatLocation({
    country: '中国',
    province,
    city,
    isp,
  })
}

async function lookupIpApi(ip) {
  const data = await fetchJson(
    `http://ip-api.com/json/${encodeURIComponent(ip)}?lang=zh-CN&fields=status,message,country,regionName,city,isp,query`,
  )
  if (!data || data.status !== 'success') return null
  return formatLocation({
    country: data.country,
    province: data.regionName,
    city: data.city,
    isp: data.isp,
  })
}

export async function resolveIpLocation(ip, { force = false } = {}) {
  const value = normalizeIp(ip)
  if (!value) return null
  if (isPrivateIp(value)) return '本地/内网'
  const cached = geoCache.get(value)
  const now = Date.now()
  if (!force && cached) {
    const ttl = cached.ok ? GEO_TTL_MS : GEO_FAIL_TTL_MS
    if (now - cached.at < ttl) return cached.value
  }
  const location =
    (await lookupIp9(value)) ||
    (await lookupPconline(value)) ||
    (await lookupIpApi(value))
  const resolved = location || '归属地未知'
  geoCache.set(value, { value: resolved, at: now, ok: Boolean(location) })
  if (geoCache.size > 5000) {
    const first = geoCache.keys().next().value
    geoCache.delete(first)
  }
  return resolved
}

export function mapDeviceRow(row) {
  return {
    id: Number(row.id),
    platform: row.platform || null,
    brand: row.brand || null,
    model: row.model || null,
    label: row.label || '未知设备',
    osVersion: row.os_version || null,
    appVersion: row.app_version || null,
    lastIp: row.last_ip || null,
    lastIpLocation: row.last_ip_location || null,
    firstSeenAt: row.first_seen_at ? new Date(row.first_seen_at).toISOString() : null,
    lastSeenAt: row.last_seen_at ? new Date(row.last_seen_at).toISOString() : null,
    loginCount: Number(row.login_count || 0),
  }
}
