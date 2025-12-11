package com.example.handcricket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.databinding.FragmentStatsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private var tournamentId: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tournamentId = arguments?.getInt("TOURNAMENT_ID") ?: 0

        // Initialize SwipeRefreshLayout from the binding
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadAllStats()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )

        loadAllStats()
    }

    private fun loadAllStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val performances = db.tournamentDao().getPlayerPerformances(tournamentId)
                val tournament = db.tournamentDao().getTournamentById(tournamentId)

                withContext(Dispatchers.Main) {
                    binding.tvTournamentName.text = tournament?.name ?: "Tournament"

                    if (performances.isEmpty()) {

                    } else {
                        hideAllProgressBars()
                        calculateAndDisplayAllStats(performances)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorState(e.message ?: "Unknown error")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.tvLastUpdated.text = "Last updated: ${getCurrentTime()}"
                }
            }
        }
    }

    private fun calculateAndDisplayAllStats(performances: List<com.example.handcricket.data.PlayerPerformance>) {
        // Group performances by player
        val batterPerformances = performances.filter { it.role == "batter" }.groupBy { it.playerName }
        val bowlerPerformances = performances.filter { it.role == "bowler" }.groupBy { it.playerName }
        val allPlayers = (batterPerformances.keys + bowlerPerformances.keys).distinct()

        // Calculate and display each section
        displayTopScorers(batterPerformances)
        displayTopBowlers(bowlerPerformances)
        displayMilestones(batterPerformances)
        displayBoundaryStats(batterPerformances)
        displayBestEconomy(bowlerPerformances)
        displayFantasyPoints(batterPerformances, bowlerPerformances)
        displayTournamentSummary(performances, allPlayers.size)
    }

    private fun displayTopScorers(batterPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>) {
        val topScorers = batterPerformances.map { (name, perfList) ->
            val totalRuns = perfList.sumOf { it.runs }
            val totalBalls = perfList.sumOf { it.balls }
            val strikeRate = if (totalBalls > 0) (totalRuns.toFloat() / totalBalls * 100) else 0f
            TopScorer(
                name,
                totalRuns,
                totalBalls,
                perfList.sumOf { it.fours },
                perfList.sumOf { it.sixes },
                strikeRate,
                perfList.filter { it.runs >= 100 }.size,
                perfList.filter { it.runs in 50..99 }.size
            )
        }.sortedByDescending { it.runs }.take(10)

        binding.tvTopScorersCount.text = "${topScorers.size}"
        binding.tvTopScorers.text = buildString {
            topScorers.forEachIndexed { index, scorer ->
                append("${index + 1}. ${scorer.name}\n")
                append("   ${scorer.runs} runs")
                append(" • SR: ${"%.2f".format(scorer.strikeRate)}")
                append(" • ${scorer.fours}4/${scorer.sixes}6")
                if (scorer.centuries > 0) append(" • 💯x${scorer.centuries}")
                if (scorer.fifties > 0) append(" • 50x${scorer.fifties}")
                append("\n\n")
            }
        }
    }

    private fun displayTopBowlers(bowlerPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>) {
        val topBowlers = bowlerPerformances.map { (name, perfList) ->
            val totalWickets = perfList.sumOf { it.wickets }
            val totalOvers = perfList.sumOf { it.overs.toDouble() }.toFloat()
            val totalRuns = perfList.sumOf { it.runsConceded }
            val economy = if (totalOvers > 0) totalRuns.toFloat() / totalOvers else 0f
            val bestFigures = perfList.maxByOrNull { it.wickets }?.let {
                "${it.wickets}/${it.runsConceded}"
            } ?: "0/0"

            TopBowler(
                name,
                totalWickets,
                totalOvers,
                economy,
                bestFigures,
                perfList.filter { it.wickets >= 3 }.size
            )
        }.sortedByDescending { it.wickets }.take(10)

        binding.tvTopBowlersCount.text = "${topBowlers.size}"
        binding.tvTopBowlers.text = buildString {
            topBowlers.forEachIndexed { index, bowler ->
                append("${index + 1}. ${bowler.name}\n")
                append("   ${bowler.wickets} wickets")
                append(" • Eco: ${"%.2f".format(bowler.economy)}")
                append(" • Best: ${bowler.bestFigures}")
                if (bowler.threePlusWickets > 0) append(" • 3+W: ${bowler.threePlusWickets}")
                append("\n\n")
            }
        }
    }

    private fun displayMilestones(batterPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>) {
        val centuries = batterPerformances.flatMap { it.value.filter { perf -> perf.runs >= 100 } }
        val fifties = batterPerformances.flatMap { it.value.filter { perf -> perf.runs in 50..99 } }

        val centuryLeaders = centuries.groupBy { it.playerName }
            .map { (name, list) -> name to list.size }
            .sortedByDescending { it.second }
            .take(3)

        val fiftyLeaders = fifties.groupBy { it.playerName }
            .map { (name, list) -> name to list.size }
            .sortedByDescending { it.second }
            .take(3)

        binding.tvCenturiesCount.text = "${centuries.size}"
        binding.tvFiftiesCount.text = "${fifties.size}"

        binding.tvCenturiesTop.text = if (centuryLeaders.isNotEmpty()) {
            "${centuryLeaders[0].first} (${centuryLeaders[0].second})"
        } else "-"

        binding.tvFiftiesTop.text = if (fiftyLeaders.isNotEmpty()) {
            "${fiftyLeaders[0].first} (${fiftyLeaders[0].second})"
        } else "-"

        binding.tvMilestones.text = buildString {
            if (centuryLeaders.isNotEmpty()) {
                append("Century Leaders:\n")
                centuryLeaders.forEachIndexed { index, (name, count) ->
                    append("  ${index + 1}. $name - $count century${if (count > 1) "s" else ""}\n")
                }
            }
            if (fiftyLeaders.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Fifty Leaders:\n")
                fiftyLeaders.forEachIndexed { index, (name, count) ->
                    append("  ${index + 1}. $name - $count fifty${if (count > 1) "s" else ""}\n")
                }
            }
            if (isEmpty()) {
                append("No milestones achieved yet.")
            }
        }
    }

    private fun displayBoundaryStats(batterPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>) {
        val totalFours = batterPerformances.flatMap { it.value }.sumOf { it.fours }
        val totalSixes = batterPerformances.flatMap { it.value }.sumOf { it.sixes }

        val fourLeaders = batterPerformances.map { (name, list) ->
            name to list.sumOf { it.fours }
        }.sortedByDescending { it.second }.take(3)

        val sixLeaders = batterPerformances.map { (name, list) ->
            name to list.sumOf { it.sixes }
        }.sortedByDescending { it.second }.take(3)

        binding.tvTotalFours.text = "$totalFours"
        binding.tvTotalSixes.text = "$totalSixes"

        binding.tvMostFours.text = if (fourLeaders.isNotEmpty()) {
            "${fourLeaders[0].first} (${fourLeaders[0].second})"
        } else "-"

        binding.tvMostSixes.text = if (sixLeaders.isNotEmpty()) {
            "${sixLeaders[0].first} (${sixLeaders[0].second})"
        } else "-"

        binding.tvBoundaryLeaders.text = buildString {
            append("Most Fours:\n")
            if (fourLeaders.isNotEmpty()) {
                fourLeaders.forEachIndexed { index, (name, count) ->
                    append("  ${index + 1}. $name - $count fours\n")
                }
            } else {
                append("  No data\n")
            }

            append("\nMost Sixes:\n")
            if (sixLeaders.isNotEmpty()) {
                sixLeaders.forEachIndexed { index, (name, count) ->
                    append("  ${index + 1}. $name - $count sixes\n")
                }
            } else {
                append("  No data\n")
            }
        }
    }

    private fun displayBestEconomy(bowlerPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>) {
        val economyLeaders = bowlerPerformances.map { (name, perfList) ->
            val totalOvers = perfList.sumOf { it.overs.toDouble() }.toFloat()
            val totalRuns = perfList.sumOf { it.runsConceded }
            val economy = if (totalOvers >= 5) totalRuns.toFloat() / totalOvers else Float.MAX_VALUE
            val wickets = perfList.sumOf { it.wickets }

            EconomyRecord(name, economy, wickets, totalOvers)
        }.filter { it.overs >= 5f }
            .sortedBy { it.economy }
            .take(5)

        binding.tvBestEconomyValue.text = if (economyLeaders.isNotEmpty()) {
            "${"%.2f".format(economyLeaders[0].economy)}"
        } else "N/A"

        binding.tvBestEconomy.text = buildString {
            if (economyLeaders.isNotEmpty()) {
                economyLeaders.forEachIndexed { index, record ->
                    append("${index + 1}. ${record.name}\n")
                    append("   Eco: ${"%.2f".format(record.economy)}")
                    append(" • Wkts: ${record.wickets}")
                    append(" • Overs: ${"%.1f".format(record.overs)}\n\n")
                }
            } else {
                append("No bowlers with sufficient overs (min 5 overs)")
            }
        }
    }

    private fun displayFantasyPoints(
        batterPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>,
        bowlerPerformances: Map<String, List<com.example.handcricket.data.PlayerPerformance>>
    ) {
        val fantasyPlayers = (batterPerformances.keys + bowlerPerformances.keys).distinct().map { name ->
            val batterPerf = batterPerformances[name] ?: emptyList()
            val bowlerPerf = bowlerPerformances[name] ?: emptyList()

            var battingPoints = 0
            var bowlingPoints = 0

            // Calculate batting points
            batterPerf.forEach { perf ->
                // Basic runs
                battingPoints += perf.runs
                // Boundary bonuses
                battingPoints += perf.fours * 1
                battingPoints += perf.sixes * 2
                // Milestone bonuses
                battingPoints += when {
                    perf.runs >= 100 -> 50
                    perf.runs >= 50 -> 30
                    perf.runs >= 30 -> 10
                    else -> 0
                }
                // Strike rate bonus
                if (perf.balls >= 10) {
                    val sr = (perf.runs.toFloat() / perf.balls) * 100
                    battingPoints += when {
                        sr >= 200 -> 20
                        sr >= 150 -> 15
                        sr >= 120 -> 10
                        else -> 0
                    }
                }
            }

            // Calculate bowling points
            bowlerPerf.forEach { perf ->
                // Wicket points
                bowlingPoints += perf.wickets * 25
                // Wicket haul bonuses
                if (perf.wickets >= 5) bowlingPoints += 30
                else if (perf.wickets >= 3) bowlingPoints += 20
                // Economy bonus
                if (perf.overs >= 2) {
                    val economy = if (perf.overs > 0) perf.runsConceded / perf.overs else 0f
                    bowlingPoints += when {
                        economy < 6 -> 25
                        economy < 8 -> 15
                        economy < 10 -> 10
                        else -> 0
                    }
                }
            }

            FantasyPlayer(
                name,
                battingPoints + bowlingPoints,
                battingPoints,
                bowlingPoints,
                batterPerf.size + bowlerPerf.size
            )
        }.sortedByDescending { it.totalPoints }.take(5)

        binding.tvTopFantasyPoints.text = if (fantasyPlayers.isNotEmpty()) {
            "${fantasyPlayers[0].totalPoints} pts"
        } else "0 pts"

        binding.tvFantasyPoints.text = buildString {
            if (fantasyPlayers.isNotEmpty()) {
                fantasyPlayers.forEachIndexed { index, player ->
                    append("${index + 1}. ${player.name}\n")
                    append("   ${player.totalPoints} pts")
                    append(" (Bat: ${player.battingPoints}")
                    append(", Bowl: ${player.bowlingPoints})")
                    append(" • ${player.matches} match${if (player.matches != 1) "es" else ""}\n\n")
                }
            } else {
                append("No fantasy points data available")
            }
        }
    }

    private fun displayTournamentSummary(performances: List<com.example.handcricket.data.PlayerPerformance>, totalPlayers: Int) {
        val totalMatches = performances.groupBy { it.matchId }.size
        val totalRuns = performances.filter { it.role == "batter" }.sumOf { it.runs }
        val totalWickets = performances.filter { it.role == "bowler" }.sumOf { it.wickets }

        binding.tvTotalMatches.text = "$totalMatches"
        binding.tvTotalRuns.text = "$totalRuns"
        binding.tvTotalWickets.text = "$totalWickets"
        binding.tvTotalPlayers.text = "$totalPlayers"
    }

    private fun hideAllProgressBars() {
        binding.pbTopScorers.visibility = View.GONE
        binding.pbTopBowlers.visibility = View.GONE
        binding.pbEconomy.visibility = View.GONE
        binding.pbFantasy.visibility = View.GONE
    }

    private fun showEmptyState() {
        // Show empty state message
        binding.tvTopScorers.text = "No data available"
        binding.tvTopBowlers.text = "No data available"
        binding.tvMilestones.text = "No milestones achieved yet"
        binding.tvBoundaryLeaders.text = "No boundary data available"
        binding.tvBestEconomy.text = "No bowling data available"
        binding.tvFantasyPoints.text = "No fantasy points data available"
        hideAllProgressBars()
    }

    private fun showErrorState(error: String) {
        binding.tvLastUpdated.text = "Error: $error"
        // You can add more detailed error handling here
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tournamentId: Int): StatsFragment {
            val fragment = StatsFragment()
            val args = Bundle()
            args.putInt("TOURNAMENT_ID", tournamentId)
            fragment.arguments = args
            return fragment
        }
    }
}

// Data classes for statistics (keep these in StatsFragment.kt)
data class TopScorer(
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val centuries: Int = 0,
    val fifties: Int = 0
)

data class TopBowler(
    val name: String,
    val wickets: Int,
    val overs: Float,
    val economy: Float,
    val bestFigures: String,
    val threePlusWickets: Int = 0
)

data class EconomyRecord(
    val name: String,
    val economy: Float,
    val wickets: Int,
    val overs: Float
)

data class FantasyPlayer(
    val name: String,
    val totalPoints: Int,
    val battingPoints: Int,
    val bowlingPoints: Int,
    val matches: Int = 0
)