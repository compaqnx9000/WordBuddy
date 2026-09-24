import { NavLink, Outlet } from 'react-router-dom'
import { useSession } from '../api/session'

const NAV: { to: string; label: string; end?: boolean }[] = [
  { to: '/', label: '查词', end: true },
  { to: '/notebook', label: '生词本' },
  { to: '/shorts', label: '短视频' },
  { to: '/podcast', label: '播客' },
  { to: '/me', label: '我' },
]

export function Shell() {
  const { user } = useSession()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">你的词搭子</span>
          <span className="brand-sub">WordBuddy · 桌面版</span>
        </div>
        <nav>
          <ul className="nav-list">
            {NAV.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="sidebar-foot">
          {user ? (
            <span>
              {user.nickname?.trim() || '词搭子'}
              {user.buddyId ? ` · ID ${user.buddyId}` : ''}
            </span>
          ) : (
            <span>登录后同步生词与积分</span>
          )}
        </div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  )
}
