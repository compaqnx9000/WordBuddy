const lettersOnly = /^[a-z]+$/

function enc(value: string): string {
  return encodeURIComponent(value)
}

async function httpGet(url: string): Promise<string> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`http ${res.status}`)
  return res.text()
}

function freqFromTags(tags?: string[]): number {
  if (!tags) return 0
  for (const tag of tags) {
    if (tag.startsWith('f:')) return Number(tag.slice(2)) || 0
  }
  return 0
}

function parseScored(raw: string): [string, number][] {
  const array = JSON.parse(raw) as { word?: string; tags?: string[] }[]
  return array
    .map((obj) => {
      const word = (obj.word ?? '').trim().toLowerCase()
      if (!word) return null
      return [word, freqFromTags(obj.tags)] as [string, number]
    })
    .filter((x): x is [string, number] => x != null)
}

async function scoredExact(sp: string): Promise<[string, number][]> {
  const raw = await httpGet(`/api/datamuse/words?sp=${enc(sp)}&md=f&max=6`).catch(() => null)
  if (!raw) return []
  return parseScored(raw).filter(([w]) => w === sp)
}

async function scoredPattern(pattern: string): Promise<[string, number][]> {
  const raw = await httpGet(
    `/api/datamuse/words?sp=${enc(pattern)}&md=f&max=12`,
  ).catch(() => null)
  if (!raw) return []
  return parseScored(raw)
}

export function editDistanceOne(a: string, b: string): boolean {
  if (Math.abs(a.length - b.length) > 1) return false
  if (a.length === b.length) {
    let diff = 0
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i] && ++diff > 1) return false
    }
    return diff === 1
  }
  const shorter = a.length < b.length ? a : b
  const longer = a.length < b.length ? b : a
  let si = 0
  let li = 0
  let skipped = 0
  while (si < shorter.length && li < longer.length) {
    if (shorter[si] === longer[li]) {
      si++
      li++
    } else {
      if (++skipped > 1) return false
      li++
    }
  }
  skipped += longer.length - li
  return skipped === 1 && si === shorter.length
}

function isInflectionOf(base: string, other: string): boolean {
  const forms = new Set<string>([`${base}s`, `${base}es`, `${base}ed`, `${base}ing`])
  if (base.endsWith('e')) {
    forms.add(`${base}d`)
    forms.add(`${base.slice(0, -1)}ing`)
  }
  if (base.endsWith('y') && base.length > 1 && !'aeiou'.includes(base[base.length - 2])) {
    forms.add(`${base.slice(0, -1)}ies`)
    forms.add(`${base.slice(0, -1)}ied`)
  }
  return forms.has(other)
}

/** Lighter than Android: sample insert/sub positions to keep H5 snappy. */
export async function findNearWords(word: string, min = 2, max = 5): Promise<string[]> {
  const q = word.trim().toLowerCase()
  if (q.length < 2 || !lettersOnly.test(q)) return []

  const jobs: Promise<[string, number][]>[] = []

  for (let i = 0; i < q.length; i++) {
    const shorter = q.slice(0, i) + q.slice(i + 1)
    if (shorter.length >= 2) jobs.push(scoredExact(shorter))
  }

  // insert: sample every other position + ends
  for (let i = 0; i <= q.length; i++) {
    if (i !== 0 && i !== q.length && i % 2 === 1) continue
    jobs.push(scoredPattern(q.slice(0, i) + '?' + q.slice(i)))
  }

  // substitute: sample every other letter
  for (let i = 0; i < q.length; i++) {
    if (i % 2 === 1 && q.length > 4) continue
    jobs.push(scoredPattern(q.slice(0, i) + '?' + q.slice(i + 1)))
  }

  const rows = (await Promise.all(jobs)).flat()
  const best = new Map<string, number>()
  for (const [w, freq] of rows) {
    if (
      w === q ||
      !lettersOnly.test(w) ||
      !editDistanceOne(q, w) ||
      isInflectionOf(q, w) ||
      (freq < 0.05 && w.length > q.length + 1)
    ) {
      continue
    }
    best.set(w, Math.max(best.get(w) ?? 0, freq))
  }

  return [...best.entries()]
    .sort((a, b) => {
      if (b[1] !== a[1]) return b[1] - a[1]
      const da = Math.abs(a[0].length - q.length)
      const db = Math.abs(b[0].length - q.length)
      if (da !== db) return da - db
      if (a[0].length !== b[0].length) return a[0].length - b[0].length
      return a[0].localeCompare(b[0])
    })
    .map(([w]) => w)
    .slice(0, max)
    .filter((_, __, arr) => arr.length >= min || true)
}
