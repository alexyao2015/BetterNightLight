package io.github.paulsnuff.betternightlight.domain

import android.util.Log
import io.github.paulsnuff.betternightlight.data.AppForegroundTracker
import io.github.paulsnuff.betternightlight.data.DeviceLocationPermissionChecker
import io.github.paulsnuff.betternightlight.data.LocationPermissionLevel
import io.github.paulsnuff.betternightlight.data.UserPreferencesRepository
import io.github.paulsnuff.betternightlight.domain.model.AutomationLocationSource
import io.github.paulsnuff.betternightlight.domain.model.AutomationSchedule
import io.github.paulsnuff.betternightlight.domain.model.AutomationTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Singleton
class DeviceLocationRefreshCoordinator
    @Inject
    constructor(
        private val deviceLocationProvider: DeviceLocationProvider,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val permissionChecker: DeviceLocationPermissionChecker,
        private val foregroundTracker: AppForegroundTracker,
        private val applicationScope: CoroutineScope,
    ) {
        private var loopJob: Job? = null

        fun start() {
            if (loopJob?.isActive == true) return
            loopJob =
                applicationScope.launch {
                    delay(INITIAL_REFRESH_DELAY)
                    while (true) {
                        runSafely { refreshIfNeeded() }
                        delay(nextTickDelay())
                    }
                }
        }

        private suspend fun nextTickDelay(): Duration {
            val lastRefresh = userPreferencesRepository.lastLocationRefreshMillis()
            val nextRefreshAt = (lastRefresh ?: 0L) + REFRESH_INTERVAL.inWholeMilliseconds
            val remaining = (nextRefreshAt - System.currentTimeMillis()).milliseconds + TICK_SLACK
            return remaining.coerceIn(MIN_TICK_DELAY, REFRESH_INTERVAL)
        }

        private suspend fun refreshIfNeeded(): Boolean {
            val schedule = userPreferencesRepository.automationScheduleFlow.first()
            if (!shouldRefresh(schedule)) return false

            val lastRefresh = userPreferencesRepository.lastLocationRefreshMillis()
            val now = System.currentTimeMillis()
            if (lastRefresh != null && now - lastRefresh < MIN_REFRESH_GAP.inWholeMilliseconds) {
                Log.i(TAG, "Location refreshed recently, skipping until interval elapses")
                return false
            }

            val permissionLevel = permissionChecker.currentLevel()
            if (!permissionChecker.canAccessNow(foregroundTracker.isForeground)) {
                val reason =
                    when (permissionLevel) {
                        LocationPermissionLevel.NONE -> "location permission not granted"
                        LocationPermissionLevel.WHILE_IN_USE -> "permission only allows foreground access and app is in background"
                        LocationPermissionLevel.ALWAYS -> "unknown reason"
                    }
                Log.i(TAG, "Skipping location refresh: $reason")
                return false
            }

            val location =
                deviceLocationProvider.getCurrentLocation() ?: run {
                    Log.i(TAG, "Location refresh skipped: no location fix available")
                    return false
                }

            val latitude = (location.latitude * 100.0).roundToInt() / 100.0
            val longitude = (location.longitude * 100.0).roundToInt() / 100.0
            userPreferencesRepository.markLocationRefreshed(now)
            if (schedule.latitude == latitude && schedule.longitude == longitude) {
                Log.i(TAG, "Location unchanged, no schedule update needed")
                return false
            }

            userPreferencesRepository.updateAutomationSchedule(
                schedule.copy(latitude = latitude, longitude = longitude),
            )
            Log.i(TAG, "Device location refreshed silently")
            return true
        }

        private suspend fun runSafely(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "location refresh failed", e)
            }
        }

        private fun shouldRefresh(schedule: AutomationSchedule): Boolean = schedule.enabled && schedule.trigger == AutomationTrigger.LOCATION && schedule.locationSource == AutomationLocationSource.DEVICE

        companion object {
            private const val TAG = "BnlLocationRefresh"
            private val INITIAL_REFRESH_DELAY = 30.seconds
            private val REFRESH_INTERVAL = 1.hours

            private val MIN_REFRESH_GAP = REFRESH_INTERVAL
            private val TICK_SLACK = 1.minutes
            private val MIN_TICK_DELAY = 1.minutes
        }
    }
