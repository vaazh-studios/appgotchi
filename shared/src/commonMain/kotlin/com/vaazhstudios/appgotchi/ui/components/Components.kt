package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.app_name
import appgotchi.shared.generated.resources.component_collapsed
import appgotchi.shared.generated.resources.component_expanded
import appgotchi.shared.generated.resources.component_paste
import appgotchi.shared.generated.resources.component_paste_into
import com.vaazhstudios.appgotchi.ui.theme.AppgotchiTheme
import kotlinx.coroutines.launch
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

/** One numbered instruction; [url] adds a button under the text that opens it in the browser. */
data class ChecklistItem(
    val text: String,
    val note: String? = null,
    val linkLabel: String? = null,
    val url: String? = null,
)

@Composable
fun StepChecklist(items: List<ChecklistItem>, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(24.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.text, style = MaterialTheme.typography.bodyLarge)
                    item.note?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.url != null && item.linkLabel != null) {
                        // Opening a browser can fail (no handler); never crash over it
                        val linkName = "${item.linkLabel.removeSuffix("↗").trim()}: ${item.text}"
                        SecondaryButton(
                            onClick = { runCatching { uriHandler.openUri(item.url) } },
                            modifier = Modifier.semantics { contentDescription = linkName },
                        ) {
                            Text("${item.linkLabel} ↗")
                        }
                    }
                }
            }
        }
    }
}

/** Full-width list card used to choose between options (e.g. which store to connect). */
@Composable
fun ChoiceCard(
    title: String,
    description: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().focusRing(interactionSource, MaterialTheme.shapes.medium).semantics { role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        color = AppgotchiTheme.colors.cardFill,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        interactionSource = interactionSource,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, style = MaterialTheme.typography.titleSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Text field for IDs people copy from a console, with a Paste button and an error line. */
@Composable
fun PasteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    monospace: Boolean = true,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pasteIntoLabel = stringResource(Res.string.component_paste_into, label)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            textStyle = if (monospace) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            colors = appTextFieldColors(),
            trailingIcon = {
                QuietButton(
                    onClick = { scope.launch { readClipboardText(clipboard)?.let { onValueChange(it.trim()) } } },
                    modifier = Modifier.semantics { contentDescription = pasteIntoLabel },
                    enabled = enabled,
                ) { Text(stringResource(Res.string.component_paste)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** A collapsed "trouble?" note that expands in place. */
@Composable
fun HelpDisclosure(title: String, body: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val state = stringResource(if (expanded) Res.string.component_expanded else Res.string.component_collapsed)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = AppgotchiTheme.colors.warningContainer,
        border = BorderStroke(1.dp, AppgotchiTheme.colors.pending.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            QuietButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.semantics {
                    role = Role.Button
                    stateDescription = state
                },
            ) {
                Text(title, color = AppgotchiTheme.colors.onWarningContainer)
            }
            if (expanded) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppgotchiTheme.colors.onWarningContainer,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                )
            }
        }
    }
}
