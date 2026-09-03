/**
 * Convert mahavivo 中考英语词汇表.txt into the same TSV as CET books:
 *   word<TAB>pos/meaning
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const SRC = process.argv[2] || path.join(ROOT, 'tmp_dict', 'zhongkao-source.txt')
const DEST = path.join(ROOT, 'app/src/main/assets/wordbooks/zhongkao.tsv')

function stripNoise(text) {
  return String(text)
    .replace(/[\u00a0\u3000]/g, ' ')
    .replace(/[\uE008\uE816]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeHead(raw) {
  let head = stripNoise(raw)
  if (!head) return ''
  head = head.split('=')[0].trim()
  head = head.replace(/\s*[\(（][^）)]*[\)）]\s*/g, ' ').trim()
  head = head.replace(/\s+/g, ' ')
  if (/^[A-Z]$/.test(head)) return ''
  return head
}

function parseLine(line) {
  const raw = stripNoise(line)
  if (!raw || /^[A-Z]$/.test(raw)) return null

  let match = raw.match(/^(.*?)\s*\[.*?\]\s*(.+)$/)
  if (match) {
    const word = normalizeHead(match[1])
    const meaning = stripNoise(match[2]).replace(/^l\s+/, '')
    if (word && meaning) return { word, meaning }
  }

  match = raw.match(/^([A-Za-z][A-Za-z.\-'/ ]*?)\s*\/[^/]+\/\s*(.+)$/)
  if (match) {
    const word = normalizeHead(match[1].split('/')[0])
    const meaning = stripNoise(match[2])
    if (word && meaning) return { word, meaning }
  }

  match = raw.match(/^([A-Za-z][A-Za-z.\-']*)\s+([a-z]+\..+)$/)
  if (match) {
    const word = normalizeHead(match[1])
    const meaning = stripNoise(match[2])
    if (word && meaning) return { word, meaning }
  }

  return null
}

const seen = new Map()
for (const line of fs.readFileSync(SRC, 'utf8').split(/\r?\n/)) {
  const row = parseLine(line)
  if (!row) continue
  const key = row.word.toLowerCase()
  if (!seen.has(key)) seen.set(key, row)
}

const rows = [...seen.values()]
fs.mkdirSync(path.dirname(DEST), { recursive: true })
fs.writeFileSync(
  DEST,
  rows.map((r) => `${r.word}\t${r.meaning}`).join('\n') + '\n',
  'utf8',
)
console.log(`Wrote ${rows.length} words -> ${DEST}`)
console.log('sample', rows.slice(0, 3))
