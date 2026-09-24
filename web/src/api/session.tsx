import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  api,
  setStoredToken,
  getStoredToken,
  type AuthResult,
  type UserSession,
} from './client'

type SessionContextValue = {
  token: string | null
  user: UserSession | null
  loading: boolean
  loginFromAuth: (result: AuthResult) => void
  logout: () => void
  refresh: () => Promise<void>
}

const SessionContext = createContext<SessionContextValue | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getStoredToken())
  const [user, setUser] = useState<UserSession | null>(null)
  const [loading, setLoading] = useState(true)

  const applySession = useCallback((session: UserSession | null) => {
    if (session) {
      setStoredToken(session.token)
      setToken(session.token)
      setUser(session)
      api.setToken(session.token)
    } else {
      setStoredToken(null)
      setToken(null)
      setUser(null)
      api.setToken(null)
    }
  }, [])

  const refresh = useCallback(async () => {
    const t = getStoredToken()
    if (!t) {
      applySession(null)
      return
    }
    api.setToken(t)
    setToken(t)
    try {
      const me = await api.fetchMe()
      if (me) applySession(me)
      else applySession(null)
    } catch {
      applySession(null)
    }
  }, [applySession])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      await refresh()
      if (!cancelled) setLoading(false)
    })()
    return () => {
      cancelled = true
    }
  }, [refresh])

  const loginFromAuth = useCallback(
    (result: AuthResult) => {
      if (result.session) applySession(result.session)
    },
    [applySession],
  )

  const logout = useCallback(() => {
    applySession(null)
  }, [applySession])

  const value = useMemo(
    () => ({ token, user, loading, loginFromAuth, logout, refresh }),
    [token, user, loading, loginFromAuth, logout, refresh],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession 须在 SessionProvider 内使用')
  return ctx
}
