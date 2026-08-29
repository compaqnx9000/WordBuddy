import { useEffect, useState } from 'react'
import { BottomBar } from './components/Chrome'
import { useHotWords } from './store/useHotWords'
import { CardScreen } from './screens/CardScreen'
import { HomeScreen } from './screens/HomeScreen'
import { NotebookScreen } from './screens/NotebookScreen'
import { ProfileScreen } from './screens/ProfileScreen'
import { SettingsScreen } from './screens/SettingsScreen'

type Tab = 'home' | 'notebook' | 'me'
type Overlay = 'none' | 'card'

export default function App() {
  const api = useHotWords()
  const [tab, setTab] = useState<Tab>('home')
  const [overlay, setOverlay] = useState<Overlay>('none')
  const [showSettings, setShowSettings] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (!toast) return
    const t = window.setTimeout(() => setToast(null), 2200)
    return () => window.clearTimeout(t)
  }, [toast])

  if (showSettings) {
    return (
      <div className="app-shell">
        <SettingsScreen api={api} onBack={() => setShowSettings(false)} />
        {toast ? <div className="toast">{toast}</div> : null}
      </div>
    )
  }

  if (overlay === 'card') {
    return (
      <div className="app-shell">
        <CardScreen
          api={api}
          onBack={() => {
            api.stopAutoPlay()
            setOverlay('none')
          }}
          onOpenSettings={() => setShowSettings(true)}
        />
        {toast ? <div className="toast">{toast}</div> : null}
      </div>
    )
  }

  const showBottom = tab !== 'notebook'

  return (
    <div className="app-shell">
      {tab === 'home' ? (
        <HomeScreen api={api} onOpenSettings={() => setShowSettings(true)} />
      ) : null}
      {tab === 'notebook' ? (
        <NotebookScreen
          api={api}
          onCardMode={() => {
            api.openCard(false)
            setOverlay('card')
          }}
          onRecite={() => {
            api.openCard(true)
            setOverlay('card')
          }}
        />
      ) : null}
      {tab === 'me' ? (
        <ProfileScreen
          api={api}
          onOpenSettings={() => setShowSettings(true)}
          toast={(msg) => setToast(msg)}
        />
      ) : null}
      {showBottom ? <BottomBar tab={tab} onSelect={setTab} /> : null}
      {tab === 'notebook' ? (
        <div
          style={{
            padding: '8px 14px calc(8px + env(safe-area-inset-bottom))',
            borderTop: '1px solid var(--divider)',
            display: 'flex',
            justifyContent: 'center',
          }}
        >
          <button
            type="button"
            className="ghost-btn"
            style={{ padding: '10px 24px', borderRadius: 12 }}
            onClick={() => setTab('home')}
          >
            返回首页
          </button>
        </div>
      ) : null}
      {toast ? <div className="toast">{toast}</div> : null}
    </div>
  )
}
