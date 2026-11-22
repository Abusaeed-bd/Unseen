package io.github.abusaeedbd.unseen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.abusaeedbd.unseen.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows a short pill message at the top of the screen, like an iOS HUD. */
val LocalHud = staticCompositionLocalOf<(String) -> Unit> { { } }

private val GroupShape = RoundedCornerShape(16.dp)

/** An iOS "inset grouped" card. */
@Composable
fun IosGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(Grouped)
            .border(0.5.dp, CardBorder, GroupShape),
        content = content,
    )
}

/** A 0.5 dp separator, inset from the left like iOS lists. */
@Composable
fun IosHairline(start: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Hairline)
    )
}

@Composable
fun IosSectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = Label2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, color = Label3, fontSize = 11.sp)
    }
}

@Composable
fun IosFooter(text: String) {
    Text(
        text,
        color = Label2,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
    )
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp,
        modifier = modifier,
    )
}

/** Apple-style UISwitch: 51×31, springy thumb. Pass onChange = null when the row handles the tap. */
@Composable
fun IosSwitch(checked: Boolean, enabled: Boolean = true, onChange: ((Boolean) -> Unit)?) {
    val thumbX by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "thumb",
    )
    val track by animateColorAsState(
        when {
            checked && enabled -> Accent
            checked -> Accent.copy(alpha = 0.4f)
            else -> SwitchOff
        },
        label = "track",
    )
    val toggle = if (onChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
    } else Modifier
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .then(toggle)
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .alpha(if (enabled) 1f else 0.65f)
        )
    }
}

/** A list row: content on the left, an optional trailing piece, tap to open. */
@Composable
fun IosRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), content = content)
        trailing()
    }
}

/** A setting with a title, explanation and switch; the whole row toggles. */
@Composable
fun IosSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Label2, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        IosSwitch(checked = checked, enabled = enabled, onChange = null)
    }
}

/** iOS UISegmentedControl. */
@Composable
fun IosSegmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Grouped)
            .padding(2.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) Tertiary else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, color = if (active) Color.White else Label2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** iOS search field. */
@Composable
fun IosSearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Grouped)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Label2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Label2, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Small rounded A–F badge. */
@Composable
fun GradeBadge(letter: Char, size: Dp, corner: Dp = size / 4) {
    val color = gradeColor(letter)
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.toString(), color = color, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.46f).sp)
    }
}

fun gradeColor(letter: Char): Color = when (letter) {
    'A' -> IosGreen
    'B' -> IosCyan
    'C' -> IosYellow
    'D' -> IosOrange
    else -> IosRed
}

/** A coloured dot + label + count, as on the "blocked today" card. */
@Composable
fun CountPill(label: String, count: Int, dot: Color, countColor: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Elevated)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text("%,d".format(count), color = countColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Rounded capsule button (pill). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    background: Color = Grouped,
    textColor: Color = AccentSoft,
    bordered: Boolean = true,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (bordered) Modifier.border(0.5.dp, CardBorder, RoundedCornerShape(50)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Full-width primary button, iOS style. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The app's real icon, rounded like an iOS app tile. */
@Composable
fun AppIcon(packageName: String, size: Dp, corner: Dp = size / 3.6f) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(corner)
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Elevated)
                .border(0.5.dp, CardBorder, shape)
        )
    }
}

/** The HUD pill shown at the top for ~2 seconds. */
@Composable
fun HudPill(message: String?, modifier: Modifier = Modifier) {
    // Keep showing the last text while the pill fades out.
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .shadow(16.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Elevated.copy(alpha = 0.96f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
            Spacer(Modifier.width(8.dp))
            Text(last, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
