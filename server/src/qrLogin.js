import crypto from 'node:crypto'

const tickets = new Map()
const TTL_MS = 3 * 60 * 1000

function prune(now) {
  for (const [id, row] of tickets) {
    if (row.expires <= now) tickets.delete(id)
  }
}

export function createQrTicket(channel, extra = {}) {
  const now = Date.now()
  prune(now)
  const id = crypto.randomBytes(24).toString('hex')
  tickets.set(id, {
    channel,
    status: 'pending',
    token: null,
    expires: now + TTL_MS,
    nonce: String(extra.nonce || ''),
  })
  return id
}

export function getQrTicket(id) {
  const row = tickets.get(String(id || ''))
  if (!row || row.expires <= Date.now()) return null
  return row
}

export function completeQrTicket(id, token) {
  const row = getQrTicket(id)
  if (!row || row.status !== 'pending' || !token) return false
  row.status = 'done'
  row.token = token
  return true
}

/** Pending stays open. A finished ticket is returned once, then forgotten. */
export function takeQrResult(id) {
  const key = String(id || '')
  const row = tickets.get(key)
  if (!row || row.expires <= Date.now()) {
    tickets.delete(key)
    return { status: 'expired' }
  }
  if (row.status !== 'done') return { status: 'pending' }
  const token = row.token
  tickets.delete(key)
  return { status: 'done', token }
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function scanDonePage(message) {
  const text = escapeHtml(message || '已登录，请回到电脑上的词搭子')
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>你的词搭子</title><body style="font-family:sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f5f7fb;color:#1f2329"><p>${text}</p></body></html>`
}

export function browserLoginPage(token) {
  const safe = JSON.stringify(String(token || ''))
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>正在登录</title><body style="font-family:sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f5f7fb;color:#1f2329"><p>正在回到词搭子…</p><script>localStorage.setItem('wb.token', ${safe});location.replace('/desk/#/books')</script></body></html>`
}
