package dev.kwonsu.standby

import android.app.Application

class StandbyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.initialize(this)
        Diagnostics.record("process_started", "앱 ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}")
    }
}
