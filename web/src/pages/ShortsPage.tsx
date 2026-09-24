import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, type ShortClip } from '../api/client'

export function ShortsPage() {
  const [clips, setClips] = useState<ShortClip[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [active, setActive] = useState<ShortClip | null>(null)
  const [tab, setTab] = useState<'feed' | 'favorites'>('feed')

  const loadFeed = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const items = await api.shortsFeed(30)
      setClips(items)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  const loadFavorites = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const items = await api.listShortFavorites()
      setClips(items)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (tab === 'feed') void loadFeed()
    else void loadFavorites()
  }, [tab, loadFeed, loadFavorites])

  async function toggleFavorite(clip: ShortClip) {
    try {
      const next = await api.shortFavorite(clip.id, !clip.favorited)
      setClips((list) => list.map((c) => (c.id === clip.id ? { ...c, favorited: next } : c)))
      if (active?.id === clip.id) setActive({ ...clip, favorited: next })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '操作失败')
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">短视频</h1>
        <p className="page-desc">精选口语短视频，配合字幕练听力（无广告、无激励视频）</p>
      </header>

      <div className="tabs">
        <button type="button" className={tab === 'feed' ? 'tab active' : 'tab'} onClick={() => setTab('feed')}>
          推荐
        </button>
        <button
          type="button"
          className={tab === 'favorites' ? 'tab active' : 'tab'}
          onClick={() => setTab('favorites')}
        >
          收藏
        </button>
        <button type="button" className="tab" onClick={() => (tab === 'feed' ? loadFeed() : loadFavorites())}>
          刷新
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <p className="muted">加载中…</p>}

      {!loading && clips.length === 0 && (
        <div className="card">
          <p className="muted" style={{ margin: 0 }}>
            暂无视频，请稍后再试。
          </p>
        </div>
      )}

      <div className="shorts-grid">
        {clips.map((clip) => (
          <article key={clip.id} className="short-tile" onClick={() => setActive(clip)}>
            <div className="short-cover">
              {clip.coverUrl ? (
                <img src={clip.coverUrl} alt="" loading="lazy" />
              ) : (
                <span>{clip.categoryName}</span>
              )}
            </div>
            <div className="short-meta">
              <h3>{clip.title || '词搭子短视频'}</h3>
              <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>
                {clip.author}
                {clip.favorited ? ' · 已收藏' : ''}
              </p>
            </div>
          </article>
        ))}
      </div>

      {active && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={() => setActive(null)}>
          <div className="modal-panel" onClick={(ev) => ev.stopPropagation()}>
            <video src={active.videoUrl} controls autoPlay playsInline />
            <div className="modal-body">
              <h3 style={{ margin: '0 0 8px' }}>{active.title}</h3>
              {active.caption && <p className="muted">{active.caption}</p>}
              {active.relatedWords.length > 0 && (
                <p style={{ fontSize: '0.9rem' }}>
                  相关词：{active.relatedWords.join(' · ')}
                </p>
              )}
              <div className="toolbar" style={{ marginTop: 12, marginBottom: 0 }}>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => void toggleFavorite(active)}>
                  {active.favorited ? '取消收藏' : '收藏'}
                </button>
                <button type="button" className="btn btn-primary btn-sm" onClick={() => setActive(null)}>
                  关闭
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
