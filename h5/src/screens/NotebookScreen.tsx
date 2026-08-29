import { useState } from 'react'
import { DefinitionList } from '../components/DefinitionList'
import { EditMeaningDialog } from '../components/EditMeaning'
import { ExampleSentencesBlock } from '../components/ExampleBlock'
import { ImageSourceDialog } from '../components/ImageDialog'
import { RelatedWordsRow } from '../components/RelatedWords'
import type { HotWordsApi } from '../store/useHotWords'
import type { SortMode, VocabEntry, WordFilter } from '../types'

const SORT_LABEL: Record<SortMode, string> = {
  MANUAL: '手动',
  TIME_DESC: '新→旧',
  TIME_ASC: '旧→新',
  ALPHA: 'A→Z',
}

export function NotebookScreen({
  api,
  onCardMode,
  onRecite,
}: {
  api: HotWordsApi
  onCardMode: () => void
  onRecite: () => void
}) {
  const { ui, filtered } = api
  const [editEntry, setEditEntry] = useState<VocabEntry | null>(null)
  const [imageEntry, setImageEntry] = useState<VocabEntry | null>(null)

  return (
    <div className="screen">
      <div className="notebook-top">
        {(['ALL', 'WORDS', 'PHRASES'] as WordFilter[]).map((f) => (
          <button
            key={f}
            type="button"
            className={`pill${ui.filter === f ? ' active' : ''}`}
            onClick={() => api.setFilter(f)}
          >
            {f === 'ALL' ? '全部' : f === 'WORDS' ? '单词' : '短语'}
          </button>
        ))}
        <button type="button" className="pill" onClick={api.cycleSort}>
          排序 · {SORT_LABEL[ui.sortMode]}
        </button>
        <button type="button" className="pill" onClick={api.toggleHideDefinitions}>
          {ui.hideDefinitions ? '隐藏释义' : '显示释义'}
        </button>
        <button
          type="button"
          className="pill"
          onClick={() => api.setSearching(!ui.searching)}
        >
          搜索
        </button>
        {ui.searching ? (
          <input
            className="search-input"
            placeholder="搜单词 / 释义"
            value={ui.query}
            onChange={(e) => api.setQuery(e.target.value)}
            autoFocus
          />
        ) : null}
      </div>
      <div className="notebook-actions">
        <button type="button" className="action-btn" onClick={onCardMode}>
          卡片模式
        </button>
        <button type="button" className="action-btn ghost" onClick={onRecite}>
          乱序背诵
        </button>
      </div>
      <div className="screen-scroll" style={{ paddingTop: 0 }}>
        {!filtered.length ? (
          <div className="center-empty">生词本还是空的</div>
        ) : (
          filtered.map((entry) => {
            const revealed = ui.revealedIds.includes(entry.id)
            const masked = ui.hideDefinitions && !revealed
            return (
              <article key={entry.id} className="word-card">
                <div className="word-card-head">
                  <strong style={{ fontSize: '1.15rem', flex: 1 }}>{entry.text}</strong>
                  <button
                    type="button"
                    className="icon-btn"
                    onClick={() => void api.speak(entry)}
                    aria-label="朗读"
                  >
                    ♪
                  </button>
                  <button
                    type="button"
                    className="icon-btn"
                    style={{ color: 'var(--danger)' }}
                    onClick={() => {
                      if (confirm(`删除「${entry.text}」？`)) api.deleteWord(entry.id)
                    }}
                  >
                    ⌫
                  </button>
                </div>
                {masked ? (
                  <button
                    type="button"
                    className="mask"
                    onClick={() => api.toggleReveal(entry.id)}
                  >
                    点击显示释义
                  </button>
                ) : (
                  <>
                    <div style={{ marginTop: 10 }}>
                      <DefinitionList definitions={entry.definitions} />
                    </div>
                    <button type="button" className="linkish" onClick={() => setEditEntry(entry)}>
                      编辑释义
                    </button>
                    {entry.imageUrl ? <img className="thumb" src={entry.imageUrl} alt="" /> : null}
                    <button type="button" className="linkish" onClick={() => setImageEntry(entry)}>
                      换图
                    </button>
                    <ExampleSentencesBlock
                      headword={entry.text}
                      examples={entry.examples}
                      onSpeak={(t) => void api.speakText(t)}
                    />
                    <RelatedWordsRow
                      entry={entry}
                      isSaved={api.isWordSaved}
                      onSpeak={(t) => void api.speakText(t)}
                      onToggleStar={(e) => void api.toggleSaveRelatedWord(e)}
                    />
                    {ui.hideDefinitions ? (
                      <button
                        type="button"
                        className="linkish"
                        onClick={() => api.toggleReveal(entry.id)}
                      >
                        重新遮挡
                      </button>
                    ) : null}
                  </>
                )}
              </article>
            )
          })
        )}
      </div>

      {editEntry ? (
        <EditMeaningDialog
          definitions={editEntry.definitions}
          onSave={(defs) => api.updateDefinitions(editEntry.id, defs)}
          onClose={() => setEditEntry(null)}
        />
      ) : null}
      {imageEntry ? (
        <ImageSourceDialog
          definitions={imageEntry.definitions}
          busy={ui.imageBusy}
          error={ui.imageError}
          onPickFile={(file) => {
            void api.setEntryImage(imageEntry.id, file)
            setImageEntry(null)
          }}
          onGenerateAi={(hint) => void api.generateAiImage(imageEntry.id, hint)}
          onDismiss={() => {
            setImageEntry(null)
            api.clearImageError()
          }}
        />
      ) : null}
    </div>
  )
}
