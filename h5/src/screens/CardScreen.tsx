import { useEffect, useState, type SyntheticEvent } from 'react'
import { DefinitionList } from '../components/DefinitionList'
import { EditMeaningDialog } from '../components/EditMeaning'
import { highlightHeadword } from '../components/ExampleBlock'
import { ImageSourceDialog } from '../components/ImageDialog'
import { RelatedWordsRow } from '../components/RelatedWords'
import { useHorizontalSwipe } from '../hooks/useHorizontalSwipe'
import type { HotWordsApi } from '../store/useHotWords'
import type { ExampleSentence, VocabEntry } from '../types'

export function CardScreen({
  api,
  onBack,
  onOpenSettings,
}: {
  api: HotWordsApi
  onBack: () => void
  onOpenSettings: () => void
}) {
  const deck = api.studyDeck()
  const { ui } = api
  const index = deck.length ? Math.min(ui.cardIndex, deck.length - 1) : 0
  const entry = deck[index]
  const [exIndex, setExIndex] = useState(0)
  const [editOpen, setEditOpen] = useState(false)
  const [imageOpen, setImageOpen] = useState(false)

  useEffect(() => {
    setExIndex(0)
  }, [entry?.id])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') api.step(-1)
      if (e.key === 'ArrowRight') api.step(1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [api])

  /** Swipe outside the example box flips words. */
  const wordSwipe = useHorizontalSwipe(
    (dir) => api.step(dir),
    { enabled: deck.length > 0 && !editOpen && !imageOpen },
  )

  return (
    <div className="screen card-screen">
      <div className="card-top">
        <button type="button" className="icon-btn" onClick={onBack} aria-label="返回">
          ←
        </button>
        <strong>生词本</strong>
        <button type="button" className="icon-btn" onClick={onOpenSettings} aria-label="设置">
          模式设置
        </button>
      </div>

      {!entry ? (
        <div className="center-empty">词库是空的</div>
      ) : (
        <>
          <div className="screen-scroll card-word-swipe" {...wordSwipe}>
            <CardBody
              entry={entry}
              accent={ui.settings.accent}
              exIndex={exIndex}
              onExIndex={setExIndex}
              onSpeakAccent={(uk) => {
                api.updateSettings((s) => ({ ...s, accent: uk ? 'UK' : 'US' }))
                void api.speak(entry)
              }}
              onSpeakWord={() => void api.speak(entry)}
              onEdit={() => setEditOpen(true)}
              onImage={() => setImageOpen(true)}
              api={api}
            />
          </div>
          <div className="card-nav">
            <button type="button" className="icon-btn" onClick={() => api.step(-1)}>
              ‹
            </button>
            <span>
              {index + 1} / {deck.length}
            </span>
            <button type="button" className="icon-btn" onClick={() => api.step(1)}>
              ›
            </button>
          </div>
          <div className="card-controls">
            <button type="button" className="icon-btn" onClick={api.toggleShuffle} aria-label="乱序">
              {ui.shuffledIds ? '乱序中' : '乱序'}
            </button>
            <button
              type="button"
              className="play-fab"
              onClick={api.toggleAutoPlay}
              aria-label="播放"
            >
              {ui.playing ? '❚❚' : '▶'}
            </button>
            <button type="button" className="icon-btn" onClick={api.speakCurrent} aria-label="朗读">
              ♪
            </button>
          </div>
        </>
      )}

      {editOpen && entry ? (
        <EditMeaningDialog
          definitions={entry.definitions}
          onSave={(defs) => api.updateDefinitions(entry.id, defs)}
          onClose={() => setEditOpen(false)}
        />
      ) : null}
      {imageOpen && entry ? (
        <ImageSourceDialog
          definitions={entry.definitions}
          busy={ui.imageBusy}
          error={ui.imageError}
          onPickFile={(file) => {
            void api.setEntryImage(entry.id, file)
            setImageOpen(false)
          }}
          onGenerateAi={(hint) => void api.generateAiImage(entry.id, hint)}
          onDismiss={() => {
            setImageOpen(false)
            api.clearImageError()
          }}
        />
      ) : null}
    </div>
  )
}

