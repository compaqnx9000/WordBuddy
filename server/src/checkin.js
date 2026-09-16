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

function monthStartOf(todayYmd) {
  const [y, m] = todayYmd.split('-').map(Number)
  return `${y}-${String(m).padStart(2, '0')}-01`
}

function monthEndOf(todayYmd) {
  const [y, m] = todayYmd.split('-').map(Number)
  // Day 0 of next month = last day of this month (UTC calendar math).
  const last = new Date(Date.UTC(y, m, 0))
  const yy = last.getUTCFullYear()
  const mm = String(last.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(last.getUTCDate()).padStart(2, '0')
  return `${yy}-${mm}-${dd}`
}

/** Makeup is allowed for any missed day in the current Shanghai calendar month. */
export const MAKEUP_LOOKBACK_DAYS = 31

/** Makeup reward is fixed at 1 point (does not inflate streak reward). */
export const MAKEUP_REWARD_POINTS = 1

export function mapCheckInState(row, today = todayShanghai(), recentDates = []) {
  const last = dateString(row?.last_checkin_date)
  const storedStreak = Math.max(0, Number(row?.streak_days || 0))
  const totalPoints = Math.max(0, Number(row?.total_points || 0))
  const checkedInToday = last === today || recentDates.includes(today)
  const continuous = last != null && (last === today || last === yesterdayOf(today))
  const streakDays = continuous ? storedStreak : 0
  let todayReward = 1
  if (checkedInToday) todayReward = rewardForDay(Math.max(storedStreak, 1))
  else if (last === yesterdayOf(today)) todayReward = rewardForDay(storedStreak + 1)
  return {
    totalPoints,
    streakDays,
    lastCheckInDate: last,
    checkedInToday,
    todayReward,
    recentDates,
  }
}

async function loadRecentDates(userId, today = todayShanghai()) {
  const from = monthStartOf(today)
  const to = monthEndOf(today)
  const rows = (
    await query(
      `SELECT checkin_date
       FROM user_checkin_logs
       WHERE user_id = $1 AND checkin_date >= $2::date AND checkin_date <= $3::date
       ORDER BY checkin_date ASC`,
      [userId, from, to],
    )
  ).rows
  return rows.map((r) => dateString(r.checkin_date)).filter(Boolean)
}

async function recomputeStreakFromLogs(client, userId, today = todayShanghai()) {
  const rows = (
    await client.query(
      `SELECT checkin_date FROM user_checkin_logs
       WHERE user_id = $1
       ORDER BY checkin_date DESC
       LIMIT 60`,
      [userId],
    )
  ).rows
  const dates = new Set(rows.map((r) => dateString(r.checkin_date)).filter(Boolean))
  let cursor = dates.has(today) ? today : yesterdayOf(today)
  let streak = 0
  while (dates.has(cursor)) {
    streak += 1
    cursor = yesterdayOf(cursor)
  }
  let last = null
  for (const d of dates) {
    if (!last || d > last) last = d
  }
  const totalRow = (
    await client.query(`SELECT total_points FROM user_checkins WHERE user_id = $1`, [userId])
  ).rows[0]
  const totalPoints = Math.max(0, Number(totalRow?.total_points || 0))
  await client.query(
    `INSERT INTO user_checkins (user_id, total_points, streak_days, last_checkin_date, updated_at)
     VALUES ($1, $2, $3, $4::date, now())
     ON CONFLICT (user_id) DO UPDATE SET
       streak_days = EXCLUDED.streak_days,
       last_checkin_date = EXCLUDED.last_checkin_date,
       updated_at = now()`,
    [userId, totalPoints, streak, last],
  )
  return { streakDays: streak, lastCheckInDate: last, totalPoints }
}

export async function getUserCheckIn(userId, today = todayShanghai()) {
  const row = (
    await query(
      `SELECT total_points, streak_days, last_checkin_date
       FROM user_checkins WHERE user_id = $1`,
      [userId],
    )
  ).rows[0]
  const recentDates = await loadRecentDates(userId, today)
  return mapCheckInState(row, today, recentDates)
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

    const recentDates = await loadRecentDates(userId, today)
    const state = mapCheckInState(
      { total_points: newTotal, streak_days: newStreak, last_checkin_date: today },
      today,
      recentDates,
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

/**
 * Makeup a missed past day after watching a rewarded video.
 * @returns {{ ok: true, pointsEarned, state } | { ok: false, error, state? }}
 */
export async function performMakeupCheckIn(userId, dateYmd, today = todayShanghai()) {
  const date = dateString(dateYmd)
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return { ok: false, error: '补签日期无效' }
  }
  if (date >= today) {
    return { ok: false, error: '只能补签过去的日期' }
  }
  const monthStart = monthStartOf(today)
  if (date < monthStart) {
    return { ok: false, error: '仅支持补签本月漏签日期' }
  }
  if (date.slice(0, 7) !== today.slice(0, 7)) {
    return { ok: false, error: '仅支持补签本月漏签日期' }
  }

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

    const already = (
      await client.query(
        `SELECT 1 FROM user_checkin_logs
         WHERE user_id = $1 AND checkin_date = $2::date
         LIMIT 1`,
        [userId, date],
      )
    ).rowCount > 0
    if (already) {
      await client.query('COMMIT')
      const state = await getUserCheckIn(userId, today)
      return { ok: false, already: true, error: '该日已签到', state }
    }

    const prevPoints = Math.max(0, Number(existing?.total_points || 0))
    const earned = MAKEUP_REWARD_POINTS
    const newTotal = prevPoints + earned

    await client.query(
      `INSERT INTO user_checkins (user_id, total_points, streak_days, last_checkin_date, updated_at)
       VALUES ($1, $2, $3, $4::date, now())
       ON CONFLICT (user_id) DO UPDATE SET
         total_points = EXCLUDED.total_points,
         updated_at = now()`,
      [
        userId,
        newTotal,
        Math.max(0, Number(existing?.streak_days || 0)),
        dateString(existing?.last_checkin_date),
      ],
    )

    const inserted = await client.query(
      `INSERT INTO user_checkin_logs (user_id, checkin_date, streak_days, points_earned)
       VALUES ($1, $2::date, $3, $4)
       ON CONFLICT (user_id, checkin_date) DO NOTHING
       RETURNING id`,
      [userId, date, 0, earned],
    )
    if (inserted.rowCount === 0) {
      await client.query('ROLLBACK')
      const state = await getUserCheckIn(userId, today)
      return { ok: false, already: true, error: '该日已签到', state }
    }

    await recomputeStreakFromLogs(client, userId, today)
    await client.query('COMMIT')

    const state = await getUserCheckIn(userId, today)
    return {
      ok: true,
      pointsEarned: earned,
      streakDays: state.streakDays,
      totalPoints: state.totalPoints,
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
