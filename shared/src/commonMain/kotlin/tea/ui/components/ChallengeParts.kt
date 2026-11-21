package tea.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.engine.ChallengeEngine
import tea.engine.DialLevel
import tea.generated.resources.Res
import tea.generated.resources.challenge_dial_desc
import tea.generated.resources.challenge_level_critical
import tea.generated.resources.challenge_level_steady
import tea.generated.resources.challenge_level_strained
import tea.ui.theme.LocalOnSpiderPanel
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.hardShadow

/** A small rounded label (`.lesson-pill`, `.ransom-chip`): 2dp outline, pill shape. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = TeaTheme.colors.highlight,
    color: Color = TeaTheme.colors.onHighlight,
    borderColor: Color = TeaTheme.colors.line,
) {
    Text(
        text,
        modifier
            .clip(TeaShapes.pill)
            .background(background)
            .border(TeaSize.border, borderColor, TeaShapes.pill)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = TeaTheme.type.smallStrong,
        color = color,
    )
}

/** A clock time in mono (`.time-chip`), outlined. [now] marks the current moment. */
@Composable
fun TimeChip(time: String, modifier: Modifier = Modifier, now: Boolean = false) {
    val colors = TeaTheme.colors
    val dark = LocalOnSpiderPanel.current
    Text(
        time,
        modifier
            .clip(TeaShapes.sm)
            .background(if (now) colors.highlight else if (dark) TeaColors.webCard else colors.paper)
            .border(TeaSize.border, if (dark) TeaColors.webLine else colors.line, TeaShapes.sm)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = TeaTheme.type.monoSmall,
        color = if (now) colors.onHighlight else if (dark) TeaColors.webInk else colors.ink,
    )
}

@Composable
fun dialLevelWord(level: DialLevel): String = stringResource(
    when (level) {
        DialLevel.STEADY -> Res.string.challenge_level_steady
        DialLevel.STRAINED -> Res.string.challenge_level_strained
        DialLevel.CRITICAL -> Res.string.challenge_level_critical
    },
)

/**
 * One crisis dial (`.dial`): label and value, an outlined bar whose fill slides when the value
 * changes, and the level word (Steady / Strained / Critical), so colour never carries meaning alone.
 */
@Composable
fun CrisisDial(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    val level = ChallengeEngine.levelOf(value)
    val fill = when (level) {
        DialLevel.STEADY -> colors.ok
        DialLevel.STRAINED -> colors.highlight
        DialLevel.CRITICAL -> colors.bad
    }
    val word = dialLevelWord(level)
    val animated by animateFloatAsState(value.coerceIn(0, 100) / 100f, tween(400), label = "dial")
    val description = stringResource(Res.string.challenge_dial_desc, label, value, word)
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(value / 100f, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = TeaTheme.type.smallStrong, color = colors.ink, modifier = Modifier.weight(1f, fill = false))
            Text("$value%", style = TeaTheme.type.smallStrong, color = colors.ink)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(TeaShapes.pill)
                .background(colors.track)
                .border(TeaSize.border, colors.line, TeaShapes.pill),
        ) {
            if (animated > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(TeaShapes.pill).background(fill))
            }
        }
        Text(word, style = TeaTheme.type.xs, color = colors.inkSoft)
    }
}

/** Initials in an outlined circle (`.seat-ico`, `.pic`). */
@Composable
fun InitialsCircle(initials: String, modifier: Modifier = Modifier, background: Color = TeaTheme.colors.paper) {
    Box(
        modifier
            .size(TeaSize.avatarSm)
            .clip(TeaShapes.circle)
            .background(background)
            .border(TeaSize.border, TeaTheme.colors.line, TeaShapes.circle)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = TeaTheme.type.monoSmall, color = TeaTheme.colors.ink)
    }
}

/** A colleague's message in a generic messenger (`.chat-mock`): no brand, no real app look. */
@Composable
fun ChatMessage(initials: String, name: String, role: String, time: String, text: String, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .hardShadow(colors.shadow, TeaShapes.md, TeaSize.shadowSm)
            .clip(TeaShapes.md)
            .background(colors.card)
            .border(TeaSize.border, colors.line, TeaShapes.md)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialsCircle(initials)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = TeaTheme.type.bodyStrong, color = colors.ink, modifier = Modifier.align(Alignment.CenterVertically))
                Pill(role, Modifier.align(Alignment.CenterVertically), background = colors.paper, color = colors.inkSoft, borderColor = colors.rule)
                Text(time, style = TeaTheme.type.monoSmall, color = colors.inkSoft, modifier = Modifier.align(Alignment.CenterVertically))
            }
            Text(text, style = TeaTheme.type.body, color = colors.ink)
        }
    }
}

/**
 * Dial changes as chips (`.delta`): "▼ Legal standing −20" / "▲ Systems +15". Arrow and sign carry
 * the meaning, colour only repeats it. [labels] maps dial key → display label (also the order).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeltaChips(fx: Map<String, Int>, labels: Map<String, String>, modifier: Modifier = Modifier) {
    val entries = labels.keys.filter { (fx[it] ?: 0) != 0 }.map { it to fx.getValue(it) }
    if (entries.isEmpty()) return
    val colors = TeaTheme.colors
    val dark = LocalOnSpiderPanel.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { (key, v) ->
            val up = v > 0
            val text = (if (up) "▲ " else "▼ ") + labels.getValue(key) + " " + (if (up) "+" else "−") + kotlin.math.abs(v)
            val (bg, fg) = when {
                dark && up -> TeaColors.webCard to TeaColors.webOk
                dark -> TeaColors.webCard to TeaColors.webInk
                up -> colors.okSoft to colors.okText
                else -> colors.badSoft to colors.badText
            }
            Text(
                text,
                Modifier
                    .clip(TeaShapes.pill)
                    .background(bg)
                    .border(TeaSize.border, if (dark) TeaColors.webLine else colors.line, TeaShapes.pill)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                style = TeaTheme.type.smallStrong,
                color = fg,
            )
        }
    }
}
