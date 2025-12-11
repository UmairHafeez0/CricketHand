package com.example.handcricket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.data.Match
import com.example.handcricket.data.MatchResult
import com.example.handcricket.data.Team
import com.example.handcricket.databinding.FragmentStandingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StandingsFragment : Fragment() {

    private var _binding: FragmentStandingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StandingsAdapter
    private var tournamentId: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStandingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tournamentId = arguments?.getInt("TOURNAMENT_ID") ?: 0

        setupRecyclerView()
        loadStandings()
    }

    private fun setupRecyclerView() {
        adapter = StandingsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadStandings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val teams = db.tournamentDao().getTeams(tournamentId)
            val matches = db.tournamentDao().getMatches(tournamentId)
            val matchResults = db.tournamentDao().getMatchResults(tournamentId)

            // Calculate standings
            val standings = calculateStandings(teams, matches, matchResults)

            withContext(Dispatchers.Main) {
                adapter.submitList(standings)
                // Update group info text
                binding.tvGroupInfo.text = getGroupInfo(teams)
            }
        }
    }

    private fun getGroupInfo(teams: List<Team>): String {
        val groups = teams.mapNotNull { it.groupName }.distinct()
        return if (groups.isEmpty()) {
            "All Teams"
        } else {
            "Groups: ${groups.joinToString(", ")}"
        }
    }

    private fun calculateStandings(
        teams: List<Team>,
        matches: List<Match>,
        matchResults: List<MatchResult>
    ): List<StandingItem> {
        val standingsMap = mutableMapOf<Int, TeamStanding>()

        // Initialize standings for each team
        teams.forEach { team ->
            standingsMap[team.id] = TeamStanding(
                teamId = team.id,
                teamName = team.name,
                group = team.groupName ?: ""
            )
        }

        // Process each match result
        matchResults.forEach { result ->
            val team1Standing = standingsMap[result.team1Id]
            val team2Standing = standingsMap[result.team2Id]

            if (team1Standing != null && team2Standing != null) {
                // Parse scores to extract runs and overs
                val (team1Runs, team1Overs) = parseScore(result.team1Score)
                val (team2Runs, team2Overs) = parseScore(result.team2Score)

                // Update matches count
                team1Standing.matches++
                team2Standing.matches++

                // Update runs and overs for NRR calculation
                team1Standing.totalRunsFor += team1Runs
                team1Standing.totalOversFor += team1Overs
                team1Standing.totalRunsAgainst += team2Runs
                team1Standing.totalOversAgainst += team2Overs

                team2Standing.totalRunsFor += team2Runs
                team2Standing.totalOversFor += team2Overs
                team2Standing.totalRunsAgainst += team1Runs
                team2Standing.totalOversAgainst += team1Overs

                // Update wins and points
                if (result.winnerTeamId == result.team1Id) {
                    team1Standing.wins++
                    team1Standing.points += 2
                    team2Standing.losses++
                } else if (result.winnerTeamId == result.team2Id) {
                    team2Standing.wins++
                    team2Standing.points += 2
                    team1Standing.losses++
                } else {
                    // Draw/Tie - both get 1 point
                    team1Standing.points += 1
                    team2Standing.points += 1
                }
            }
        }

        // Convert to StandingItem list with calculated NRR
        return standingsMap.values.map { standing ->
            StandingItem(
                teamName = standing.teamName,
                group = standing.group,
                matches = standing.matches,
                wins = standing.wins,
                losses = standing.losses,
                points = standing.points,
                nrr = calculateNRR(standing)
            )
        }.sortedWith(compareByDescending<StandingItem> { it.points }
            .thenByDescending { it.nrr })
    }

    private fun parseScore(score: String): Pair<Int, Float> {
        try {
            // Score format: "186/10 (9.1)" or "188/3 (9.0)"
            val runsPart = score.substringBefore(" ")
            val oversPart = score.substringAfter("(").substringBefore(")")

            val runs = runsPart.substringBefore("/").toInt()
            val overs = oversPart.toFloatOrNull() ?: 0f

            return Pair(runs, overs)
        } catch (e: Exception) {
            return Pair(0, 0f)
        }
    }

    private fun calculateNRR(standing: TeamStanding): Double {
        if (standing.totalOversFor == 0f || standing.totalOversAgainst == 0f) {
            return 0.0
        }

        val runRateFor = standing.totalRunsFor.toDouble() / standing.totalOversFor
        val runRateAgainst = standing.totalRunsAgainst.toDouble() / standing.totalOversAgainst

        return runRateFor - runRateAgainst
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tournamentId: Int): StandingsFragment {
            val fragment = StandingsFragment()
            val args = Bundle()
            args.putInt("TOURNAMENT_ID", tournamentId)
            fragment.arguments = args
            return fragment
        }
    }
}

data class StandingItem(
    val teamName: String,
    val group: String,
    val matches: Int,
    val wins: Int,
    val losses: Int,
    val points: Int,
    val nrr: Double
)

// Helper class for calculating standings
private data class TeamStanding(
    val teamId: Int,
    val teamName: String,
    val group: String,
    var matches: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var points: Int = 0,
    var totalRunsFor: Int = 0,
    var totalOversFor: Float = 0f,
    var totalRunsAgainst: Int = 0,
    var totalOversAgainst: Float = 0f
)