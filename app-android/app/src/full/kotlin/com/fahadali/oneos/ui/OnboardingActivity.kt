package com.fahadali.oneos.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.RootKind
import com.fahadali.oneos.core.model.RootRules
import com.fahadali.oneos.core.root.RootProbe
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveStatusPill

/** Welcome -> Prerequisites -> Before you start flow. Never relaxes switching gates. */
class OnboardingActivity : AppCompatActivity() {
    private var page by mutableIntStateOf(0)
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt("page") ?: 0
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        super.onSaveInstanceState(outState)
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = refreshTick
        BackHandler(enabled = page > 0) { page-- }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { i ->
                                Surface(
                                    modifier = Modifier.size(width = if (i == page) 24.dp else 8.dp, height = 8.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                ) {}
                            }
                        }
                        Button(
                            onClick = {
                                if (page < 2) {
                                    page++
                                } else {
                                    BeforeYouStart.acknowledge(this@OnboardingActivity)
                                    startActivity(
                                        Intent(this@OnboardingActivity, MainActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                    )
                                    finish()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                text = stringResource(if (page == 0) R.string.onboarding_start else R.string.before_continue),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "onboarding_page",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding(),
            ) { currentPage ->
                when (currentPage) {
                    0 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.onboarding_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(116.dp),
                                )
                                ExpressiveStatusPill(
                                    text = "Galaxy A53 5G · DualOS 2.0",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_welcome),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.about_what_summary),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    1 -> {
                        val root = RootRules.kind(RootProbe(this@OnboardingActivity).signals())
                        val lockStatus = when (FactProbe.screenLockSet(this@OnboardingActivity)) {
                            true -> R.string.onboarding_lock_on
                            false -> R.string.onboarding_lock_off
                            null -> R.string.environment_unknown
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.onboarding_prerequisites),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            item {
                                ExpressiveGroupedCard {
                                    ExpressiveListItem(
                                        title = stringResource(R.string.onboarding_root),
                                        subtitle = if (root == RootKind.NONE) stringResource(R.string.onboarding_no_root)
                                        else stringResource(R.string.onboarding_root_found, root.label),
                                        icon = DualOsIcons.Security,
                                        showChevron = true,
                                        onClick = { startActivity(Intent(this@OnboardingActivity, DeviceReportActivity::class.java)) },
                                    )
                                    ExpressiveListDivider()
                                    ExpressiveListItem(
                                        title = stringResource(R.string.onboarding_lock),
                                        subtitle = stringResource(R.string.onboarding_lock_summary) + "\n" + stringResource(lockStatus),
                                        icon = Icons.Rounded.Lock,
                                        showChevron = true,
                                        onClick = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.before_title),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            item {
                                ExpressiveGroupedCard {
                                    ExpressiveListItem(
                                        title = stringResource(R.string.onboarding_experimental),
                                        subtitle = stringResource(R.string.onboarding_experimental_summary),
                                        icon = Icons.Rounded.Info,
                                    )
                                    ExpressiveListDivider()
                                    ExpressiveListItem(
                                        title = stringResource(R.string.onboarding_backup),
                                        subtitle = stringResource(R.string.onboarding_backup_summary),
                                        icon = DualOsIcons.FolderOpen,
                                    )
                                    ExpressiveListDivider()
                                    ExpressiveListItem(
                                        title = stringResource(R.string.onboarding_data),
                                        subtitle = stringResource(R.string.onboarding_data_summary),
                                        icon = DualOsIcons.Storage,
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }
            }
        }
    }
}
