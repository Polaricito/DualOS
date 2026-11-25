package com.fahadali.oneos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem

/**
 * The "Before you start" acknowledgement, kept in app-private
 * SharedPreferences (allowBackup is off and data_extraction_rules.xml
 * excludes it from backup and device transfer). Bump [VERSION] when the
 * screen's content changes so everyone reads it again.
 */
object BeforeYouStart {
    const val VERSION = 3
    private const val PREFS = "oneos"
    private const val KEY_ACK_VERSION = "before_you_start_ack_version"

    fun isAcknowledged(context: Context): Boolean = prefs(context).getInt(KEY_ACK_VERSION, 0) >= VERSION

    fun acknowledge(context: Context) {
        prefs(context).edit().putInt(KEY_ACK_VERSION, VERSION).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal data class BeforeItem(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector,
)

/**
 * Before you start: shown at every launch until the user ticks the checkbox
 * and taps Continue (Back closes the app). Opened again from Home or About
 * once acknowledged, it is read-only with a back arrow.
 */
class BeforeYouStartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val firstRun = !BeforeYouStart.isAcknowledged(this)
        val items = EditionBeforeYouStart.items()
        setContent {
            DualOsTheme {
                var checked by rememberSaveable { mutableStateOf(false) }
                DualOsScaffold(
                    title = stringResource(R.string.before_title),
                    onBack = if (firstRun) null else ({ finish() }),
                    bottomBar = {
                        if (firstRun) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 3.dp,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { checked = !checked },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { checked = it },
                                        )
                                        Text(
                                            text = stringResource(EditionBeforeYouStart.ackRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (!checked) return@Button
                                            BeforeYouStart.acknowledge(this@BeforeYouStartActivity)
                                            startActivity(Intent(this@BeforeYouStartActivity, MainActivity::class.java))
                                            finish()
                                        },
                                        enabled = checked,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.before_continue),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            ExpressiveGroupedCard {
                                items.forEachIndexed { index, item ->
                                    if (index > 0) ExpressiveListDivider()
                                    ExpressiveListItem(
                                        title = stringResource(item.titleRes),
                                        subtitle = stringResource(item.summaryRes),
                                        icon = item.icon,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}
