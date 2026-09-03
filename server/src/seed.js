import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import dotenv from 'dotenv'
import pg from 'pg'

const here = path.dirname(fileURLToPath(import.meta.url))
dotenv.config({ path: path.resolve(here, '../.env') })

const ROOT = path.resolve(here, '../..')
const ASSETS = path.join(ROOT, 'app/src/main/assets/wordbooks')

// Add PET / TOEFL / IELTS later as extra catalog rows + jsonl files.
const CATALOGS = [
  { slug: 'zhongkao', name: '中考词汇', sortOrder: 0, file: 'zhongkao.enriched.jsonl' },
  { slug: 'gaokao', name: '高考词汇', sortOrder: 1, file: 'gaokao.enriched.jsonl' },
  { slug: 'cet4', name: '大学四级', sortOrder: 2, file: 'cet4.enriched.jsonl' },
  { slug: 'cet6', name: '大学六级', sortOrder: 3, file: 'cet6.enriched.jsonl' },
]

function readJsonl(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`missing ${filePath}`)
  }
  const rows = []
  for (const line of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed) continue
    rows.push(JSON.parse(trimmed))
  }
  return rows
}

async function upsertCatalog(client, catalog) {
  const existing = await client.query('SELECT id FROM notebooks WHERE slug = $1', [catalog.slug])
  if (existing.rowCount > 0) {
    const id = existing.rows[0].id
    await client.query(
      `UPDATE notebooks SET name = $2, sort_order = $3, published = TRUE, kind = 'catalog'
       WHERE id = $1`,
      [id, catalog.name, catalog.sortOrder],
    )
    return id
  }
  const inserted = await client.query(
    `INSERT INTO notebooks (kind, slug, name, sort_order, published)
     VALUES ('catalog', $1, $2, $3, TRUE)
     RETURNING id`,
    [catalog.slug, catalog.name, catalog.sortOrder],
  )
  return inserted.rows[0].id
}

async function replaceWords(client, notebookId, rows) {
  await client.query('DELETE FROM words WHERE notebook_id = $1', [notebookId])
  const prepared = []
  const seen = new Set()
  for (const row of rows) {
    const text = String(row.text || '').trim()
    if (!text) continue
    const key = text.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    prepared.push({
      text,
      isPhrase: Boolean(row.isPhrase || text.includes(' ')),
      ipaUk: row.ipaUk || null,
      ipaUs: row.ipaUs || null,
      definitions: JSON.stringify(
        (row.definitions || []).map((d) => ({ pos: d.pos || '', meaning: d.meaning || '' })),
      ),
      examples: JSON.stringify(
        (row.examples || []).map((e) => ({
          en: e.en ?? e.english ?? '',
          zh: e.zh ?? e.chinese ?? '',
        })),
      ),
      nearWords: JSON.stringify(row.nearWords || []),
      synonyms: JSON.stringify(row.synonyms || []),
      antonyms: JSON.stringify(row.antonyms || []),
    })
  }
  const batchSize = 200
  for (let start = 0; start < prepared.length; start += batchSize) {
    const chunk = prepared.slice(start, start + batchSize)
    const params = []
    const values = chunk.map((row, offset) => {
      const i = offset * 11
      params.push(
        notebookId,
        row.text,
        row.isPhrase,
        row.ipaUk,
        row.ipaUs,
        row.definitions,
        row.examples,
        row.nearWords,
        row.synonyms,
        row.antonyms,
        start + offset,
      )
      return `($${i + 1}, $${i + 2}, $${i + 3}, $${i + 4}, $${i + 5}, $${i + 6}::jsonb, $${i + 7}::jsonb, $${i + 8}::jsonb, $${i + 9}::jsonb, $${i + 10}::jsonb, $${i + 11})`
    })
    await client.query(
      `INSERT INTO words (
         notebook_id, word, is_phrase, ipa_uk, ipa_us,
         definitions, examples, near_words, synonyms, antonyms, sort_order
       ) VALUES ${values.join(',')}`,
      params,
    )
    console.log(`  ${catalogLabel(notebookId)} ${Math.min(start + chunk.length, prepared.length)}/${prepared.length}`)
  }
  return prepared.length
}

const catalogNames = new Map()
function catalogLabel(id) {
  return catalogNames.get(id) || String(id)
}

async function main() {
  const client = new pg.Client({ connectionString: process.env.DATABASE_URL })
  await client.connect()
  try {
    for (const catalog of CATALOGS) {
      const notebookId = await upsertCatalog(client, catalog)
      catalogNames.set(notebookId, catalog.slug)
      const rows = readJsonl(path.join(ASSETS, catalog.file))
      console.log(`Seeding ${catalog.slug} (${rows.length} words) -> notebook ${notebookId}`)
      await client.query('BEGIN')
      try {
        const count = await replaceWords(client, notebookId, rows)
        await client.query('COMMIT')
        console.log(`  wrote ${count} words`)
      } catch (e) {
        await client.query('ROLLBACK')
        throw e
      }
    }
  } finally {
    await client.end()
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
