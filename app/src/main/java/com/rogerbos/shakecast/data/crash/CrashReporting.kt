package com.rogerbos.shakecast.data.crash

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.rogerbos.shakecast.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import timber.log.Timber

/**
 * Opt-in crash reporting through Sentry. Nothing is initialised unless the build
 * carries a DSN and the person turned the switch on; turning it off closes the SDK.
 */
object CrashReporting {
    val available: Boolean get() = BuildConfig.SENTRY_DSN.isNotBlank()

    /**
     * The consent is mirrored in a plain preference so the SDK can start synchronously in
     * `Application.onCreate`, before the settings DataStore has been read; otherwise crashes
     * during cold start would never be reported.
     */
    fun startIfConsented(context: Context) {
        if (available && consentMirror(context).getBoolean(KEY_ENABLED, false)) start(context)
    }

    fun apply(context: Context, enabled: Boolean) {
        if (!available) return
        consentMirror(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled && !Sentry.isEnabled()) start(context) else if (!enabled && Sentry.isEnabled()) Sentry.close()
    }

    private fun consentMirror(context: Context) = context.getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)

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
            // The SDK keys events by a random per-install id; drop it so reports carry no identifier at all.
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                event.user = null
                event
            }
        }
    }

    /**
     * Timber tree for release builds: warnings become breadcrumbs on the next crash report.
     * Only the warning's label and the exception class travel. Timber appends the stack trace
     * to the message, and the part after " for " or ":" is where feed titles, addresses and
     * file names live, so everything from the first newline, " for " or ":" is dropped.
     */
    private const val KEY_ENABLED = "enabled"

    class BreadcrumbTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!Sentry.isEnabled()) return
            val crumb = Breadcrumb(redact(message, t)).apply {
                category = tag ?: "app"
                level = if (priority >= Log.ERROR) SentryLevel.ERROR else SentryLevel.WARNING
            }
            Sentry.addBreadcrumb(crumb)
        }

        internal fun redact(message: String, t: Throwable?): String {
            val cut = listOf("\n", " for ", ":").mapNotNull { sep -> message.indexOf(sep).takeIf { it >= 0 } }.minOrNull()
            val label = (if (cut == null) message else message.substring(0, cut)).trim()
            return if (t == null) label else "$label (${t.javaClass.simpleName})"
        }
    }
}
