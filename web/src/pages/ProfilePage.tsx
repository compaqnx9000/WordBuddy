import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError, type CheckInState, type PointPackage } from '../api/client'
import { useSession } from '../api/session'

export function ProfilePage() {
  const { user, logout, refresh } = useSession()
  const navigate = useNavigate()
  const [checkIn, setCheckIn] = useState<CheckInState | null>(null)
  const [packages, setPackages] = useState<PointPackage[]>([])
  const [msg, setMsg] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [checkingIn, setCheckingIn] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [ci, pkgs] = await Promise.all([api.getCheckIn(), api.fetchPointPackages()])
      setCheckIn(ci)
      setPackages(pkgs.items)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function handleCheckIn() {
    setCheckingIn(true)
    setMsg(null)
    try {
      const result = await api.performCheckIn()
      if (result.kind === 'already') {
        setMsg('今日已签到')
      } else {
        setMsg(`签到成功，获得 ${result.pointsEarned} 积分 · 连续 ${result.streakDays} 天`)
      }
      await load()
      await refresh()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '签到失败')
    } finally {
      setCheckingIn(false)
    }
  }

  function handleLogout() {
    logout()
    navigate('/login')
  }

  const displayName = user?.nickname?.trim() || '词搭子'

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">我</h1>
        <p className="page-desc">账号信息与每日签到；充值与提现请在 App 完成</p>
      </header>

      {error && <div className="alert alert-error">{error}</div>}
      {msg && <div className="alert alert-success">{msg}</div>}

      <div className="profile-grid">
        <section className="card">
          <h2 style={{ marginTop: 0, fontSize: '1.1rem' }}>账号</h2>
          <p style={{ margin: '8px 0' }}>
            <strong>{displayName}</strong>
          </p>
          <p className="muted" style={{ margin: '4px 0' }}>
            手机 {user?.phone?.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') ?? '—'}
          </p>
          {user?.buddyId && (
            <p className="muted" style={{ margin: '4px 0' }}>
              词搭子 ID：{user.buddyId}
            </p>
          )}
          {user?.region && (
            <p className="muted" style={{ margin: '4px 0' }}>
              地区：{user.region}
            </p>
          )}
          {user?.signature && (
            <p style={{ marginTop: 12, fontStyle: 'italic' }}>{user.signature}</p>
          )}
          <div className="toolbar" style={{ marginTop: 20 }}>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void refresh()}>
              刷新资料
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
              退出登录
            </button>
          </div>
        </section>

        <section className="card">
          <h2 style={{ marginTop: 0, fontSize: '1.1rem' }}>签到与积分</h2>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
            <div className="stat-pill">
              <strong>{checkIn?.totalPoints ?? '—'}</strong>
              <span>当前积分</span>
            </div>
            <div className="stat-pill">
              <strong>{checkIn?.streakDays ?? 0}</strong>
              <span>连续签到（天）</span>
            </div>
            <div className="stat-pill">
              <strong>+{checkIn?.todayReward ?? 1}</strong>
              <span>今日可领</span>
            </div>
          </div>
          <button
            type="button"
            className="btn btn-primary"
            disabled={checkingIn || checkIn?.checkedInToday}
            onClick={() => void handleCheckIn()}
          >
            {checkIn?.checkedInToday ? '今日已签到' : checkingIn ? '签到中…' : '立即签到'}
          </button>
          <p className="muted" style={{ marginTop: 14, fontSize: '0.85rem' }}>
            Web 版不支持补签与看广告领奖励；如需补签请使用手机 App。
          </p>
        </section>
      </div>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 style={{ marginTop: 0, fontSize: '1.1rem' }}>积分套餐（只读）</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          支付宝 / 微信支付与账号绑定仅在 App 内可用。
        </p>
        {packages.length === 0 ? (
          <p className="muted">暂无套餐信息</p>
        ) : (
          <ul className="def-list">
            {packages.map((p) => (
              <li key={p.id}>
                <strong>{p.title}</strong>
                {p.subtitle && <span className="muted"> — {p.subtitle}</span>}
                <br />
                <span className="muted">
                  ¥{(p.priceFen / 100).toFixed(2)} · {p.points} 积分
                  {p.badge ? ` · ${p.badge}` : ''}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="muted" style={{ marginTop: 24 }}>
        想查词？回到 <Link to="/">查词</Link> 页。
      </p>
    </div>
  )
}
