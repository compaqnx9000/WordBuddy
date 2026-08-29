import type { StudySettings, VocabEntry } from '../types'
import { DEFAULT_SETTINGS } from '../types'

const WORDS_KEY = 'hotwords.h5.words'
const SETTINGS_KEY = 'hotwords.h5.settings'
const ID_KEY = 'hotwords.h5.nextId'

export function loadWords(): VocabEntry[] {
  try {
    const raw = localStorage.getItem(WORDS_KEY)
    if (!raw) return []
    return JSON.parse(raw) as VocabEntry[]
  } catch {
    return []
  }
}

export function saveWords(words: VocabEntry[]) {
  localStorage.setItem(WORDS_KEY, JSON.stringify(words))
}

export function loadSettings(): StudySettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (!raw) return { ...DEFAULT_SETTINGS }
    return { ...DEFAULT_SETTINGS, ...(JSON.parse(raw) as StudySettings) }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

export function saveSettings(settings: StudySettings) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings))
}

export function nextId(): number {
  const cur = Number(localStorage.getItem(ID_KEY) || '1')
  localStorage.setItem(ID_KEY, String(cur + 1))
  return cur
}
