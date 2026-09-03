/**
 * Batch-enrich CET-4 / CET-6 wordbooks with the same pipeline as in-app lookup:
 * Youdao (+ Baidu fallback) defs/IPA/examples, Datamuse synonyms/antonyms,
 * and edit-distance-1 near-words scored by a local frequency lexicon
 * (same filters as NearWordsFinder, without per-word Datamuse fan-out).
 *
 * Usage:
 *   node scripts/enrich-wordbooks.mjs
 *   node scripts/enrich-wordbooks.mjs --concurrency 6
 *   node scripts/enrich-wordbooks.mjs --limit 20   # smoke test
 *
 * Writes:
 *   tmp_dict/enrich_cache.jsonl          (resumable)
 *   app/src/main/assets/wordbooks/zhongkao.enriched.jsonl
 *   app/src/main/assets/wordbooks/gaokao.enriched.jsonl
 *   app/src/main/assets/wordbooks/cet4.enriched.jsonl
 *   app/src/main/assets/wordbooks/cet6.enriched.jsonl
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const ASSETS = path.join(ROOT, 'app/src/main/assets/wordbooks')
const TMP = path.join(ROOT, 'tmp_dict')
const CACHE_PATH = path.join(TMP, 'enrich_cache.jsonl')
const FREQ_PATH = path.join(TMP, 'en_50k.txt')
const FREQ_URL =
  'https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt'

const args = process.argv.slice(2)
function argNum(name, fallback) {
  const i = args.indexOf(name)
  if (i >= 0 && args[i + 1]) return Number(args[i + 1])
  return fallback
}
const CONCURRENCY = Math.max(1, argNum('--concurrency', 5))
const LIMIT = argNum('--limit', 0)
const DELAY_MS = argNum('--delay', 80)
const BOOKS_ARG = (() => {
  const i = args.indexOf('--books')
  if (i >= 0 && args[i + 1]) {
    return new Set(
      args[i + 1]
        .split(',')
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean),
    )
  }
  return null
})()
function wantBook(name) {
  return !BOOKS_ARG || BOOKS_ARG.has(name)
}

const POS_PREFIX = /^([a-z]+\.)\s*(.+)$/i
const lettersOnly = /^[a-z]+$/
const nameSense = /人名|【名】|（名）|\(名\)|姓氏|地名|河名|山名/
const senseSplit = /[；;]/
const tagStrip = /<[^>]+>/g
const parenNoise = /[（(][^）)]*[）)]/g

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

function enc(value) {
  return encodeURIComponent(value)
}

async function httpGet(url, retries = 3) {
  let lastErr
  for (let attempt = 0; attempt < retries; attempt++) {
    try {
      const res = await fetch(url, {
        headers: {
          Accept: 'application/json,text/plain,*/*',
          'User-Agent': 'Mozilla/5.0 (compatible; HotWordsEnrich/1.0)',
        },
        signal: AbortSignal.timeout(15000),
      })
      if (!res.ok) throw new Error(`http ${res.status}`)
      return await res.text()
    } catch (e) {
      lastErr = e
      await sleep(400 * (attempt + 1))
    }
  }
  throw lastErr
}

function jsonObjOrFirst(parent, key) {
  if (!parent || parent[key] == null) return null
  const value = parent[key]
  if (typeof value === 'object' && !Array.isArray(value)) return value
  if (Array.isArray(value) && value[0] && typeof value[0] === 'object') return value[0]
  return null
}

