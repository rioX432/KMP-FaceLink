package io.github.kmpfacelink.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.actions.ActionEvent
import io.github.kmpfacelink.actions.ActionSystem
import io.github.kmpfacelink.actions.DefaultStreamBindings
import io.github.kmpfacelink.interact.mock.MockConfig
import io.github.kmpfacelink.interact.mock.MockStreamEventSource
import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

private val CORNER_RADIUS = 12.dp
private val SECTION_PADDING = 16.dp
private val ITEM_SPACING = 8.dp
private val DOT_SIZE = 8.dp
private val ACTION_FEED_HEIGHT = 80.dp
private const val ACTION_DISPLAY_DURATION_MS = 4000L
private const val ACTION_PRUNE_INTERVAL_MS = 2000L
private const val MAX_EVENTS = 20
private const val MOCK_INTERVAL_MS = 2000L
private const val MAX_VISIBLE_ACTIONS = 3

// Event type color palette
private val ColorChat = Color(0xFF8E8E93)
private val ColorGift = Color(0xFFFFB300)
private val ColorFollow = Color(0xFF4CAF50)
private val ColorRaid = Color(0xFF7C4DFF)
private val ColorSuperChat = Color(0xFFFF5722)
private val ColorChannelPoint = Color(0xFF00BCD4)

/**
 * Demo screen for the interact module showing live stream event simulation.
 *
 * Displays a mock event source with connection control, a scrolling event log,
 * and an action feed showing triggered [ActionEvent]s from [DefaultStreamBindings].
 */
@OptIn(ExperimentalAtomicApi::class)
@Composable
public fun InteractDemoScreen(
    modifier: Modifier = Modifier,
) {
    val timeSource = remember { TimeSource.Monotonic }
    val startMark = remember { timeSource.markNow() }
    val elapsedMs: () -> Long = remember(startMark) {
        { startMark.elapsedNow().inWholeMilliseconds }
    }
    val eventCounter = remember { AtomicLong(0L) }
    val mockSource = remember {
        MockStreamEventSource(MockConfig(intervalMs = MOCK_INTERVAL_MS), timeProvider = elapsedMs)
    }
    val actionSystem = remember { ActionSystem() }
    val events = remember { mutableStateListOf<IndexedEvent>() }
    val actionEvents = remember { mutableStateListOf<TimestampedAction>() }
    var connected by remember { mutableStateOf(false) }
    val connectionState by mockSource.connectionState.collectAsState()

    RegisterBindings(actionSystem)
    CollectEvents(mockSource, actionSystem, events, actionEvents, connected, elapsedMs, eventCounter)
    CleanupOnDispose(actionSystem)

    InteractScreenContent(
        connectionState = connectionState,
        connected = connected,
        onToggle = { connected = !connected },
        events = events,
        actionEvents = actionEvents,
        modifier = modifier,
    )
}

@Composable
private fun InteractScreenContent(
    connectionState: ConnectionState,
    connected: Boolean,
    onToggle: () -> Unit,
    events: List<IndexedEvent>,
    actionEvents: List<TimestampedAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SampleColors.Background)
            .padding(horizontal = SECTION_PADDING),
    ) {
        Spacer(modifier = Modifier.height(SECTION_PADDING))
        ConnectionControl(
            connectionState = connectionState,
            connected = connected,
            onToggle = onToggle,
        )
        Spacer(modifier = Modifier.height(SECTION_PADDING))
        SectionLabel(text = "Event Log")
        Spacer(modifier = Modifier.height(ITEM_SPACING))
        EventLog(events = events, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(SECTION_PADDING))
        SectionLabel(text = "Action Feed")
        Spacer(modifier = Modifier.height(ITEM_SPACING))
        ActionFeed(actionEvents = actionEvents)
        Spacer(modifier = Modifier.height(SECTION_PADDING))
    }
}

/** Wrapper pairing a stream event with a unique index for stable LazyColumn keys. */
private data class IndexedEvent(
    val index: Long,
    val event: StreamEvent,
)

/** Wrapper holding an action event and the monotonic timestamp it was received. */
private data class TimestampedAction(
    val event: ActionEvent,
    val receivedAtMs: Long,
)

@Composable
private fun RegisterBindings(actionSystem: ActionSystem) {
    LaunchedEffect(actionSystem) {
        actionSystem.register(DefaultStreamBindings.giftThrow())
        actionSystem.register(DefaultStreamBindings.raidScreenShake())
    }
}

