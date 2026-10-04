# Calm Developer-Tool Theme — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stock Material 3 look with Appgotchi's calm developer-tool theme (approved prototype: `design/theme/index.html`): zinc neutrals, near-black primary buttons (white in dark mode), a sparing lime accent, Inter, 8 dp controls, ringed 12 dp cards.

**Architecture:** A small design system in `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/` — colour schemes, extended colours (`AppgotchiTheme.colors`), Inter typography, shapes — plus reusable components (`PrimaryButton`, `SecondaryButton`, `QuietButton`, `SegmentedControl`, `AppCard`, `StatusDot`, `BrandMark`, `appTextFieldColors()`). Screens switch to these components; behaviour and view models are unchanged.

**Tech Stack:** Compose Multiplatform 1.11.1, Material 3 (`compose-material3` 1.11.0-alpha07), Compose resources (fonts, plurals).

**Spec / reference:** `design/theme/index.html` (lime accent, cards) and `design/connect-wizard/index.html` (wizard structure). Decisions recorded in this plan.

## Global Constraints

- Neutrals are zinc; never default Material purple/indigo. Light: background/surface `#FFFFFF`, text `#09090B`. Dark: background/surface `#09090B`, text `#FAFAFA`.
- Primary buttons: `#09090B` with white text (light), `#FAFAFA` with `#09090B` text (dark). Only one primary button per screen state.
- Accent (lime) only for: logo dot, wizard progress, focus/drag highlight, "live" status. Light `#84CC16`, dark `#A3E635`.
- Borders/dividers use opacity, never solid greys: outline = text colour at 15 %, outlineVariant = text colour at 10 %.
- Font: Inter (Regular 400, Medium 500, SemiBold 600) bundled under `shared/src/commonMain/composeResources/font/` with its OFL licence committed at `THIRD_PARTY_LICENSES/Inter-OFL.txt`. Headings SemiBold, never Bold.
- Shapes: controls 8 dp, cards 12 dp.
- Behaviour, strings' meaning, view models and tests stay unchanged; existing tests must keep passing (`./gradlew :shared:jvmTest` = 37).
- This plan is visual: no new unit tests (nothing behavioural to assert). Verification = compile all targets + a human/agent visual check of the desktop app.
- Never run `./gradlew --stop`. Never push (CI is disabled and the user pays for it).
- Commit after every task; every message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: Design system — colours, Inter, shapes, components

**Files:**
- Create: `shared/src/commonMain/composeResources/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`
- Create: `THIRD_PARTY_LICENSES/Inter-OFL.txt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/Color.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/Type.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/Theme.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/components/Components.kt`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/App.kt`

**Interfaces:**
- Produces (package `com.vaazhstudios.appgotchi.ui.theme`): `@Composable fun AppgotchiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`; `object AppgotchiTheme { val colors: AppgotchiColors }` with fields `live`, `pending`, `warningContainer`, `onWarningContainer`, `selectedSegment`, `cardFill`.
- Produces (package `com.vaazhstudios.appgotchi.ui.components`): `PrimaryButton`, `SecondaryButton`, `QuietButton` (all `(onClick, modifier = Modifier, enabled = true, content: @Composable RowScope.() -> Unit)`), `SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier = Modifier, enabled = true)`, `AppCard(modifier = Modifier, contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit)`, `StatusDot(color: Color, modifier = Modifier)`, `BrandMark(modifier = Modifier)`, `@Composable fun appTextFieldColors(): TextFieldColors`.

- [ ] **Step 1: Add the Inter font files and licence**

Download the official Inter release and copy three static TTFs (do not use the variable font):

```bash
mkdir -p /private/tmp/claude-501/inter && cd /private/tmp/claude-501/inter
curl -fsSL -o inter.zip https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip
unzip -o -q inter.zip
find . -name 'Inter-Regular.ttf' -o -name 'Inter-Medium.ttf' -o -name 'Inter-SemiBold.ttf' -o -name 'LICENSE.txt'
```

Copy (paths from the `find` output; the static TTFs are under `extras/ttf/`):

```bash
R="/Users/nirmaljeffrey/tap and touch studios/appgotchi"
mkdir -p "$R/shared/src/commonMain/composeResources/font" "$R/THIRD_PARTY_LICENSES"
cp extras/ttf/Inter-Regular.ttf  "$R/shared/src/commonMain/composeResources/font/inter_regular.ttf"
cp extras/ttf/Inter-Medium.ttf   "$R/shared/src/commonMain/composeResources/font/inter_medium.ttf"
cp extras/ttf/Inter-SemiBold.ttf "$R/shared/src/commonMain/composeResources/font/inter_semibold.ttf"
cp LICENSE.txt "$R/THIRD_PARTY_LICENSES/Inter-OFL.txt"
```

If the release layout differs, find the equivalent static TTFs in the zip; keep the destination names.

- [ ] **Step 2: Colours**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/Color.kt`:

