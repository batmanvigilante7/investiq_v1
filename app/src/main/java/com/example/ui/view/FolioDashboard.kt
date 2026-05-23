package com.example.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.GeminiClient
import com.example.data.model.MarketEvent
import com.example.data.model.ThesisSnapshot
import com.example.data.model.TickerThesis
import com.example.data.model.WatchlistAlert
import com.example.ui.ChatMessage
import com.example.ui.FolioViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioDashboard(
    viewModel: FolioViewModel,
    modifier: Modifier = Modifier
) {
    val theses by viewModel.theses.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    
    val selectedSymbol by viewModel.selectedSymbol.collectAsStateWithLifecycle()
    val activeThesis by viewModel.activeThesis.collectAsStateWithLifecycle()
    val activeSnapshots by viewModel.activeSnapshots.collectAsStateWithLifecycle()
    val isSimulatorActive by viewModel.isSimulatorActive.collectAsStateWithLifecycle()
    val isIngesting by viewModel.isIngesting.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("THESIS") }
    var showAddTickerDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize().background(HorizonBlack),
        topBar = {
            TerminalHeader(
                isSimulatorActive = isSimulatorActive,
                activeSymbol = selectedSymbol,
                activeThesis = activeThesis,
                onSimulatorToggle = { viewModel.toggleSimulator() },
                alerts = alerts,
                onTabSelect = { activeTab = "ALERTS" }
            )
        },
        bottomBar = {
            TerminalNavBar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )
        },
        containerColor = HorizonBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(HorizonBlack)
        ) {
            // Ticker selector bar (horizontal cards)
            TickerSelectorRow(
                theses = theses,
                selectedSymbol = selectedSymbol,
                onSelected = { viewModel.selectSymbol(it) },
                onToggleWatchlist = { sym, watched -> viewModel.toggleWatchlist(sym, watched) },
                onAddTickerClick = { showAddTickerDialog = true }
            )

            // Main Tab Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                when (activeTab) {
                    "THESIS" -> ThesisTabContent(
                        thesis = activeThesis,
                        snapshots = activeSnapshots,
                        events = events.filter { it.symbol == selectedSymbol },
                        isKeyValid = GeminiClient.isKeyValid(),
                        onToggleWatchlist = { sym, watched -> viewModel.toggleWatchlist(sym, watched) }
                    )
                    "PIPELINE" -> PipelineTabContent(
                        events = events,
                        isIngesting = isIngesting,
                        selectedSymbol = selectedSymbol,
                        onInject = { sys, src, raw -> viewModel.ingestCustomEvent(sys, src, raw) },
                        onClear = { viewModel.clearHistory() }
                    )
                    "WHAT-IF" -> WhatIfTabContent(
                        viewModel = viewModel
                    )
                    "ALERTS" -> AlertsTabContent(
                        alerts = alerts,
                        onMarkRead = { viewModel.markAlertAsRead(it) }
                    )
                    "COPILOT" -> CopilotTabContent(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showAddTickerDialog) {
        AddTickerDialog(
            onDismiss = { showAddTickerDialog = false },
            onSubmit = { sym, name ->
                viewModel.addCustomTicker(sym, name)
                showAddTickerDialog = false
            }
        )
    }
}

