package com.athar

import android.app.Application
import com.athar.core.data.AppDataInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class AtharApplication : Application() {

    @Inject lateinit var dataInitializer: AppDataInitializer

    private val initErrorHandler = CoroutineExceptionHandler { _, t ->
        Timber.e(t, "AppDataInitializer failed")
        writeCrashLog("AppDataInitializer.initialize() threw", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + initErrorHandler)

    override fun onCreate() {
        super.onCreate()
        // Plant logging in all builds. Logcat is the canonical surface (`adb logcat | grep athar`).
        // Beta + production builds also persist uncaught exceptions to `files/crash.log` so the
        // user can share it without a connected dev machine.
        Timber.plant(Timber.DebugTree())
        installCrashHandler()
        Timber.i("Athar starting · versionName=%s", BuildConfig.VERSION_NAME)

        scope.launch { dataInitializer.initialize(seedTmoap = BuildConfig.SEED_ON_FIRST_LAUNCH) }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog("Uncaught on thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(prefix: String, t: Throwable) {
        runCatching {
            val log = File(filesDir, "crash.log")
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            log.appendText(
                "===== $timestamp =====\n" +
                    "$prefix\n" +
                    "versionName=${BuildConfig.VERSION_NAME}\n" +
                    sw.toString() +
                    "\n",
            )
        }
    }
}
