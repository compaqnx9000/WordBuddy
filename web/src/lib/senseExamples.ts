import type { Definition, ExampleSentence } from '../types'

const nameSense = /人名|【名】|（名）|\(名\)|姓氏|地名|河名|山名/
const senseSplit = /[；;]/
const tagStrip = /<[^>]+>/g
const parenNoise = /[（(][^）)]*[）)]/g

type Sense = { pos: string; meaning: string }

function isNameSense(text: string): boolean {
  const t = text.trim()
  if (!t) return false
  if (nameSense.test(t)) return true
  if (t.includes('（') && /（[A-Z][a-zA-Z'-]+）/.test(t)) {
    return t.includes('英') || t.includes('美') || t.includes('人名')
  }
  return false
}

function cleanMeaning(raw: string): string {
  return raw.replace(tagStrip, '').replace(/^[,，、\s]+/, '').trim()
}

function splitMeaning(raw: string): string[] {
  const parts = raw.split(senseSplit).map((p) => p.trim()).filter(Boolean)
  return parts.length ? parts : [raw.trim()]
}

function isMoveSense(meaning: string): boolean {
  if (['让步', '主意', '态度', '意见'].some((x) => meaning.includes(x))) return false
  return ['移动', '挪', '动'].some((x) => meaning.includes(x)) || meaning.includes('地方')
}

export function extractSenses(definitions: Definition[]): Sense[] {
  const raw = definitions.flatMap((def) => {
    if (isNameSense(def.meaning) || isNameSense(def.pos)) return []
    return splitMeaning(def.meaning)
      .map((part) => {
        const cleaned = cleanMeaning(part)
        if (!cleaned || isNameSense(cleaned)) return null
        return { pos: def.pos, meaning: cleaned } as Sense
      })
      .filter((s): s is Sense => s != null)
  })

  const distinct: Sense[] = []
  const seen = new Set<string>()
  for (const s of raw) {
    if (seen.has(s.meaning)) continue
    seen.add(s.meaning)
    distinct.push(s)
  }

  const collapsed: Sense[] = []
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

function shortGloss(meaning: string): string {
  const cleaned = meaning
    .replace(tagStrip, '')
    .replace(parenNoise, '')
    .replace(/^（使）|^使/, '')
    .trim()
  return (cleaned || meaning).slice(0, 12)
}

function mindSense(meaning: string): boolean {
  return ['让步', '主意', '态度', '意见'].some((x) => meaning.includes(x))
}

function synthesize(word: string, sense: Sense): ExampleSentence {
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
    return {
      english: `Could you ${word} up a little?`,
      chinese: '你能稍微挪开一点吗？',
    }
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

function scoreMatch(example: ExampleSentence, sense: Sense): number {
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

export function examplesFor(
  word: string,
  definitions: Definition[],
  corpus: ExampleSentence[],
): ExampleSentence[] {
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
