import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError, type Notebook } from '../api/client'
import { useSession } from '../api/session'
import type { VocabEntry } from '../types'
import { defLabel } from '../types'

export function NotebookPage() {
  const { user } = useSession()
  const navigate = useNavigate()
  const [notebooks, setNotebooks] = useState<Notebook[]>([])
  const [notebookId, setNotebookId] = useState<number | null>(null)
  const [words, setWords] = useState<VocabEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [newName, setNewName] = useState('')
  const [creating, setCreating] = useState(false)

  const loadNotebooks = useCallback(async () => {
    const list = await api.listNotebooks()
    setNotebooks(list)
    const preferred = user?.vocabNotebookId
    const pick = list.find((n) => n.id === preferred) ?? list[0]
    setNotebookId(pick?.id ?? null)
  }, [user?.vocabNotebookId])

  const loadWords = useCallback(async (id: number) => {
    const page = await api.listWords(id, null, 500)
    setWords(page.items)
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        await loadNotebooks()
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : '加载失败')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [loadNotebooks])

  useEffect(() => {
    if (!notebookId) return
    let cancelled = false
    ;(async () => {
      try {
        await loadWords(notebookId)
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : '单词加载失败')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [notebookId, loadWords])

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return words
    return words.filter((w) => w.text.toLowerCase().includes(q))
  }, [words, filter])

  async function handleCreateNotebook() {
    const name = newName.trim()
    if (!name) return
    setCreating(true)
    try {
      const nb = await api.createNotebook(name)
      setNewName('')
      await loadNotebooks()
      setNotebookId(nb.id)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '创建失败')
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(id: number) {
    if (!window.confirm('确定删除该生词本？本内单词将一并移除。')) return
    try {
      await api.deleteNotebook(id)
      await loadNotebooks()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '删除失败')
    }
  }

  async function handleDeleteWord(id: number) {
    if (!window.confirm('从生词本移除该词？')) return
    try {
      await api.deleteWord(id)
      if (notebookId) await loadWords(notebookId)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '删除失败')
    }
  }

  function openCard(index: number) {
    if (!notebookId) return
    navigate(`/notebook/card?notebook=${notebookId}&index=${index}`)
  }

  const current = notebooks.find((n) => n.id === notebookId)

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">生词本</h1>
        <p className="page-desc">管理单词列表，进入卡片模式背诵复习</p>
      </header>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="toolbar">
        <select
          className="select"
          value={notebookId ?? ''}
          onChange={(ev) => setNotebookId(Number(ev.target.value))}
          disabled={loading || notebooks.length === 0}
        >
          {notebooks.map((nb) => (
            <option key={nb.id} value={nb.id}>
              {nb.name}（{nb.wordCount || '—'}）
            </option>
          ))}
        </select>
        <input
          className="input"
          style={{ maxWidth: 220 }}
          placeholder="搜索单词…"
          value={filter}
          onChange={(ev) => setFilter(ev.target.value)}
        />
        {filtered.length > 0 && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => openCard(0)}>
            卡片模式
          </button>
        )}
        {current && current.kind === 'user' && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void handleDelete(current.id)}>
            删除当前本
          </button>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="input-row">
          <input
            className="input"
            placeholder="新建生词本名称"
            value={newName}
            onChange={(ev) => setNewName(ev.target.value)}
          />
          <button type="button" className="btn btn-ghost" disabled={creating} onClick={() => void handleCreateNotebook()}>
            {creating ? '创建中…' : '新建'}
          </button>
        </div>
      </div>

      {loading ? (
        <p className="muted">加载中…</p>
      ) : filtered.length === 0 ? (
        <div className="card">
          <p className="muted" style={{ margin: 0 }}>
            还没有单词。去 <Link to="/">查词</Link> 页搜索后加入生词本吧。
          </p>
        </div>
      ) : (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="word-table">
            <thead>
              <tr>
                <th>单词</th>
                <th>释义</th>
                <th style={{ width: 140 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((w) => {
                const idx = words.findIndex((x) => x.id === w.id)
                return (
                  <tr key={w.id}>
                    <td>
                      <strong>{w.text}</strong>
                    </td>
                    <td className="muted">{w.definitions.map(defLabel).join('；') || '—'}</td>
                    <td>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => openCard(idx >= 0 ? idx : 0)}>
                        卡片
                      </button>{' '}
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => void handleDeleteWord(w.id)}>
                        移除
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
