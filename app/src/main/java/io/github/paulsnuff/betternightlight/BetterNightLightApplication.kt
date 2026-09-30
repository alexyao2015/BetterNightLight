package io.github.paulsnuff.betternightlight

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import io.github.paulsnuff.betternightlight.data.AppForegroundTracker
import io.github.paulsnuff.betternightlight.domain.DeviceLocationRefreshCoordinator
import javax.inject.Inject

@HiltAndroidApp
class BetterNightLightApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appForegroundTracker: AppForegroundTracker

    @Inject
    lateinit var locationRefreshCoordinator: DeviceLocationRefreshCoordinator

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        appForegroundTracker.start(this)
        locationRefreshCoordinator.start()
        Shell.setDefaultBuilder(Shell.Builder.create().setContext(this))
    }
}