function cleanExampleText(raw) {
  return String(raw || '')
    .replace(/<\/?b>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim()
}

function looksLikeProperNameExample(eng, zh) {
  if (zh.includes('人名')) return true
  return /^[A-Z][a-z]+ [A-Z][a-z]+,/.test(eng)
}

function everydayScore(example) {
  const eng = example.english
  let score = 0
  if (eng.length > 90) score += 3
  if (eng.length > 60) score += 1
  if (eng.includes(',') && eng.length > 50) score += 1
  const formalHints = ['whereas', 'hereby', 'thereof', 'aforesaid', 'pursuant']
  if (formalHints.some((h) => eng.toLowerCase().includes(h))) score += 4
  return score
}

function extractPlainText(value) {
  if (typeof value === 'string') return value.trim() || null
  if (Array.isArray(value)) {
    const parts = value.map((v) => String(v).trim()).filter(Boolean)
    return parts.join(' ') || null
  }
  return null
}

function extractPlainTextList(value) {
  if (typeof value === 'string') return value.trim() ? [value.trim()] : []
  if (Array.isArray(value)) return value.map((v) => String(v).trim()).filter(Boolean)
  return []
}

function splitPosMeaning(raw) {
  const match = POS_PREFIX.exec(raw.trim())
  if (match) return { pos: match[1], meaning: match[2].trim() }
  return { pos: '', meaning: raw.trim() }
}

function parseTrs(trs) {
  if (!Array.isArray(trs)) return []
  const out = []
  for (const item of trs) {
    if (!item || typeof item !== 'object') continue
    const tran = String(item.tran ?? '').trim()
    if (tran) {
      out.push({ pos: String(item.pos ?? '').trim(), meaning: tran })
      continue
    }
    const trArray = item.tr
    if (!Array.isArray(trArray)) continue
    for (const tr of trArray) {
      if (!tr || typeof tr !== 'object') continue
      const payload = tr.l && typeof tr.l === 'object' ? tr.l.i : undefined
      for (const line of extractPlainTextList(payload)) out.push(splitPosMeaning(line))
    }
  }
  return out
}

function parseReturnPhrase(word) {
  if (!('return-phrase' in word) || word['return-phrase'] == null) return null
  const value = word['return-phrase']
  if (typeof value === 'string') return value.trim() || null
  if (typeof value === 'object' && value) {
    const l = value.l
    const i = l && typeof l === 'object' ? l.i : l
    return extractPlainText(i)
  }
  return null
}

function parseYoudao(root, fallback) {
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
    text,
    isPhrase: text.includes(' '),
    ipaUk: uk,
    ipaUs: us,
    definitions: defs.length ? defs : [{ pos: '', meaning: text }],
  }
}

