package com.example.handcricket


import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.FragmentStatsBinding
import com.example.handcricket.databinding.ItemStatCategoryBinding
import com.example.handcricket.databinding.ItemTopPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

data class TeamStats(
    val name: String,
    var matches: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var runsFor: Int = 0,
    var oversFaced: Double = 0.0,
    var runsAgainst: Int = 0,
    var oversBowled: Double = 0.0,
    var nrr: Double = 0.0
)

data class PlayerStats(
    val name: String,
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var highestScore: Int = 0,
    var centuries: Int = 0,
    var halfCenturies: Int = 0,
    var wickets: Int = 0,
    var overs: Double = 0.0,
    var runsGiven: Int = 0,
    var bestBowlingWickets: Int = 0,
    var bestBowlingRuns: Int = 0,
    var matches: Int = 0,
    var strikeRate: Double = 0.0,
    var battingAverage: Double = 0.0,
    var bowlingAverage: Double = 0.0,
    var economy: Double = 0.0,
    var fantasyPoints: Int = 0
)

data class MatchPerformance(
    val player: String,
    val matchId: Int,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val overs: Double,
    val runsConceded: Int
)

data class StatCategory(
    val title: String,
    val description: String,
    val topPlayers: List<TopPlayer>
)

data class TopPlayer(
    val name: String,
    val value: String,
    val details: String
)

