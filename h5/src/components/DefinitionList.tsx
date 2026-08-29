import type { Definition } from '../types'
import { defLabel } from '../types'

export function DefinitionList({
  definitions,
  maxLines,
}: {
  definitions: Definition[]
  maxLines?: number
}) {
  const list = maxLines ? definitions.slice(0, maxLines) : definitions
  return (
    <div className="def-list">
      {list.map((d, i) => (
        <div key={`${d.pos}-${d.meaning}-${i}`} className={`def-item${d.isUserAdded ? ' user' : ''}`}>
          {d.pos ? <span className="def-pos">{d.pos}</span> : null}
          <span>{d.meaning || defLabel(d)}</span>
        </div>
      ))}
    </div>
  )
}
