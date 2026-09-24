import { useState } from 'react'
import { LIVE_RADIO_SHOWS, type PodcastEpisode, type PodcastShow } from '../lib/podcastCatalog'

export function PodcastPage() {
  const [playing, setPlaying] = useState<{ show: PodcastShow; episode: PodcastEpisode } | null>(null)

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">播客 · 电台</h1>
        <p className="page-desc">NPR、LBC、China Plus 英文直播流，适合磨耳朵（HTML5 播放器）</p>
      </header>

      <div className="podcast-grid">
        {LIVE_RADIO_SHOWS.map((show) => (
          <article key={show.id} className="card podcast-card">
            <div className="toolbar" style={{ marginBottom: 8 }}>
              <span className="tag" style={{ borderLeft: `3px solid ${show.accentColor}` }}>
                {show.accentLabel}
              </span>
              <span className="tag">直播</span>
            </div>
            <h3>{show.title}</h3>
            <p className="muted" style={{ margin: '0 0 8px', fontSize: '0.9rem' }}>
              {show.host}
            </p>
            <p style={{ margin: '0 0 16px' }}>{show.blurb}</p>
            {show.episodes.map((ep) => (
              <button
                key={ep.id}
                type="button"
                className="btn btn-primary btn-sm"
                style={{ marginRight: 8 }}
                onClick={() => setPlaying({ show, episode: ep })}
              >
                播放 · {ep.title}
              </button>
            ))}
          </article>
        ))}
      </div>

      {playing && (
        <div className="player-bar">
          <strong>
            正在播放：{playing.show.title} — {playing.episode.title}
          </strong>
          <p className="muted" style={{ margin: '6px 0 0' }}>
            {playing.episode.summary ?? playing.show.blurb}
          </p>
          <audio key={playing.episode.audioUrl} src={playing.episode.audioUrl} controls autoPlay />
          <p className="muted" style={{ marginTop: 10, fontSize: '0.8rem' }}>
            若无法播放，可能是浏览器不支持该流格式（如 m3u8）；可换用 Chrome 或先在 App 播客页试听。
          </p>
        </div>
      )}
    </div>
  )
}