function CardBody({
  entry,
  exIndex,
  onExIndex,
  onSpeakAccent,
  onSpeakWord,
  onEdit,
  onImage,
  api,
}: {
  entry: VocabEntry
  exIndex: number
  onExIndex: (i: number) => void
  accent: 'UK' | 'US'
  onSpeakAccent: (uk: boolean) => void
  onSpeakWord: () => void
  onEdit: () => void
  onImage: () => void
  api: HotWordsApi
}) {
  return (
    <div className="card-page">
      <div className="lined-banner">
        <div className="word">{entry.text}</div>
      </div>

      <div className="ipa-row" style={{ justifyContent: 'stretch', marginTop: 12 }}>
        {entry.ipaUk ? (
          <button type="button" className="ipa-pill ipa-pill-wide" onClick={() => onSpeakAccent(true)}>
            英 /{entry.ipaUk}/
          </button>
        ) : null}
        {entry.ipaUs ? (
          <button type="button" className="ipa-pill ipa-pill-wide" onClick={() => onSpeakAccent(false)}>
            美 /{entry.ipaUs}/
          </button>
        ) : null}
      </div>

      <div style={{ marginTop: 14 }}>
        <DefinitionList definitions={entry.definitions} />
      </div>
      <button type="button" className="linkish" onClick={onEdit}>
        编辑释义
      </button>

      <button type="button" className="split-btn split-btn-block" onClick={onSpeakWord}>
        拆分发音
      </button>

      {entry.examples.length > 0 ? (
        <ExampleCarousel
          word={entry.text}
          examples={entry.examples}
          index={exIndex}
          onIndex={onExIndex}
          onSpeak={(t) => void api.speakText(t)}
        />
      ) : null}

      {entry.imageUrl ? <img className="thumb" src={entry.imageUrl} alt="" /> : null}
      <button type="button" className="linkish" onClick={onImage}>
        更换图片
      </button>

      <RelatedWordsRow
        entry={entry}
        isSaved={api.isWordSaved}
        onSpeak={(t) => void api.speakText(t)}
        onToggleStar={(e) => void api.toggleSaveRelatedWord(e)}
      />
    </div>
  )
}

/**
 * Nested pager: horizontal swipe only changes examples.
 * Stops propagation so the outer word swipe never fires here.
 */
function ExampleCarousel({
  word,
  examples,
  index,
  onIndex,
  onSpeak,
}: {
  word: string
  examples: ExampleSentence[]
  index: number
  onIndex: (i: number) => void
  onSpeak: (text: string) => void
}) {
  const safeIndex = examples.length ? Math.min(index, examples.length - 1) : 0

  const exampleSwipe = useHorizontalSwipe(
    (dir) => {
      if (examples.length <= 1) return
      const next = Math.max(0, Math.min(safeIndex + dir, examples.length - 1))
      onIndex(next)
    },
    { enabled: examples.length > 1 },
  )

  const trap = (e: SyntheticEvent) => {
    e.stopPropagation()
  }

  return (
    <div
      className="mint-carousel"
      onTouchStart={(e) => {
        trap(e)
        exampleSwipe.onTouchStart(e)
      }}
      onTouchMove={(e) => {
        trap(e)
        exampleSwipe.onTouchMove(e)
      }}
      onTouchEnd={(e) => {
        trap(e)
        exampleSwipe.onTouchEnd(e)
      }}
      onTouchCancel={(e) => {
        trap(e)
        exampleSwipe.onTouchCancel()
      }}
    >
      <div className="mint-label">例句</div>
      <div className="mint-viewport">
        <div
          className="mint-track"
          style={{ transform: `translateX(-${safeIndex * 100}%)` }}
        >
          {examples.map((ex, i) => (
            <div className="mint-slide" key={`${ex.english}-${i}`}>
              <div className="example-en mint-en">{highlightHeadword(ex.english, word)}</div>
              <div className="example-zh mint-zh">{ex.chinese}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="mint-actions">
        <button
          type="button"
          className="mint-action"
          onClick={() => onSpeak(examples[safeIndex]?.english ?? '')}
          aria-label="朗读例句"
        >
          ≈
        </button>
        <button
          type="button"
          className="mint-action"
          onClick={() => onSpeak(examples[safeIndex]?.english ?? '')}
          aria-label="慢速"
        >
          慢
        </button>
        <button type="button" className="mint-action muted-action" aria-label="跟读" disabled>
          ⌘
        </button>
      </div>

      {examples.length > 1 ? (
        <div className="dots">
          {examples.map((_, i) => (
            <button
              key={i}
              type="button"
              className={`dot${i === safeIndex ? ' active' : ''}`}
              onClick={() => onIndex(i)}
              aria-label={`例句 ${i + 1}`}
            />
          ))}
        </div>
      ) : null}
    </div>
  )
}
