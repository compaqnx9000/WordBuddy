import type { ReactNode } from 'react'
import type { ExampleSentence } from '../types'

export function highlightHeadword(text: string, headword: string): ReactNode {
  const w = headword.trim()
  if (!w) return text
  const re = new RegExp(`(${escapeReg(w)})`, 'gi')
  const parts = text.split(re)
  return parts.map((part, i) =>
    part.toLowerCase() === w.toLowerCase() ? (
      <span className="hl" key={i}>
        {part}
      </span>
    ) : (
      <span key={i}>{part}</span>
    ),
  )
}

function escapeReg(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function ExampleSentencesBlock({
  headword,
  examples,
  onSpeak,
}: {
  headword: string
  examples: ExampleSentence[]
  onSpeak?: (text: string) => void
}) {
  if (!examples.length) return null
  return (
    <div className="examples">
      <div className="muted" style={{ marginBottom: 8, fontWeight: 600 }}>
        例句
      </div>
      {examples.map((ex, i) => (
        <div className="example" key={`${ex.english}-${i}`}>
          <div
            className="example-en"
            onClick={() => onSpeak?.(ex.english)}
            role={onSpeak ? 'button' : undefined}
          >
            {highlightHeadword(ex.english, headword)}
          </div>
          <div className="example-zh">{ex.chinese}</div>
        </div>
      ))}
    </div>
  )
}
