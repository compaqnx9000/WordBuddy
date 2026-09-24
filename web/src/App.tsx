import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { SessionProvider, useSession } from './api/session'
import { Shell } from './components/Shell'
import { LoginPage } from './pages/LoginPage'
import { LookupPage } from './pages/LookupPage'
import { NotebookPage } from './pages/NotebookPage'
import { CardPage } from './pages/CardPage'
import { ShortsPage } from './pages/ShortsPage'
import { PodcastPage } from './pages/PodcastPage'
import { ProfilePage } from './pages/ProfilePage'
import type { ReactNode } from 'react'

function RequireAuth({ children }: { children: ReactNode }) {
  const { token, loading } = useSession()
  const location = useLocation()
  if (loading) {
    return (
      <div className="page">
        <p className="muted">正在恢复登录状态…</p>
      </div>
    )
  }
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return children
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Shell />}>
        <Route index element={<LookupPage />} />
        <Route
          path="notebook"
          element={
            <RequireAuth>
              <NotebookPage />
            </RequireAuth>
          }
        />
        <Route
          path="notebook/card"
          element={
            <RequireAuth>
              <CardPage />
            </RequireAuth>
          }
        />
        <Route
          path="shorts"
          element={
            <RequireAuth>
              <ShortsPage />
            </RequireAuth>
          }
        />
        <Route
          path="podcast"
          element={
            <RequireAuth>
              <PodcastPage />
            </RequireAuth>
          }
        />
        <Route
          path="me"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <SessionProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </SessionProvider>
  )
}
