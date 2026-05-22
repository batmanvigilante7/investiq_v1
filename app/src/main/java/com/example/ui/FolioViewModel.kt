package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.model.MarketEvent
import com.example.data.model.ThesisSnapshot
import com.example.data.model.TickerThesis
import com.example.data.model.WatchlistAlert
import com.example.data.repository.FolioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ChatMessage(
    val sender: String, // "USER" or "COPILOT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalCoroutinesApi::class)
class FolioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FolioRepository(application)

    // Selection
    val selectedSymbol = MutableStateFlow("NVDA")

    // Loader limits
    val isIngesting = MutableStateFlow(false)
    val isCopilotThinking = MutableStateFlow(false)

    // Chat Copilot Logs
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "COPILOT",
                content = "Greetings. I am **FolioAI Terminal Copilot**. Ask me to synthesize, perform cross-ticker risk comparisons, or highlight 'Why NVDA conviction decayed yesterday' based on live news inputs."
            )
        )
    )
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    // Simulator Active Status
    val isSimulatorActive = MutableStateFlow(false)

    // Core flows from repository
    val theses: StateFlow<List<TickerThesis>> = repository.allTheses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<MarketEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<WatchlistAlert>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active screen flows
    val activeThesis: StateFlow<TickerThesis?> = selectedSymbol
        .flatMapLatest { repository.getThesisBySymbol(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeSnapshots: StateFlow<List<ThesisSnapshot>> = selectedSymbol
        .flatMapLatest { repository.getSnapshotsForSymbol(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Seed database instantly if first launch
            repository.seedMockDataIfEmpty()
            // Start the simulator loop coordinate
            startSimulatorLoop()
        }
    }

    fun selectSymbol(symbol: String) {
        selectedSymbol.value = symbol
    }

    /**
     * User triggers direct custom event ingestion
     */
    fun ingestCustomEvent(symbol: String, sourceType: String, title: String) {
        viewModelScope.launch {
            isIngesting.value = true
            try {
                repository.ingestEvent(symbol, sourceType, title)
            } catch (e: Exception) {
                Log.e("FolioViewModel", "Ingestion failed: ${e.message}")
            } finally {
                isIngesting.value = false
            }
        }
    }

    /**
     * Interactive Chat Copilot
     */
    fun askCopilot(query: String) {
        if (query.trim().isEmpty()) return
        
        viewModelScope.launch {
            _chatHistory.value = _chatHistory.value + ChatMessage("USER", query)
            isCopilotThinking.value = true
            
            try {
                // Get active theses & events state
                val activeThesesList = theses.value
                val activeEventsList = events.value
                
                val answer = GeminiClient.copilotSearchAndReason(query, activeThesesList, activeEventsList)
                _chatHistory.value = _chatHistory.value + ChatMessage("COPILOT", answer)
            } catch (e: Exception) {
                _chatHistory.value = _chatHistory.value + ChatMessage("COPILOT", "Error coordinating reasoning matrix: ${e.message}")
            } finally {
                isCopilotThinking.value = false
            }
        }
    }

    fun toggleSimulator() {
        isSimulatorActive.value = !isSimulatorActive.value
    }

    fun markAlertAsRead(alertId: Int) {
        viewModelScope.launch {
            repository.markAlertAsRead(alertId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllEvents()
        }
    }

    private val poolOfSimulatedEvents = listOf(
        // NVDA
        SimEvent("NVDA", "SEC Filing", "SEC Form 4 reports major institutional consolidation of shares, signaling heavy corporate backing."),
        SimEvent("NVDA", "Analyst Report", "Large investment banks raise price target reflecting complete gross margins control and high pre-bookings of Rubin nodes."),
        SimEvent("NVDA", "Macro Event", "Regulatory body signals further export adjustments on AI accelerator cores to specified trading partners."),
        SimEvent("NVDA", "News", "Unanticipated packaging node delay from outsourced wafer foundries could temporarily cap shipping velocities."),
        SimEvent("NVDA", "Social Sentiment", "AI research developers flood social outlets reporting massive performance breakthroughs inside TensorCore execution pipelines."),
        
        // TSLA
        SimEvent("TSLA", "Earnings Call", "Management indicates substantial cost-of-goods-sold improvement, driving battery pack cost curves down to all-time structural minimums."),
        SimEvent("TSLA", "News", "Major ride-share group signs initial intent of system onboarding with upcoming Tesla custom autonomous fleet services."),
        SimEvent("TSLA", "Macro Event", "Government grants significant subsidy adjustments to local high-volume battery storage cells manufactured domestically."),
        SimEvent("TSLA", "Analyst Report", "Concerns grow over margin compression due to localized EV price adjustments in competitive Asian market channels."),
        SimEvent("TSLA", "SEC Filing", "Filing details strategic capital allocations pointing to major gigafactory expansion schedules for robotic production nodes."),

        // AAPL
        SimEvent("AAPL", "News", "Reports confirm on-device local intelligence chips have secure supply allocation to surpass past annual upgrade limits."),
        SimEvent("AAPL", "Analyst Report", "Brokerages double down on services margin resilience, raising services slice representation in overall valuation modules."),
        SimEvent("AAPL", "Social Sentiment", "Tech reviewers note stellar consumer responsiveness on neural engine local translations, cementing high consumer moat protection."),
        SimEvent("AAPL", "SEC Filing", "Annual 10-K outlines elevated risk clauses regarding antitrust suits on premium digital marketplace fee architectures."),
        
        // MSFT
        SimEvent("MSFT", "News", "Secures massive clean power supply contracts with small nuclear reactor builders, guaranteeing stable datacenters operations."),
        SimEvent("MSFT", "Earnings Call", "CFO highlights Commercial Cloud segment maintaining robust 21% scale expansion, outpacing overall IT spend contractions."),
        SimEvent("MSFT", "Macro Event", "Sovereign cloud regulations in Western Europe mandate localized partition compliance for public records datacenters."),
        SimEvent("MSFT", "Social Sentiment", "Enterprise admins share substantial efficiency improvements in engineering divisions through auto-copilot deployment cycles.")
    )

    private fun startSimulatorLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            var ticks = 0
            while (true) {
                if (isSimulatorActive.value) {
                    ticks++
                    if (ticks % 3 == 0) {
                        try {
                            repository.decayAllConviction()
                        } catch (e: Exception) {
                            Log.e("FolioViewModel", "Decay failed: ${e.message}", e)
                        }
                    }

                    // Pull random mock event, ingest it
                    val idx = Random.nextInt(poolOfSimulatedEvents.size)
                    val sim = poolOfSimulatedEvents[idx]

                    try {
                        repository.ingestEvent(sim.symbol, sim.source, sim.content)
                    } catch (e: Exception) {
                        Log.e("FolioViewModel", "Simulator ingestion fail: ${e.message}")
                    }
                }
                // Simulate delay - in active mode, adds news every 14 seconds
                delay(14000)
            }
        }
    }

    private data class SimEvent(val symbol: String, val source: String, val content: String)
}
