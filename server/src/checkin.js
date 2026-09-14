import { pool, query } from './db.js'

/** Calendar date in Asia/Shanghai as yyyy-MM-dd. */
export function todayShanghai(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now)
}

export function rewardForDay(streakDay) {
  const n = Number(streakDay) || 0
  if (n < 1) return 1
  return Math.min(n, 7)
}

/** Normalize pg DATE / string / Date to yyyy-MM-dd without timezone drift. */
export function dateString(value) {
  if (value == null || value === '') return null
  if (typeof value === 'string') {
    const m = value.trim().match(/^(\d{4}-\d{2}-\d{2})/)
    return m ? m[1] : null
  }
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    // node-pg historically returns DATE as UTC midnight of that calendar day.
    const y = value.getUTCFullYear()
    const m = String(value.getUTCMonth() + 1).padStart(2, '0')
    const d = String(value.getUTCDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
  }
  const raw = String(value)
  const m = raw.match(/^(\d{4}-\d{2}-\d{2})/)
  return m ? m[1] : null
}

function yesterdayOf(todayYmd) {
  const [y, m, d] = todayYmd.split('-').map(Number)
  const utc = Date.UTC(y, m - 1, d)
  const prev = new Date(utc - 24 * 60 * 60 * 1000)
  const yy = prev.getUTCFullYear()
  const mm = String(prev.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(prev.getUTCDate()).padStart(2, '0')
  return `${yy}-${mm}-${dd}`
}

export function mapCheckInState(row, today = todayShanghai()) {
  const last = dateString(row?.last_checkin_date)
  const storedStreak = Math.max(0, Number(row?.streak_days || 0))
  const totalPoints = Math.max(0, Number(row?.total_points || 0))
  const checkedInToday = last === today
  const continuous = last != null && (last === today || last === yesterdayOf(today))
  const streakDays = continuous ? storedStreak : 0
  let todayReward = 1
  if (checkedInToday) todayReward = rewardForDay(storedStreak)
  else if (last === yesterdayOf(today)) todayReward = rewardForDay(storedStreak + 1)
  return {
    totalPoints,
    streakDays,
    lastCheckInDate: last,
    checkedInToday,
    todayReward,
  }
}

export async function getUserCheckIn(userId, today = todayShanghai()) {
  const row = (
    await query(
      `SELECT total_points, streak_days, last_checkin_date
       FROM user_checkins WHERE user_id = $1`,
      [userId],
    )
  ).rows[0]
  return mapCheckInState(row, today)
}

/**
 * @returns {{ already: true, state } | { already: false, pointsEarned, streakDays, totalPoints, state }}
 */
export async function performUserCheckIn(userId, today = todayShanghai()) {
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    const existing = (
      await client.query(
        `SELECT total_points, streak_days, last_checkin_date
         FROM user_checkins WHERE user_id = $1 FOR UPDATE`,
        [userId],
      )
    ).rows[0]

    // Hard guard: one row per user per calendar day in logs.
    const loggedToday = (
      await client.query(
        `SELECT 1 FROM user_checkin_logs
         WHERE user_id = $1 AND checkin_date = $2::date
         LIMIT 1`,
        [userId, today],
      )
    ).rowCount > 0

    const last = dateString(existing?.last_checkin_date)
    if (loggedToday || last === today) {
      // Repair last_checkin_date if log says checked but summary drifted.
      if (loggedToday && last !== today) {
        await client.query(
          `UPDATE user_checkins
           SET last_checkin_date = $2::date, updated_at = now()
           WHERE user_id = $1`,
          [userId, today],
        )
      }
      await client.query('COMMIT')
      const state = await getUserCheckIn(userId, today)
      return { already: true, state }
    }

    const storedStreak = Math.max(0, Number(existing?.streak_days || 0))
    const prevPoints = Math.max(0, Number(existing?.total_points || 0))
    const newStreak = last === yesterdayOf(today) ? storedStreak + 1 : 1
    const earned = rewardForDay(newStreak)
    const newTotal = prevPoints + earned

    await client.query(
      `INSERT INTO user_checkins (user_id, total_points, streak_days, last_checkin_date, updated_at)
       VALUES ($1, $2, $3, $4::date, now())
       ON CONFLICT (user_id) DO UPDATE SET
         total_points = EXCLUDED.total_points,
         streak_days = EXCLUDED.streak_days,
         last_checkin_date = EXCLUDED.last_checkin_date,
         updated_at = now()`,
      [userId, newTotal, newStreak, today],
    )
    const inserted = await client.query(
      `INSERT INTO user_checkin_logs (user_id, checkin_date, streak_days, points_earned)
       VALUES ($1, $2::date, $3, $4)
       ON CONFLICT (user_id, checkin_date) DO NOTHING
       RETURNING id`,
      [userId, today, newStreak, earned],
    )
    // Race: another request already inserted today's log — roll back point grant.
    if (inserted.rowCount === 0) {
      await client.query('ROLLBACK')
      const state = await getUserCheckIn(userId, today)
      return { already: true, state }
    }
    await client.query('COMMIT')

    const state = mapCheckInState(
      { total_points: newTotal, streak_days: newStreak, last_checkin_date: today },
      today,
    )
    return {
      already: false,
      pointsEarned: earned,
      streakDays: newStreak,
      totalPoints: newTotal,
      state,
    }
  } catch (error) {
    try {
      await client.query('ROLLBACK')
    } catch {
      /* ignore */
    }
    throw error
  } finally {
    client.release()
  }
}
