package io.github.paulsnuff.betternightlight.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppForegroundTracker
    @Inject
    constructor() {
        private var activityCount = 0

        private var started = false

        private val _isForeground = MutableStateFlow(false)

        val isForeground: Boolean
            get() = _isForeground.value

        fun start(application: Application) {
            if (started) return
            started = true
            application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityStarted(activity: Activity) {
                        activityCount++
                        if (activityCount == 1) _isForeground.value = true
                    }

                    override fun onActivityStopped(activity: Activity) {
                        activityCount = (activityCount - 1).coerceAtLeast(0)
                        if (activityCount == 0) _isForeground.value = false
                    }

                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?,
                    ) = Unit

                    override fun onActivityResumed(activity: Activity) = Unit

                    override fun onActivityPaused(activity: Activity) = Unit

                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        outState: Bundle,
                    ) = Unit

                    override fun onActivityDestroyed(activity: Activity) = Unit
                },
            )
        }
    }
