export function BottomBar({
  tab,
  onSelect,
}: {
  tab: 'home' | 'notebook' | 'me'
  onSelect: (t: 'home' | 'notebook' | 'me') => void
}) {
  return (
    <nav className="bottom-bar">
      <button
        type="button"
        className={`tab-btn${tab === 'home' ? ' active' : ''}`}
        onClick={() => onSelect('home')}
      >
        <HomeIcon />
        首页
      </button>
      <button
        type="button"
        className={`tab-btn${tab === 'notebook' ? ' active' : ''}`}
        onClick={() => onSelect('notebook')}
      >
        <BookIcon />
        生词本
      </button>
      <button
        type="button"
        className={`tab-btn${tab === 'me' ? ' active' : ''}`}
        onClick={() => onSelect('me')}
      >
        <MeIcon />
        我
      </button>
    </nav>
  )
}

function HomeIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1v-9.5Z" />
    </svg>
  )
}

function BookIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M5 4.5h11a3 3 0 0 1 3 3V20H8a3 3 0 0 0-3 3V4.5Z" />
      <path d="M5 4.5A3 3 0 0 1 8 1.5h11" />
    </svg>
  )
}

function MeIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 19.5c1.8-3.2 4.2-4.5 7-4.5s5.2 1.3 7 4.5" />
    </svg>
  )
}

export function ProfileHeader({
  userName,
  wordCount,
  onToggleTheme,
  onOpenSettings,
}: {
  userName: string
  wordCount: number
  onToggleTheme: () => void
  onOpenSettings: () => void
}) {
  return (
    <header className="profile-header">
      <div className="avatar">{userName.slice(0, 1) || '热'}</div>
      <div className="profile-meta">
        <div className="profile-name">{userName}</div>
        <div className="profile-sub">生词本 · {wordCount} 词</div>
      </div>
      <button type="button" className="icon-btn" aria-label="主题" onClick={onToggleTheme}>
        <ThemeIcon />
      </button>
      <button type="button" className="icon-btn" aria-label="设置" onClick={onOpenSettings}>
        <GearIcon />
      </button>
    </header>
  )
}

function ThemeIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M12 3a9 9 0 1 0 9 9c0-4-4-3-5.5-5.5C14 4 13 3 12 3Z" />
    </svg>
  )
}

function GearIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9c.3.6.9 1 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" />
    </svg>
  )
}
