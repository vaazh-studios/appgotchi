package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.app_name
import com.vaazhstudios.appgotchi.ui.theme.AppgotchiTheme
import org.jetbrains.compose.resources.stringResource

private val ButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

// Material's disabled-content emphasis
private const val DisabledAlpha = 0.38f
private val FocusRingWidth = 2.dp
private val FocusRingOffset = 2.dp

/**
 * Lime keyboard-focus ring drawn [FocusRingOffset] outside the control, like the prototype's focus-visible outline.
 * It is painted behind the content outside the bounds, so layout size does not change.
 */
@Composable
fun Modifier.focusRing(interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val color = AppgotchiTheme.colors.focus
    if (!focused) return this
    return drawBehind {
        val width = FocusRingWidth.toPx()
        // The stroke is centred on its path, so grow by half of it as well to keep the gap exact
        val grow = FocusRingOffset.toPx() + width / 2
        val outline = shape.createOutline(Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
        translate(-grow, -grow) { drawOutline(outline, color, style = Stroke(width)) }
    }
}

/** The one prominent action on a screen: near-black (light) / near-white (dark). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.focusRing(interactionSource, MaterialTheme.shapes.small),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        interactionSource = interactionSource,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** Hairline-ringed secondary action. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val outline = MaterialTheme.colorScheme.outline
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.focusRing(interactionSource, MaterialTheme.shapes.small),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (enabled) outline else outline.copy(alpha = DisabledAlpha * outline.alpha)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        interactionSource = interactionSource,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** Text-only action such as Back, Close or Remove. */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.focusRing(interactionSource, MaterialTheme.shapes.small),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        interactionSource = interactionSource,
        contentPadding = ButtonPadding,
        content = content,
    )
}

/** Pill-style switch between a few options (e.g. App Store / Google Play). */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val segmentShape = RoundedCornerShape(6.dp)
    val emphasis = if (enabled) 1f else DisabledAlpha
    // background(shape) rather than clip(), so the focus ring around a segment isn't cut off by the track
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val interactionSource = remember { MutableInteractionSource() }
            val selectedFill = AppgotchiTheme.colors.selectedSegment
            val ringColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .focusRing(interactionSource, segmentShape)
                    .clip(segmentShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(selectedFill.copy(alpha = selectedFill.alpha * emphasis))
                                .border(1.dp, ringColor.copy(alpha = ringColor.alpha * emphasis), segmentShape)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(value) },
                    )
                    .heightIn(min = 40.dp)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = (if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary).let {
                        it.copy(alpha = it.alpha * emphasis)
                    },
                )
            }
        }
    }
}

/** Ringed card used for list items and grouped content. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = AppgotchiTheme.colors.cardFill,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Lime dot + wordmark, shown at the top of the main screen. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Outlined text field colours: hairline border, lime focus ring. */
@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppgotchiTheme.colors.focus,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)