```kotlin
package com.vaazhstudios.appgotchi.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tailwind palette values, so the app matches the HTML prototypes in design/
private object Zinc {
    val c50 = Color(0xFFFAFAFA)
    val c100 = Color(0xFFF4F4F5)
    val c200 = Color(0xFFE4E4E7)
    val c400 = Color(0xFFA1A1AA)
    val c500 = Color(0xFF71717A)
    val c600 = Color(0xFF52525B)
    val c800 = Color(0xFF27272A)
    val c900 = Color(0xFF18181B)
    val c950 = Color(0xFF09090B)
}

private object Lime {
    val c100 = Color(0xFFECFCCB)
    val c200 = Color(0xFFD9F99D)
    val c400 = Color(0xFFA3E635)
    val c500 = Color(0xFF84CC16)
    val c900 = Color(0xFF365314)
    val c950 = Color(0xFF1A2E05)
}

private object Amber {
    val c50 = Color(0xFFFFFBEB)
    val c200 = Color(0xFFFDE68A)
    val c400 = Color(0xFFFBBF24)
    val c500 = Color(0xFFF59E0B)
    val c900 = Color(0xFF78350F)
    val c950 = Color(0xFF451A03)
}

private object Red {
    val c50 = Color(0xFFFEF2F2)
    val c200 = Color(0xFFFECACA)
    val c400 = Color(0xFFF87171)
    val c600 = Color(0xFFDC2626)
    val c900 = Color(0xFF7F1D1D)
    val c950 = Color(0xFF450A0A)
}

internal val LightColors = lightColorScheme(
    primary = Zinc.c950,
    onPrimary = Color.White,
    primaryContainer = Zinc.c100,
    onPrimaryContainer = Zinc.c950,
    secondary = Zinc.c600,
    onSecondary = Color.White,
    secondaryContainer = Zinc.c100,
    onSecondaryContainer = Zinc.c950,
    tertiary = Lime.c500,
    onTertiary = Zinc.c950,
    tertiaryContainer = Lime.c100,
    onTertiaryContainer = Lime.c900,
    background = Color.White,
    onBackground = Zinc.c950,
    surface = Color.White,
    onSurface = Zinc.c950,
    surfaceVariant = Zinc.c100,
    onSurfaceVariant = Zinc.c500,
    surfaceTint = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Zinc.c50,
    surfaceContainer = Zinc.c100,
    surfaceContainerHigh = Zinc.c100,
    surfaceContainerHighest = Zinc.c200,
    outline = Zinc.c950.copy(alpha = 0.15f),
    outlineVariant = Zinc.c950.copy(alpha = 0.10f),
    error = Red.c600,
    onError = Color.White,
    errorContainer = Red.c50,
    onErrorContainer = Red.c900,
    inverseSurface = Zinc.c900,
    inverseOnSurface = Zinc.c50,
    inversePrimary = Lime.c400,
)

internal val DarkColors = darkColorScheme(
    primary = Zinc.c50,
    onPrimary = Zinc.c950,
    primaryContainer = Zinc.c800,
    onPrimaryContainer = Zinc.c50,
    secondary = Zinc.c400,
    onSecondary = Zinc.c950,
    secondaryContainer = Zinc.c800,
    onSecondaryContainer = Zinc.c50,
    tertiary = Lime.c400,
    onTertiary = Zinc.c950,
    tertiaryContainer = Lime.c950,
    onTertiaryContainer = Lime.c200,
    background = Zinc.c950,
    onBackground = Zinc.c50,
    surface = Zinc.c950,
    onSurface = Zinc.c50,
    surfaceVariant = Zinc.c900,
    onSurfaceVariant = Zinc.c400,
    surfaceTint = Zinc.c950,
    surfaceContainerLowest = Zinc.c950,
    surfaceContainerLow = Zinc.c900,
    surfaceContainer = Zinc.c900,
    surfaceContainerHigh = Zinc.c800,
    surfaceContainerHighest = Zinc.c800,
    outline = Zinc.c50.copy(alpha = 0.15f),
    outlineVariant = Zinc.c50.copy(alpha = 0.10f),
    error = Red.c400,
    onError = Zinc.c950,
    errorContainer = Red.c950,
    onErrorContainer = Red.c200,
    inverseSurface = Zinc.c50,
    inverseOnSurface = Zinc.c950,
    inversePrimary = Lime.c500,
)

/** Colours Material 3 has no slot for. */
@Immutable
data class AppgotchiColors(
    val live: Color,
    val pending: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val selectedSegment: Color,
    val cardFill: Color,
)

internal val LightAppgotchiColors = AppgotchiColors(
    live = Lime.c500,
    pending = Amber.c500,
    warningContainer = Amber.c50,
    onWarningContainer = Amber.c900,
    selectedSegment = Color.White,
    cardFill = Color.White,
)

internal val DarkAppgotchiColors = AppgotchiColors(
    live = Lime.c400,
    pending = Amber.c400,
    warningContainer = Amber.c950,
    onWarningContainer = Amber.c200,
    selectedSegment = Zinc.c800,
    cardFill = Zinc.c50.copy(alpha = 0.025f),
)
```