class StatsFragment : Fragment() {
    companion object {
        fun newInstance(files: List<Uri>, useSample: Boolean = false): StatsFragment {
            return StatsFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList("files", ArrayList(files))
                    putBoolean("useSample", useSample)
                }
            }
        }
    }

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StatsAdapter

    private val teamMap = mapOf(
        "Saim" to "Pakistan",
        "Virat" to "India",
        "Kohli" to "India",
        "David Warner" to "Australia",
        "Jos Buttler" to "England",
        "Quinton" to "South Africa",
        "Finn Allen" to "New Zealand"
    )

    private val teams = mutableMapOf<String, TeamStats>()
    private val players = mutableMapOf<String, PlayerStats>()
    private val matchPerformances = mutableListOf<MatchPerformance>()
    private val statCategories = mutableListOf<StatCategory>()

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

        binding.progressBar.visibility = View.VISIBLE

        adapter = StatsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        GlobalScope.launch(Dispatchers.IO) {
            val files = arguments?.getParcelableArrayList<Uri>("files") ?: emptyList()
            val useSample = arguments?.getBoolean("useSample") ?: false

            if (useSample) {
                loadSampleData()
            } else if (files.isNotEmpty()) {
                processCSVFiles(files)
            }

            calculateStatistics()

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                updateUI()
            }
        }
    }

    private suspend fun processCSVFiles(files: List<Uri>) {
        var matchId = 0

        files.forEach { uri ->
            matchId++
            try {
                val content = readUriContent(requireContext(), uri)
                processMatch(content, matchId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun readUriContent(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readText()
        } ?: ""
    }

    private fun processMatch(content: String, matchId: Int) {
        val lines = content.trim().split("\n")

        // Extract team names
        val team1Index = lines.indexOfFirst { "Batting" in it && "Computer Batting" !in it }
        val team2Index = lines.indexOfFirst { "Computer Batting" in it }

        val team1Name = detectTeamFromInnings(lines, team1Index)
        val team2Name = detectTeamFromInnings(lines, team2Index)

        addTeam(team1Name)
        addTeam(team2Name)

        // Extract scores
        val team1Runs = extractRuns(lines[team1Index])
        val team2Runs = extractRuns(lines[team2Index])
        val team1Overs = extractOvers(lines[team1Index])
        val team2Overs = extractOvers(lines[team2Index])

        // Determine winner
        val winLine = lines.firstOrNull { "won the game" in it } ?: ""
        val (winner, loser) = if ("Computer won" in winLine) {
            Pair(team2Name, team1Name)
        } else {
            Pair(team1Name, team2Name)
        }

        // Update team stats
        teams[team1Name]?.let { team ->
            team.matches++
            if (winner == team1Name) team.wins++ else team.losses++
            team.runsFor += team1Runs
            team.oversFaced += team1Overs
            team.runsAgainst += team2Runs
            team.oversBowled += team2Overs
        }

        teams[team2Name]?.let { team ->
            team.matches++
            if (winner == team2Name) team.wins++ else team.losses++
            team.runsFor += team2Runs
            team.oversFaced += team2Overs
            team.runsAgainst += team1Runs
            team.oversBowled += team1Overs
        }

        // Process player batting
        processBattingStats(lines, team1Index + 2, matchId)
        processBattingStats(lines, team2Index + 2, matchId)

        // Process bowling stats
        val bowlIndices = lines.mapIndexedNotNull { index, line ->
            if ("Bowler ID,Bowler Name,Overs,Runs,Wickets,Economy" in line) index else null
        }

        bowlIndices.forEach { headerIndex ->
            for (j in headerIndex + 1..headerIndex + 6) {
                if (j >= lines.size) break
                val row = lines[j].split(",")
                if (row.size >= 5) {
                    val name = row[1].trim()
                    val overs = row[2].toDoubleOrNull() ?: 0.0
                    val runsConceded = row[3].toIntOrNull() ?: 0
                    val wickets = row[4].toIntOrNull() ?: 0

                    updatePlayer(
                        name = name,
                        runs = 0,
                        balls = 0,
                        fours = 0,
                        sixes = 0,
                        wickets = wickets,
                        overs = overs,
                        runsConceded = runsConceded,
                        matchId = matchId
                    )
                }
            }
        }
    }

    private fun processBattingStats(lines: List<String>, startIndex: Int, matchId: Int) {
        for (i in startIndex until startIndex + 11) {
            if (i >= lines.size) break
            val row = lines[i].split(",")
            if (row.size < 6) continue

            val name = row[1].split("(")[0].trim()
            val runs = row[2].toIntOrNull() ?: 0
            val balls = row[3].toIntOrNull() ?: 0
            val fours = row[4].toIntOrNull() ?: 0
            val sixes = row[5].toIntOrNull() ?: 0

            updatePlayer(
                name = name,
                runs = runs,
                balls = balls,
                fours = fours,
                sixes = sixes,
                matchId = matchId
            )
        }
    }

    private fun detectTeamFromInnings(lines: List<String>, start: Int): String {
        val block = lines.subList(start, minOf(start + 60, lines.size))
            .joinToString("\n").lowercase()

        teamMap.forEach { (key, team) ->
            if (key.lowercase() in block) return team
        }
        return "Computer"
    }

    private fun addTeam(name: String) {
        if (!teams.containsKey(name)) {
            teams[name] = TeamStats(name)
        }
    }

    private fun updatePlayer(
        name: String,
        runs: Int,
        balls: Int,
        fours: Int,
        sixes: Int,
        wickets: Int = 0,
        overs: Double = 0.0,
        runsConceded: Int = 0,
        matchId: Int? = null
    ) {
        var player = players[name]
        if (player == null) {
            player = PlayerStats(name)
            players[name] = player
        }

        // Update batting stats
        player.runs += runs
        player.balls += balls
        player.fours += fours
        player.sixes += sixes

        if (runs > player.highestScore) {
            player.highestScore = runs
        }
        if (runs >= 100) {
            player.centuries++
        } else if (runs >= 50) {
            player.halfCenturies++
        }

        // Update bowling stats
        player.wickets += wickets
        player.overs += overs
        player.runsGiven += runsConceded

        if (wickets > 0) {
            if (wickets > player.bestBowlingWickets ||
                (wickets == player.bestBowlingWickets && runsConceded < player.bestBowlingRuns)) {
                player.bestBowlingWickets = wickets
                player.bestBowlingRuns = runsConceded
            }
        }

        player.matches++

        // Store match performance
        if (matchId != null) {
            matchPerformances.add(
                MatchPerformance(name, matchId, runs, balls, fours, sixes, wickets, overs, runsConceded)
            )
        }
    }

    private fun calculateStatistics() {
        // Calculate NRR for teams
        teams.values.forEach { team ->
            if (team.oversFaced > 0 && team.oversBowled > 0) {
                team.nrr = (team.runsFor / team.oversFaced) - (team.runsAgainst / team.oversBowled)
            }
        }

        // Calculate player stats
        players.values.forEach { player ->
            // Batting stats
            player.strikeRate = if (player.balls > 0) {
                (player.runs.toDouble() / player.balls * 100)
            } else 0.0

            player.battingAverage = if (player.balls > 0) {
                player.runs.toDouble() / (player.balls / 6.0)
            } else 0.0

            // Bowling stats
            player.bowlingAverage = if (player.wickets > 0) {
                player.runsGiven.toDouble() / player.wickets
            } else 0.0

            player.economy = if (player.overs > 0) {
                player.runsGiven.toDouble() / player.overs
            } else 0.0

            // Fantasy points
            player.fantasyPoints = calculateFantasyPoints(player)
        }

        createStatCategories()
    }

    private fun calculateFantasyPoints(player: PlayerStats): Int {
        var points = 0

        // Batting points
        points += player.runs  // 1 point per run
        points += player.fours * 1  // 1 point per four
        points += player.sixes * 2  // 2 points per six
        points += player.halfCenturies * 8  // 8 points per 50
        points += player.centuries * 16  // 16 points per 100

        // Bowling points
        points += player.wickets * 25  // 25 points per wicket

        // Economy bonus/penalty
        if (player.overs > 0) {
            val economy = player.runsGiven / player.overs
            points += when {
                economy < 6 -> 20
                economy < 8 -> 10
                economy > 10 -> -10
                else -> 0
            }
        }

        return points
    }

    private fun createStatCategories() {
        statCategories.clear()

        // 1. Top Run Scorers
        val topScorers = players.values
            .sortedByDescending { it.runs }
            .take(6)
            .map { TopPlayer(it.name, "${it.runs} runs", "${it.balls} balls, SR: ${"%.2f".format(it.strikeRate)}") }

        statCategories.add(
            StatCategory("🏆 Top Run Scorers", "Most runs in tournament", topScorers)
        )

        // 2. Most Centuries
        val topCenturies = players.values
            .sortedByDescending { it.centuries }
            .take(6)
            .map { TopPlayer(it.name, "${it.centuries} centuries", "HS: ${it.highestScore}, Runs: ${it.runs}") }

        statCategories.add(
            StatCategory("💯 Most Centuries", "Players with most hundreds", topCenturies)
        )

        // 3. Most Wickets
        val topWickets = players.values
            .filter { it.wickets > 0 }
            .sortedByDescending { it.wickets }
            .take(6)
            .map { TopPlayer(it.name, "${it.wickets} wickets", "Best: ${it.bestBowlingWickets}/${it.bestBowlingRuns}") }

        statCategories.add(
            StatCategory("🎯 Most Wickets", "Highest wicket-takers", topWickets)
        )

        // 4. Best Strike Rate
        val topStrikeRate = players.values
            .filter { it.balls >= 30 }
            .sortedByDescending { it.strikeRate }
            .take(6)
            .map { TopPlayer(it.name, "SR: ${"%.2f".format(it.strikeRate)}", "${it.runs} runs in ${it.balls} balls") }

        statCategories.add(
            StatCategory("⚡ Best Strike Rate", "Min 30 balls faced", topStrikeRate)
        )

        // 5. Most Sixes
        val topSixes = players.values
            .sortedByDescending { it.sixes }
            .take(6)
            .map { TopPlayer(it.name, "${it.sixes} sixes", "${it.runs} runs, ${it.balls} balls") }

        statCategories.add(
            StatCategory("💥 Most Sixes", "Big hitters of the tournament", topSixes)
        )

        // 6. Fantasy Points Leaders
        val fantasyLeaders = players.values
            .sortedByDescending { it.fantasyPoints }
            .take(6)
            .map { TopPlayer(it.name, "${it.fantasyPoints} pts", "${it.runs}R/${it.wickets}W") }

        statCategories.add(
            StatCategory("🏅 Fantasy Points", "Top fantasy performers", fantasyLeaders)
        )

        // 7. Team Standings
        val sortedTeams = teams.values
            .sortedWith(compareByDescending<TeamStats> { it.wins }.thenByDescending { it.nrr })
            .take(6)

        val teamStandings = sortedTeams.mapIndexed { index, team ->
            TopPlayer(
                "${index + 1}. ${team.name}",
                "${team.wins}W/${team.losses}L",
                "NRR: ${"%.3f".format(team.nrr)}"
            )
        }

        statCategories.add(
            StatCategory("📊 Team Standings", "Tournament rankings", teamStandings)
        )
    }

    private fun updateUI() {
        adapter.submitList(statCategories)

        // Show total stats summary
        binding.tvSummary.text = buildString {
            append("📈 Tournament Summary\n\n")
            append("• ${teams.size} Teams\n")
            append("• ${players.size} Players\n")
            append("• ${matchPerformances.size / 22} Matches\n")

            val topRunScorer = players.values.maxByOrNull { it.runs }
            val topWicketTaker = players.values.maxByOrNull { it.wickets }

            append("\n🏆 Top Performer:\n")
            append("  Runs: ${topRunScorer?.name} (${topRunScorer?.runs})\n")
            append("  Wickets: ${topWicketTaker?.name} (${topWicketTaker?.wickets})")
        }
    }

    private fun loadSampleData() {
        // Create sample data for demonstration
        addSampleTeams()
        addSamplePlayers()
    }

    private fun addSampleTeams() {
        val sampleTeams = listOf(
            TeamStats("India", 5, 4, 1, 1250, 48.2, 980, 50.0, 1.25),
            TeamStats("Australia", 5, 3, 2, 1100, 49.5, 1050, 49.0, 0.85),
            TeamStats("England", 5, 3, 2, 1150, 47.8, 1080, 48.5, 0.92),
            TeamStats("Pakistan", 5, 2, 3, 980, 46.5, 1020, 47.0, -0.15)
        )

        sampleTeams.forEach { team ->
            teams[team.name] = team
        }
    }

    private fun addSamplePlayers() {
        val samplePlayers = listOf(
            PlayerStats("Virat Kohli", 450, 300, 45, 12, 132, 1, 3, 2, 10.0, 85, 2, 35, 5),
            PlayerStats("David Warner", 380, 250, 38, 15, 128, 1, 2, 0, 0.0, 0, 0, 0, 5),
            PlayerStats("Jos Buttler", 320, 180, 28, 18, 110, 1, 2, 0, 0.0, 0, 0, 0, 5),
            PlayerStats("Saim Ayub", 280, 200, 32, 8, 95, 0, 2, 5, 20.0, 150, 3, 25, 5),
            PlayerStats("Jasprit Bumrah", 30, 20, 4, 1, 25, 0, 0, 15, 25.0, 180, 5, 22, 5),
            PlayerStats("Pat Cummins", 45, 35, 5, 2, 28, 0, 0, 12, 22.0, 165, 4, 18, 5)
        )

        samplePlayers.forEach { player ->
            players[player.name] = player
        }
    }

    private fun extractRuns(line: String): Int {
        val pattern = """(\d+)\s*/""".toRegex()
        return pattern.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractOvers(line: String): Double {
        val pattern = """\((\d+(?:\.\d+)?)\)""".toRegex()
        return pattern.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class StatsAdapter : RecyclerView.Adapter<StatsAdapter.ViewHolder>() {
    private val items = mutableListOf<StatCategory>()

    fun submitList(newItems: List<StatCategory>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemStatCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: StatCategory) {
            binding.tvCategoryTitle.text = category.title
            binding.tvCategoryDescription.text = category.description

            // Clear previous views
            binding.playersLayout.removeAllViews()

            // Add player items
            category.topPlayers.forEach { player ->
                val playerView = LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.item_top_player, binding.playersLayout, false)

                val playerBinding = ItemTopPlayerBinding.bind(playerView)
                playerBinding.tvPlayerName.text = player.name
                playerBinding.tvPlayerValue.text = player.value
                playerBinding.tvPlayerDetails.text = player.details

                binding.playersLayout.addView(playerView)
            }
        }
    }
}