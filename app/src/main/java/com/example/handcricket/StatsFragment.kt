// StatsFragment.kt (Complete implementation)
package com.example.handcricket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.databinding.FragmentStatsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnTopScorers.setOnClickListener { showTopScorers() }
        binding.btnTopBowlers.setOnClickListener { showTopBowlers() }
        binding.btnCenturies.setOnClickListener { showCenturies() }
        binding.btnFifties.setOnClickListener { showFifties() }
        binding.btnMostFours.setOnClickListener { showMostFours() }
        binding.btnMostSixes.setOnClickListener { showMostSixes() }
        binding.btnBestEconomy.setOnClickListener { showBestEconomy() }
        binding.btnFantasyPoints.setOnClickListener { showFantasyPoints() }
    }

    private fun showTopScorers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val topScorers = performances
                .filter { it.role == "batter" }
                .groupBy { it.playerName }
                .map { entry ->
                    val totalRuns = entry.value.sumOf { it.runs }
                    val totalBalls = entry.value.sumOf { it.balls }
                    val strikeRate = if (totalBalls > 0) (totalRuns.toFloat() / totalBalls * 100) else 0f
                    TopScorer(
                        entry.key,
                        totalRuns,
                        totalBalls,
                        entry.value.sumOf { it.fours },
                        entry.value.sumOf { it.sixes },
                        strikeRate,
                        entry.value.filter { it.runs >= 100 }.size, // centuries
                        entry.value.filter { it.runs >= 50 && it.runs < 100 }.size // fifties
                    )
                }
                .sortedByDescending { it.runs }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("🏆 TOP 10 SCORERS 🏆\n\n")
                    topScorers.forEachIndexed { index, scorer ->
                        append("${index + 1}. ${scorer.name}: ${scorer.runs} runs\n")
                        append("   SR: ${"%.2f".format(scorer.strikeRate)}")
                        append(", 4s: ${scorer.fours}")
                        append(", 6s: ${scorer.sixes}")
                        append(", 💯: ${scorer.centuries}")
                        append(", 50: ${scorer.fifties}\n\n")
                    }
                }
            }
        }
    }

    // Updated showBestEconomy method:
    private fun showBestEconomy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val bestEconomy = performances
                .filter { it.role == "bowler" && it.overs >= 5f } // Min 5 overs bowled
                .groupBy { it.playerName }
                .map { entry ->
                    val totalOvers = entry.value.sumOf { it.overs.toDouble() }.toFloat()
                    val totalRuns = entry.value.sumOf { it.runsConceded }
                    val economy = if (totalOvers > 0) totalRuns.toFloat() / totalOvers else 0f
                    val wickets = entry.value.sumOf { it.wickets }

                    EconomyRecord(
                        entry.key,
                        economy,
                        wickets,
                        totalOvers
                    )
                }
                .sortedBy { it.economy } // Lower is better
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("💰 BEST ECONOMY 💰\n\n")
                    if (bestEconomy.isEmpty()) {
                        append("No bowlers with sufficient overs\n")
                    } else {
                        bestEconomy.forEachIndexed { index, record ->
                            append("${index + 1}. ${record.name}: ${"%.2f".format(record.economy)} economy\n")
                            append("   Wickets: ${record.wickets}")
                            append(", Overs: ${"%.1f".format(record.overs)}\n\n")
                        }
                    }
                }
            }
        }
    }

    // Updated showTopBowlers method:
    private fun showTopBowlers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val topBowlers = performances
                .filter { it.role == "bowler" && it.overs > 0f }
                .groupBy { it.playerName }
                .map { entry ->
                    val totalWickets = entry.value.sumOf { it.wickets }
                    val totalOvers = entry.value.sumOf { it.overs.toDouble() }.toFloat()
                    val totalRuns = entry.value.sumOf { it.runsConceded }
                    val economy = if (totalOvers > 0) totalRuns.toFloat() / totalOvers else 0f
                    val bestFigures = entry.value.maxByOrNull { it.wickets }?.let {
                        "${it.wickets}/${it.runsConceded}"
                    } ?: "0/0"

                    TopBowler(
                        entry.key,
                        totalWickets,
                        totalOvers,
                        economy,
                        bestFigures,
                        entry.value.filter { it.wickets >= 3 }.size // 3+ wicket hauls
                    )
                }
                .sortedByDescending { it.wickets }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("🎯 TOP 10 BOWLERS 🎯\n\n")
                    topBowlers.forEachIndexed { index, bowler ->
                        append("${index + 1}. ${bowler.name}: ${bowler.wickets} wickets\n")
                        append("   Economy: ${"%.2f".format(bowler.economy)}")
                        append(", Overs: ${"%.1f".format(bowler.overs)}")
                        append(", Best: ${bowler.bestFigures}")
                        append(", 3+: ${bowler.threePlusWickets}\n\n")
                    }
                }
            }
        }
    }


    private fun showCenturies() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val centuries = performances
                .filter { it.role == "batter" && it.runs >= 100 }
                .groupBy { it.playerName }
                .map { entry ->
                    val centuryList = entry.value.filter { it.runs >= 100 }
                    CenturyRecord(
                        entry.key,
                        centuryList.size,
                        centuryList.maxOfOrNull { it.runs } ?: 0,
                        centuryList.sumOf { it.runs },
                        centuryList.sumOf { it.sixes },
                        centuryList.sumOf { it.fours }
                    )
                }
                .sortedByDescending { it.count }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("💯 CENTURY MAKERS 💯\n\n")
                    if (centuries.isEmpty()) {
                        append("No centuries scored yet\n")
                    } else {
                        centuries.forEachIndexed { index, century ->
                            append("${index + 1}. ${century.name}: ${century.count} centuries\n")
                            append("   Highest: ${century.highest}")
                            append(", Total: ${century.totalRuns}")
                            append(", 6s: ${century.sixes}")
                            append(", 4s: ${century.fours}\n\n")
                        }
                    }
                }
            }
        }
    }

    private fun showFifties() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val fifties = performances
                .filter { it.role == "batter" && it.runs >= 50 && it.runs < 100 }
                .groupBy { it.playerName }
                .map { entry ->
                    val fiftyList = entry.value.filter { it.runs >= 50 && it.runs < 100 }
                    FiftyRecord(
                        entry.key,
                        fiftyList.size,
                        fiftyList.maxOfOrNull { it.runs } ?: 0,
                        fiftyList.sumOf { it.runs }
                    )
                }
                .sortedByDescending { it.count }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("🔥 HALF-CENTURIES 🔥\n\n")
                    if (fifties.isEmpty()) {
                        append("No half-centuries scored yet\n")
                    } else {
                        fifties.forEachIndexed { index, fifty ->
                            append("${index + 1}. ${fifty.name}: ${fifty.count} fifties\n")
                            append("   Highest: ${fifty.highest}")
                            append(", Total: ${fifty.totalRuns}\n\n")
                        }
                    }
                }
            }
        }
    }

    private fun showMostFours() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val mostFours = performances
                .filter { it.role == "batter" }
                .groupBy { it.playerName }
                .map { entry ->
                    val totalFours = entry.value.sumOf { it.fours }
                    val totalRuns = entry.value.sumOf { it.runs }
                    val percentage = if (totalRuns > 0) (totalFours * 4 * 100f / totalRuns) else 0f

                    FourRecord(
                        entry.key,
                        totalFours,
                        totalRuns,
                        percentage
                    )
                }
                .sortedByDescending { it.fours }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("4️⃣ MOST FOURS 4️⃣\n\n")
                    mostFours.forEachIndexed { index, record ->
                        append("${index + 1}. ${record.name}: ${record.fours} fours\n")
                        append("   Runs: ${record.runs}")
                        append(", % from 4s: ${"%.1f".format(record.percentageFromFours)}%\n\n")
                    }
                }
            }
        }
    }

    private fun showMostSixes() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val mostSixes = performances
                .filter { it.role == "batter" }
                .groupBy { it.playerName }
                .map { entry ->
                    val totalSixes = entry.value.sumOf { it.sixes }
                    val totalRuns = entry.value.sumOf { it.runs }
                    val percentage = if (totalRuns > 0) (totalSixes * 6 * 100f / totalRuns) else 0f

                    SixRecord(
                        entry.key,
                        totalSixes,
                        totalRuns,
                        percentage
                    )
                }
                .sortedByDescending { it.sixes }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("6️⃣ MOST SIXES 6️⃣\n\n")
                    mostSixes.forEachIndexed { index, record ->
                        append("${index + 1}. ${record.name}: ${record.sixes} sixes\n")
                        append("   Runs: ${record.runs}")
                        append(", % from 6s: ${"%.1f".format(record.percentageFromSixes)}%\n\n")
                    }
                }
            }
        }
    }


    private fun showFantasyPoints() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val performances = db.tournamentDao().getPlayerPerformances(tournamentId)

            val fantasyPoints = performances
                .groupBy { it.playerName }
                .map { entry ->
                    val batterPerf = entry.value.filter { it.role == "batter" }
                    val bowlerPerf = entry.value.filter { it.role == "bowler" }

                    // Batting points calculation
                    val battingPoints = batterPerf.sumOf { perf ->
                        var points = 0
                        // Basic runs
                        points += perf.runs
                        // Boundary bonuses
                        points += perf.fours * 1
                        points += perf.sixes * 2
                        // Milestone bonuses
                        points += when {
                            perf.runs >= 100 -> 50  // Century bonus
                            perf.runs >= 50 -> 30   // Half-century bonus
                            perf.runs >= 30 -> 10   // 30+ runs bonus
                            else -> 0
                        }
                        // Strike rate bonus
                        if (perf.balls >= 10) {
                            val sr = (perf.runs.toFloat() / perf.balls) * 100
                            points += when {
                                sr >= 200 -> 20
                                sr >= 150 -> 15
                                sr >= 120 -> 10
                                else -> 0
                            }
                        }
                        points
                    }

                    // Bowling points calculation
                    val bowlingPoints = bowlerPerf.sumOf { perf ->
                        var points = 0
                        // Wicket points
                        points += perf.wickets * 25
                        // 3+ wicket haul bonus
                        if (perf.wickets >= 3) points += 20
                        if (perf.wickets >= 5) points += 30  // 5-wicket haul bonus
                        // Economy bonus
                        if (perf.overs >= 2) {
                            val economy = if (perf.overs > 0) perf.runsConceded / perf.overs else 0f
                            points += when {
                                economy < 6 -> 25
                                economy < 8 -> 15
                                economy < 10 -> 10
                                else -> 0
                            }
                        }
                        points
                    }

                    FantasyPlayer(
                        entry.key,
                        battingPoints + bowlingPoints,
                        battingPoints,
                        bowlingPoints,
                        batterPerf.size + bowlerPerf.size  // matches played
                    )
                }
                .sortedByDescending { it.totalPoints }
                .take(10)

            withContext(Dispatchers.Main) {
                binding.tvStatsDisplay.text = buildString {
                    append("⭐ FANTASY POINTS ⭐\n\n")
                    fantasyPoints.forEachIndexed { index, player ->
                        append("${index + 1}. ${player.name}: ${player.totalPoints} pts\n")
                        append("   Batting: ${player.battingPoints}")
                        append(", Bowling: ${player.bowlingPoints}")
                        append(", Matches: ${player.matches}\n\n")
                    }
                }
            }
        }
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

// Data classes for statistics
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

data class CenturyRecord(
    val name: String,
    val count: Int,
    val highest: Int,
    val totalRuns: Int,
    val sixes: Int,
    val fours: Int
)

data class FiftyRecord(
    val name: String,
    val count: Int,
    val highest: Int,
    val totalRuns: Int
)

data class FourRecord(
    val name: String,
    val fours: Int,
    val runs: Int,
    val percentageFromFours: Float
)

data class SixRecord(
    val name: String,
    val sixes: Int,
    val runs: Int,
    val percentageFromSixes: Float
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