- [ ] **Step 3: Typography**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/Type.kt`:

```kotlin
package com.vaazhstudios.appgotchi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.inter_medium
import appgotchi.shared.generated.resources.inter_regular
import appgotchi.shared.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

@Composable
private fun interFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
)

@Composable
internal fun appgotchiTypography(): Typography {
    val inter = interFontFamily()
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displaySmall = style(32, 40, FontWeight.SemiBold, -0.5),
        headlineLarge = style(28, 36, FontWeight.SemiBold, -0.4),
        headlineMedium = style(24, 32, FontWeight.SemiBold, -0.3),
        headlineSmall = style(20, 28, FontWeight.SemiBold, -0.2),
        titleLarge = style(18, 26, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(13, 18, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium),
    )
}
```

- [ ] **Step 4: Theme**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/theme/Theme.kt`:

```kotlin
package com.vaazhstudios.appgotchi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val AppgotchiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val LocalAppgotchiColors = staticCompositionLocalOf { LightAppgotchiColors }

@Composable
fun AppgotchiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppgotchiColors provides if (darkTheme) DarkAppgotchiColors else LightAppgotchiColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = appgotchiTypography(),
            shapes = AppgotchiShapes,
            content = content,
        )
    }
}

object AppgotchiTheme {
    val colors: AppgotchiColors
        @Composable @ReadOnlyComposable get() = LocalAppgotchiColors.current
}
```

- [ ] **Step 5: Components**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/ui/components/Components.kt`:

```kotlin
package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.app_name
import com.vaazhstudios.appgotchi.ui.theme.AppgotchiTheme
import org.jetbrains.compose.resources.stringResource

private val ButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

/** The one prominent action on a screen: near-black (light) / near-white (dark). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
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
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
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
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(AppgotchiTheme.colors.selectedSegment)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, segmentShape)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleSmall)
    }
}

/** Outlined text field colours: hairline border, lime focus ring. */
@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.tertiary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)
```

- [ ] **Step 6: Use the theme**

In `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/App.kt`, replace the `MaterialTheme(colorScheme = …) { Surface(modifier = Modifier.fillMaxSize()) { … } }` wrapper with:

```kotlin
    AppgotchiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // existing NavHost unchanged
        }
    }