@OptIn(ExperimentalAtomicApi::class)
@Composable
private fun CollectEvents(
    mockSource: MockStreamEventSource,
    actionSystem: ActionSystem,
    events: MutableList<IndexedEvent>,
    actionEvents: MutableList<TimestampedAction>,
    connected: Boolean,
    elapsedMs: () -> Long,
    eventCounter: AtomicLong,
) {
    LaunchedEffect(connected) {
        if (connected) {
            mockSource.connect()
        } else {
            mockSource.disconnect()
            return@LaunchedEffect
        }
    }

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        launch { collectStreamEvents(mockSource, actionSystem, events, actionEvents, elapsedMs, eventCounter) }
        launch { pruneExpiredActions(actionEvents, elapsedMs) }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private suspend fun collectStreamEvents(
    mockSource: MockStreamEventSource,
    actionSystem: ActionSystem,
    events: MutableList<IndexedEvent>,
    actionEvents: MutableList<TimestampedAction>,
    elapsedMs: () -> Long,
    eventCounter: AtomicLong,
) {
    mockSource.events.collect { event ->
        val idx = eventCounter.fetchAndAdd(1L)
        events.add(0, IndexedEvent(idx, event))
        if (events.size > MAX_EVENTS) events.removeAt(events.lastIndex)
        val actions = actionSystem.processStreamEvent(event)
        val now = elapsedMs()
        actions.forEach { actionEvents.add(0, TimestampedAction(it, now)) }
    }
}

private suspend fun pruneExpiredActions(
    actionEvents: MutableList<TimestampedAction>,
    elapsedMs: () -> Long,
) {
    while (true) {
        delay(ACTION_PRUNE_INTERVAL_MS)
        val cutoff = elapsedMs() - ACTION_DISPLAY_DURATION_MS
        actionEvents.removeAll { it.receivedAtMs < cutoff }
    }
}

@Composable
private fun CleanupOnDispose(actionSystem: ActionSystem) {
    DisposableEffect(Unit) {
        onDispose { actionSystem.release() }
    }
}

@Composable
private fun ConnectionControl(
    connectionState: ConnectionState,
    connected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .background(SampleColors.Surface)
            .padding(SECTION_PADDING),
    ) {
        ConnectionDot(connectionState)
        Spacer(modifier = Modifier.width(ITEM_SPACING))
        ConnectionLabel(connectionState, modifier = Modifier.weight(1f))
        ConnectButton(connected = connected, onClick = onToggle)
    }
}

@Composable
private fun ConnectionDot(connectionState: ConnectionState) {
    val dotColor = when (connectionState) {
        ConnectionState.CONNECTED -> SampleColors.StatusActive
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> SampleColors.WarningYellow
        ConnectionState.DISCONNECTED -> SampleColors.StatusInactive
    }
    Box(
        modifier = Modifier
            .size(DOT_SIZE)
            .clip(CircleShape)
            .background(dotColor),
    )
}

@Composable
private fun ConnectionLabel(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    Text(
        text = connectionState.name.lowercase().replaceFirstChar { it.uppercase() },
        color = SampleColors.TextPrimary,
        fontSize = 14.sp,
        modifier = modifier,
    )
}

@Composable
private fun ConnectButton(
    connected: Boolean,
    onClick: () -> Unit,
) {
    val label = if (connected) "Disconnect" else "Connect"
    val bgColor = if (connected) {
        SampleColors.ErrorRed.copy(alpha = 0.15f)
    } else {
        SampleColors.Primary.copy(alpha = 0.15f)
    }
    val textColor = if (connected) SampleColors.ErrorRed else SampleColors.Primary
    Text(
        text = label,
        color = textColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .background(bgColor)
            .semantics { role = Role.Button }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = ITEM_SPACING),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SampleColors.TextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EventLog(
    events: List<IndexedEvent>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .background(SampleColors.Surface),
    ) {
        items(events, key = { it.index }) { indexed ->
            EventRow(indexed.event)
        }
    }
}

@Composable
private fun EventRow(event: StreamEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = ITEM_SPACING),
    ) {
        EventTypeBadge(event)
        Spacer(modifier = Modifier.width(ITEM_SPACING))
        EventContent(event, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EventTypeBadge(event: StreamEvent) {
    val (label, color) = eventTypeInfo(event)
    Text(
        text = label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun EventContent(
    event: StreamEvent,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = event.author,
            color = SampleColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = eventSummary(event),
            color = SampleColors.TextTertiary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionFeed(actionEvents: List<TimestampedAction>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .background(SampleColors.Surface)
            .padding(12.dp)
            .height(ACTION_FEED_HEIGHT),
    ) {
        if (actionEvents.isEmpty()) {
            Text(
                text = "No actions triggered yet",
                color = SampleColors.TextTertiary,
                fontSize = 12.sp,
            )
        } else {
            actionEvents.take(MAX_VISIBLE_ACTIONS).forEach { timestamped ->
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    ActionRow(timestamped.event)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(event: ActionEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = event.actionId,
            color = SampleColors.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionLabel(event),
            color = SampleColors.TextTertiary,
            fontSize = 11.sp,
        )
    }
}

private fun eventTypeInfo(event: StreamEvent): Pair<String, Color> = when (event) {
    is ChatMessage -> "CHAT" to ColorChat
    is Gift -> "GIFT" to ColorGift
    is Follow -> "FOLLOW" to ColorFollow
    is Raid -> "RAID" to ColorRaid
    is SuperChat -> "SUPERCHAT" to ColorSuperChat
    is ChannelPointRedemption -> "POINTS" to ColorChannelPoint
}

private fun eventSummary(event: StreamEvent): String = when (event) {
    is ChatMessage -> event.text
    is Gift -> "$${event.amount.toInt()} ${event.currency} (tier ${event.tier})"
    is Follow -> "New follower"
    is Raid -> "${event.viewerCount} viewers"
    is SuperChat -> "$${event.amount.toInt()} — ${event.text}"
    is ChannelPointRedemption -> "${event.rewardTitle} (${event.cost} pts)"
}

private fun actionLabel(event: ActionEvent): String = when (event) {
    is ActionEvent.Started -> "Started"
    is ActionEvent.Held -> "Held ${event.durationMs}ms"
    is ActionEvent.Released -> "Released ${event.totalDurationMs}ms"
}
