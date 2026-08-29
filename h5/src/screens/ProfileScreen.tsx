import { useRef, useState } from 'react'
import { ProfileHeader } from '../components/Chrome'
import type { HotWordsApi } from '../store/useHotWords'

export function ProfileScreen({
  api,
  onOpenSettings,
  toast,
}: {
  api: HotWordsApi
  onOpenSettings: () => void
  toast: (msg: string) => void
}) {
  const { ui, filtered } = api
  const fileRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)

  return (
    <div className="screen">
      <ProfileHeader
        userName={ui.settings.displayName}
        wordCount={filtered.length}
        onToggleTheme={() =>
          api.updateSettings((s) => ({
            ...s,
            appTheme: s.appTheme === 'Light' ? 'Dark' : 'Light',
          }))
        }
        onOpenSettings={onOpenSettings}
      />
      <div className="screen-scroll">
        <button
          type="button"
          className="menu-row"
          style={{ width: '100%' }}
          disabled={busy}
          onClick={() => {
            const json = api.exportJson()
            const blob = new Blob([json], { type: 'application/json' })
            const a = document.createElement('a')
            a.href = URL.createObjectURL(blob)
            a.download = api.suggestedExportFileName()
            a.click()
            URL.revokeObjectURL(a.href)
            toast('导出成功')
          }}
        >
          <span className="label">导出生词本</span>
          <span className="hint">JSON</span>
        </button>
        <button
          type="button"
          className="menu-row"
          style={{ width: '100%' }}
          disabled={busy}
          onClick={() => fileRef.current?.click()}
        >
          <span className="label">导入生词本</span>
          <span className="hint">合并</span>
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          hidden
          onChange={async (e) => {
            const file = e.target.files?.[0]
            e.target.value = ''
            if (!file) return
            setBusy(true)
            try {
              const text = await file.text()
              const result = api.importJson(text)
              toast(`导入完成：新增 ${result.added} 个，更新 ${result.updated} 个`)
            } catch (err) {
              toast(err instanceof Error ? err.message : '导入失败')
            } finally {
              setBusy(false)
            }
          }}
        />
        <div className="menu-row">
          <span className="label">存储</span>
          <span className="hint">浏览器 localStorage</span>
        </div>
      </div>
    </div>
  )
}