```

Import `com.vaazhstudios.appgotchi.ui.theme.AppgotchiTheme`; remove the now-unused `isSystemInDarkTheme`, `darkColorScheme`, `lightColorScheme` imports.

- [ ] **Step 7: Build all targets and run tests**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL; `shared` 37 tests passing. If an M3 1.11 parameter name differs (e.g. a `lightColorScheme` slot), fix only that call and record it.

- [ ] **Step 8: Commit**

```bash
git add shared THIRD_PARTY_LICENSES design/theme/index.html docs/superpowers/plans/2026-10-04-calm-theme.md
git commit -m "Add Appgotchi's calm developer-tool theme and components

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Restyle the Today screen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayScreen.kt` (replace)
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: Task 1 components and theme; existing `TodayViewModel` (`state`, `refresh()`), `TodayUiState`, `StoreSection`, `Store.displayName`.
- Produces: `TodayScreen(onConnect, viewModel = koinViewModel())` (same signature as today).

- [ ] **Step 1: Strings**

In `strings.xml`, add after `today_no_apps` (typographic apostrophes only):

```xml
    <string name="today_title">Today</string>
    <plurals name="today_apps">
        <item quantity="one">%1$d app</item>
        <item quantity="other">%1$d apps</item>
    </plurals>
    <plurals name="today_stores">
        <item quantity="one">%1$d store</item>
        <item quantity="other">%1$d stores</item>
    </plurals>
    <string name="today_summary">%1$s across %2$s.</string>
```

- [ ] **Step 2: Replace the screen**

Replace `TodayScreen.kt` with:

```kotlin
package com.vaazhstudios.appgotchi.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.today_apps
import appgotchi.shared.generated.resources.today_connect_button
import appgotchi.shared.generated.resources.today_empty_body
import appgotchi.shared.generated.resources.today_empty_title
import appgotchi.shared.generated.resources.today_no_apps
import appgotchi.shared.generated.resources.today_refresh
import appgotchi.shared.generated.resources.today_stores
import appgotchi.shared.generated.resources.today_summary
import appgotchi.shared.generated.resources.today_title
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.data.StoreSection
import com.vaazhstudios.appgotchi.data.displayName
import com.vaazhstudios.appgotchi.ui.components.AppCard
import com.vaazhstudios.appgotchi.ui.components.BrandMark
import com.vaazhstudios.appgotchi.ui.components.PrimaryButton
import com.vaazhstudios.appgotchi.ui.components.SecondaryButton
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val ContentWidth = 720.dp

@Composable
fun TodayScreen(
    onConnect: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        TodayUiState.Loading -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TodayUiState.NoStores -> EmptyState(onConnect)
        is TodayUiState.Loaded -> LoadedState(current.sections, onConnect, onRefresh = viewModel::refresh)
    }
}

@Composable
private fun EmptyState(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.today_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.today_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        PrimaryButton(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
    }
}

