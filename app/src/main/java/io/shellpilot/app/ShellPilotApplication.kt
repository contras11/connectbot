package io.shellpilot.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.shellpilot.app.logging.TimberInitializer
import javax.inject.Inject

@HiltAndroidApp
class ShellPilotApplication : Application() {

    @Inject
    lateinit var timberInitializer: TimberInitializer

    override fun onCreate() {
        super.onCreate()
        timberInitializer.initialize()
    }
}
