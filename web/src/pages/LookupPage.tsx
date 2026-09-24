import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useSession } from '../api/session'
import { lookupWord } from '../lib/dictionary'
import { speakText } from '../lib/tts'
import type { VocabEntry } from '../types'
import { defLabel } from '../types'

export function LookupPage() {
  const { token, user } = useSession()
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [entry, setEntry] = useState<VocabEntry | null>(null)
  const [saveMsg, setSaveMsg] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSearch(e?: FormEvent) {
    e?.preventDefault()
    const q = query.trim()
    if (!q) return
    setLoading(true)
    setError(null)
    setSaveMsg(null)
    try {
      const result = await lookupWord(q)
      setEntry(result)
    } catch (err) {
      console.error('[lookup]', err)
      setEntry(null)
      setError('暂时查不到该词的释义，请换个词或稍后再试')
    } finally {
      setLoading(false)
    }
  }

  async function handleAddNotebook() {
    if (!entry || !token || !user) return
    const notebookId = user.vocabNotebookId
    if (!notebookId) {
      setSaveMsg('尚未分配生词本，请重新登录')
      return
    }
    setSaving(true)
    setSaveMsg(null)
    try {
      await api.createWord(notebookId, entry)
      setSaveMsg(`「${entry.text}」已加入生词本`)
    } catch (err) {
      setSaveMsg(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">查词</h1>
        <p className="page-desc">输入单词或短语，查看释义、音标与例句；支持英音 / 美音朗读</p>
      </header>

      <form className="lookup-search" onSubmit={(e) => void handleSearch(e)}>
        <input
          className="input"
          placeholder="例如：serendipity"
          value={query}
          onChange={(ev) => setQuery(ev.target.value)}
          autoFocus
        />
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? '查询中…' : '查询'}
        </button>
      </form>

      {error && <div className="alert alert-error">{error}</div>}

      {entry && (
        <article className="card">
          <div className="toolbar">
            <h2 className="word-headline" style={{ flex: 1, margin: 0 }}>
              {entry.text}
            </h2>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void speakText(entry.text, 'UK')}>
              英音
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void speakText(entry.text, 'US')}>
              美音
            </button>
            {token ? (
              <button
                type="button"
                className="btn btn-primary btn-sm"
                disabled={saving}
                onClick={() => void handleAddNotebook()}
              >
                加入生词本
              </button>
            ) : (
              <Link to="/login" className="btn btn-ghost btn-sm">
                登录后加入生词本
              </Link>
            )}
          </div>

          {saveMsg && (
            <div className={`alert ${saveMsg.includes('已加入') ? 'alert-success' : 'alert-info'}`}>{saveMsg}</div>
          )}

          <div className="ipa-row">
            {entry.ipaUk && <span>英 /{entry.ipaUk}/</span>}
            {entry.ipaUs && <span>美 /{entry.ipaUs}/</span>}
          </div>

          <ul className="def-list">
            {entry.definitions.map((d, i) => (
              <li key={i}>{defLabel(d)}</li>
            ))}
          </ul>

          {entry.examples.length > 0 && (
            <section style={{ marginTop: 20 }}>
              <h3 style={{ fontSize: '1rem', marginBottom: 12 }}>例句</h3>
              {entry.examples.slice(0, 5).map((ex, i) => (
                <div key={i} className="example-block">
                  <p style={{ margin: '0 0 4px' }}>{ex.english}</p>
                  {ex.chinese && <p className="muted" style={{ margin: 0 }}>{ex.chinese}</p>}
                </div>
              ))}
            </section>
          )}

          {(entry.synonyms.length > 0 || entry.antonyms.length > 0) && (
            <section style={{ marginTop: 20 }} className="muted">
              {entry.synonyms.length > 0 && <p>近义：{entry.synonyms.join(' · ')}</p>}
              {entry.antonyms.length > 0 && <p>反义：{entry.antonyms.join(' · ')}</p>}
            </section>
          )}
        </article>
      )}
    </div>
  )
}
