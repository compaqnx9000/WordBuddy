import { useRef } from 'react'
import type { Definition } from '../types'

export function ImageSourceDialog({
  definitions,
  busy,
  error,
  onPickFile,
  onGenerateAi,
  onDismiss,
}: {
  definitions: Definition[]
  busy: boolean
  error: string | null
  onPickFile: (file: File) => void
  onGenerateAi: (meaning: string) => void
  onDismiss: () => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const hint = definitions[0] ? `${definitions[0].pos} ${definitions[0].meaning}`.trim() : ''

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="modal-title">配图</div>
        {error ? <p style={{ color: 'var(--danger)' }}>{error}</p> : null}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={() => inputRef.current?.click()}>
            从相册选择
          </button>
          <button
            type="button"
            className="ghost"
            disabled={busy}
            onClick={() => onGenerateAi(hint)}
          >
            {busy ? '生成中…' : 'AI 生成助记图'}
          </button>
          <button type="button" className="ghost" onClick={onDismiss}>
            取消
          </button>
        </div>
        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) onPickFile(file)
          }}
        />
      </div>
    </div>
  )
}
