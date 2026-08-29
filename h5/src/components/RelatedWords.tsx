import { useMemo, useState } from 'react'
import type { VocabEntry } from '../types'

type Tab = 'near' | 'syn' | 'ant'

export function RelatedWordsRow({
  entry,
  isSaved,
  onSpeak,
  onToggleStar,
  onLookup,
}: {
  entry: VocabEntry
  isSaved: (word: string) => boolean
  onSpeak: (text: string) => void
  onToggleStar: (entry: VocabEntry) => void
  onLookup?: (word: string) => void
}) {
  const tabs = useMemo(() => {
    const list: { id: Tab; label: string; words: string[]; cls: string }[] = []
    if (entry.nearWords.length) list.push({ id: 'near', label: '形近', words: entry.nearWords, cls: 'near' })
    if (entry.synonyms.length) list.push({ id: 'syn', label: '近义', words: entry.synonyms, cls: 'syn' })
    if (entry.antonyms.length) list.push({ id: 'ant', label: '反义', words: entry.antonyms, cls: 'ant' })
    return list
  }, [entry])

  const [tab, setTab] = useState<Tab>(tabs[0]?.id ?? 'syn')
  const [popup, setPopup] = useState<string | null>(null)

  if (!tabs.length) return null
  const active = tabs.find((t) => t.id === tab) ?? tabs[0]

  return (
    <div className="related">
      <div className="related-tabs">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            className={`related-tab${active.id === t.id ? ' active' : ''}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div className="chips">
        {active.words.map((w) => (
          <button
            key={w}
            type="button"
            className={`chip ${active.cls}`}
            onClick={() => setPopup(w)}
          >
            {w}
          </button>
        ))}
      </div>

      {popup ? (
        <>
          <div className="modal-backdrop" onClick={() => setPopup(null)} />
          <div className="popup">
            <div className="modal-title">{popup}</div>
            <div className="modal-actions">
              <button type="button" onClick={() => { onSpeak(popup); }}>
                朗读
              </button>
              <button
                type="button"
                className="ghost"
                onClick={() => {
                  onToggleStar({
                    id: 0,
                    text: popup,
                    isPhrase: false,
                    definitions: [],
                    examples: [],
                    nearWords: [],
                    synonyms: [],
                    antonyms: [],
                    sortOrder: 0,
                    addedAtMillis: Date.now(),
                  })
                  setPopup(null)
                }}
              >
                {isSaved(popup) ? '取消收藏' : '加入生词本'}
              </button>
              {onLookup ? (
                <button
                  type="button"
                  className="ghost"
                  onClick={() => {
                    onLookup(popup)
                    setPopup(null)
                  }}
                >
                  查词
                </button>
              ) : null}
              <button type="button" className="ghost" onClick={() => setPopup(null)}>
                关闭
              </button>
            </div>
          </div>
        </>
      ) : null}
    </div>
  )
}
