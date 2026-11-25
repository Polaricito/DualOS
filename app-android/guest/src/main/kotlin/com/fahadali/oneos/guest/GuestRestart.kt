package com.fahadali.oneos.guest

import android.app.Activity
import android.content.Intent
import java.io.File
import java.io.FileOutputStream

/** Authenticated by the same private app-data boundary as the existing return request. */
object GuestRestart {
    const val EXTRA_RESTART = "restart_guest"

    fun launch(activity: Activity) {
        activity.startActivity(Intent(activity, ReturnSplashActivity::class.java).putExtra(EXTRA_RESTART, true))
    }

    fun pending(context: android.content.Context) = File(context.filesDir, "oneos_restart").isFile

    fun send(context: android.content.Context) {
        val tmp = File(context.filesDir, "oneos_restart.tmp")
        FileOutputStream(tmp).use { out ->
            out.write("restart ${System.currentTimeMillis() / 1000}\n".toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(File(context.filesDir, "oneos_restart"))) {
            tmp.delete()
            throw java.io.IOException("Could not request guest restart")
        }
    }
}
