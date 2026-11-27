package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.WebForgeAccent
import com.example.ui.theme.WebForgePrimary
import com.example.ui.theme.WebForgeSuccess

val PrimaryCtaBrush: Brush
    get() = Brush.horizontalGradient(listOf(WebForgePrimary, WebForgeAccent))

val MintCtaBrush: Brush
    get() = Brush.horizontalGradient(listOf(WebForgeSuccess, WebForgeAccent))

@Composable
fun WebForgeTopBar(
    projectName: String,
    isOffline: Boolean,
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val ext = LocalWebForgeColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onLogoClick)
                .padding(end = 8.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.webforge_icon_default),
                contentDescription = strings.appName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = strings.appName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = projectName.ifBlank { strings.appSubtitle },
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val statusBg by animateColorAsState(
            targetValue = if (isOffline) ext.warning.copy(alpha = 0.16f) else ext.success.copy(alpha = 0.16f),
            label = "statusBg",
        )
        val statusColor = if (isOffline) ext.warning else ext.success

        Surface(
            color = statusBg,
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                    contentDescription = if (isOffline) strings.statusOffline else strings.statusOnline,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (isOffline) strings.statusOffline else strings.statusOnline,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun GlowingFrameCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    glowing: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val ext = LocalWebForgeColors.current
    val borderBrush = if (glowing) {
        Brush.linearGradient(
            listOf(
                ext.glowBorder.copy(alpha = 0.85f),
                ext.accent.copy(alpha = 0.55f),
                ext.glowBorder.copy(alpha = 0.85f),
            ),
        )
    } else {
        Brush.linearGradient(listOf(ext.cardBorder, ext.cardBorder))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = 1.5.dp, brush = borderBrush, shape = shape),
        content = content,
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = PrimaryCtaBrush,
    icon: ImageVector? = null,
    testTag: String = "",
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    height: Dp = 50.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "btnScale",
    )

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = height)
            .clip(shape)
            .background(
                brush = if (enabled) brush else Brush.horizontalGradient(
                    listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)),
                ),
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OutlinedGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = "",
    height: Dp = 50.dp,
) {
    val ext = LocalWebForgeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "outBtnScale",
    )

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
            .border(1.5.dp, ext.glowBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = ext.accent),
                onClick = onClick,
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ext.accent,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ExportOptionCard(
    title: String,
    subtitle: String,
    badgeColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ext = LocalWebForgeColors.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ext.cardBorder),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF0C0A20),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
