/**
 * Server-side dictionary lookup for the desktop site.
 * Same sources as the Android client: Youdao jsonapi, Baidu suggest fallback.
 */

const POS_PREFIX = /^([a-z]+\.)\s*(.+)$/i

function fail(status, message) {
  const error = new Error(message)
  error.status = status
  return error
}

async function fetchText(url) {
  const response = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 WordBuddyDesk/1.0',
      Accept: 'application/json,text/plain,*/*',
    },
    signal: AbortSignal.timeout(12000),
  })
  if (!response.ok) throw fail(502, '词典暂时不可用')
  return response.text()
}

function asObject(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value
  if (Array.isArray(value)) return value.find((item) => item && typeof item === 'object') || null
  return null
}

function plainText(value) {
  if (typeof value === 'string') return value.trim()
  if (Array.isArray(value)) {
    return value
      .map((part) => (typeof part === 'string' ? part.trim() : ''))
      .filter(Boolean)
      .join(' ')
  }
  return ''
}

function plainList(value) {
  if (typeof value === 'string') return value.trim() ? [value.trim()] : []
  if (Array.isArray(value)) return value.map((part) => plainText(part)).filter(Boolean)
  return []
}

function returnPhrase(word) {
  const value = word?.['return-phrase']
  if (typeof value === 'string') return value.trim()
  if (value && typeof value === 'object') return plainText(value.l?.i ?? value.l)
  return ''
}

function splitPosMeaning(raw) {
  const line = String(raw || '').trim()
  const match = POS_PREFIX.exec(line)
  if (!match) return { pos: '', meaning: line }
  return { pos: match[1], meaning: match[2].trim() }
}

function parseTrs(trs) {
  if (!Array.isArray(trs)) return []
  const defs = []
  for (const item of trs) {
    if (!item || typeof item !== 'object') continue
    const tran = String(item.tran || '').trim()
    if (tran) {
      defs.push({ pos: String(item.pos || '').trim(), meaning: tran })
      continue
    }
    const trArray = Array.isArray(item.tr) ? item.tr : []
    for (const tr of trArray) {
      const payload = tr?.l?.i
      for (const line of plainList(payload)) defs.push(splitPosMeaning(line))
    }
  }
  return defs.filter((def) => def.meaning)
}

function cleanExample(raw) {
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

function collectExamples(root) {
  const out = []
  const pairs = root?.blng_sents_part?.['sentence-pair']
  if (Array.isArray(pairs)) {
    for (const obj of pairs) {
      const english = cleanExample(obj?.sentence || obj?.['sentence-eng'])
      const chinese = cleanExample(obj?.['sentence-translation'])
      if (english && chinese && !chinese.includes('人名')) out.push({ english, chinese })
    }
  }
  const sents = root?.auth_sents_part?.sent
  if (Array.isArray(sents)) {
    for (const obj of sents) {
      const english = cleanExample(obj?.foreign || obj?.speech)
      const chinese = cleanExample(obj?.translation)
      if (english && chinese && !chinese.includes('人名')) out.push({ english, chinese })
    }
  }
  const seen = new Set()
  return out
    .filter((item) => {
      const key = item.english.toLowerCase()
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .sort((a, b) => a.english.length - b.english.length)
    .slice(0, 6)
}

function youdaoSynonyms(root) {
  const synos = root?.syno?.synos
  if (!Array.isArray(synos)) return []
  const words = []
  for (const item of synos) {
    const ws = item?.syno?.ws
    if (!Array.isArray(ws)) continue
    for (const entry of ws) {
      const word = String(entry?.w || entry || '').trim()
      if (word && !words.includes(word)) words.push(word)
    }
  }
  return words.slice(0, 8)
}

function parseYoudao(root, fallback) {
  const word = asObject(asObject(root?.ec)?.word) || asObject(asObject(root?.simple)?.word)
  if (!word) return null
  const text = returnPhrase(word) || fallback
  const ipaUk = String(word.ukphone || '').trim() || null
  const ipaUs = String(word.usphone || '').trim() || null
  const definitions = parseTrs(word.trs)
  if (!definitions.length && !ipaUk && !ipaUs) return null
  return {
    text,
    isPhrase: text.includes(' '),
    ipaUk,
    ipaUs,
    definitions: definitions.length ? definitions : [{ pos: '', meaning: text }],
    examples: collectExamples(root),
    synonyms: youdaoSynonyms(root),
    antonyms: [],
    nearWords: [],
  }
}

function parseBaidu(raw, query) {
  let root
  try {
    root = JSON.parse(raw)
  } catch {
    return null
  }
  const data = Array.isArray(root?.data) ? root.data : []
  let meaning = ''
  for (const item of data) {
    if (String(item?.k || '').toLowerCase() === query.toLowerCase()) {
      meaning = String(item?.v || '').trim()
      break
    }
  }
  if (!meaning && data[0]) meaning = String(data[0].v || '').trim()
  if (!meaning) return null
  return {
    text: query,
    isPhrase: query.includes(' '),
    ipaUk: null,
    ipaUs: null,
    definitions: [{ pos: '', meaning }],
    examples: [],
    synonyms: [],
    antonyms: [],
    nearWords: [],
  }
}

export async function lookupDict(query) {
  const q = String(query || '').trim()
  if (!q) throw fail(400, '请输入要查的词')
  if (q.length > 80) throw fail(400, '查询太长')
  let youdaoRoot = null
  let fromYoudao = null
  try {
    const raw = await fetchText(`https://dict.youdao.com/jsonapi?q=${encodeURIComponent(q)}&doctype=json`)
    youdaoRoot = JSON.parse(raw)
    fromYoudao = parseYoudao(youdaoRoot, q)
  } catch (error) {
    if (error?.status === 400) throw error
    fromYoudao = null
  }
  if (fromYoudao?.definitions?.length) return fromYoudao
  try {
    const raw = await fetchText(`https://fanyi.baidu.com/sug?kw=${encodeURIComponent(q)}`)
    const fromBaidu = parseBaidu(raw, q)
    if (fromBaidu) {
      return {
        ...fromBaidu,
        ipaUk: fromYoudao?.ipaUk || null,
        ipaUs: fromYoudao?.ipaUs || null,
        examples: fromYoudao?.examples || [],
        synonyms: fromYoudao?.synonyms || [],
      }
    }
  } catch {
    // fall through
  }
  if (fromYoudao) return fromYoudao
  throw fail(404, '没有查到这个词')
}
