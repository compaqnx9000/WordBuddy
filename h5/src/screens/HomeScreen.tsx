import { useState } from 'react'
import { ProfileHeader } from '../components/Chrome'
import { DefinitionList } from '../components/DefinitionList'
import { EditMeaningDialog } from '../components/EditMeaning'
import { ExampleSentencesBlock } from '../components/ExampleBlock'
import { ImageSourceDialog } from '../components/ImageDialog'
import { RelatedWordsRow } from '../components/RelatedWords'
import type { HotWordsApi } from '../store/useHotWords'

export function HomeScreen({
  api,
  onOpenSettings,
}: {
  api: HotWordsApi
  onOpenSettings: () => void
}) {
  const { ui, filtered } = api
  const [editOpen, setEditOpen] = useState(false)
  const [imageOpen, setImageOpen] = useState(false)
  const result = ui.lookupResult

  return (
    <div className="screen">
      <ProfileHeader
        userName={ui.settings.displayName}
        wordCount={filtered.length}
        onToggleTheme={() =>
          api.updateSettings((s) => ({
            ...s,
            appTheme: s.appTheme === 'Light' ? 'Dark' : 'Light',
          }))
        }
        onOpenSettings={onOpenSettings}
      />
      <div className="lookup-box">
        <textarea
          placeholder="输入英文单词或短语…"
          value={ui.lookupQuery}
          onChange={(e) => api.setLookupQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              api.submitLookup()
            }
          }}
        />
      </div>
      <div className="screen-scroll">
        {ui.lookupLoading ? <div className="loading">查询中…</div> : null}
        {ui.lookupError ? (
          <div className="loading" style={{ color: 'var(--danger)' }}>
            {ui.lookupError}
          </div>
        ) : null}
        {result ? (
          <>
            <div className="word-title-row">
              <div className="word-title">{result.entry.text}</div>
              <button
                type="button"
                className="icon-btn"
                onClick={() => void api.speak(result.entry)}
                aria-label="朗读"
              >
                ♪
              </button>
              <button type="button" className="icon-btn" onClick={api.toggleStar} aria-label="收藏">
                <span style={{ color: result.saved ? 'var(--star)' : undefined }}>
                  {result.saved ? '★' : '☆'}
                </span>
              </button>
            </div>
            <div className="ipa-row">
              {result.entry.ipaUk ? (
                <button
                  type="button"
                  className="ipa-pill"
                  onClick={() => {
                    api.updateSettings((s) => ({ ...s, accent: 'UK' }))
                    void api.speak(result.entry)
                  }}
                >
                  英 /{result.entry.ipaUk}/
                </button>
              ) : null}
              {result.entry.ipaUs ? (
                <button
                  type="button"
                  className="ipa-pill"
                  onClick={() => {
                    api.updateSettings((s) => ({ ...s, accent: 'US' }))
                    void api.speak(result.entry)
                  }}
                >
                  美 /{result.entry.ipaUs}/
                </button>
              ) : null}
            </div>
            <DefinitionList definitions={result.entry.definitions} />
            <button type="button" className="linkish" onClick={() => setEditOpen(true)}>
              编辑释义
            </button>
            {result.entry.imageUrl ? <img className="thumb" src={result.entry.imageUrl} alt="" /> : null}
            {result.saved ? (
              <button type="button" className="linkish" onClick={() => setImageOpen(true)}>
                换图
              </button>
            ) : null}
            <ExampleSentencesBlock
              headword={result.entry.text}
              examples={result.entry.examples}
              onSpeak={(t) => void api.speakText(t)}
            />
            <RelatedWordsRow
              entry={result.entry}
              isSaved={api.isWordSaved}
              onSpeak={(t) => void api.speakText(t)}
              onToggleStar={(e) => void api.toggleSaveRelatedWord(e)}
              onLookup={(w) => api.setLookupQuery(w)}
            />
          </>
        ) : !ui.lookupLoading && !ui.lookupQuery.trim() ? (
          <div className="center-empty">在上方输入单词开始查询</div>
        ) : null}
      </div>

      {editOpen && result ? (
        <EditMeaningDialog
          definitions={result.entry.definitions}
          onSave={(defs) => api.updateDefinitions(result.entry.id, defs)}
          onClose={() => setEditOpen(false)}
        />
      ) : null}
      {imageOpen && result ? (
        <ImageSourceDialog
          definitions={result.entry.definitions}
          busy={ui.imageBusy}
          error={ui.imageError}
          onPickFile={(file) => {
            void api.setEntryImage(result.entry.id, file)
            setImageOpen(false)
          }}
          onGenerateAi={(hint) => void api.generateAiImage(result.entry.id, hint)}
          onDismiss={() => {
            setImageOpen(false)
            api.clearImageError()
          }}
        />
      ) : null}
    </div>
  )
}
