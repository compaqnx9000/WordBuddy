import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fileToDataUrl, generateAiImageUrl } from '../lib/aiImage'
import { lookupWord } from '../lib/dictionary'
import { findNearWords } from '../lib/nearWords'
import {
  exportNotebookJson,
  importNotebookJson,
  suggestedExportFileName,
} from '../lib/notebookIo'
import { loadSettings, loadWords, nextId, saveSettings, saveWords } from '../lib/storage'
import { speakText, stopSpeaking } from '../lib/tts'
import type {
  Definition,
  NotebookImportResult,
  SortMode,
  StudySettings,
  VocabEntry,
  VocabUiState,
  WordFilter,
} from '../types'
import { DEFAULT_SETTINGS } from '../types'

function filterAndSort(words: VocabEntry[], state: VocabUiState): VocabEntry[] {
  let list = [...words]
  if (state.filter === 'WORDS') list = list.filter((w) => !w.isPhrase)
  if (state.filter === 'PHRASES') list = list.filter((w) => w.isPhrase)
  const q = state.query.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (w) =>
        w.text.toLowerCase().includes(q) ||
        w.definitions.some((d) => d.meaning.toLowerCase().includes(q)),
    )
  }
  switch (state.sortMode) {
    case 'TIME_DESC':
      return list.sort((a, b) => b.addedAtMillis - a.addedAtMillis)
    case 'TIME_ASC':
      return list.sort((a, b) => a.addedAtMillis - b.addedAtMillis)
    case 'ALPHA':
      return list.sort((a, b) => a.text.localeCompare(b.text))
    default:
      return list.sort((a, b) => a.sortOrder - b.sortOrder)
  }
}

