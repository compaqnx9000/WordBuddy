import type { Definition, ExampleSentence, VocabEntry } from '../types'

const EXPORT_VERSION = 1

export function exportNotebookJson(entries: VocabEntry[]): string {
  const root = {
    version: EXPORT_VERSION,
    exportedAt: Date.now(),
    entries: entries.map((entry) => ({
      word: entry.text,
      isPhrase: entry.isPhrase,
      definitions: entry.definitions.map((d) => ({
        pos: d.pos,
        meaning: d.meaning,
        user: !!d.isUserAdded,
      })),
      examples: entry.examples.map((e) => ({ en: e.english, zh: e.chinese })),
      nearWords: entry.nearWords,
      synonyms: entry.synonyms,
      antonyms: entry.antonyms,
      sortOrder: entry.sortOrder,
      addedAt: entry.addedAtMillis,
    })),
  }
  return JSON.stringify(root, null, 2)
}

export function importNotebookJson(raw: string): VocabEntry[] {
  const root = JSON.parse(raw) as {
    version?: number
    entries?: Record<string, unknown>[]
  }
  if (root.version !== EXPORT_VERSION) {
    throw new Error('不支持的备份版本')
  }
  if (!Array.isArray(root.entries)) {
    throw new Error('备份文件格式无效')
  }
  return root.entries
    .map((obj) => {
      const word = String(obj.word ?? '').trim()
      if (!word) return null
      const defs = Array.isArray(obj.definitions)
        ? (obj.definitions as Record<string, unknown>[]).map(
            (d): Definition => ({
              pos: String(d.pos ?? ''),
              meaning: String(d.meaning ?? ''),
              isUserAdded: !!d.user,
            }),
          )
        : []
      const examples = Array.isArray(obj.examples)
        ? (obj.examples as Record<string, unknown>[])
            .map((e): ExampleSentence | null => {
              const en = String(e.en ?? '').trim()
              const zh = String(e.zh ?? '').trim()
              return en && zh ? { english: en, chinese: zh } : null
            })
            .filter((e): e is ExampleSentence => e != null)
        : []
      const list = (key: string) =>
        Array.isArray(obj[key])
          ? (obj[key] as unknown[]).map((w) => String(w).trim()).filter(Boolean)
          : []
      return {
        id: 0,
        text: word,
        isPhrase: !!obj.isPhrase,
        definitions: defs,
        examples,
        nearWords: list('nearWords'),
        synonyms: list('synonyms'),
        antonyms: list('antonyms'),
        sortOrder: Number(obj.sortOrder ?? 0),
        addedAtMillis: Number(obj.addedAt ?? Date.now()),
      } satisfies VocabEntry
    })
    .filter((e): e is VocabEntry => e != null)
}

export function suggestedExportFileName(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `hotwords-backup-${stamp}.json`
}
