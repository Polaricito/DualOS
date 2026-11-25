package com.fahadali.oneos.report

import android.content.Context
import android.os.Build
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.core.model.UsageHello
import java.util.concurrent.Executors

/**
 * Anonymous usage count (Settings, on by default): at most once per UTC day the app tells the
 * report service (POST /v1/hello, [ReportSender]'s address and upload key) a random install id,
 * the phone model, the OneOS version, the phone profile, the systems set up and how many
 * switches ran since the last check-in, so the team can count active phones. The id is created
 * once here and is never derived from anything on the phone. Failures are silent and the next
 * app start tries again; the switch counter is reset only after the service's 200.
 * The JSON and the day gate are [UsageHello] (unit tested).
 */
object UsageCheckIn {
    private const val PREFS = "oneos_usage"
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "oneos-usage").apply { isDaemon = true } }

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = p(ctx).getBoolean("enabled", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean("enabled", on).apply()
    }

    /** A launch (switch) was started. */
    fun switchStarted(ctx: Context) {
        val s = p(ctx)
        s.edit().putInt("switches", (s.getInt("switches", 0) + 1).coerceAtMost(UsageHello.MAX_SWITCHES)).apply()
    }

    /** Upstream telemetry disabled in DualOS. */
    fun maybeSend(ctx: Context, profile: () -> String?, systems: () -> Collection<String?>) {
        return
    }
}
