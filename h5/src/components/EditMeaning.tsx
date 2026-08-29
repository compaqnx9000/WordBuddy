import { useState } from 'react'
import type { Definition } from '../types'

export function EditMeaningDialog({
  definitions,
  onSave,
  onClose,
}: {
  definitions: Definition[]
  onSave: (next: Definition[]) => void
  onClose: () => void
}) {
  const [text, setText] = useState('')

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="modal-title">追加释义</div>
        <p className="muted" style={{ marginTop: 0 }}>
          仅追加你的笔记，不会覆盖词典释义。
        </p>
        <textarea
          className="lookup-box"
          style={{ minHeight: 96, margin: '0 0 12px' }}
          placeholder="输入你的理解 / 笔记"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <div className="modal-actions">
          <button
            type="button"
            onClick={() => {
              const meaning = text.trim()
              if (!meaning) return
              onSave([
                ...definitions,
                { pos: '笔记', meaning, isUserAdded: true },
              ])
              onClose()
            }}
          >
            保存
          </button>
          <button type="button" className="ghost" onClick={onClose}>
            取消
          </button>
        </div>
      </div>
    </div>
  )
}
