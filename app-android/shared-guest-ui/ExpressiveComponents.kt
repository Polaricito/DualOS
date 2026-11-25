package com.fahadali.oneos.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class StatusTone { OK, WARN, BLOCK, INFO, NEUTRAL }

@Composable
fun statusToneColors(tone: StatusTone): Pair<Color, Color> {
    val status = LocalDualOsStatusColors.current
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.OK -> status.okContainer to status.onOkContainer
        StatusTone.WARN -> status.warnContainer to status.onWarnContainer
        StatusTone.BLOCK -> status.blockContainer to status.onBlockContainer
        StatusTone.INFO -> status.infoContainer to status.onInfoContainer
        StatusTone.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

@Composable
fun statusAccentColor(tone: StatusTone): Color {
    val status = LocalDualOsStatusColors.current
    return when (tone) {
        StatusTone.OK -> status.ok
        StatusTone.WARN -> status.warn
        StatusTone.BLOCK -> status.block
        StatusTone.INFO -> status.info
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingTone: StatusTone = StatusTone.INFO,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        if (!trailing.isNullOrBlank()) {
            ExpressiveStatusPill(text = trailing, tone = trailingTone)
        }
    }
}

@Composable
fun ExpressiveStatusPill(
    text: String,
    tone: StatusTone = StatusTone.INFO,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val (bg, fg) = statusToneColors(tone)
    Surface(
        modifier = modifier,
        color = bg,
        contentColor = fg,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ExpressiveGroupedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun ExpressiveListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
fun ExpressiveListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: StatusTone? = null,
    badge: String? = null,
    badgeTone: StatusTone = StatusTone.INFO,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.48f)
        .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 18.dp, vertical = 14.dp)

    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val (bg, fg) = if (iconTone != null) {
                statusToneColors(iconTone)
            } else {
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!badge.isNullOrBlank()) {
                    ExpressiveStatusPill(text = badge, tone = badgeTone)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        } else if (showChevron && onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun ExpressiveSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: StatusTone? = null,
    enabled: Boolean = true,
) {
    ExpressiveListItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTone = iconTone,
        enabled = enabled,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingBox(
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ExpressiveSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val shapes = when {
                options.size == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { if (it) onSelect(value) },
                enabled = enabled,
                shapes = shapes,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpressiveModuleCard(
    name: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean,
    version: String = "",
    author: String = "",
    status: String? = null,
    toggleEnabled: Boolean = true,
    options: List<Pair<String, () -> Unit>> = emptyList(),
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onToggle,
                    enabled = enabled && toggleEnabled,
                )
                if (options.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = enabled,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Module options",
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            options.forEach { (label, action) ->
                                val isDanger = label.startsWith("Uninstall")
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        action()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (version.isNotBlank() || author.isNotBlank() || !status.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (version.isNotBlank()) {
                        ExpressiveStatusPill(text = "v$version", tone = StatusTone.NEUTRAL)
                    }
                    if (author.isNotBlank()) {
                        ExpressiveStatusPill(text = author, tone = StatusTone.NEUTRAL)
                    }
                    if (!status.isNullOrBlank()) {
                        val tone = if (status.contains("Uninstall", ignoreCase = true)) StatusTone.WARN else StatusTone.INFO
                        ExpressiveStatusPill(text = status, tone = tone)
                    }
                }
            }
        }
    }
}