@Composable
fun TerminalHeader(
    isSimulatorActive: Boolean,
    activeSymbol: String,
    activeThesis: TickerThesis?,
    onSimulatorToggle: () -> Unit,
    alerts: List<WatchlistAlert>,
    onTabSelect: () -> Unit
) {
    val unreadAlerts = alerts.filter { !it.isRead }.size

    // Pulsing animation for simulator running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaColor by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand logo & title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "F",
                    color = Color(0xFF0A0A0B),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Column {
                Text(
                    buildAnnotatedString {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.White, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append("FOLIO")
                        }
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = CyberGreen)) {
                            append("AI")
                        }
                    },
                    fontSize = 18.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "ACTIVE COMMAND PROTOCOL",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        color = TextGray
                    )
                )
            }
        }

        // Live stats indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Status Metadata Block
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "INGESTION STREAM",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = TextGray
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSimulatorActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = CyberGreen,
                                        alpha = alphaColor,
                                        radius = size.minDimension / 2
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isSimulatorActive) "LIVE · SIMULATING" else "STREAM IDLE",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSimulatorActive) CyberGreen else TextGray
                        )
                    )
                }
            }

            // Simulator Control
            IconButton(
                onClick = onSimulatorToggle,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSimulatorActive) CyberGreen.copy(alpha = 0.15f) else ModuleSlate)
                    .border(1.dp, if (isSimulatorActive) CyberGreen else GridBorder, RoundedCornerShape(8.dp))
                    .testTag("simulator_toggle_button")
            ) {
                Icon(
                    imageVector = if (isSimulatorActive) Icons.Default.Refresh else Icons.Default.PlayArrow,
                    contentDescription = "Toggle simulator stream",
                    tint = if (isSimulatorActive) CyberGreen else TextGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Alerts bell view
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ModuleSlate)
                    .border(1.dp, if (unreadAlerts > 0) CyberAmber else GridBorder, RoundedCornerShape(8.dp))
                    .clickable { onTabSelect() }
                    .testTag("alerts_bell_icon"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts view",
                    tint = if (unreadAlerts > 0) CyberAmber else TextGray,
                    modifier = Modifier.size(16.dp)
                )
                if (unreadAlerts > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(14.dp)
                            .background(CyberRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$unreadAlerts",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TickerSelectorRow(
    theses: List<TickerThesis>,
    selectedSymbol: String,
    onSelected: (String) -> Unit,
    onToggleWatchlist: (String, Boolean) -> Unit,
    onAddTickerClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .border(1.dp, GridBorder, RoundedCornerShape(12.dp))
            .background(TerminalCoal)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            theses.forEach { thesis ->
                val isSelected = thesis.symbol == selectedSymbol
                
                val trColor = when (thesis.trajectory) {
                    "UPGOING" -> CyberGreen
                    "DOWNGOING" -> CyberRed
                    else -> CyberAmber
                }
                
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color.White else Color.Transparent)
                        .border(1.dp, if (isSelected) Color.White else GridBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelected(thesis.symbol) }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .testTag("ticker_select_${thesis.symbol}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = thesis.symbol,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFF0A0A0B) else Color.White
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = when (thesis.trajectory) {
                                        "UPGOING" -> "▲"
                                        "DOWNGOING" -> "▼"
                                        else -> "▶"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = if (isSelected) Color(0xFF0A0A0B) else trColor
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Watchlist status",
                                tint = if (isSelected) {
                                    if (thesis.isWatchlisted) Color(0xFFEAB308) else Color(0xFF64748B).copy(alpha = 0.3f)
                                } else {
                                    if (thesis.isWatchlisted) CyberAmber else TextGray.copy(alpha = 0.3f)
                                },
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onToggleWatchlist(thesis.symbol, !thesis.isWatchlisted) }
                                    .testTag("star_toggle_${thesis.symbol}")
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "CIV:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = if (isSelected) Color(0xFF64748B) else TextGray
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${thesis.convictionScore}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (isSelected) Color(0xFF0A0A0B) else CyberGreen
                            )
                        }
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ModuleSlate)
                .border(1.dp, GridBorder, RoundedCornerShape(8.dp))
                .clickable { onAddTickerClick() }
                .testTag("add_ticker_icon_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Track custom stock symbol",
                tint = CyberCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ThesisTabContent(
    thesis: TickerThesis?,
    snapshots: List<ThesisSnapshot>,
    events: List<MarketEvent>,
    isKeyValid: Boolean,
    onToggleWatchlist: (String, Boolean) -> Unit
) {
    if (thesis == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CyberGreen)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banner if API Key is Placeholder
        if (!isKeyValid) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("api_key_caution_card"),
                    colors = CardDefaults.cardColors(containerColor = CyberAmber.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp),
                    border = borderColor(CyberAmber.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(CyberAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Security Alert", tint = CyberAmber, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PROTOTYPE SIMULATOR ACTIVE", 
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp),
                                color = CyberAmber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Using advanced locally-engineered heuristics. Please bind your GEMINI_API_KEY inside the AI Studio Secrets panel to connect live multi-source reasoning.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextGray
                            )
                        }
                    }
                }
            }
        }

        // Conviction Level Card (Bloomberg Style Metric Grid)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalCoal),
                shape = RoundedCornerShape(24.dp),
                border = borderColor(GridBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header Tag & Company name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GridBorder)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$${thesis.symbol}",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            Text(
                                text = thesis.name,
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = TextGray
                                )
                            )
                            
                            IconButton(
                                onClick = { onToggleWatchlist(thesis.symbol, !thesis.isWatchlisted) },
                                modifier = Modifier.size(32.dp).testTag("header_watchlist_star")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Toggle watchlist",
                                    tint = if (thesis.isWatchlisted) CyberAmber else TextGray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // level pill indicators from HTML theme
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val filledBlocks = ((thesis.convictionScore) / 20).coerceAtLeast(1)
                            for (i in 1..5) {
                                val isFilled = i <= filledBlocks
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(4.dp)
                                        .background(
                                            color = if (isFilled) CyberGreen else CyberGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Huge Bold Conviction display
                    Text(
                        text = "CONVICTION: ${thesis.convictionScore}",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            letterSpacing = (-1).sp,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Trajectory Comment Callout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = CyberGreen)) {
                                    append("Trajectory: ")
                                }
                                append(
                                    when (thesis.trajectory) {
                                        "UPGOING" -> "Accelerating signal velocity indicates highly positive thematic alignment and upward momentum."
                                        "DOWNGOING" -> "Cooling structural margins indicate near-term resistance and declining momentum vectors."
                                        else -> "Neutral trading bounds suggest range-bound pricing and quiet pipeline activity."
                                    }
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = GridBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Metric Quad Rows
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricColumn(
                            heading = "RISK PROFILE",
                            value = thesis.riskProfile,
                            color = when (thesis.riskProfile) {
                                "LOW" -> CyberCyan
                                "MEDIUM" -> CyberAmber
                                "HIGH", "CRITICAL" -> CyberRed
                                else -> TextGray
                            }
                        )
                        MetricColumn(
                            heading = "THEMATIC TRAJECTORY",
                            value = thesis.trajectory,
                            color = when (thesis.trajectory) {
                                "UPGOING" -> CyberGreen
                                "DOWNGOING" -> CyberRed
                                else -> CyberAmber
                            }
                        )
                        MetricColumn(
                            heading = "LATEST SIGNAL",
                            value = if (events.isNotEmpty()) events.first().sourceType.uppercase() else "NONE",
                            color = CyberCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Render Conviction History Timeline Chart
                    Text(
                        text = "CONVICTION EVOLUTION (TIME GRAPH)",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            color = TextGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ConvictionEvolutionChart(snapshots = snapshots)
                }
            }
        }

        // Notion-Style Synthesized Narrative Synopsis Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TerminalCoal),
                shape = RoundedCornerShape(24.dp),
                border = borderColor(GridBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Synopsis", tint = CyberGreen, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = "EVOLVING MASTER SYNOPSIS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = CyberGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = thesis.synopsis,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
            }
        }

        // Bull Vs Bear Split Cards
        item {
            BullBearSplitViews(thesis = thesis)
        }

        // Narrative Graph Connection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                colors = CardDefaults.cardColors(containerColor = TerminalCoal),
                shape = RoundedCornerShape(24.dp),
                border = borderColor(GridBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "NARRATIVE COUPLING RELATION_GRAPH",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = CyberCyan
                    )
                    Text(
                        text = "Visual map of digested signals linking directly to focal conviction points",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Render Visual Graph inside Canvas!
                    NarrativeGraphCanvas(thesis = thesis, events = events)
                }
            }
        }

        // Spacer along screen edge
        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun MetricColumn(heading: String, value: String, color: Color) {
    Column {
        Text(
            text = heading, 
            fontFamily = FontFamily.Monospace, 
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp, 
            color = TextGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = color
        )
    }
}

