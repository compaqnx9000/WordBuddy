import type { ExampleSentence, VocabEntry } from '../types'
import { examplesFor } from './senseExamples'

const POS_PREFIX = /^([a-z]+\.)\s*(.+)$/i

function enc(value: string): string {
  return encodeURIComponent(value)
}

async function httpGet(url: string): Promise<string> {
  const res = await fetch(url, {
    headers: { Accept: 'application/json,text/plain,*/*' },
  })
  if (!res.ok) throw new Error(`http ${res.status}`)
  return res.text()
}

function jsonObjOrFirst(parent: Record<string, unknown> | null | undefined, key: string): Record<string, unknown> | null {
  if (!parent || !(key in parent) || parent[key] == null) return null
  const value = parent[key]
  if (typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>
  if (Array.isArray(value) && value[0] && typeof value[0] === 'object') {
    return value[0] as Record<string, unknown>
  }
  return null
}

function cleanExampleText(raw: string): string {
  return raw
    .replace(/<\/?b>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim()
}

function looksLikeProperNameExample(eng: string, zh: string): boolean {
  if (zh.includes('人名')) return true
  return /^[A-Z][a-z]+ [A-Z][a-z]+,/.test(eng)
}

function everydayScore(example: ExampleSentence): number {
  const eng = example.english
  let score = 0
  if (eng.length > 90) score += 3
  if (eng.length > 60) score += 1
  if (eng.includes(',') && eng.length > 50) score += 1
  const formalHints = ['whereas', 'hereby', 'thereof', 'aforesaid', 'pursuant']
  if (formalHints.some((h) => eng.toLowerCase().includes(h))) score += 4
  return score
}

function extractPlainText(value: unknown): string | null {
  if (typeof value === 'string') return value.trim() || null
  if (Array.isArray(value)) {
    const parts = value.map((v) => String(v).trim()).filter(Boolean)
    return parts.join(' ') || null
  }
  return null
}

function extractPlainTextList(value: unknown): string[] {
  if (typeof value === 'string') return value.trim() ? [value.trim()] : []
  if (Array.isArray(value)) {
    return value.map((v) => String(v).trim()).filter(Boolean)
  }
  return []
}

function splitPosMeaning(raw: string) {
  const match = POS_PREFIX.exec(raw.trim())
  if (match) return { pos: match[1], meaning: match[2].trim(), isUserAdded: false }
  return { pos: '', meaning: raw.trim(), isUserAdded: false }
}

function parseTrs(trs: unknown): { pos: string; meaning: string; isUserAdded: boolean }[] {
  if (!Array.isArray(trs)) return []
  const out: { pos: string; meaning: string; isUserAdded: boolean }[] = []
  for (const item of trs) {
    if (!item || typeof item !== 'object') continue
    const obj = item as Record<string, unknown>
    const tran = String(obj.tran ?? '').trim()
    if (tran) {
      out.push({ pos: String(obj.pos ?? '').trim(), meaning: tran, isUserAdded: false })
      continue
    }
    const trArray = obj.tr
    if (!Array.isArray(trArray)) continue
    for (const tr of trArray) {
      if (!tr || typeof tr !== 'object') continue
      const payload = (tr as Record<string, unknown>).l
      const i =
        payload && typeof payload === 'object'
          ? (payload as Record<string, unknown>).i
          : undefined
      for (const line of extractPlainTextList(i)) {
        out.push(splitPosMeaning(line))
      }
    }
  }
  return out
}

function parseReturnPhrase(word: Record<string, unknown>): string | null {
  if (!('return-phrase' in word) || word['return-phrase'] == null) return null
  const value = word['return-phrase']
  if (typeof value === 'string') return value.trim() || null
  if (typeof value === 'object' && value) {
    const l = (value as Record<string, unknown>).l
    const i =
      l && typeof l === 'object' ? (l as Record<string, unknown>).i : l
    return extractPlainText(i)
  }
  return null
}

function parseYoudao(root: Record<string, unknown>, fallback: string): VocabEntry | null {
  const wordObj =
    jsonObjOrFirst(jsonObjOrFirst(root, 'ec'), 'word') ??
    jsonObjOrFirst(jsonObjOrFirst(root, 'simple'), 'word')
  if (!wordObj) return null
  const text = parseReturnPhrase(wordObj) ?? fallback
  const uk = String(wordObj.ukphone ?? '').trim() || null
  const us = String(wordObj.usphone ?? '').trim() || null
  const defs = parseTrs(wordObj.trs)
  if (defs.length === 0 && !uk && !us) return null
  return {
    id: 0,
    text,
    isPhrase: text.includes(' '),
    ipaUk: uk,
    ipaUs: us,
    definitions: defs.length ? defs : [{ pos: '', meaning: text }],
    examples: [],
    nearWords: [],
    synonyms: [],
    antonyms: [],
    sortOrder: 0,
    addedAtMillis: Date.now(),
  }
}

function collectCorpusExamples(root: Record<string, unknown> | null): ExampleSentence[] {
  if (!root) return []
  const candidates: ExampleSentence[] = []

  const pairs = (root.blng_sents_part as Record<string, unknown> | undefined)?.['sentence-pair']
  if (Array.isArray(pairs)) {
    for (const item of pairs) {
      if (!item || typeof item !== 'object') continue
      const obj = item as Record<string, unknown>
      const eng = cleanExampleText(
        String(obj.sentence || obj['sentence-eng'] || ''),
      )
      const zh = cleanExampleText(String(obj['sentence-translation'] || ''))
      if (eng && zh && !looksLikeProperNameExample(eng, zh)) {
        candidates.push({ english: eng, chinese: zh })
      }
    }
  }

  const sents = (root.auth_sents_part as Record<string, unknown> | undefined)?.sent
  if (Array.isArray(sents)) {
    for (const item of sents) {
      if (!item || typeof item !== 'object') continue
      const obj = item as Record<string, unknown>
      const eng = cleanExampleText(String(obj.foreign || obj.speech || ''))
      const zh = cleanExampleText(String(obj.translation || ''))
      if (eng && zh && !looksLikeProperNameExample(eng, zh)) {
        candidates.push({ english: eng, chinese: zh })
      }
    }
  }

  const seen = new Set<string>()
  return candidates
    .filter((e) => {
      const key = e.english.toLowerCase()
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .sort((a, b) => {
      const d = everydayScore(a) - everydayScore(b)
      return d !== 0 ? d : a.english.length - b.english.length
    })
}

function parseBaiduSug(raw: string, query: string): VocabEntry | null {
  const root = JSON.parse(raw) as { data?: { k: string; v: string }[] }
  const data = root.data
  if (!Array.isArray(data) || data.length === 0) return null
  let meaning =
    data.find((d) => d.k.toLowerCase() === query.toLowerCase())?.v?.trim() ??
    data[0]?.v?.trim()
  if (!meaning) return null
  return {
    id: 0,
    text: query,
    isPhrase: query.includes(' '),
    definitions: [{ pos: '', meaning }],
    examples: [],
    nearWords: [],
    synonyms: [],
    antonyms: [],
    sortOrder: 0,
    addedAtMillis: Date.now(),
  }
}

function isSingleCommonLemma(word: string): boolean {
  const w = word.trim().toLowerCase()
  if (w.length < 2 || w.length > 14) return false
  if (w.includes(' ')) return false
  return /^[a-z-]+$/.test(w)
}

function parseDatamuseScored(raw: string): [string, number][] {
  const array = JSON.parse(raw) as { word?: string; tags?: string[] }[]
  const out: [string, number][] = []
  for (const obj of array) {
    const w = (obj.word ?? '').trim().toLowerCase()
    if (!isSingleCommonLemma(w)) continue
    let freq = 0
    for (const t of obj.tags ?? []) {
      if (t.startsWith('f:')) freq = Number(t.slice(2)) || 0
    }
    out.push([w, freq])
  }
  return out
}

async function datamuseRelatedScored(rel: string, word: string): Promise<[string, number][]> {
  const q = word.trim().toLowerCase()
  if (q.length < 2 || q.includes(' ')) return []
  const raw = await httpGet(`/api/datamuse/words?${rel}=${enc(q)}&md=f&max=20`).catch(() => null)
  if (!raw) return []
  return parseDatamuseScored(raw)
}

async function datamuseTagged(word: string, tag: string): Promise<[string, number][]> {
  const q = word.trim().toLowerCase()
  if (q.length < 2 || q.includes(' ')) return []
  const raw = await httpGet(`/api/datamuse/words?ml=${enc(q)}&md=f&max=25`).catch(() => null)
  if (!raw) return []
  const array = JSON.parse(raw) as { word?: string; tags?: string[] }[]
  const out: [string, number][] = []
  for (const obj of array) {
    const tags = obj.tags ?? []
    if (!tags.includes(tag)) continue
    let freq = 0
    for (const t of tags) {
      if (t.startsWith('f:')) freq = Number(t.slice(2)) || 0
    }
    const w = (obj.word ?? '').trim().toLowerCase()
    if (isSingleCommonLemma(w)) out.push([w, freq])
  }
  return out
}

function parseYoudaoSynonyms(root: Record<string, unknown> | null): string[] {
  if (!root) return []
  const synos = (root.syno as Record<string, unknown> | undefined)?.synos
  if (!Array.isArray(synos)) return []
  const words = new Set<string>()
  for (const item of synos) {
    if (!item || typeof item !== 'object') continue
    const ws = ((item as Record<string, unknown>).syno as Record<string, unknown> | undefined)?.ws
    if (!Array.isArray(ws)) continue
    for (const wObj of ws) {
      if (!wObj || typeof wObj !== 'object') continue
      const w = String((wObj as Record<string, unknown>).w ?? '').trim()
      if (isSingleCommonLemma(w)) words.add(w.toLowerCase())
    }
  }
  return [...words]
}

function mergeCommonWords(
  primary: string[],
  scored: [string, number][],
  exclude: string,
): string[] {
  const out = new Set<string>()
  for (const w of primary) {
    const lw = w.toLowerCase()
    if (isSingleCommonLemma(lw) && lw !== exclude.toLowerCase()) out.add(lw)
  }
  for (const [w, freq] of [...scored].sort((a, b) => b[1] - a[1])) {
    if (out.size >= 5) break
    if (freq < 0.3 && out.size >= 2) break
    const lw = w.toLowerCase()
    if (isSingleCommonLemma(lw) && lw !== exclude.toLowerCase()) out.add(lw)
  }
  return [...out].slice(0, 5)
}

async function loadSynonyms(
  root: Record<string, unknown> | null,
  word: string,
): Promise<string[]> {
  let scored = await datamuseRelatedScored('rel_syn', word)
  if (!scored.length) scored = await datamuseTagged(word, 'syn')
  const primary = scored.sort((a, b) => b[1] - a[1]).map(([w]) => w)
  const fromYoudao = parseYoudaoSynonyms(root)
  return mergeCommonWords(
    primary,
    fromYoudao.map((w) => [w, 1] as [string, number]),
    word,
  )
}

async function loadCommonAntonyms(word: string): Promise<string[]> {
  const fromAnt = await datamuseRelatedScored('rel_ant', word)
  const fromMlAnt = await datamuseTagged(word, 'ant')
  return mergeCommonWords(
    fromAnt.sort((a, b) => b[1] - a[1]).map(([w]) => w),
    fromMlAnt,
    word,
  )
}

export async function lookupWord(query: string): Promise<VocabEntry> {
  const q = query.trim()
  if (!q) throw new Error('empty query')

  let youdaoRoot: Record<string, unknown> | null = null
  let fromYoudao: VocabEntry | null = null
  try {
    const raw = await httpGet(`/api/youdao/jsonapi?q=${enc(q)}&doctype=json`)
    youdaoRoot = JSON.parse(raw) as Record<string, unknown>
    fromYoudao = parseYoudao(youdaoRoot, q)
  } catch {
    fromYoudao = null
  }

  let base: VocabEntry
  if (fromYoudao && fromYoudao.definitions.length > 0) {
    base = fromYoudao
  } else {
    let fromBaidu: VocabEntry | null = null
    try {
      fromBaidu = parseBaiduSug(await httpGet(`/api/baidu/sug?kw=${enc(q)}`), q)
    } catch {
      fromBaidu = null
    }
    if (!fromBaidu) throw new Error('查不到释义')
    base = {
      ...fromBaidu,
      ipaUk: fromYoudao?.ipaUk,
      ipaUs: fromYoudao?.ipaUs,
    }
  }

  const [synonyms, antonyms] = await Promise.all([
    loadSynonyms(youdaoRoot, base.text),
    loadCommonAntonyms(base.text),
  ])
  const corpus = collectCorpusExamples(youdaoRoot)
  const examples = examplesFor(base.text, base.definitions, corpus)

  return {
    ...base,
    examples,
    synonyms,
    antonyms,
  }
}
