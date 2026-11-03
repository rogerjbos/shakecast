package com.rogerbos.shakecast.data.crash

import android.content.Context
import android.util.Log
import com.rogerbos.shakecast.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import timber.log.Timber

/**
 * Opt-in crash reporting through Sentry. Nothing is initialised unless the build
 * carries a DSN and the person turned the switch on; turning it off closes the SDK.
 */
object CrashReporting {
    val available: Boolean get() = BuildConfig.SENTRY_DSN.isNotBlank()

    fun apply(context: Context, enabled: Boolean) {
        if (!available) return
        if (enabled && !Sentry.isEnabled()) start(context) else if (!enabled && Sentry.isEnabled()) Sentry.close()
    }

    private fun start(context: Context) {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.isSendDefaultPii = false
            options.isEnableAutoSessionTracking = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
            options.isEnableActivityLifecycleBreadcrumbs = true
            options.tracesSampleRate = null
        }
    }

    /** Timber tree for release builds: warnings and errors become breadcrumbs on the next crash report. */
    class BreadcrumbTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!Sentry.isEnabled()) return
            val crumb = Breadcrumb(message).apply {
                category = tag ?: "app"
                level = if (priority >= Log.ERROR) SentryLevel.ERROR else SentryLevel.WARNING
            }
            Sentry.addBreadcrumb(crumb)
            if (t != null && priority >= Log.ERROR) Sentry.captureException(t)
        }
    }
}