@Composable
fun ConvictionEvolutionChart(snapshots: List<ThesisSnapshot>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .border(1.dp, GridBorder, RoundedCornerShape(8.dp))
            .background(HorizonBlack)
    ) {
        val points = snapshots.takeLast(10)
        if (points.size < 2) {
            return@Canvas
        }

        val paddingX = 30.dp.toPx()
        val paddingY = 15.dp.toPx()
        val chartWidth = size.width - paddingX * 2
        val chartHeight = size.height - paddingY * 2

        val maxScore = 100f
        val minScore = 0f

        val path = Path()
        val gridLines = 4

        // Draw horizontal grid lines
        for (i in 0..gridLines) {
            val y = paddingY + chartHeight * (i.toFloat() / gridLines)
            drawLine(
                color = GridBorder.copy(alpha = 0.5f),
                start = Offset(paddingX, y),
                end = Offset(size.width - paddingX, y),
                strokeWidth = 1f
            )
        }

        // Map snapshots to coordinates
        val pointsToCoordinates = points.mapIndexed { idx, snapshot ->
            val x = paddingX + chartWidth * (idx.toFloat() / (points.size - 1))
            val percent = (snapshot.convictionScore - minScore) / (maxScore - minScore)
            val y = paddingY + chartHeight * (1f - percent)
            Offset(x, y)
        }

        // Draw line graph
        path.moveTo(pointsToCoordinates.first().x, pointsToCoordinates.first().y)
        for (i in 1 until pointsToCoordinates.size) {
            path.lineTo(pointsToCoordinates[i].x, pointsToCoordinates[i].y)
        }

        drawPath(
            path = path,
            color = CyberGreen,
            style = Stroke(width = 4f)
        )

        // Draw dots and value text labels on nodes
        pointsToCoordinates.forEachIndexed { i, offset ->
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = offset
            )
            drawCircle(
                color = CyberGreen,
                radius = 3f,
                center = offset
            )
        }
    }
}

