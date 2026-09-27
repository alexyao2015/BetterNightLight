package io.github.paulsnuff.betternightlight.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class LocationPermissionLevel {
    NONE,
    WHILE_IN_USE,
    ALWAYS,
}

@Singleton
class DeviceLocationPermissionChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun currentLevel(): LocationPermissionLevel {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return LocationPermissionLevel.NONE

            val hasBackground = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            return if (hasBackground) LocationPermissionLevel.ALWAYS else LocationPermissionLevel.WHILE_IN_USE
        }

        fun canAccessNow(isAppInForeground: Boolean): Boolean =
            when (currentLevel()) {
                LocationPermissionLevel.NONE -> false
                LocationPermissionLevel.WHILE_IN_USE -> isAppInForeground
                LocationPermissionLevel.ALWAYS -> true
            }
    }
