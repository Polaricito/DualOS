package com.fahadali.oneos.ui

import android.graphics.Typeface
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.FragmentActivity
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.CarrierRules
import com.fahadali.oneos.core.model.GatePhase
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.SwitchGates
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.session.Readiness
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck

/**
 * The Switch confirmation dialog: explains what happens, lists every way back
 * to One UI, and requires explicit ticks for any unacknowledged warnings before
 * enabling Switch. LaunchJob re-runs every safety check before root launches.
 */
internal object SwitchConfirm {
    fun show(a: FragmentActivity) {
        val r = StageCheck.state.result?.takeIf { it.installd != null } ?: return Readiness.tick(a, force = true)
        val facts = FactProbe(a).refresh(r.facts)
        val report = StageCheck.gates(a, r, GatePhase.SWITCH, screenLock = facts.screenLockSet, facts = facts)
        if (report.blocked) {
            Toast.makeText(a, a.getString(R.string.switch_blocked_now), Toast.LENGTH_LONG).show()
            return
        }
        val profile = report.profile ?: return
        val image = report.image ?: return
        val system = image.variant?.takeIf { it.length <= 12 }?.let { "${image.name} ($it)" } ?: image.name
        val remembered = SessionPrefs.acks(a, profile.id, image.id)
        val asks = report.toTick.filter { it.tickKey !in remembered }
        val hideGms = SessionPrefs.hideGms(a) && image.gmsHideable
        val secIms = SessionPrefs.secIms(a)
        val edmStub = SessionPrefs.edmStub(a)

        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        root.addView(TextView(a).apply {
            text = a.getString(R.string.confirm_switch_msg, system)
            textSize = 14f
        })

        val notes = buildList {
            report.find(SwitchGates.ID_CARRIER)?.finding?.takeIf { !secIms && it.level == Level.INFO && it.reason == CarrierRules.R_2G_3G }?.let {
                add(a.getString(R.string.confirm_calls_2g, system))
            }
            if (hideGms) add(a.getString(R.string.confirm_gms_hidden))
            if (!SessionPrefs.heatCap(a)) add(a.getString(R.string.confirm_heat_cap_off))
            if (secIms) add(a.getString(R.string.confirm_sec_ims, system))
            if (!edmStub) add(a.getString(R.string.confirm_edm_stub, system))
            if (image.presentsGms) add(a.getString(R.string.confirm_setup_wizard, system))
        }
        if (notes.isNotEmpty()) {
            root.addView(TextView(a).apply {
                text = notes.joinToString("\n") { "•  $it" }
                textSize = 13f
                setPadding(0, dp(10), 0, 0)
            })
        }

        val ways = buildList {
            if (report.set?.hasReturnApp == true) {
                add(a.getString(R.string.home_sheet_back_app) to a.getString(R.string.home_sheet_back_app_line, system))
            }
            add(a.getString(R.string.home_sheet_back_keys) to a.getString(R.string.home_sheet_back_keys_line))
            add(a.getString(R.string.home_sheet_back_stuck) to a.getString(R.string.home_sheet_back_stuck_line))
        }
        root.addView(TextView(a).apply {
            text = a.getString(if (ways.size == 3) R.string.home_sheet_ways_three else R.string.home_sheet_ways_two)
            setTypeface(null, Typeface.BOLD)
            textSize = 14f
            setPadding(0, dp(14), 0, dp(4))
        })
        for ((wTitle, wLine) in ways) {
            root.addView(TextView(a).apply {
                text = "• $wTitle — $wLine"
                textSize = 13f
                setPadding(0, dp(3), 0, dp(3))
            })
        }

        val boxes = asks.map { g ->
            CheckBox(a).apply {
                text = g.ack
                tag = g.tickKey
                setPadding(dp(4), dp(8), 0, dp(4))
                root.addView(this)
            }
        }

        val scroll = ScrollView(a).apply { addView(root) }
        val dialog = MaterialAlertDialogBuilder(a)
            .setTitle(a.getString(R.string.confirm_switch_title, system))
            .setView(scroll)
            .setPositiveButton(R.string.confirm_switch_go, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val go = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            fun update() { go.isEnabled = boxes.all { it.isChecked } }
            boxes.forEach { b -> b.setOnCheckedChangeListener { _, _ -> update() } }
            update()
            go.setOnClickListener {
                if (!boxes.all { it.isChecked }) return@setOnClickListener
                if (Readiness.anyBusy()) {
                    Toast.makeText(a, a.getString(R.string.home_checking), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val ticked = remembered + boxes.mapNotNull { it.tag as? String }
                dialog.dismiss()
                LaunchJob.start(a, dryRun = false, ticked = ticked, hideGms = hideGms, secIms = secIms, edmStub = edmStub)
                if (LaunchJob.state.busy) a.startActivity(BootProgressActivity.intent(a, image))
            }
        }
        dialog.show()
    }
}