@Composable
fun BullBearSplitViews(thesis: TickerThesis) {
    val bulls = thesis.bullPoints.split("||").filter { it.trim().isNotEmpty() }
    val bears = thesis.bearPoints.split("||").filter { it.trim().isNotEmpty() }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Bullish Catalysts Case
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(TerminalCoal)
                .drawBehind {
                    // Left 3dp border line
                    drawRect(
                        color = CyberGreen,
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                    )
                }
                .border(width = 1.dp, color = GridBorder, shape = RoundedCornerShape(16.dp))
                .padding(start = 14.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Bull Case",
                        tint = CyberGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "BULL CATALYSTS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = CyberGreen
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                bulls.forEachIndexed { idx, bp ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "0${idx + 1}.",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = bp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextGray
                            )
                        }
                    }
                }
                if (bulls.isEmpty()) {
                    Text("Synthesizing metrics...", style = MaterialTheme.typography.bodyMedium, color = TextGray)
                }
            }
        }

        // Bearish Threats Case
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(TerminalCoal)
                .drawBehind {
                    // Left 3dp border line
                    drawRect(
                        color = CyberRed,
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                    )
                }
                .border(width = 1.dp, color = GridBorder, shape = RoundedCornerShape(16.dp))
                .padding(start = 14.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Bear Case",
                        tint = CyberRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "BEARISH RISKS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = CyberRed
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                bears.forEachIndexed { idx, bp ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "0${idx + 1}.",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = bp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextGray
                            )
                        }
                    }
                }
                if (bears.isEmpty()) {
                    Text("Synthesizing metrics...", style = MaterialTheme.typography.bodyMedium, color = TextGray)
                }
            }
        }
    }
}

@Composable
fun NarrativeGraphCanvas(thesis: TickerThesis, events: List<MarketEvent>?) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, GridBorder, RoundedCornerShape(4.dp))
            .background(HorizonBlack)
    ) {
        val cx = size.width / 2
        val cy = size.height / 2

        drawCircle(
            color = CyberGreen.copy(alpha = 0.15f),
            radius = 36.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = CyberGreen,
            radius = 8.dp.toPx(),
            center = Offset(cx, cy)
        )

        val orbitsCount = 3
        val radiusDist = 72.dp.toPx()

        val satColors = listOf(CyberRed, CyberGreen, CyberCyan)

        for (i in 0 until orbitsCount) {
            val angleRad = (i * 2 * Math.PI / orbitsCount) - Math.PI / 2
            val sx = cx + radiusDist * Math.cos(angleRad).toFloat()
            val sy = cy + radiusDist * Math.sin(angleRad).toFloat()

            drawLine(
                color = GridBorder,
                start = Offset(cx, cy),
                end = Offset(sx, sy),
                strokeWidth = 2f
            )

            drawCircle(
                color = satColors[i].copy(alpha = 0.2f),
                radius = 20.dp.toPx(),
                center = Offset(sx, sy)
            )
            drawCircle(
                color = satColors[i],
                radius = 6.dp.toPx(),
                center = Offset(sx, sy)
            )
        }
    }
}

@Composable
fun PipelineTabContent(
    events: List<MarketEvent>,
    isIngesting: Boolean,
    selectedSymbol: String,
    onInject: (String, String, String) -> Unit,
    onClear: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalCoal),
            border = borderColor(GridBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REAL-TIME NEWS PIPELINE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = CyberCyan
                    )
                    Text(
                        text = "Automatic signal parsing and relevance extraction",
                        fontSize = 10.sp,
                        color = TextGray
                    )
                }

                Row {
                    Button(
                        onClick = { showCustomDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("custom_inject_trigger")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add custom document", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DOC_INJECT", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(28.dp).testTag("clear_logs_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear all data", tint = CyberRed)
                    }
                }
            }
        }

        if (isIngesting) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(bottom = 6.dp),
                color = CyberCyan,
                trackColor = GridBorder
            )
        }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, GridBorder).background(TerminalCoal),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Empty Stream", tint = TextGray, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "READY FOR SIGNAL INGRESS",
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Turn 'SIMULATE' on to launch a live stream of realistic tech news files or tap 'DOC_INJECT' to manually act as an analyst.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = rememberLazyListState()
            ) {
                items(events) { event ->
                    EventPipelineRow(event = event)
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomSignalDialog(
            defaultSymbol = selectedSymbol,
            onDismiss = { showCustomDialog = false },
            onSubmit = { sys, src, txt ->
                onInject(sys, src, txt)
                showCustomDialog = false
            }
        )
    }
}

