package com.hotgis.wordbuddy.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CheckInState(
    val totalPoints: Int = 0,
    /** Consecutive days ending at last check-in; 0 if broken or never. */
    val streakDays: Int = 0,
    val lastCheckInDate: String? = null,
    val checkedInToday: Boolean = false,
    /** Points awarded if the user checks in now (or already earned today). */
    val todayReward: Int = 1,
    /** Real check-in dates (yyyy-MM-dd) in the recent window from server logs. */
    val recentDates: List<String> = emptyList(),
)

sealed class CheckInResult {
    data class Success(
        val pointsEarned: Int,
        val streakDays: Int,
        val totalPoints: Int,
    ) : CheckInResult()

    data object AlreadyCheckedIn : CheckInResult()

    data class Failed(val message: String) : CheckInResult()

    data object NeedLogin : CheckInResult()
}

class CheckInStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(today: LocalDate = todayShanghai()): CheckInState {
        val lastRaw = prefs.getString(KEY_LAST_DATE, null)
        val last = lastRaw?.let { runCatching { LocalDate.parse(it, DATE_FMT) }.getOrNull() }
        val storedStreak = prefs.getInt(KEY_STREAK, 0).coerceAtLeast(0)
        val totalPoints = prefs.getInt(KEY_POINTS, 0).coerceAtLeast(0)
        val checkedToday = last == today
        val continuous = last != null && (last == today || last == today.minusDays(1))
        val streakDays = if (continuous) storedStreak else 0
        val todayReward = when {
            checkedToday -> rewardForDay(storedStreak)
            last == today.minusDays(1) -> rewardForDay(storedStreak + 1)
            else -> 1
        }
        return CheckInState(
            totalPoints = totalPoints,
            streakDays = streakDays,
            lastCheckInDate = lastRaw,
            checkedInToday = checkedToday,
            todayReward = todayReward,
            recentDates = emptyList(),
        )
    }

    fun applyRemote(state: CheckInState) {
        prefs.edit()
            .putString(KEY_LAST_DATE, state.lastCheckInDate)
            .putInt(KEY_STREAK, state.streakDays.coerceAtLeast(0))
            .putInt(KEY_POINTS, state.totalPoints.coerceAtLeast(0))
            .apply()
    }

    fun applyRemoteRaw(totalPoints: Int, streakDays: Int, lastCheckInDate: String?) {
        prefs.edit()
            .putString(KEY_LAST_DATE, lastCheckInDate)
            .putInt(KEY_STREAK, streakDays.coerceAtLeast(0))
            .putInt(KEY_POINTS, totalPoints.coerceAtLeast(0))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "hotwords_checkin"
        private const val KEY_LAST_DATE = "last_checkin_date"
        private const val KEY_STREAK = "streak_days"
        private const val KEY_POINTS = "total_points"
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")

        fun todayShanghai(): LocalDate = LocalDate.now(SHANGHAI)

        /** Day 1→1 … day 7→7, then always 7. */
        fun rewardForDay(streakDay: Int): Int = streakDay.coerceIn(1, 7)

        fun consecutiveEndingAt(claimed: Set<LocalDate>, date: LocalDate): Int {
            var n = 0
            var cursor = date
            while (cursor in claimed) {
                n += 1
                cursor = cursor.minusDays(1)
            }
            return n
        }

        fun claimedDates(state: CheckInState): Set<LocalDate> = buildSet {
            state.recentDates.forEach { raw ->
                runCatching { LocalDate.parse(raw) }.getOrNull()?.let { add(it) }
            }
            state.lastCheckInDate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { add(it) }
        }

        /** Points a makeup would award if that date is filled into the existing log. */
        fun makeupReward(state: CheckInState, date: LocalDate): Int {
            val claimed = claimedDates(state)
            if (date in claimed) return 1
            return rewardForDay(consecutiveEndingAt(claimed + date, date)).coerceAtLeast(1)
        }
    }
}
