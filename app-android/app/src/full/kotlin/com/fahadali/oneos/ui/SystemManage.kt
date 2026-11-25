package com.fahadali.oneos.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.root.OldStage
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.RemoveJob

/**
 * Start fresh, Remove and Delete saved copy of one system: confirmations,
 * live progress dialog, and result reporting.
 */
internal object SystemManage {
    private enum class Waiting { NONE, REMOVE, DELETE, DELETE_ALL }

    private var waiting = Waiting.NONE
    private var removeKind: RemoveJob.Kind? = null
    private var savedName: String? = null
    private var dialogUp = false

    fun canRun(): Boolean = RemoveJob.canStart()

    fun confirmStartFresh(a: Activity, image: ImageRef, onDone: (Result) -> Unit = {}) {
        if (!guard(a)) return
        AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.manage_start_fresh_title, image.name))
            .setMessage(a.getString(R.string.manage_start_fresh_message, image.name))
            .setPositiveButton(R.string.manage_start_fresh_run) { _, _ -> run(a, RemoveJob.Kind.DATA, image, onDone) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun confirmRemove(a: Activity, image: ImageRef, onDone: (Result) -> Unit = {}) {
        if (!guard(a)) return
        AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.manage_remove_title, image.name))
            .setMessage(a.getString(R.string.manage_remove_message, image.name))
            .setPositiveButton(R.string.remove_confirm_run) { _, _ -> run(a, RemoveJob.Kind.SYSTEM, image, onDone) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun confirmDeleteSaved(a: Activity, image: ImageRef, savedCopy: String, onDone: (Result) -> Unit = {}) {
        if (!guard(a)) return
        AlertDialog.Builder(a)
            .setTitle(R.string.space_delete_title)
            .setMessage(a.getString(R.string.manage_delete_saved_message, image.name))
            .setPositiveButton(R.string.space_delete_run) { _, _ ->
                if (!canRun()) { busy(a); return@setPositiveButton }
                waiting = Waiting.DELETE
                savedName = "Saved ${image.name}"
                OldStagesJob.delete(a, OldStage(savedCopy, 0, image.id, hasData = true, busy = false))
                progress(a, a.getString(R.string.manage_progress_deleting_saved, image.name), onDone)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun deleteCopy(a: Activity, stage: OldStage, label: String) {
        if (!canRun()) { busy(a); return }
        waiting = Waiting.DELETE
        savedName = label
        OldStagesJob.delete(a, stage)
        progress(a, a.getString(R.string.manage_progress_deleting_copy, label)) {}
    }

    fun deleteAllCopies(a: Activity) {
        if (!canRun()) { busy(a); return }
        waiting = Waiting.DELETE_ALL
        OldStagesJob.deleteAll(a)
        progress(a, a.getString(R.string.manage_progress_deleting_all)) {}
    }

    fun removeAll(a: Activity) {
        if (!canRun()) { busy(a); return }
        waiting = Waiting.REMOVE
        removeKind = RemoveJob.Kind.ALL
        RemoveJob.start(a, RemoveJob.Kind.ALL)
        progress(a, a.getString(R.string.remove_running).trimEnd('…')) {}
    }

    private fun run(a: Activity, kind: RemoveJob.Kind, image: ImageRef, onDone: (Result) -> Unit) {
        if (!canRun()) { busy(a); return }
        waiting = Waiting.REMOVE
        removeKind = kind
        RemoveJob.start(a, kind, image.name)
        val title = when (kind) {
            RemoveJob.Kind.DATA -> a.getString(R.string.manage_progress_erasing, image.name)
            else -> a.getString(R.string.manage_progress_removing, image.name)
        }
        progress(a, title, onDone)
    }

    private fun guard(a: Activity): Boolean {
        if (canRun()) return true
        busy(a)
        return false
    }

    private fun busy(a: Activity) = Toast.makeText(a, R.string.manage_busy, Toast.LENGTH_LONG).show()

    private var allSince = 0L
    private fun running(): Pair<String?, Long> = when (waiting) {
        Waiting.DELETE -> OldStagesJob.state.let { it.step to it.startedAt }
        Waiting.DELETE_ALL -> OldStagesJob.state.let { s ->
            if (allSince == 0L) allSince = SystemClock.elapsedRealtime()
            s.batch?.let { (i, n) -> "Deleting copy $i of $n…" } to allSince
        }
        else -> RemoveJob.state.let { it.step to it.startedAt }
    }

    private fun elapsed(since: Long): String {
        val s = ((SystemClock.elapsedRealtime() - since) / 1000).coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    private fun progress(a: Activity, title: String, onDone: (Result) -> Unit) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val spinner = ProgressBar(a).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(16) }
        }
        val step = TextView(a).apply { textSize = 15f }
        val time = TextView(a).apply { textSize = 13f; alpha = 0.72f }
        val textCol = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(step)
            addView(time)
        }
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(spinner)
            addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val handler = Handler(Looper.getMainLooper())
        val since = SystemClock.elapsedRealtime()
        val dialog = AlertDialog.Builder(a)
            .setTitle(title)
            .setView(row)
            .setCancelable(false)
            .setPositiveButton(R.string.manage_progress_hide, null)
            .create()
        val tick = object : Runnable {
            override fun run() {
                val r = take()
                val now = running().first
                if (r == null) {
                    step.text = now ?: a.getString(R.string.manage_progress_starting)
                    time.text = a.getString(R.string.manage_progress_time, elapsed(since))
                    handler.postDelayed(this, 500)
                    return
                }
                spinner.visibility = View.GONE
                dialog.setTitle(if (r.failed) a.getString(R.string.environment_manage_failed) else a.getString(R.string.manage_progress_done))
                step.text = r.message
                time.text = a.getString(R.string.manage_progress_took, elapsed(since))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    setText(android.R.string.ok)
                    setOnClickListener { dialog.dismiss(); onDone(r) }
                }
            }
        }
        dialog.setOnShowListener { handler.post(tick) }
        dialog.setOnDismissListener {
            handler.removeCallbacks(tick)
            dialogUp = false
        }
        dialogUp = true
        dialog.show()
    }

    class Result(val message: String, val failed: Boolean, val systemRemoved: Boolean)

    fun takeResult(): Result? = if (dialogUp) null else take()

    private fun take(): Result? {
        when (waiting) {
            Waiting.NONE -> return null
            Waiting.REMOVE -> {
                val s = RemoveJob.state
                if (s.busy || s.phase == RemoveJob.Phase.IDLE) return null
                waiting = Waiting.NONE
                val failed = s.phase == RemoveJob.Phase.FAILED
                return Result(s.message.orEmpty(), failed, !failed && removeKind == RemoveJob.Kind.SYSTEM)
            }
            Waiting.DELETE_ALL -> {
                val s = OldStagesJob.state
                if (s.busy || (s.phase != OldStagesJob.Phase.DONE && s.phase != OldStagesJob.Phase.FAILED)) return null
                waiting = Waiting.NONE
                allSince = 0L
                return Result(s.message.orEmpty(), s.phase == OldStagesJob.Phase.FAILED, false)
            }
            Waiting.DELETE -> {
                val s = OldStagesJob.state
                if (s.busy || (s.phase != OldStagesJob.Phase.DONE && s.phase != OldStagesJob.Phase.FAILED)) return null
                waiting = Waiting.NONE
                val freed = s.freedKib?.let { OldStagesJob.gib(it) }
                val line = if (s.deleteFailed || freed == null) s.message.orEmpty() else "${savedName?.replaceFirstChar { it.uppercase() }} deleted: $freed freed."
                return Result(line, s.deleteFailed, false)
            }
        }
    }

    fun showResult(a: Activity): Result? = takeResult()?.also {
        if (it.message.isNotEmpty()) Toast.makeText(a.applicationContext, it.message, Toast.LENGTH_LONG).show()
    }
}