@Composable
fun EventPipelineRow(event: MarketEvent) {
    val focusThemeCol = when (event.sourceType) {
        "SEC Filing" -> CyberCyan
        "Earnings Call" -> CyberGreen
        "Macro Event" -> CyberRed
        "Analyst Report" -> CyberAmber
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("market_event_row_${event.id}"),
        colors = CardDefaults.cardColors(containerColor = TerminalCoal),
        border = borderColor(GridBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(focusThemeCol.copy(alpha = 0.15f))
                            .border(1.dp, focusThemeCol, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.sourceType.uppercase(),
                            color = focusThemeCol,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.symbol,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RELEVANCE: ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = TextGray
                    )
                    Text(
                        text = "${event.relevanceScore}%",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (event.relevanceScore > 80) CyberAmber else CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SENT:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = TextGray
                    )
                    val sVal = event.sentimentScore
                    Text(
                        text = if (sVal > 0) "+$sVal" else "$sVal",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (sVal > 0) CyberGreen else if (sVal < 0) CyberRed else CyberAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = GridBorder.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            Row {
                Text(
                    text = "WHY THIS MATTERS: ",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = CyberGreen
                )
                Text(
                    text = event.impactReasoning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (event.socialSentimentScore != 0 || event.socialSourceBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("social_sentiment_card_${event.id}"),
                    colors = CardDefaults.cardColors(containerColor = HorizonBlack),
                    border = borderColor(GridBorder.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "🌐 SOCIAL SENTIMENT INDEX:",
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = CyberCyan)
                                )
                                val sScore = event.socialSentimentScore
                                Text(
                                    text = if (sScore > 0) "+$sScore" else "$sScore",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = if (sScore > 20) CyberGreen else if (sScore < -20) CyberRed else CyberAmber
                                )
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("X", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 8.sp, color = Color.White)
                                }
                                Box(modifier = Modifier.background(CyberRed.copy(alpha = 0.08f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("Reddit", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 8.sp, color = CyberRed)
                                }
                                Box(modifier = Modifier.background(CyberCyan.copy(alpha = 0.08f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("Discord", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 8.sp, color = CyberCyan)
                                }
                            }
                        }
                        
                        if (event.socialSourceBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "BREAKDOWN: ${event.socialSourceBreakdown}",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = TextGray)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsTabContent(
    alerts: List<WatchlistAlert>,
    onMarkRead: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalCoal),
            border = borderColor(GridBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ADAPTIVE ALERTING MATRIX",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CyberAmber
                )
                Text(
                    text = "Notifications trigger with narrative trajectory variations and risk shifts",
                    fontSize = 10.sp,
                    color = TextGray
                )
            }
        }

        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, GridBorder).background(TerminalCoal),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Quiet pipeline", tint = CyberGreen, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "SYSTEM STABLE",
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Watchlists are healthy. Heavy trajectory shocks or risk profile changes will trigger instantaneous micro alerts here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(alerts) { alert ->
                val severityColor = when (alert.severity) {
                    "CRITICAL" -> CyberRed
                    "WARNING" -> CyberAmber
                    else -> CyberCyan
                }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("alert_row_${alert.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (alert.isRead) TerminalCoal else ModuleSlate
                    ),
                    border = borderColor(if (alert.isRead) GridBorder else severityColor)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(severityColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = alert.symbol,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${alert.severity})",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = severityColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = alert.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (alert.isRead) TextGray else Color.White
                                )
                            }
                        }

                        if (!alert.isRead) {
                            IconButton(
                                onClick = { onMarkRead(alert.id) },
                                modifier = Modifier.size(32.dp).testTag("alert_mark_read_btn_${alert.id}")
                            ) {
                                Icon(Icons.Default.Done, contentDescription = "Mark Read", tint = CyberGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CopilotTabContent(
    viewModel: FolioViewModel
) {
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isCopilotThinking by viewModel.isCopilotThinking.collectAsStateWithLifecycle()
    var inputQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalCoal),
            border = borderColor(GridBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TERMINAL ANALYTICS COPILOT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CyberGreen
                )
                Text(
                    text = "Perplexity-style search over portfolio narrative databases",
                    fontSize = 10.sp,
                    color = TextGray
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            reverseLayout = true
        ) {
            if (isCopilotThinking) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberGreen, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Synthesizing multi-source signals inside narrative databases...",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CyberGreen)
                        )
                    }
                }
            }

            items(chatHistory.reversed()) { msg ->
                ChatBubbleRow(msg = msg)
            }
        }

        if (chatHistory.size == 1 && !isCopilotThinking) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val suggestions = listOf(
                    "Why did Nvidia conviction increase?",
                    "Relative risk comparison matrix for entire portfolio",
                    "Analyze macro bottlenecks on EV batteries margins"
                )
                suggestions.forEach { sug ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GridBorder, RoundedCornerShape(4.dp))
                            .background(TerminalCoal)
                            .clickable {
                                inputQuery = sug
                                viewModel.askCopilot(sug)
                                inputQuery = ""
                                focusManager.clearFocus()
                            }
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = "Quick Query", tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sug,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextGray
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(TerminalCoal, RoundedCornerShape(4.dp))
                .border(1.dp, GridBorder, RoundedCornerShape(4.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = {
                    Text(
                        "Search/Reason across themes...", 
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextGray)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                modifier = Modifier.weight(1f).testTag("copilot_input_field")
            )

            Button(
                onClick = {
                    if (inputQuery.trim().isNotEmpty()) {
                        viewModel.askCopilot(inputQuery)
                        inputQuery = ""
                        focusManager.clearFocus()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(2.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                enabled = inputQuery.trim().isNotEmpty() && !isCopilotThinking,
                modifier = Modifier.testTag("copilot_send_button")
            ) {
                Text("RUN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChatBubbleRow(msg: ChatMessage) {
    val isCopilot = msg.sender == "COPILOT"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isCopilot) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .testTag("chat_row_${msg.sender}"),
            colors = CardDefaults.cardColors(
                containerColor = if (isCopilot) TerminalCoal else ModuleSlate
            ),
            border = borderColor(if (isCopilot) GridBorder else CyberGreen.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCopilot) "COPILOT" else "ANALYST",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = if (isCopilot) CyberGreen else CyberCyan
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                FormattedTerminalText(text = msg.content)
            }
        }
    }
}

@Composable
fun FormattedTerminalText(text: String) {
    val parts = text.split("**")
    if (parts.size == 1) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.95f)
        )
    } else {
        Text(
            text = buildAnnotatedString {
                parts.forEachIndexed { i, p ->
                    if (i % 2 == 1) {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = CyberGreen)) {
                            append(p)
                        }
                    } else {
                        append(p)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.95f)
        )
    }
}

