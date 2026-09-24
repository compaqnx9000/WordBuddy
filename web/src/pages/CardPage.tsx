import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { speakText } from '../lib/tts'
import type { VocabEntry } from '../types'
import { defLabel } from '../types'

export function CardPage() {
  const [params] = useSearchParams()
  const notebookId = Number(params.get('notebook') ?? 0)
  const startIndex = Math.max(0, Number(params.get('index') ?? 0))

  const [words, setWords] = useState<VocabEntry[]>([])
  const [index, setIndex] = useState(startIndex)
  const [showMeaning, setShowMeaning] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!notebookId) {
      setError('未指定生词本')
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const page = await api.listWords(notebookId, null, 500)
      setWords(page.items)
      setIndex(Math.min(startIndex, Math.max(0, page.items.length - 1)))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [notebookId, startIndex])

  useEffect(() => {
    void load()
  }, [load])

  const current = words[index]

  useEffect(() => {
    setShowMeaning(false)
  }, [index])

  useEffect(() => {
    if (!current) return
    void speakText(current.text, 'US')
  }, [current])

  function prev() {
    setIndex((i) => Math.max(0, i - 1))
  }

  function next() {
    setIndex((i) => Math.min(words.length - 1, i + 1))
  }

  if (loading) {
    return (
      <div className="page">
        <p className="muted">加载卡片…</p>
      </div>
    )
  }

  if (error || !current) {
    return (
      <div className="page">
        <div className="alert alert-error">{error ?? '没有可复习的单词'}</div>
        <Link to="/notebook">返回生词本</Link>
      </div>
    )
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">卡片复习</h1>
        <p className="page-desc">
          第 {index + 1} / {words.length} 张 · 点击显示释义
        </p>
      </header>

      <div className="card flashcard">
        <p className="flashcard-word">{current.text}</p>
        {(current.ipaUk || current.ipaUs) && (
          <p className="muted" style={{ marginBottom: 16 }}>
            {current.ipaUk && <>英 /{current.ipaUk}/ </>}
            {current.ipaUs && <>美 /{current.ipaUs}/</>}
          </p>
        )}
        {showMeaning ? (
          <div className="flashcard-meaning">
            {current.definitions.map(defLabel).join('；') || '暂无释义'}
          </div>
        ) : (
          <button type="button" className="btn btn-ghost" onClick={() => setShowMeaning(true)}>
            显示释义
          </button>
        )}
        <div className="flashcard-nav">
          <button type="button" className="btn btn-ghost" disabled={index <= 0} onClick={prev}>
            上一张
          </button>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void speakText(current.text, 'UK')}>
            英音
          </button>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void speakText(current.text, 'US')}>
            美音
          </button>
          <button type="button" className="btn btn-ghost" disabled={index >= words.length - 1} onClick={next}>
            下一张
          </button>
        </div>
      </div>

      <p style={{ marginTop: 20 }}>
        <Link to="/notebook">← 返回列表</Link>
      </p>
    </div>
  )
}
