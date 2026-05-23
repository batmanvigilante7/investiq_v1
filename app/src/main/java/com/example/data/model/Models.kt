package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ticker_theses")
data class TickerThesis(
    @PrimaryKey val symbol: String, // e.g. "NVDA"
    val name: String,
    val convictionScore: Int, // 0 - 100
    val prevConvictionScore: Int,
    val updatedAt: Long,
    val synopsis: String,
    val bullPoints: String, // Delimited by ||
    val bearPoints: String, // Delimited by ||
    val riskProfile: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val trajectory: String, // "UPGOING", "DOWNGOING", "STABLE"
    val isWatchlisted: Boolean = false
)

@Entity(tableName = "market_events")
data class MarketEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val title: String,
    val sourceType: String, // "SEC Filing", "Earnings Call", "Macro Event", "Social Sentiment", "Analyst Report"
    val content: String,
    val timestamp: Long,
    val relevanceScore: Int, // 0 - 100
    val sentimentScore: Int, // -10 to +10
    val impactReasoning: String,
    val signalClustered: String? = null, // For anomaly/clustering grouping, null if standard event
    val socialSentimentScore: Int = 0,   // -100 to +100 (%)
    val socialSourceBreakdown: String = "" // Breakdown text, e.g. "X: +35% | Reddit: +10% | Discord: -5%"
)

@Entity(tableName = "thesis_snapshots")
data class ThesisSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val convictionScore: Int,
    val timestamp: Long
)

@Entity(tableName = "watchlist_alerts")
data class WatchlistAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val message: String,
    val timestamp: Long,
    val severity: String, // "INFO", "WARNING", "CRITICAL"
    val isRead: Boolean = false
)

@Entity(tableName = "scenario_simulations")
data class ScenarioSimulation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val macroVariables: String, // Rates:-0.50%||Demand:+10%||SupplyCosts:+15%
    val resultComments: String,
    val scoreChanges: String, // NVDA:-12||AAPL:+4||MSFT:+2
    val portfolioImpact: String,
    val timestamp: Long
)