@Composable
fun CustomSignalDialog(
    defaultSymbol: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var symbol by remember { mutableStateOf(defaultSymbol) }
    var sourceType by remember { mutableStateOf("SEC Filing") }
    var rawText by remember { mutableStateOf("") }

    val sources = listOf("SEC Filing", "Earnings Call", "Macro Event", "Analyst Report")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "MANUAL SIGNAL INJECT_BOX",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CyberCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Act as an administrative filer or corporate analyst. Input raw market developments text which will be parsed dynamically.",
                    fontSize = 11.sp,
                    color = TextGray
                )

                // Symbol Picker Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NVDA", "TSLA", "AAPL", "MSFT").forEach { sym ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (symbol == sym) CyberCyan else GridBorder, RoundedCornerShape(2.dp))
                                .background(if (symbol == sym) ModuleSlate else Color.Transparent)
                                .clickable { symbol = sym }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sym,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (symbol == sym) Color.White else TextGray
                            )
                        }
                    }
                }

                // Source selection Row
                Text("DOCUMENT TYPE", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = TextGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sources.take(2).forEach { src ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (sourceType == src) CyberCyan else GridBorder, RoundedCornerShape(2.dp))
                                .background(if (sourceType == src) ModuleSlate else Color.Transparent)
                                .clickable { sourceType = src }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                src.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (sourceType == src) Color.White else TextGray
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sources.drop(2).forEach { src ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (sourceType == src) CyberCyan else GridBorder, RoundedCornerShape(2.dp))
                                .background(if (sourceType == src) ModuleSlate else Color.Transparent)
                                .clickable { sourceType = src }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                src.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (sourceType == src) Color.White else TextGray
                            )
                        }
                    }
                }

                // Content TextField
                TextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("Input raw transcript or file statement here... (e.g., 'NVDA Blackwell delivery schedule expanded')", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextGray)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp).testTag("custom_raw_text_field"),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TerminalCoal,
                        unfocusedContainerColor = TerminalCoal,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawText.trim().isNotEmpty()) {
                        onSubmit(symbol, sourceType, rawText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(2.dp),
                enabled = rawText.trim().isNotEmpty(),
                modifier = Modifier.testTag("signal_submit_button")
            ) {
                Text("RUN_INGRESS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("CANCEL", fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TerminalCoal,
        shape = RoundedCornerShape(4.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    )
}

@Composable
fun TerminalNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    val tabList = listOf(
        TabItem("THESIS", Icons.Default.Info, "Thesis Matrices"),
        TabItem("PIPELINE", Icons.Default.List, "News Ingestion"),
        TabItem("WHAT-IF", Icons.Default.Settings, "Scenario Sandbox"),
        TabItem("COPILOT", Icons.Default.Face, "Copilot AI"),
        TabItem("ALERTS", Icons.Default.Notifications, "Adaptive Alerts")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HorizonBlack)
            .border(width = 1.dp, color = GridBorder)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabList.forEach { tab ->
            val isSelected = activeTab == tab.id
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color.White else TerminalCoal)
                    .border(width = 1.dp, color = if (isSelected) Color.White else GridBorder, shape = RoundedCornerShape(16.dp))
                    .clickable { onTabSelected(tab.id) }
                    .testTag("nav_item_${tab.id.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) Color(0xFF0A0A0B) else TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.id,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp,
                            color = if (isSelected) Color(0xFF0A0A0B) else TextGray
                        )
                    )
                }
            }
        }
    }
}

