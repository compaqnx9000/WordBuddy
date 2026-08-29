export type Definition = {
  pos: string
  meaning: string
  isUserAdded?: boolean
}

export type ExampleSentence = {
  english: string
  chinese: string
}

export type VocabEntry = {
  id: number
  text: string
  isPhrase: boolean
  ipaUk?: string | null
  ipaUs?: string | null
  definitions: Definition[]
  examples: ExampleSentence[]
  nearWords: string[]
  synonyms: string[]
  antonyms: string[]
  /** data URL or remote URL */
  imageUrl?: string | null
  sortOrder: number
  addedAtMillis: number
}

export type LookupResult = {
  entry: VocabEntry
  saved: boolean
}

export type WordFilter = 'ALL' | 'WORDS' | 'PHRASES'
export type SortMode = 'MANUAL' | 'TIME_DESC' | 'TIME_ASC' | 'ALPHA'
export type Accent = 'UK' | 'US'
export type AppTheme = 'Light' | 'Dark'

export type FontSizeOption = 'Small' | 'Normal' | 'Large' | 'ExtraLarge'

export const FONT_SCALES: Record<FontSizeOption, number> = {
  Small: 0.9,
  Normal: 1,
  Large: 1.12,
  ExtraLarge: 1.25,
}

export type StudySettings = {
  displayName: string
  accent: Accent
  autoPlayIntervalMs: number
  loop: boolean
  speakOnPageChange: boolean
  fontScale: number
  appTheme: AppTheme
}

export type VocabUiState = {
  filter: WordFilter
  sortMode: SortMode
  query: string
  searching: boolean
  hideDefinitions: boolean
  revealedIds: number[]
  cardIndex: number
  shuffledIds: number[] | null
  playing: boolean
  settings: StudySettings
  lookupQuery: string
  lookupLoading: boolean
  lookupError: string | null
  lookupResult: LookupResult | null
  imageBusy: boolean
  imageError: string | null
}

export type NotebookImportResult = {
  added: number
  updated: number
}

export const DEFAULT_SETTINGS: StudySettings = {
  displayName: '热词学习者',
  accent: 'US',
  autoPlayIntervalMs: 2500,
  loop: true,
  speakOnPageChange: true,
  fontScale: 1,
  appTheme: 'Light',
}

export function defLabel(d: Definition): string {
  return d.pos.trim() ? `${d.pos} ${d.meaning}` : d.meaning
}

export function emptyEntry(text: string): VocabEntry {
  return {
    id: 0,
    text,
    isPhrase: text.includes(' '),
    definitions: [],
    examples: [],
    nearWords: [],
    synonyms: [],
    antonyms: [],
    sortOrder: 0,
    addedAtMillis: Date.now(),
  }
}