function collectCorpusExamples(root) {
  if (!root) return []
  const candidates = []
  const pairs = root.blng_sents_part?.['sentence-pair']
  if (Array.isArray(pairs)) {
    for (const item of pairs) {
      if (!item || typeof item !== 'object') continue
      const eng = cleanExampleText(String(item.sentence || item['sentence-eng'] || ''))
      const zh = cleanExampleText(String(item['sentence-translation'] || ''))
      if (eng && zh && !looksLikeProperNameExample(eng, zh)) {
        candidates.push({ english: eng, chinese: zh })
      }
    }
  }
  const sents = root.auth_sents_part?.sent
  if (Array.isArray(sents)) {
    for (const item of sents) {
      if (!item || typeof item !== 'object') continue
      const eng = cleanExampleText(String(item.foreign || item.speech || ''))
      const zh = cleanExampleText(String(item.translation || ''))
      if (eng && zh && !looksLikeProperNameExample(eng, zh)) {
        candidates.push({ english: eng, chinese: zh })
      }
    }
  }
  const seen = new Set()
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

function parseBaiduSug(raw, query) {
  const root = JSON.parse(raw)
  const data = root.data
  if (!Array.isArray(data) || data.length === 0) return null
  let meaning =
    data.find((d) => String(d.k).toLowerCase() === query.toLowerCase())?.v?.trim() ??
    data[0]?.v?.trim()
  if (!meaning) return null
  return {
    text: query,
    isPhrase: query.includes(' '),
    ipaUk: null,
    ipaUs: null,
    definitions: [{ pos: '', meaning }],
  }
}

function isSingleCommonLemma(word) {
  const w = word.trim().toLowerCase()
  if (w.length < 2 || w.length > 14) return false
  if (w.includes(' ')) return false
  return /^[a-z-]+$/.test(w)
}

function parseDatamuseScored(raw) {
  const array = JSON.parse(raw)
  const out = []
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

async function datamuseRelatedScored(rel, word) {
  const q = word.trim().toLowerCase()
  if (q.length < 2 || q.includes(' ')) return []
  const raw = await httpGet(`https://api.datamuse.com/words?${rel}=${enc(q)}&md=f&max=20`).catch(
    () => null,
  )
  if (!raw) return []
  return parseDatamuseScored(raw)
}

async function datamuseTagged(word, tag) {
  const q = word.trim().toLowerCase()
  if (q.length < 2 || q.includes(' ')) return []
  const raw = await httpGet(`https://api.datamuse.com/words?ml=${enc(q)}&md=f&max=25`).catch(
    () => null,
  )
  if (!raw) return []
  const array = JSON.parse(raw)
  const out = []
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

function parseYoudaoSynonyms(root) {
  if (!root) return []
  const synos = root.syno?.synos
  if (!Array.isArray(synos)) return []
  const words = new Set()
  for (const item of synos) {
    const ws = item?.syno?.ws
    if (!Array.isArray(ws)) continue
    for (const wObj of ws) {
      const w = String(wObj?.w ?? '').trim()
      if (isSingleCommonLemma(w)) words.add(w.toLowerCase())
    }
  }
  return [...words]
}

function mergeCommonWords(primary, scored, exclude) {
  const out = new Set()
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

async function loadSynonyms(root, word) {
  let scored = await datamuseRelatedScored('rel_syn', word)
  if (!scored.length) scored = await datamuseTagged(word, 'syn')
  const primary = scored.sort((a, b) => b[1] - a[1]).map(([w]) => w)
  const fromYoudao = parseYoudaoSynonyms(root)
  return mergeCommonWords(
    primary,
    fromYoudao.map((w) => [w, 1]),
    word,
  )
}

async function loadAntonyms(word) {
  const fromAnt = await datamuseRelatedScored('rel_ant', word)
  const fromMlAnt = await datamuseTagged(word, 'ant')
  return mergeCommonWords(
    fromAnt.sort((a, b) => b[1] - a[1]).map(([w]) => w),
    fromMlAnt,
    word,
  )
}

function isNameSense(text) {
  const t = text.trim()
  if (!t) return false
  if (nameSense.test(t)) return true
  if (t.includes('（') && /（[A-Z][a-zA-Z'-]+）/.test(t)) {
    return t.includes('英') || t.includes('美') || t.includes('人名')
  }
  return false
}

function cleanMeaning(raw) {
  return raw.replace(tagStrip, '').replace(/^[,，、\s]+/, '').trim()
}

function splitMeaning(raw) {
  const parts = raw.split(senseSplit).map((p) => p.trim()).filter(Boolean)
  return parts.length ? parts : [raw.trim()]
}

function isMoveSense(meaning) {
  if (['让步', '主意', '态度', '意见'].some((x) => meaning.includes(x))) return false
  return ['移动', '挪', '动'].some((x) => meaning.includes(x)) || meaning.includes('地方')
}

function extractSenses(definitions) {
  const raw = definitions.flatMap((def) => {
    if (isNameSense(def.meaning) || isNameSense(def.pos)) return []
    return splitMeaning(def.meaning)
      .map((part) => {
        const cleaned = cleanMeaning(part)
        if (!cleaned || isNameSense(cleaned)) return null
        return { pos: def.pos, meaning: cleaned }
      })
      .filter(Boolean)
  })
  const distinct = []
  const seen = new Set()
  for (const s of raw) {
    if (seen.has(s.meaning)) continue
    seen.add(s.meaning)
    distinct.push(s)
  }
  const collapsed = []
  for (const sense of distinct) {
    const moveLike = isMoveSense(sense.meaning)
    const existingMove = collapsed.findIndex((s) => isMoveSense(s.meaning))
    if (moveLike && existingMove >= 0) {
      if (sense.meaning.length < collapsed[existingMove].meaning.length) {
        collapsed[existingMove] = sense
      }
      continue
    }
    collapsed.push(sense)
  }
  return collapsed.slice(0, 5)
}

function shortGloss(meaning) {
  const cleaned = meaning
    .replace(tagStrip, '')
    .replace(parenNoise, '')
    .replace(/^（使）|^使/, '')
    .trim()
  return (cleaned || meaning).slice(0, 12)
}

function mindSense(meaning) {
  return ['让步', '主意', '态度', '意见'].some((x) => meaning.includes(x))
}

function synthesize(word, sense) {
  const meaning = sense.meaning.replace(tagStrip, '').trim()
  const pos = sense.pos.toLowerCase()
  const gloss = shortGloss(meaning)
  if (meaning.includes('皮') || (pos.startsWith('n') && !pos.startsWith('num'))) {
    return {
      english: `This coat is lined with ${word}.`,
      chinese: `这件外套用${gloss}做衬里。`,
    }
  }
  if (mindSense(meaning)) {
    return {
      english: `They refused to ${word} on the issue.`,
      chinese: `在这个问题上他们不肯${meaning.includes('让步') ? '让步' : '改变主意'}。`,
    }
  }
  if (meaning.includes('挪') || meaning.includes('地方')) {
    return { english: `Could you ${word} up a little?`, chinese: '你能稍微挪开一点吗？' }
  }
  if (pos.startsWith('v') || meaning.includes('移动') || meaning.includes('动')) {
    return {
      english: `The heavy box wouldn't ${word}.`,
      chinese: '那个沉重的箱子怎么也挪不动。',
    }
  }
  return {
    english: `People often use "${word}" in daily life.`,
    chinese: `人们常在日常生活中用到「${word}」（${gloss}）。`,
  }
}

function scoreMatch(example, sense) {
  const zh = example.chinese
  const meaning = sense.meaning
  let score = 0
  const keywords = [
    ...meaning.replace(tagStrip, '').replace(parenNoise, '').matchAll(/[\u4e00-\u9fff]{2,}/g),
  ].map((m) => m[0])
  for (const kw of keywords) {
    if (zh.includes(kw)) score += kw.length + 3
    else if (kw.length >= 2 && zh.includes(kw.slice(0, 2))) score += 1
  }
  const moveHints = ['移动', '挪开', '挪', '动弹', '动不了', '稍微']
  const mindHints = ['让步', '改变主意', '主意', '态度', '意见', '妥协']
  const nounHints = ['羔羊皮', '羊皮', '皮']
  const senseMove = moveHints.some((h) => meaning.includes(h))
  const senseMind = mindHints.some((h) => meaning.includes(h))
  const senseNoun = nounHints.some((h) => meaning.includes(h))
  const zhMove = ['动弹', '动不了', '挪', '移动'].some((h) => zh.includes(h))
  const zhMind = ['让步', '主意', '妥协', '改变'].some((h) => zh.includes(h))
  const zhNoun = ['皮', '毛', '革'].some((h) => zh.includes(h))
  if (senseMind && zhMind) score += 10
  else if (senseMove && zhMove && !zhMind) score += 8
  else if (senseNoun && zhNoun) score += 10
  if (senseMind && zhMove) score -= 2
  if (senseMove && zhMind) score -= 2
  if (/\b[A-Z][a-z]+ [A-Z]/.test(example.english)) score -= 4
  return score
}

function examplesFor(word, definitions, corpus) {
  const senses = extractSenses(definitions)
  if (!senses.length) {
    return [...corpus].sort((a, b) => a.english.length - b.english.length).slice(0, 2)
  }
  const remaining = [...corpus]
  return senses.map((sense) => {
    let bestIndex = -1
    let bestScore = 0
    remaining.forEach((ex, i) => {
      const s = scoreMatch(ex, sense)
      if (s > bestScore) {
        bestScore = s
        bestIndex = i
      }
    })
    if (bestIndex >= 0 && bestScore > 0) {
      const [best] = remaining.splice(bestIndex, 1)
      return best
    }
    return synthesize(word, sense)
  })
}

function editDistanceOne(a, b) {
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

function isInflectionOf(base, other) {
  const forms = new Set([`${base}s`, `${base}es`, `${base}ed`, `${base}ing`])
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

/** Same selection rules as NearWordsFinder, scored via local frequency lexicon. */
function findNearWordsLocal(word, freqMap, min = 2, max = 5) {
  const q = word.trim().toLowerCase()
  if (q.length < 2 || !lettersOnly.test(q)) return []
  const alphabet = 'abcdefghijklmnopqrstuvwxyz'
  const candidates = new Set()
  for (let i = 0; i < q.length; i++) {
    const shorter = q.slice(0, i) + q.slice(i + 1)
    if (shorter.length >= 2) candidates.add(shorter)
  }
  for (let i = 0; i <= q.length; i++) {
    for (const ch of alphabet) {
      candidates.add(q.slice(0, i) + ch + q.slice(i))
    }
  }
  for (let i = 0; i < q.length; i++) {
    for (const ch of alphabet) {
      if (ch === q[i]) continue
      candidates.add(q.slice(0, i) + ch + q.slice(i + 1))
    }
  }
  const scored = []
  for (const w of candidates) {
    if (w === q || !lettersOnly.test(w) || !editDistanceOne(q, w) || isInflectionOf(q, w)) continue
    const freq = freqMap.get(w)
    if (freq == null) continue
    // Keep uncommon short edits; drop only very rare longer inserts.
    if (freq < 0.02 && w.length > q.length + 1) continue
    scored.push([w, freq])
  }
  return scored
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
}

function parseTsvMeaning(meaning) {
  const match = POS_PREFIX.exec(meaning.trim())
  if (match) return [{ pos: match[1], meaning: match[2].trim() }]
  // Split "vt. ... n. ..." style exam glosses into rough POS chunks when possible.
  const parts = meaning.split(/(?=\b(?:n|v|vt|vi|adj|adv|prep|conj|pron|art|num|aux)\.\s)/i)
  if (parts.length > 1) {
    return parts
      .map((p) => splitPosMeaning(p.trim()))
      .filter((d) => d.meaning)
  }
  return [{ pos: '', meaning: meaning.trim() }]
}

async function lookupWord(query, fallbackMeaning, freqMap) {
  const q = query.trim()
  let youdaoRoot = null
  let fromYoudao = null
  try {
    const raw = await httpGet(`https://dict.youdao.com/jsonapi?q=${enc(q)}&doctype=json`)
    youdaoRoot = JSON.parse(raw)
    fromYoudao = parseYoudao(youdaoRoot, q)
  } catch {
    fromYoudao = null
  }

  let base
  if (fromYoudao && fromYoudao.definitions.length > 0) {
    base = fromYoudao
  } else {
    let fromBaidu = null
    try {
      fromBaidu = parseBaiduSug(
        await httpGet(`https://fanyi.baidu.com/sug?kw=${enc(q)}`),
        q,
      )
    } catch {
      fromBaidu = null
    }
    if (fromBaidu) {
      base = {
        ...fromBaidu,
        ipaUk: fromYoudao?.ipaUk ?? null,
        ipaUs: fromYoudao?.ipaUs ?? null,
      }
    } else if (fallbackMeaning) {
      base = {
        text: q,
        isPhrase: q.includes(' '),
        ipaUk: fromYoudao?.ipaUk ?? null,
        ipaUs: fromYoudao?.ipaUs ?? null,
        definitions: parseTsvMeaning(fallbackMeaning),
      }
    } else {
      throw new Error('no definition')
    }
  }

  const [synonyms, antonyms] = await Promise.all([
    loadSynonyms(youdaoRoot, base.text),
    loadAntonyms(base.text),
  ])
  const corpus = collectCorpusExamples(youdaoRoot)
  const examples = examplesFor(base.text, base.definitions, corpus)
  const nearWords = findNearWordsLocal(base.text, freqMap)

  return {
    text: base.text,
    isPhrase: base.isPhrase,
    ipaUk: base.ipaUk,
    ipaUs: base.ipaUs,
    definitions: base.definitions,
    examples,
    synonyms,
    antonyms,
    nearWords,
  }
}

function readTsv(filePath) {
  const rows = []
  for (const raw of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const line = raw.replace(/\r$/, '')
    if (!line.trim()) continue
    const tab = line.indexOf('\t')
    if (tab <= 0) continue
    const word = line.slice(0, tab).trim()
    const meaning = line.slice(tab + 1).trim()
    if (!word) continue
    rows.push({ word, meaning })
  }
  return rows
}

function loadCache() {
  const map = new Map()
  if (!fs.existsSync(CACHE_PATH)) return map
  for (const line of fs.readFileSync(CACHE_PATH, 'utf8').split(/\r?\n/)) {
    if (!line.trim()) continue
    try {
      const obj = JSON.parse(line)
      if (obj?.query) map.set(String(obj.query).toLowerCase(), obj)
    } catch {
      // skip corrupt line
    }
  }
  return map
}

function appendCache(entry) {
  fs.appendFileSync(CACHE_PATH, JSON.stringify(entry) + '\n', 'utf8')
}

async function ensureFreqMap() {
  fs.mkdirSync(TMP, { recursive: true })
  if (!fs.existsSync(FREQ_PATH)) {
    console.log('Downloading English frequency list…')
    const raw = await httpGet(FREQ_URL, 5)
    fs.writeFileSync(FREQ_PATH, raw, 'utf8')
  }
  const freqMap = new Map()
  const lines = fs.readFileSync(FREQ_PATH, 'utf8').split(/\r?\n/)
  let maxCount = 1
  const parsed = []
  for (const line of lines) {
    const [w, countStr] = line.trim().split(/\s+/)
    if (!w || !lettersOnly.test(w)) continue
    const count = Number(countStr) || 0
    if (count > maxCount) maxCount = count
    parsed.push([w, count])
  }
  // Map corpus count → Datamuse-like f: scale roughly in 0.01–30.
  for (const [w, count] of parsed) {
    const freq = Math.max(0.01, (count / maxCount) * 30)
    freqMap.set(w, freq)
  }
  console.log(`Frequency lexicon: ${freqMap.size} words`)
  return freqMap
}

async function mapPool(items, concurrency, worker) {
  let index = 0
  let done = 0
  const total = items.length
  const errors = []
  async function run() {
    while (true) {
      const i = index++
      if (i >= total) return
      try {
        await worker(items[i], i)
      } catch (e) {
        errors.push({ item: items[i], error: String(e?.message || e) })
      }
      done++
      if (done % 25 === 0 || done === total) {
        console.log(`  progress ${done}/${total} (errors=${errors.length})`)
      }
      if (DELAY_MS > 0) await sleep(DELAY_MS)
    }
  }
  await Promise.all(Array.from({ length: concurrency }, () => run()))
  return errors
}

function dedupeExamples(examples) {
  const seen = new Set()
  const out = []
  for (const e of examples || []) {
    const en = (e.english ?? e.en ?? '').trim()
    const zh = (e.chinese ?? e.zh ?? '').trim()
    if (!en || !zh) continue
    const key = en.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push({ en, zh })
  }
  return out
}

function toAssetLine(enriched) {
  return JSON.stringify({
    text: enriched.text,
    isPhrase: !!enriched.isPhrase,
    ipaUk: enriched.ipaUk,
    ipaUs: enriched.ipaUs,
    definitions: (enriched.definitions || []).map((d) => ({
      pos: d.pos || '',
      meaning: d.meaning || '',
    })),
    examples: dedupeExamples(enriched.examples),
    nearWords: enriched.nearWords || [],
    synonyms: enriched.synonyms || [],
    antonyms: enriched.antonyms || [],
  })
}

async function main() {
  fs.mkdirSync(TMP, { recursive: true })
  const zhongkao = readTsv(path.join(ASSETS, 'zhongkao.tsv'))
  const gaokao = readTsv(path.join(ASSETS, 'gaokao.tsv'))
  const cet4 = readTsv(path.join(ASSETS, 'cet4.tsv'))
  const cet6 = readTsv(path.join(ASSETS, 'cet6.tsv'))
  console.log(
    `ZHONGKAO=${zhongkao.length} GAOKAO=${gaokao.length} CET4=${cet4.length} CET6=${cet6.length}`,
  )

  const meaningByWord = new Map()
  for (const row of [...zhongkao, ...gaokao, ...cet4, ...cet6]) {
    const key = row.word.toLowerCase()
    if (!meaningByWord.has(key)) meaningByWord.set(key, row)
  }
  let unique = [...meaningByWord.values()]
  if (LIMIT > 0) unique = unique.slice(0, LIMIT)
  console.log(`Unique words to enrich: ${unique.length}`)

  const freqMap = await ensureFreqMap()
  // Boost CET headwords so they can appear as near-words of each other.
  for (const row of [...zhongkao, ...gaokao, ...cet4, ...cet6]) {
    const w = row.word.toLowerCase()
    if (!lettersOnly.test(w)) continue
    const prev = freqMap.get(w) ?? 0
    freqMap.set(w, Math.max(prev, 0.8))
  }

  const cache = loadCache()
  console.log(`Cache hits: ${[...cache.keys()].filter((k) => meaningByWord.has(k)).length}`)

  const cacheFdReady = fs.existsSync(CACHE_PATH)
  if (!cacheFdReady) fs.writeFileSync(CACHE_PATH, '', 'utf8')

  const pending = unique.filter((r) => !cache.has(r.word.toLowerCase()))
  console.log(`Need fetch: ${pending.length} (concurrency=${CONCURRENCY})`)

  const errors = await mapPool(pending, CONCURRENCY, async (row) => {
    const enriched = await lookupWord(row.word, row.meaning, freqMap)
    const record = { query: row.word.toLowerCase(), ...enriched, ok: true }
    appendCache(record)
    cache.set(record.query, record)
  })
  if (errors.length) {
    console.warn(`Fetch errors: ${errors.length}`)
    fs.writeFileSync(
      path.join(TMP, 'enrich_errors.json'),
      JSON.stringify(errors, null, 2),
      'utf8',
    )
  }

  function resolveRow(row) {
    const cached = cache.get(row.word.toLowerCase())
    if (cached?.ok !== false && cached?.definitions?.length) {
      // Recompute near-words from the local lexicon (cheap) so regenerating
      // assets picks up lexicon/threshold tweaks without re-hitting APIs.
      const nearWords = findNearWordsLocal(cached.text || row.word, freqMap)
      return {
        text: cached.text || row.word,
        isPhrase: cached.isPhrase ?? row.word.includes(' '),
        ipaUk: cached.ipaUk ?? null,
        ipaUs: cached.ipaUs ?? null,
        definitions: cached.definitions,
        examples: cached.examples || [],
        nearWords: nearWords.length ? nearWords : cached.nearWords || [],
        synonyms: cached.synonyms || [],
        antonyms: cached.antonyms || [],
      }
    }
    return {
      text: row.word,
      isPhrase: row.word.includes(' '),
      ipaUk: null,
      ipaUs: null,
      definitions: parseTsvMeaning(row.meaning),
      examples: [],
      nearWords: findNearWordsLocal(row.word, freqMap),
      synonyms: [],
      antonyms: [],
    }
  }

  const writeBook = (rows, outName) => {
    const outPath = path.join(ASSETS, outName)
    const lines = rows.map((row) => toAssetLine(resolveRow(row)))
    fs.writeFileSync(outPath, lines.join('\n') + '\n', 'utf8')
    const sample = JSON.parse(lines[0])
    console.log(
      `Wrote ${outName}: ${rows.length} words; sample ${sample.text} defs=${sample.definitions.length} ex=${sample.examples.length} syn=${sample.synonyms.length} near=${sample.nearWords.length}`,
    )
  }

  if (LIMIT > 0) {
    console.log('LIMIT set — writing only limited assets for smoke test')
    if (wantBook('zhongkao')) writeBook(zhongkao.slice(0, LIMIT), 'zhongkao.enriched.jsonl')
    if (wantBook('gaokao')) writeBook(gaokao.slice(0, LIMIT), 'gaokao.enriched.jsonl')
    if (wantBook('cet4')) writeBook(cet4.slice(0, LIMIT), 'cet4.enriched.jsonl')
    if (wantBook('cet6')) writeBook(cet6.slice(0, LIMIT), 'cet6.enriched.jsonl')
  } else {
    if (wantBook('zhongkao')) writeBook(zhongkao, 'zhongkao.enriched.jsonl')
    if (wantBook('gaokao')) writeBook(gaokao, 'gaokao.enriched.jsonl')
    if (wantBook('cet4')) writeBook(cet4, 'cet4.enriched.jsonl')
    if (wantBook('cet6')) writeBook(cet6, 'cet6.enriched.jsonl')
  }
  console.log('Done.')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
