/**
 * Convert 高考英语大纲词汇 CSV (+ optional bare headword list) into CET-style TSV:
 *   word<TAB>pos/meaning
 *
 * Default sources:
 *   tmp_dict/gaokao-maimemo.csv     (busiyiworld/maimemo-export 高考英语大纲词汇)
 *   tmp_dict/gaokao-source.txt      (mahavivo Highschool_edited.txt headwords)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const CSV = process.argv[2] || path.join(ROOT, 'tmp_dict', 'gaokao-maimemo.csv')
const BARE = process.argv[3] || path.join(ROOT, 'tmp_dict', 'gaokao-source.txt')
const DEST = path.join(ROOT, 'app/src/main/assets/wordbooks/gaokao.tsv')

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
  // Keep simple English heads / short phrases.
  if (!/^[A-Za-z][A-Za-z0-9.\-' /]*$/.test(head)) return ''
  return head
}

function parseCsv(text) {
  const rows = []
  let row = []
  let cell = ''
  let q = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    const n = text[i + 1]
    if (q) {
      if (c === '"' && n === '"') {
        cell += '"'
        i++
      } else if (c === '"') {
        q = false
      } else {
        cell += c
      }
    } else if (c === '"') {
      q = true
    } else if (c === ',') {
      row.push(cell)
      cell = ''
    } else if (c === '\n' || (c === '\r' && n === '\n')) {
      if (c === '\r') i++
      row.push(cell)
      if (row.some((x) => x.trim())) rows.push(row)
      row = []
      cell = ''
    } else if (c !== '\r') {
      cell += c
    }
  }
  if (cell.length || row.length) {
    row.push(cell)
    if (row.some((x) => x.trim())) rows.push(row)
  }
  return rows
}

function flattenMeaning(raw) {
  return stripNoise(String(raw || '').replace(/\r?\n+/g, '；'))
}

const seen = new Map()

if (fs.existsSync(CSV)) {
  for (const cols of parseCsv(fs.readFileSync(CSV, 'utf8'))) {
    const word = normalizeHead(cols[0] || '')
    const meaning = flattenMeaning(cols[1] || '')
    if (!word || !meaning) continue
    const key = word.toLowerCase()
    if (!seen.has(key)) seen.set(key, { word, meaning })
  }
}

let bareAdded = 0
if (fs.existsSync(BARE)) {
  for (const line of fs.readFileSync(BARE, 'utf8').split(/\r?\n/)) {
    const word = normalizeHead(line)
    if (!word) continue
    const key = word.toLowerCase()
    if (seen.has(key)) continue
    seen.set(key, { word, meaning: '' })
    bareAdded++
  }
}

const rows = [...seen.values()]
fs.mkdirSync(path.dirname(DEST), { recursive: true })
fs.writeFileSync(
  DEST,
  rows.map((r) => `${r.word}\t${r.meaning || ' '}`).join('\n') + '\n',
  'utf8',
)
console.log(`Wrote ${rows.length} words -> ${DEST} (bare extras ${bareAdded})`)
console.log('sample', rows.slice(0, 3))