export function useHotWords() {
  const [words, setWords] = useState<VocabEntry[]>(() => loadWords())
  const [ui, setUi] = useState<VocabUiState>(() => ({
    filter: 'ALL',
    sortMode: 'MANUAL',
    query: '',
    searching: false,
    hideDefinitions: true,
    revealedIds: [],
    cardIndex: 0,
    shuffledIds: null,
    playing: false,
    settings: loadSettings(),
    lookupQuery: '',
    lookupLoading: false,
    lookupError: null,
    lookupResult: null,
    imageBusy: false,
    imageError: null,
  }))

  const lookupTimer = useRef<number | null>(null)
  const autoPlayTimer = useRef<number | null>(null)
  const wordsRef = useRef(words)
  wordsRef.current = words
  const uiRef = useRef(ui)
  uiRef.current = ui

  useEffect(() => {
    saveWords(words)
  }, [words])

  useEffect(() => {
    saveSettings(ui.settings)
    document.documentElement.dataset.theme = ui.settings.appTheme === 'Dark' ? 'dark' : 'light'
    document.documentElement.style.setProperty('--font-scale', String(ui.settings.fontScale))
  }, [ui.settings])

  const filtered = useMemo(() => filterAndSort(words, ui), [words, ui])

  const updateSettings = useCallback((transform: (s: StudySettings) => StudySettings) => {
    setUi((s) => ({ ...s, settings: transform(s.settings) }))
  }, [])

  const isWordSaved = useCallback(
    (word: string) => words.some((w) => w.text.toLowerCase() === word.trim().toLowerCase()),
    [words],
  )

  const speak = useCallback(
    async (entry: VocabEntry | string) => {
      const text = typeof entry === 'string' ? entry : entry.text
      await speakText(text, uiRef.current.settings.accent)
    },
    [],
  )

  const lookupNow = useCallback(async (query: string) => {
    setUi((s) => ({ ...s, lookupLoading: true, lookupError: null }))
    try {
      let entry = await lookupWord(query)
      const near = await findNearWords(entry.text).catch(() => [] as string[])
      entry = { ...entry, nearWords: near }
      const existing = wordsRef.current.find(
        (w) => w.text.toLowerCase() === entry.text.toLowerCase(),
      )
      if (existing) {
        entry = {
          ...entry,
          id: existing.id,
          imageUrl: existing.imageUrl ?? entry.imageUrl,
          definitions: mergeDefs(existing.definitions, entry.definitions),
          sortOrder: existing.sortOrder,
          addedAtMillis: existing.addedAtMillis,
        }
      }
      setUi((s) => ({
        ...s,
        lookupLoading: false,
        lookupResult: { entry, saved: !!existing },
        lookupError: null,
      }))
      if (uiRef.current.settings.speakOnPageChange) {
        void speakText(entry.text, uiRef.current.settings.accent)
      }
    } catch (e) {
      setUi((s) => ({
        ...s,
        lookupLoading: false,
        lookupResult: null,
        lookupError: e instanceof Error ? e.message : '查询失败',
      }))
    }
  }, [])

  const setLookupQuery = useCallback(
    (query: string) => {
      setUi((s) => ({ ...s, lookupQuery: query, lookupError: null }))
      if (lookupTimer.current) window.clearTimeout(lookupTimer.current)
      const trimmed = query.trim()
      if (!trimmed) {
        setUi((s) => ({ ...s, lookupResult: null, lookupLoading: false }))
        return
      }
      lookupTimer.current = window.setTimeout(() => {
        void lookupNow(trimmed)
      }, 420)
    },
    [lookupNow],
  )

  const submitLookup = useCallback(() => {
    if (lookupTimer.current) window.clearTimeout(lookupTimer.current)
    const trimmed = uiRef.current.lookupQuery.trim()
    if (trimmed) void lookupNow(trimmed)
  }, [lookupNow])

  const upsertWord = useCallback((entry: VocabEntry): VocabEntry => {
    let saved: VocabEntry = entry
    setWords((prev) => {
      const idx = prev.findIndex((w) => w.text.toLowerCase() === entry.text.toLowerCase())
      if (idx >= 0) {
        saved = { ...prev[idx], ...entry, id: prev[idx].id }
        const next = [...prev]
        next[idx] = saved
        return next
      }
      const id = nextId()
      saved = {
        ...entry,
        id,
        sortOrder: prev.length ? Math.max(...prev.map((w) => w.sortOrder)) + 1 : 0,
        addedAtMillis: Date.now(),
      }
      return [...prev, saved]
    })
    return saved
  }, [])

  const toggleStar = useCallback(() => {
    const result = uiRef.current.lookupResult
    if (!result) return
    if (result.saved) {
      setWords((prev) => prev.filter((w) => w.id !== result.entry.id))
      setUi((s) => ({
        ...s,
        lookupResult: {
          entry: { ...result.entry, id: 0 },
          saved: false,
        },
      }))
    } else {
      const saved = upsertWord(result.entry)
      setUi((s) => ({ ...s, lookupResult: { entry: saved, saved: true } }))
    }
  }, [upsertWord])

  const toggleSaveRelatedWord = useCallback(
    async (entry: VocabEntry) => {
      const existing = wordsRef.current.find(
        (w) => w.text.toLowerCase() === entry.text.trim().toLowerCase(),
      )
      if (existing) {
        setWords((prev) => prev.filter((w) => w.id !== existing.id))
        setUi((s) => {
          const result = s.lookupResult
          if (result && result.entry.text.toLowerCase() === entry.text.toLowerCase()) {
            return {
              ...s,
              lookupResult: { entry: { ...result.entry, id: 0 }, saved: false },
            }
          }
          return s
        })
        return
      }
      try {
        let full = entry
        if (!entry.definitions.length) {
          full = await lookupWord(entry.text)
          const near = await findNearWords(full.text).catch(() => [] as string[])
          full = { ...full, nearWords: near }
        }
        const saved = upsertWord(full)
        setUi((s) => {
          const result = s.lookupResult
          if (result && result.entry.text.toLowerCase() === entry.text.toLowerCase()) {
            return { ...s, lookupResult: { entry: saved, saved: true } }
          }
          return s
        })
      } catch {
        upsertWord(entry)
      }
    },
    [upsertWord],
  )

  const deleteWord = useCallback((id: number) => {
    setWords((prev) => prev.filter((w) => w.id !== id))
    setUi((s) => {
      const result = s.lookupResult
      if (result?.entry.id === id) {
        return {
          ...s,
          lookupResult: { entry: { ...result.entry, id: 0 }, saved: false },
        }
      }
      return s
    })
  }, [])

  const updateDefinitions = useCallback((entryId: number, definitions: Definition[]) => {
    if (entryId > 0) {
      setWords((prev) =>
        prev.map((w) => (w.id === entryId ? { ...w, definitions } : w)),
      )
    }
    setUi((s) => {
      const result = s.lookupResult
      if (!result) return s
      if (result.entry.id === entryId || entryId === 0) {
        return {
          ...s,
          lookupResult: { ...result, entry: { ...result.entry, definitions } },
        }
      }
      return s
    })
  }, [])

  const setEntryImage = useCallback(async (id: number, file: File) => {
    setUi((s) => ({ ...s, imageBusy: true, imageError: null }))
    try {
      const url = await fileToDataUrl(file)
      setWords((prev) => prev.map((w) => (w.id === id ? { ...w, imageUrl: url } : w)))
      setUi((s) => {
        const result = s.lookupResult
        if (result?.entry.id === id) {
          return {
            ...s,
            imageBusy: false,
            lookupResult: { ...result, entry: { ...result.entry, imageUrl: url } },
          }
        }
        return { ...s, imageBusy: false }
      })
    } catch {
      setUi((s) => ({ ...s, imageBusy: false, imageError: '选图失败，请换一张再试' }))
    }
  }, [])

  const generateAiImage = useCallback(async (id: number, meaningHint: string) => {
    setUi((s) => ({ ...s, imageBusy: true, imageError: null }))
    try {
      const entry = wordsRef.current.find((w) => w.id === id)
      if (!entry) throw new Error('missing')
      const url = await generateAiImageUrl(
        entry.text,
        meaningHint || entry.definitions[0]?.meaning,
      )
      setWords((prev) => prev.map((w) => (w.id === id ? { ...w, imageUrl: url } : w)))
      setUi((s) => {
        const result = s.lookupResult
        if (result?.entry.id === id) {
          return {
            ...s,
            imageBusy: false,
            lookupResult: { ...result, entry: { ...result.entry, imageUrl: url } },
          }
        }
        return { ...s, imageBusy: false }
      })
    } catch {
      setUi((s) => ({
        ...s,
        imageBusy: false,
        imageError: 'AI 生图失败，请检查网络后再试（免费接口有时会忙）',
      }))
    }
  }, [])

  const studyDeck = useCallback((): VocabEntry[] => {
    const filteredNow = filterAndSort(wordsRef.current, uiRef.current)
    const order = uiRef.current.shuffledIds
    if (!order) return filteredNow
    const map = new Map(filteredNow.map((w) => [w.id, w]))
    return [
      ...order.map((id) => map.get(id)).filter((w): w is VocabEntry => !!w),
      ...filteredNow.filter((w) => !order.includes(w.id)),
    ]
  }, [])

  const stopAutoPlay = useCallback(() => {
    if (autoPlayTimer.current) {
      window.clearInterval(autoPlayTimer.current)
      autoPlayTimer.current = null
    }
    setUi((s) => ({ ...s, playing: false }))
    stopSpeaking()
  }, [])

  const selectCard = useCallback(
    (index: number) => {
      const deck = studyDeck()
      if (!deck.length) return
      const next = Math.max(0, Math.min(index, deck.length - 1))
      setUi((s) => {
        if (s.cardIndex === next) return s
        return { ...s, cardIndex: next }
      })
      const word = deck[next]
      if (uiRef.current.settings.speakOnPageChange && !uiRef.current.playing) {
        void speak(word)
      }
    },
    [speak, studyDeck],
  )

  const step = useCallback(
    (delta: number) => {
      const deck = studyDeck()
      if (!deck.length) return
      const settings = uiRef.current.settings
      let next = uiRef.current.cardIndex + delta
      if (settings.loop) {
        next = ((next % deck.length) + deck.length) % deck.length
      } else {
        next = Math.max(0, Math.min(next, deck.length - 1))
      }
      selectCard(next)
    },
    [selectCard, studyDeck],
  )

  const startAutoPlay = useCallback(() => {
    stopAutoPlay()
    setUi((s) => ({ ...s, playing: true }))
    const tick = () => {
      const deck = studyDeck()
      if (!deck.length) {
        stopAutoPlay()
        return
      }
      const word = deck[uiRef.current.cardIndex]
      void speak(word).then(() => {
        window.setTimeout(() => {
          if (!uiRef.current.playing) return
          step(1)
        }, uiRef.current.settings.autoPlayIntervalMs)
      })
    }
    tick()
    autoPlayTimer.current = window.setInterval(tick, Math.max(uiRef.current.settings.autoPlayIntervalMs + 1500, 3000))
  }, [speak, step, stopAutoPlay, studyDeck])

  const toggleAutoPlay = useCallback(() => {
    if (uiRef.current.playing) stopAutoPlay()
    else startAutoPlay()
  }, [startAutoPlay, stopAutoPlay])

  const openCard = useCallback(
    (shuffled: boolean) => {
      stopAutoPlay()
      const filteredNow = filterAndSort(wordsRef.current, uiRef.current)
      const order = shuffled ? [...filteredNow].sort(() => Math.random() - 0.5).map((w) => w.id) : null
      setUi((s) => ({ ...s, cardIndex: 0, shuffledIds: order, playing: false }))
      const deck =
        order == null
          ? filteredNow
          : order
              .map((id) => filteredNow.find((w) => w.id === id))
              .filter((w): w is VocabEntry => !!w)
      const word = deck[0]
      if (word && uiRef.current.settings.speakOnPageChange) void speak(word)
    },
    [speak, stopAutoPlay],
  )

  const toggleShuffle = useCallback(() => {
    const filteredNow = filterAndSort(wordsRef.current, uiRef.current)
    const enabling = uiRef.current.shuffledIds == null
    const currentId = studyDeck()[uiRef.current.cardIndex]?.id
    const order = enabling
      ? [...filteredNow].sort(() => Math.random() - 0.5).map((w) => w.id)
      : null
    const deck =
      order == null
        ? filteredNow
        : order.map((id) => filteredNow.find((w) => w.id === id)).filter((w): w is VocabEntry => !!w)
    const index = Math.max(0, deck.findIndex((w) => w.id === currentId))
    setUi((s) => ({ ...s, shuffledIds: order, cardIndex: index }))
  }, [studyDeck])

  const exportJson = useCallback(() => exportNotebookJson(wordsRef.current), [])

  const importJson = useCallback((json: string): NotebookImportResult => {
    const incoming = importNotebookJson(json)
    if (!incoming.length) throw new Error('备份中没有词条')
    let added = 0
    let updated = 0
    setWords((prev) => {
      const map = new Map(prev.map((w) => [w.text.toLowerCase(), w]))
      for (const entry of incoming) {
        const key = entry.text.toLowerCase()
        const existing = map.get(key)
        if (existing) {
          updated++
          map.set(key, {
            ...existing,
            ...entry,
            id: existing.id,
            definitions: mergeDefs(existing.definitions, entry.definitions),
            imageUrl: existing.imageUrl,
          })
        } else {
          added++
          const id = nextId()
          map.set(key, {
            ...entry,
            id,
            sortOrder: map.size,
            addedAtMillis: entry.addedAtMillis || Date.now(),
          })
        }
      }
      return [...map.values()]
    })
    return { added, updated }
  }, [])

  return {
    words,
    filtered,
    ui,
    setUi,
    updateSettings,
    setLookupQuery,
    submitLookup,
    toggleStar,
    speak,
    speakText: (t: string) => speak(t),
    isWordSaved,
    toggleSaveRelatedWord,
    deleteWord,
    updateDefinitions,
    setEntryImage,
    generateAiImage,
    clearImageError: () => setUi((s) => ({ ...s, imageError: null })),
    setFilter: (filter: WordFilter) => setUi((s) => ({ ...s, filter })),
    cycleSort: () =>
      setUi((s) => {
        const order: SortMode[] = ['MANUAL', 'TIME_DESC', 'TIME_ASC', 'ALPHA']
        const i = order.indexOf(s.sortMode)
        return { ...s, sortMode: order[(i + 1) % order.length] }
      }),
    setQuery: (query: string) => setUi((s) => ({ ...s, query })),
    setSearching: (searching: boolean) =>
      setUi((s) => ({ ...s, searching, query: searching ? s.query : '' })),
    toggleHideDefinitions: () =>
      setUi((s) => ({ ...s, hideDefinitions: !s.hideDefinitions, revealedIds: [] })),
    toggleReveal: (id: number) =>
      setUi((s) => {
        const set = new Set(s.revealedIds)
        if (set.has(id)) set.delete(id)
        else set.add(id)
        return { ...s, revealedIds: [...set] }
      }),
    openCard,
    studyDeck,
    step,
    selectCard,
    toggleAutoPlay,
    stopAutoPlay,
    toggleShuffle,
    speakCurrent: () => {
      const w = studyDeck()[uiRef.current.cardIndex]
      if (w) void speak(w)
    },
    exportJson,
    importJson,
    suggestedExportFileName,
    resetSettings: () => updateSettings(() => ({ ...DEFAULT_SETTINGS })),
  }
}

function mergeDefs(existing: Definition[], incoming: Definition[]): Definition[] {
  const out = [...existing]
  for (const d of incoming) {
    const key = `${d.pos}|${d.meaning}`
    if (!out.some((x) => `${x.pos}|${x.meaning}` === key)) out.push(d)
  }
  return out
}

export type HotWordsApi = ReturnType<typeof useHotWords>
