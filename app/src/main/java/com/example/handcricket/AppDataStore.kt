package com.example.handcricket

/** Singleton that survives activity transitions and holds all processed tournament data. */
object AppDataStore {
    var teams: Map<String, TeamStats> = emptyMap()
    var players: Map<String, PlayerStats> = emptyMap()
    var matchSummaries: List<MatchSummary> = emptyList()
    var matchPerformances: List<MatchPerformance> = emptyList()

    /** playerName → batting rank (1 = best) */
    var battingRanks: Map<String, Int> = emptyMap()
    /** playerName → bowling rank (1 = best) */
    var bowlingRanks: Map<String, Int> = emptyMap()
    /** teamName → team rank (1 = best) */
    var teamRanks: Map<String, Int> = emptyMap()

    fun clear() {
        teams = emptyMap()
        players = emptyMap()
        matchSummaries = emptyList()
        matchPerformances = emptyList()
        battingRanks = emptyMap()
        bowlingRanks = emptyMap()
        teamRanks = emptyMap()
    }
}