private data class TabItem(val id: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

@Composable
private fun borderColor(color: Color) = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(color, color)))

@Composable
fun SegmentedPicker(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GridBorder, RoundedCornerShape(6.dp))
                .background(HorizonBlack)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { opt ->
                val active = selected == opt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) CyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, if (active) CyberCyan else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { onSelected(opt) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = opt,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (active) Color.White else TextGray
                    )
                }
            }
        }
    }
}

@Composable
fun WhatIfTabContent(
    viewModel: FolioViewModel
) {
    val scenarios by viewModel.scenarios.collectAsStateWithLifecycle()
    val isSimulating by viewModel.isSimulatingScenario.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var ratesState by remember { mutableStateOf("Rates: Unchanged") }
    var demandState by remember { mutableStateOf("Demand: Unchanged") }
    var supplyState by remember { mutableStateOf("Supply Costs: Neutral") }

    val rateOptions = listOf("Rates: -0.50% (Cut)", "Rates: Unchanged", "Rates: +0.75% (Hike)")
    val demandOptions = listOf("Demand: -15% (Contract)", "Demand: Unchanged", "Demand: +20% (Expand)")
    val supplyOptions = listOf("Supply Costs: -10%", "Supply Costs: Neutral", "Supply Costs: +25% (Strain)")

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("sandbox_scrollable_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Control Console Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("sandbox_control_console"),
                colors = CardDefaults.cardColors(containerColor = TerminalCoal),
                shape = RoundedCornerShape(24.dp),
                border = borderColor(GridBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Sandbox", tint = CyberCyan, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = "STRATEGIC WHAT-IF SANDBOX",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = CyberCyan
                        )
                    }

                    Text(
                        text = "Simulate how major macroeconomic shifts or hypothetical localized catastrophes affect existing investment bases, narrative conviction levels, and relative portfolio vectors.",
                        fontSize = 11.sp,
                        color = TextGray
                    )

                    // Text Input fields
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(45) },
                        label = { Text("SIMULATION NAME (e.g., Regional Power Outage)", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth().testTag("scenario_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = GridBorder,
                            focusedLabelColor = CyberCyan,
                            unfocusedLabelColor = TextGray
                        )
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(150) },
                        label = { Text("SCENARIO STATEMENT (e.g., Storm damage shuts Silicon Valley grid)", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth().height(84.dp).testTag("scenario_desc_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = GridBorder,
                            focusedLabelColor = CyberCyan,
                            unfocusedLabelColor = TextGray
                        )
                    )

                    // Macro segmented pickers
                    SegmentedPicker(
                        label = "FEDERAL CAPITAL INTEREST RATE BOUNDS",
                        options = rateOptions,
                        selected = ratesState,
                        onSelected = { ratesState = it }
                    )

                    SegmentedPicker(
                        label = "GLOBAL ENTERPRISE CONSUMER DEMAND SHIFT",
                        options = demandOptions,
                        selected = demandState,
                        onSelected = { demandState = it }
                    )

                    SegmentedPicker(
                        label = "SUPPLY CHAIN FABRICATION OVERHEADS CARRIER",
                        options = supplyOptions,
                        selected = supplyState,
                        onSelected = { supplyState = it }
                    )

                    // Trigger button
                    Button(
                        onClick = {
                            if (title.isNotBlank() && description.isNotBlank()) {
                                val vars = "Rates:$ratesState||Demand:$demandState||Supply:$supplyState"
                                viewModel.runScenario(title, description, vars)
                                // Clean fields
                                title = ""
                                description = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        enabled = title.isNotBlank() && description.isNotBlank() && !isSimulating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("run_scenario_simulation_button")
                    ) {
                        Text(
                            text = "EXECUTE SANDBOX REPORT [RUN_MATRIX]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Live Thinking / Loading Box
        if (isSimulating) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("sandbox_simulating_progress"),
                    colors = CardDefaults.cardColors(containerColor = TerminalCoal),
                    border = borderColor(CyberCyan)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "RUNNING MONTE CARLO COGNITIVE SIMULATIONS...",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Orchestrating stress pricing model delta calculations across portfolio bases via Gemini API pipeline.",
                            fontSize = 11.sp,
                            color = TextGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Historic Simulations Title Banner
        if (scenarios.isNotEmpty()) {
            item {
                Text(
                    text = "PAST SIMULATION RUNS HISTORY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(scenarios.sortedByDescending { it.timestamp }) { scen ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("scenario_card_${scen.id}"),
                    colors = CardDefaults.cardColors(containerColor = TerminalCoal),
                    border = borderColor(GridBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = scen.title.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color.White
                            )

                            IconButton(
                                onClick = { viewModel.deleteScenario(scen.id) },
                                modifier = Modifier.size(28.dp).testTag("delete_scenario_${scen.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete simulation result", tint = CyberRed, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scen.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        // Render Macro Parameter Tags
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            scen.macroVariables.split("||").forEach { v ->
                                val tagVal = v.substringAfter(":")
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                        .border(1.dp, GridBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tagVal.uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = CyberCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GridBorder.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Portfolio Impact Statement
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PORTFOLIO IMPACT: ",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = CyberCyan
                            )
                            val impStr = scen.portfolioImpact
                            Text(
                                text = impStr,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (impStr.contains("Positive", ignoreCase = true)) CyberGreen else if (impStr.contains("Negative", ignoreCase = true)) CyberRed else CyberAmber
                            )
                        }

                        // Score shifts section
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "CONVICTION SCORE ADJUSTMENT ESTIMATION:",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            scen.scoreChanges.split("||").filter { it.contains(":") }.forEach { entry ->
                                val sym = entry.substringBefore(":")
                                val changeVal = entry.substringAfter(":").toIntOrNull() ?: 0
                                val itemCol = if (changeVal > 0) CyberGreen else if (changeVal < 0) CyberRed else CyberAmber

                                Box(
                                    modifier = Modifier
                                        .border(1.dp, itemCol.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .background(itemCol.copy(alpha = 0.05f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = sym,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (changeVal > 0) "+$changeVal" else "$changeVal",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = itemCol
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = scen.resultComments,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun AddTickerDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "WATCHLIST: TRACK NEW TICKER",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CyberCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Integrate a new asset into FolioAI's ingestion pipeline. The AI will immediately formulate its baseline thesis and prioritize matching alerts.",
                    fontSize = 11.sp,
                    color = TextGray
                )
                
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.take(6).uppercase() },
                    label = { Text("TICKER SYMBOL (e.g. AMZN)", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().testTag("add_ticker_symbol_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GridBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = TextGray
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("COMPANY NAME (e.g. Amazon.com)", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().testTag("add_ticker_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GridBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = TextGray
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (symbol.isNotBlank()) {
                        onSubmit(symbol, name)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(2.dp),
                enabled = symbol.isNotBlank(),
                modifier = Modifier.testTag("submit_add_ticker_btn")
            ) {
                Text("ADD TICKER", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("CANCEL", fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TerminalCoal,
        shape = RoundedCornerShape(4.dp)
    )
}

