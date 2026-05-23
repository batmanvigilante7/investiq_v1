package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.GeminiClient
import com.example.data.db.AppDatabase
import com.example.data.model.MarketEvent
import com.example.data.model.ThesisSnapshot
import com.example.data.model.TickerThesis
import com.example.data.model.WatchlistAlert
import com.example.data.model.ScenarioSimulation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FolioRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val thesisDao = db.tickerThesisDao()
    private val eventDao = db.marketEventDao()
    private val snapshotDao = db.thesisSnapshotDao()
    private val alertDao = db.watchlistAlertDao()
    private val scenarioDao = db.scenarioSimulationDao()

    val allTheses: Flow<List<TickerThesis>> = thesisDao.getAllTheses()
    val allEvents: Flow<List<MarketEvent>> = eventDao.getAllEvents()
    val allAlerts: Flow<List<WatchlistAlert>> = alertDao.getAllAlerts()
    val allScenarios: Flow<List<ScenarioSimulation>> = scenarioDao.getAllScenarios()

    fun getThesisBySymbol(symbol: String): Flow<TickerThesis?> {
        return thesisDao.getThesisBySymbol(symbol)
    }

    fun getSnapshotsForSymbol(symbol: String): Flow<List<ThesisSnapshot>> {
        return snapshotDao.getSnapshotsForSymbol(symbol)
    }

    /**
     * Ingests a new raw event into our pipeline, triggers AI-analysis,
     * updates narrative, and generates alerts if needed.
     */
    suspend fun ingestEvent(
        symbol: String,
        sourceType: String,
        rawContent: String
    ): MarketEvent = withContext(Dispatchers.IO) {
        val analysis = GeminiClient.analyzeMarketEvent(symbol, sourceType, rawContent)
        
        val event = MarketEvent(
            symbol = symbol,
            title = analysis.cleanTitle,
            sourceType = sourceType,
            content = rawContent,
            timestamp = System.currentTimeMillis(),
            relevanceScore = analysis.relevanceScore,
            sentimentScore = analysis.sentimentScore,
            impactReasoning = analysis.whyItMatters,
            signalClustered = if (analysis.relevanceScore > 83) "CRITICAL SIGNAL" else null,
            socialSentimentScore = analysis.socialSentimentScore,
            socialSourceBreakdown = analysis.socialSourceBreakdown
        )

        val eventId = eventDao.insertEvent(event)
        val savedEvent = event.copy(id = eventId.toInt())

        // Fetch current thesis to update it
        val existingThesis = thesisDao.getThesisBySymbolDirect(symbol)
        if (existingThesis != null) {
            val oldScore = existingThesis.convictionScore
            val isWatchlisted = existingThesis.isWatchlisted

            // Gather recent events to re-synthesize
            val recentEvents = eventDao.getEventsForSymbolDirect(symbol)
            val synthesis = GeminiClient.synthesizeThesis(
                symbol = symbol,
                name = existingThesis.name,
                currentSynopsis = existingThesis.synopsis,
                recentEvents = recentEvents
            )

            val updatedThesis = TickerThesis(
                symbol = symbol,
                name = existingThesis.name,
                convictionScore = synthesis.convictionScore.coerceIn(10, 99),
                prevConvictionScore = oldScore,
                updatedAt = System.currentTimeMillis(),
                synopsis = synthesis.synopsis,
                bullPoints = synthesis.bullPoints.joinToString("||"),
                bearPoints = synthesis.bearPoints.joinToString("||"),
                riskProfile = synthesis.riskProfile,
                trajectory = synthesis.trajectory,
                isWatchlisted = isWatchlisted
            )

            thesisDao.insertThesis(updatedThesis)

            // Prioritized Watchlist alerts or standard alerts
            val watchlistPrefix = if (isWatchlisted) "⭐ [WATCHLIST PRIORITIZED] " else ""
            
            if (synthesis.riskProfile == "CRITICAL" && existingThesis.riskProfile != "CRITICAL") {
                alertDao.insertAlert(
                    WatchlistAlert(
                        symbol = symbol,
                        message = "${watchlistPrefix}Risk escalation: '$symbol' risk profile elevated to CRITICAL due to recent '${savedEvent.title}'.",
                        timestamp = System.currentTimeMillis(),
                        severity = "CRITICAL"
                    )
                )
            } else if (Math.abs(synthesis.convictionScore - oldScore) >= 5 || (isWatchlisted && Math.abs(synthesis.convictionScore - oldScore) >= 2)) {
                val trend = if (synthesis.convictionScore > oldScore) "surpasses" else "decays to"
                alertDao.insertAlert(
                    WatchlistAlert(
                        symbol = symbol,
                        message = "${watchlistPrefix}Conviction shock: $symbol conviction score $trend ${synthesis.convictionScore}.",
                        timestamp = System.currentTimeMillis(),
                        severity = if (synthesis.convictionScore > oldScore) "INFO" else "WARNING"
                    )
                )
            } else if (savedEvent.relevanceScore > 80) {
                alertDao.insertAlert(
                    WatchlistAlert(
                        symbol = symbol,
                        message = "${watchlistPrefix}High relevance signal digested: '${savedEvent.title}' on $symbol.",
                        timestamp = System.currentTimeMillis(),
                        severity = "INFO"
                    )
                )
            }

            // Save historic snapshots for timeline chart rendering
            snapshotDao.insertSnapshot(
                ThesisSnapshot(
                    symbol = symbol,
                    convictionScore = synthesis.convictionScore,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        return@withContext savedEvent
    }

    suspend fun insertThesisDirect(thesis: TickerThesis) = withContext(Dispatchers.IO) {
        thesisDao.insertThesis(thesis)
        // Insert starting snapshot too
        snapshotDao.insertSnapshot(
            ThesisSnapshot(symbol = thesis.symbol, convictionScore = thesis.convictionScore, timestamp = System.currentTimeMillis() - 86400000 * 2)
        )
    }

    suspend fun decayAllConviction() = withContext(Dispatchers.IO) {
        // Conforms to "confidence decay over time"
        val existing = thesisDao.getAllThesesDirect()
        existing.forEach { thesis ->
            if (thesis.convictionScore > 10) {
                val newScore = (thesis.convictionScore - 2).coerceAtLeast(10)
                val updated = thesis.copy(
                    prevConvictionScore = thesis.convictionScore,
                    convictionScore = newScore,
                    updatedAt = System.currentTimeMillis()
                )
                thesisDao.insertThesis(updated)
                
                // Add a snapshot for history graph recording
                snapshotDao.insertSnapshot(
                    ThesisSnapshot(
                        symbol = thesis.symbol,
                        convictionScore = newScore,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                // Alert if passing critical thresholds
                if (newScore == 80 || newScore == 70 || newScore == 60) {
                    alertDao.insertAlert(
                        WatchlistAlert(
                            symbol = thesis.symbol,
                            message = "Active narrative cooling: '${thesis.symbol}' conviction decayed to $newScore index due to signal silence.",
                            timestamp = System.currentTimeMillis(),
                            severity = "WARNING"
                        )
                    )
                }
            }
        }
    }

    suspend fun clearAllEvents() = withContext(Dispatchers.IO) {
        eventDao.clearAllEvents()
        alertDao.clearAllAlerts()
    }

    suspend fun markAlertAsRead(id: Int) = withContext(Dispatchers.IO) {
        alertDao.markAsRead(id)
    }

    /**
     * Seeds initial company models so that the terminal has stunning live states instantly on boot.
     */
    suspend fun seedMockDataIfEmpty() = withContext(Dispatchers.IO) {
        val count = thesisDao.getThesisBySymbolDirect("NVDA")
        if (count != null) return@withContext // Seeded

        Log.d("FolioRepository", "Prepopulating terminal matrix...")

        // Tickers: NVDA, TSLA, AAPL, MSFT, PLTR
        val seedTheses = listOf(
            TickerThesis(
                symbol = "NVDA",
                name = "NVIDIA Corp.",
                convictionScore = 88,
                prevConvictionScore = 85,
                updatedAt = System.currentTimeMillis() - 3600000,
                synopsis = "Sustains complete developer node stack dominance in foundational AI training silicon. Heavy capital injection across hyperscalers guards near-term booking flows.",
                bullPoints = "cuda ecosystem locking in software pipeline||blackwell architecture ramping custom enterprise margins||sovereign nation data node diversification",
                bearPoints = "restricted access to critical microfab centers||hyperscalers custom silicon ASIC substitution threat||high fab pricing pressures from raw wafer shortages",
                riskProfile = "MEDIUM",
                trajectory = "UPGOING"
            ),
            TickerThesis(
                symbol = "TSLA",
                name = "Tesla Inc.",
                convictionScore = 65,
                prevConvictionScore = 72,
                updatedAt = System.currentTimeMillis() - 7200000,
                synopsis = "Narrative relies on Robotaxi regulatory approvals and full-self driving adoption. Short term vehicle deliveries face steep global margin contraction.",
                bullPoints = "megapack grid storage utility scaling||unmatched custom automated model testing fleets||long-term lower-cost platform production ramps",
                bearPoints = "frequent brand discounts eroding high-status premium tag||unpredictable regulatory policies on visual-only automation||stiff competition across standard electric vehicle channels",
                riskProfile = "HIGH",
                trajectory = "DOWNGOING"
            ),
            TickerThesis(
                symbol = "AAPL",
                name = "Apple Inc.",
                convictionScore = 81,
                prevConvictionScore = 81,
                updatedAt = System.currentTimeMillis() - 14400000,
                synopsis = "Durable earnings backed by highly defensive consumer service ecosystem. Upgrade cycles driven by generative local intelligence integration.",
                bullPoints = "high-margin services capturing user transaction flows||unparalleled consumer physical hardware integration||highly supportive capital share buyback policies",
                bearPoints = "hardware elongation cycles across mature products||geopolitical consumer restrictions in international markets||emerging regulations targeting custom digital platform commission rates",
                riskProfile = "LOW",
                trajectory = "STABLE"
            ),
            TickerThesis(
                symbol = "MSFT",
                name = "Microsoft Corp.",
                convictionScore = 84,
                prevConvictionScore = 82,
                updatedAt = System.currentTimeMillis() - 10800000,
                synopsis = "Azure remains a core hyper-scaler landing pad for modern workloads. Sustained corporate IT cash commitment supports multi-year licensing.",
                bullPoints = "copilot subscription options expand client value capture||hybrid cloud configurations retain sensitive database markets||massive recurring multi-year contract renewals",
                bearPoints = "escalating physical datacenter capEx requirements drags roic||gradual depreciation expansion margins headwinds||cloud performance comparison volatility from rivals",
                riskProfile = "MEDIUM",
                trajectory = "UPGOING"
            )
        )

        // Seed snapshots
        seedTheses.forEach { thesis ->
            thesisDao.insertThesis(thesis)
            
            // Seed a historical timeline of snapshots to draw fine charts
            val symbol = thesis.symbol
            val baseC = thesis.convictionScore
            snapshotDao.insertSnapshot(ThesisSnapshot(symbol = symbol, convictionScore = baseC - 6, timestamp = System.currentTimeMillis() - 86400000 * 4))
            snapshotDao.insertSnapshot(ThesisSnapshot(symbol = symbol, convictionScore = baseC - 2, timestamp = System.currentTimeMillis() - 86400000 * 3))
            snapshotDao.insertSnapshot(ThesisSnapshot(symbol = symbol, convictionScore = baseC + 4, timestamp = System.currentTimeMillis() - 86400000 * 2))
            snapshotDao.insertSnapshot(ThesisSnapshot(symbol = symbol, convictionScore = baseC - 3, timestamp = System.currentTimeMillis() - 86400000 * 1))
            snapshotDao.insertSnapshot(ThesisSnapshot(symbol = symbol, convictionScore = baseC, timestamp = System.currentTimeMillis()))
        }

        // Seed initial market events
        val seedEvents = listOf(
            MarketEvent(
                symbol = "NVDA",
                title = "Blackwell System Thermal Margins Align",
                sourceType = "Earnings Call",
                content = "Management confirmed that system cabinet thermals are completely verified. Production yield is on plan, with high delivery scheduled for large cluster datacenters.",
                timestamp = System.currentTimeMillis() - 7200000,
                relevanceScore = 90,
                sentimentScore = 8,
                impactReasoning = "Settles systemic fears over engineering flaws and timelines. Bolsters near-term revenue targets and margins assurance."
            ),
            MarketEvent(
                symbol = "TSLA",
                title = "European Gigafactory Power Interruptions",
                sourceType = "Macro Event",
                content = "Regional power lines suffered damage from localized storms. Operations in Berlin plant experienced temporary power cycles and minor manufacturing delays.",
                timestamp = System.currentTimeMillis() - 14400000,
                relevanceScore = 55,
                sentimentScore = -4,
                impactReasoning = "Generates short-term operational headwinds but leaves long-term target capacity and autonomous thesis untouched."
            ),
            MarketEvent(
                symbol = "AAPL",
                title = "Regulatory Watchdogs Scrutinize Subscription Cuts",
                sourceType = "SEC Filing",
                content = "Filing notes active inquiries by regional anti-trust committees regarding default system browser fee structures and default premium application distribution models.",
                timestamp = System.currentTimeMillis() - 21600000,
                relevanceScore = 82,
                sentimentScore = -5,
                impactReasoning = "Introduces structural risk of service margin erosion if fees are forced downwards by regional court decisions."
            ),
            MarketEvent(
                symbol = "MSFT",
                title = "Large Scale SaaS Enterprise Renewals Surpass Expectations",
                sourceType = "Analyst Report",
                content = "High-tier institutional research indicates renewal rates on Enterprise Suite licenses exceeding 94%, with substantial cross-selling of AI companion tools.",
                timestamp = System.currentTimeMillis() - 28800000,
                relevanceScore = 78,
                sentimentScore = 6,
                impactReasoning = "Confirms corporate IT budgets remain highly inelastic regarding cloud applications. Secures high visibility of recurring margins."
            )
        )

        seedEvents.forEach { event ->
            eventDao.insertEvent(event)
        }

        Log.d("FolioRepository", "Terminal seed complete.")
    }

    suspend fun toggleWatchlist(symbol: String, isWatchlisted: Boolean) = withContext(Dispatchers.IO) {
        thesisDao.updateWatchlistStatus(symbol, isWatchlisted)
    }

    suspend fun addCustomTicker(symbol: String, name: String) = withContext(Dispatchers.IO) {
        val sym = symbol.trim().uppercase()
        val existing = thesisDao.getThesisBySymbolDirect(sym)
        if (existing != null) {
            thesisDao.updateWatchlistStatus(sym, true)
            return@withContext
        }
        
        val initialThesis = TickerThesis(
            symbol = sym,
            name = name.ifEmpty { "$sym Co." },
            convictionScore = 70,
            prevConvictionScore = 70,
            updatedAt = System.currentTimeMillis(),
            synopsis = "Active Watchlist asset entered by user. Narrative under continuous automated signal ingestion.",
            bullPoints = "Robust segment growth on targeted operational vectors||Loyal early adopter client base",
            bearPoints = "Evolving macro capital allocation bounds||Rising component fabrication pricing friction",
            riskProfile = "MEDIUM",
            trajectory = "STABLE",
            isWatchlisted = true
        )
        thesisDao.insertThesis(initialThesis)
        
        snapshotDao.insertSnapshot(ThesisSnapshot(symbol = sym, convictionScore = 70, timestamp = System.currentTimeMillis()))
        
        val startEvent = MarketEvent(
            symbol = sym,
            title = "Watchlist Track Begun",
            sourceType = "Macro Event",
            content = "Asset '$sym' initialized in portfolio tracker. AI pipeline actively prioritizing alerts and news digests.",
            timestamp = System.currentTimeMillis(),
            relevanceScore = 80,
            sentimentScore = 4,
            impactReasoning = "Establishes base analytics reporting layer under localized user directives.",
            socialSentimentScore = 50,
            socialSourceBreakdown = "X: +40% | Reddit: +35% | Discord: +15%"
        )
        eventDao.insertEvent(startEvent)
    }

    suspend fun runAndSaveScenario(
        title: String,
        description: String,
        macroVariables: String
    ): ScenarioSimulation = withContext(Dispatchers.IO) {
        val activeTheses = thesisDao.getAllThesesDirect()
        val result = GeminiClient.runScenarioSimulation(
            title = title,
            description = description,
            macroVariables = macroVariables,
            activeTheses = activeTheses
        )
        
        val scoreChangesStr = result.scoreChanges.entries.joinToString("||") { "${it.key}:${it.value}" }
        
        val scenario = ScenarioSimulation(
            title = title,
            description = description,
            macroVariables = macroVariables,
            resultComments = result.resultComments,
            scoreChanges = scoreChangesStr,
            portfolioImpact = result.portfolioImpact,
            timestamp = System.currentTimeMillis()
        )
        
        val id = scenarioDao.insertScenario(scenario)
        return@withContext scenario.copy(id = id.toInt())
    }

    suspend fun deleteScenario(id: Int) = withContext(Dispatchers.IO) {
        scenarioDao.deleteScenario(id)
    }
}
