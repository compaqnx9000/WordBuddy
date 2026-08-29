import { useRef, type TouchEvent } from 'react'

type Point = { x: number; y: number }

/**
 * Horizontal swipe. Nested regions that own the gesture should
 * stopPropagation so the parent does not flip pages.
 */
export function useHorizontalSwipe(
  onSwipe: (direction: -1 | 1) => void,
  options?: { threshold?: number; enabled?: boolean },
) {
  const threshold = options?.threshold ?? 48
  const enabled = options?.enabled ?? true
  const start = useRef<Point | null>(null)
  const lockedAxis = useRef<'x' | 'y' | null>(null)

  const begin = (x: number, y: number) => {
    if (!enabled) return
    start.current = { x, y }
    lockedAxis.current = null
  }

  const move = (x: number, y: number) => {
    if (!start.current || lockedAxis.current) return
    const dx = Math.abs(x - start.current.x)
    const dy = Math.abs(y - start.current.y)
    if (dx < 8 && dy < 8) return
    lockedAxis.current = dx >= dy ? 'x' : 'y'
  }

  const end = (x: number, y: number) => {
    if (!start.current || !enabled) {
      start.current = null
      lockedAxis.current = null
      return
    }
    const dx = x - start.current.x
    const dy = y - start.current.y
    const horizontal =
      lockedAxis.current === 'x' ||
      (lockedAxis.current == null && Math.abs(dx) > Math.abs(dy))
    start.current = null
    lockedAxis.current = null
    if (!horizontal) return
    if (Math.abs(dx) < threshold || Math.abs(dx) < Math.abs(dy) * 1.15) return
    onSwipe(dx < 0 ? 1 : -1)
  }

  return {
    onTouchStart: (e: TouchEvent) => {
      begin(e.touches[0].clientX, e.touches[0].clientY)
    },
    onTouchMove: (e: TouchEvent) => {
      move(e.touches[0].clientX, e.touches[0].clientY)
    },
    onTouchEnd: (e: TouchEvent) => {
      const t = e.changedTouches[0]
      end(t.clientX, t.clientY)
    },
    onTouchCancel: () => {
      start.current = null
      lockedAxis.current = null
    },
  }
}
