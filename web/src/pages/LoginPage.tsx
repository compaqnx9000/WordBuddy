import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useSession } from '../api/session'

type Mode = 'sms' | 'password' | 'register'

export function LoginPage() {
  const { token, loginFromAuth } = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [mode, setMode] = useState<Mode>('sms')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [password2, setPassword2] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    if (countdown <= 0) return
    const t = window.setTimeout(() => setCountdown((c) => c - 1), 1000)
    return () => window.clearTimeout(t)
  }, [countdown])

  if (token) {
    return <Navigate to={from} replace />
  }

  const phoneOk = /^1\d{10}$/.test(phone.trim())

  async function handleSendCode() {
    if (!phoneOk) {
      setError('请输入 11 位中国大陆手机号')
      return
    }
    setSending(true)
    setError(null)
    setInfo(null)
    try {
      const debug = await api.sendCode(phone.trim())
      setCountdown(60)
      setInfo(debug ? `验证码已发送（调试码：${debug}）` : '验证码已发送，请查收短信')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '发送失败，请稍后重试')
    } finally {
      setSending(false)
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!phoneOk) {
      setError('请输入 11 位中国大陆手机号')
      return
    }
    setError(null)
    setInfo(null)
    setSubmitting(true)
    try {
      let result
      if (mode === 'register') {
        if (password.length < 6) throw new ApiError('密码至少 6 位')
        if (password !== password2) throw new ApiError('两次密码不一致')
        if (code.trim().length < 4) throw new ApiError('请输入短信验证码')
        result = await api.register(phone.trim(), code.trim(), password, inviteCode || undefined)
      } else if (mode === 'password') {
        if (password.length < 6) throw new ApiError('密码至少 6 位')
        result = await api.loginWithPassword(phone.trim(), password)
      } else {
        if (code.trim().length < 4) throw new ApiError('请输入短信验证码')
        result = await api.login(phone.trim(), code.trim())
      }
      if (!result.session) {
        throw new ApiError('登录未完成，请检查验证码或密码')
      }
      loginFromAuth(result)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card card">
        <header className="page-header" style={{ marginBottom: 20 }}>
          <h1 className="page-title">欢迎回来</h1>
          <p className="page-desc">使用手机号登录，生词与积分将与 App 同步</p>
        </header>

        <div className="tabs">
          <button
            type="button"
            className={mode === 'sms' ? 'tab active' : 'tab'}
            onClick={() => setMode('sms')}
          >
            验证码登录
          </button>
          <button
            type="button"
            className={mode === 'password' ? 'tab active' : 'tab'}
            onClick={() => setMode('password')}
          >
            密码登录
          </button>
          <button
            type="button"
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => setMode('register')}
          >
            注册
          </button>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {info && <div className="alert alert-info">{info}</div>}

        <form onSubmit={handleSubmit}>
          <label className="muted" style={{ display: 'block', marginBottom: 6 }}>
            手机号
          </label>
          <input
            className="input"
            type="tel"
            inputMode="numeric"
            autoComplete="tel"
            placeholder="11 位手机号"
            value={phone}
            onChange={(ev) => setPhone(ev.target.value.replace(/\D/g, '').slice(0, 11))}
            style={{ marginBottom: 16 }}
          />

          {(mode === 'sms' || mode === 'register') && (
            <>
              <label className="muted" style={{ display: 'block', marginBottom: 6 }}>
                短信验证码
              </label>
              <div className="input-row" style={{ marginBottom: 16 }}>
                <input
                  className="input"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="6 位验证码"
                  value={code}
                  onChange={(ev) => setCode(ev.target.value.replace(/\D/g, '').slice(0, 6))}
                />
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={sending || countdown > 0 || !phoneOk}
                  onClick={() => void handleSendCode()}
                >
                  {countdown > 0 ? `${countdown}s` : sending ? '发送中…' : '获取验证码'}
                </button>
              </div>
            </>
          )}

          {(mode === 'password' || mode === 'register') && (
            <>
              <label className="muted" style={{ display: 'block', marginBottom: 6 }}>
                登录密码
              </label>
              <input
                className="input"
                type="password"
                autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                placeholder="至少 6 位"
                value={password}
                onChange={(ev) => setPassword(ev.target.value)}
                style={{ marginBottom: mode === 'register' ? 16 : 20 }}
              />
            </>
          )}

          {mode === 'register' && (
            <>
              <label className="muted" style={{ display: 'block', marginBottom: 6 }}>
                确认密码
              </label>
              <input
                className="input"
                type="password"
                autoComplete="new-password"
                value={password2}
                onChange={(ev) => setPassword2(ev.target.value)}
                style={{ marginBottom: 16 }}
              />
              <label className="muted" style={{ display: 'block', marginBottom: 6 }}>
                邀请码（选填）
              </label>
              <input
                className="input"
                value={inviteCode}
                onChange={(ev) => setInviteCode(ev.target.value)}
                placeholder="好友的词搭子 ID"
                style={{ marginBottom: 20 }}
              />
            </>
          )}

          <button type="submit" className="btn btn-primary" disabled={submitting} style={{ width: '100%' }}>
            {submitting ? '请稍候…' : mode === 'register' ? '注册并登录' : '登录'}
          </button>
        </form>

        <p className="muted" style={{ marginTop: 20, fontSize: '0.85rem', lineHeight: 1.6 }}>
          微信 / 支付宝快捷登录与支付请在手机 App 中使用。
          <br />
          不登录也可在 <Link to="/">查词</Link> 页免费查释义。
        </p>
      </div>
    </div>
  )
}