@Composable
private fun LoadedState(sections: List<StoreSection>, onConnect: () -> Unit, onRefresh: () -> Unit) {
    val appCount = sections.sumOf { it.apps.size }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandMark()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).widthIn(min = 200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(Res.string.today_title),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(
                                Res.string.today_summary,
                                pluralStringResource(Res.plurals.today_apps, appCount, appCount),
                                pluralStringResource(Res.plurals.today_stores, sections.size, sections.size),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(onClick = onRefresh) { Text(stringResource(Res.string.today_refresh)) }
                        PrimaryButton(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
                    }
                }
            }
        }
        sections.forEach { section ->
            item(key = "header-${section.store}") {
                Row(
                    modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        section.store.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    Text("${section.apps.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            section.errorMessage?.let { message ->
                item(key = "error-${section.store}") {
                    AppCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (section.errorMessage == null && section.apps.isEmpty()) {
                item(key = "empty-${section.store}") {
                    AppCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
                        Text(stringResource(Res.string.today_no_apps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(section.apps, key = { "${it.store}-${it.id}" }) { app ->
                AppCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) { AppRow(app) }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppRow(app: StoreApp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.bundleId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

If `FlowRow`'s `itemVerticalAlignment` parameter doesn't exist in this Compose version, remove it. If `FlowRow` needs `@OptIn(ExperimentalLayoutApi::class)`, add it to `LoadedState`.

- [ ] **Step 3: Build and test**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL; `shared` 37 tests passing.

- [ ] **Step 4: Commit**

```bash
git add shared
git commit -m "Restyle the Today screen with the new theme

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Restyle the Connect wizard

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardScreen.kt`

**Interfaces:**
- Consumes: Task 1 components/theme; the existing wizard (behaviour unchanged).

Make these targeted replacements (keep everything else — callbacks, semantics, launchers, `runCatching` wrappers, `safeDrawingPadding` — exactly as it is):

- [ ] **Step 1: Store switch** — replace the body of `StorePicker` with the theme's segmented control:

```kotlin
    val options = listOf(
        Store.AppStore to stringResource(Res.string.wizard_store_app_store),
        Store.GooglePlay to stringResource(Res.string.wizard_store_google_play),
    )
    SegmentedControl(options = options, selected = selected, onSelect = onSelect, enabled = enabled, modifier = Modifier.fillMaxWidth())
```

(This also removes the non-exhaustive `labels.getValue` map.) Remove the now-unused `SegmentedButton`, `SegmentedButtonDefaults`, `SingleChoiceSegmentedButtonRow` imports.

- [ ] **Step 2: Progress uses the accent** — in `StepProgress`, change the bar colours to `if (index <= step) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant`.

- [ ] **Step 3: Buttons** — replace, keeping each call's arguments and content:
  - `OutlinedButton(` in `ExternalLinks` → `SecondaryButton(`
  - every `TextButton(` (Close, Remove, Copy email, Back) → `QuietButton(`
  - both `Button(` in `Footer` → `PrimaryButton(`
  Remove the unused `Button`, `OutlinedButton`, `TextButton` imports.

- [ ] **Step 4: Fields** — add `colors = appTextFieldColors(),` to both `OutlinedTextField` calls (Key ID, Issuer ID).

- [ ] **Step 5: Cards and notices**
  - In `KeyFileField`'s empty state, keep the `OutlinedCard` but set its border to `BorderStroke(if (isDragging) 2.dp else 1.dp, if (isDragging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)` and add `colors = CardDefaults.outlinedCardColors(containerColor = AppgotchiTheme.colors.cardFill)`.
  - In `KeyFileField`'s chosen-file state, replace `Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Row(...) }` with `AppCard(contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)) { Row(...) }`, and drop the Row's own padding modifier (keep `fillMaxWidth()` and `verticalAlignment`).
  - In `EmailRow`, replace `OutlinedCard(modifier = Modifier.fillMaxWidth()) { Row(...) }` with `AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) { Row(...) }`, dropping the Row's own padding.
  - In `StatusNotice`'s `Waiting` branch, use `color = AppgotchiTheme.colors.warningContainer` for the `Surface` and `color = AppgotchiTheme.colors.onWarningContainer` for the text.
  - In `StatusNotice`'s `Verifying` branch, give the `CircularProgressIndicator` `color = MaterialTheme.colorScheme.onSurfaceVariant`.

- [ ] **Step 6: Build and test**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL; `shared` 37 tests passing.

- [ ] **Step 7: Desktop smoke check**

`( ./gradlew :desktopApp:run --console=plain > /private/tmp/claude-501/appgotchi-theme-run.log 2>&1 & PID=$!; sleep 60; kill $PID; sleep 3; pkill -f 'com.vaazhstudios.appgotchi.MainKt' || true )` — no exceptions in the log (font loading errors would show here). Visual check is done by the controller/user.

- [ ] **Step 8: Commit**

```bash
git add shared
git commit -m "Restyle the Connect wizard with the new theme

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
