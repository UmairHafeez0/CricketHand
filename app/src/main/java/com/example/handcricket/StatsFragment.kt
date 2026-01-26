package com.example.handcricket

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
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

data class TeamStats(
    val name: String,
    var matches: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var runsFor: Int = 0,
    var oversFaced: Double = 0.0,
    var wicketsLost: Int = 0,
    var wicketsTaken: Int = 0,
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
    val topPlayers: List<TopPlayer>,           // Limited list for main view
    val allPlayers: List<TopPlayer>            // Full list for detailed view
)
@SuppressLint("ParcelCreator")
data class TopPlayer(
    val name: String,
    val value: String,
    val details: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(value)
        parcel.writeString(details)
    }

    companion object CREATOR : Parcelable.Creator<TopPlayer> {
        override fun createFromParcel(parcel: Parcel): TopPlayer {
            return TopPlayer(parcel)
        }

        override fun newArray(size: Int): Array<TopPlayer?> {
            return arrayOfNulls(size)
        }
    }
}
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

    private var teamMap = mutableMapOf(
        "Saim" to "Pakistan",
        "Virat" to "India",
        "Kohli" to "India",
        "David Warner" to "Australia",
        "Jos Buttler" to "England",
        "Quinton" to "South Africa",
        "Finn Allen" to "New Zealand"
    )

    private fun loadTeamMapFromPreferences(): Map<String, String> {
        val context = requireContext()
        val sharedPrefs = context.getSharedPreferences("TeamMappings", Context.MODE_PRIVATE)
        val savedData = sharedPrefs.getString("team_player_data", null)

        return if (savedData != null && savedData.isNotEmpty()) {
            // Parse the saved data
            teamMap = mutableMapOf<String, String>()

            savedData.split(";;").forEach { teamData ->
                val parts = teamData.split("::")
                if (parts.size == 2) {
                    val team = parts[0].trim()
                    val playersText = parts[1].trim()

                    if (playersText.isNotEmpty()) {
                        playersText.split(",").forEach { player ->
                            val trimmedPlayer = player.trim().lowercase()
                            if (trimmedPlayer.isNotEmpty()) {
                                teamMap[trimmedPlayer] = team
                            }
                        }
                    }
                }
            }

            teamMap
        } else {
            // Load default teamMap
            mapOf(
                // 🇵🇰 Pakistan
                "babar" to "Pakistan",
                "rizwan" to "Pakistan",
                "saim" to "Pakistan",
                "iftikhar" to "Pakistan",
                "shaheen" to "Pakistan",
                "shadab" to "Pakistan",
                "fakhar" to "Pakistan",
                "haris" to "Pakistan",
                "naseem" to "Pakistan",
                "imad" to "Pakistan",
                "mohammad" to "Pakistan",
                "nawaz" to "Pakistan",
                "azam" to "Pakistan",
                "afridi" to "Pakistan",
                "hassan" to "Pakistan",

                // 🇮🇳 India
                "virat" to "India",
                "kohli" to "India",
                "rohit" to "India",
                "gill" to "India",
                "surya" to "India",
                "hardik" to "India",
                "rahul" to "India",
                "pandya" to "India",
                "jadeja" to "India",
                "bumrah" to "India",
                "shami" to "India",
                "ishan" to "India",
                "pant" to "India",
                "ashwin" to "India",
                "chahal" to "India",

                // 🇦🇺 Australia
                "warner" to "Australia",
                "smith" to "Australia",
                "maxwell" to "Australia",
                "head" to "Australia",
                "marsh" to "Australia",
                "stoinis" to "Australia",
                "cummins" to "Australia",
                "starc" to "Australia",
                "hazlewood" to "Australia",
                "zampa" to "Australia",
                "wade" to "Australia",
                "carey" to "Australia",
                "agar" to "Australia",
                "green" to "Australia",
                "finch" to "Australia",

                // 🏴 England
                "buttler" to "England",
                "bairstow" to "England",
                "malan" to "England",
                "livingstone" to "England",
                "brook" to "England",
                "stokes" to "England",
                "moeen" to "England",
                "root" to "England",
                "archer" to "England",
                "wood" to "England",
                "rashid" to "England",
                "mills" to "England",
                "salt" to "England",
                "curran" to "England",
                "ali" to "England",

                // 🇿🇦 South Africa
                "quinton" to "South Africa",
                "de kock" to "South Africa",
                "markram" to "South Africa",
                "klaasen" to "South Africa",
                "miller" to "South Africa",
                "rabada" to "South Africa",
                "bavuma" to "South Africa",
                "verreynne" to "South Africa",
                "nortje" to "South Africa",
                "shamsi" to "South Africa",
                "jansen" to "South Africa",
                "ngidi" to "South Africa",
                "peter sen" to "South Africa",
                "ricks" to "South Africa",
                "brevis" to "South Africa",

                // 🇳🇿 New Zealand
                "finn" to "New Zealand",
                "allen" to "New Zealand",
                "kane" to "New Zealand",
                "williamson" to "New Zealand",
                "conway" to "New Zealand",
                "mitchell" to "New Zealand",
                "southee" to "New Zealand",
                "boult" to "New Zealand",
                "santner" to "New Zealand",
                "phillips" to "New Zealand",
                "chapman" to "New Zealand",
                "young" to "New Zealand",
                "blundell" to "New Zealand",
                "ferguson" to "New Zealand",
                "neesham" to "New Zealand",

                // 🏝 West Indies
                "poor an" to "West Indies",
                "powell" to "West Indies",
                "king" to "West Indies",
                "hope" to "West Indies",
                "holder" to "West Indies",
                "shepherd" to "West Indies",
                "russell" to "West Indies",
                "bravo" to "West Indies",
                "narine" to "West Indies",
                "gayle" to "West Indies",
                "pollard" to "West Indies",
                "lewis" to "West Indies",
                "hetmyer" to "West Indies",
                "mayers" to "West Indies",
                "cotterell" to "West Indies",

                // 🇱🇰 Sri Lanka
                "kus al" to "Sri Lanka",
                "mendis" to "Sri Lanka",
                "shanaka" to "Sri Lanka",
                "rajapaksa" to "Sri Lanka",
                "hasaranga" to "Sri Lanka",
                "as alanka" to "Sri Lanka",
                "mathews" to "Sri Lanka",
                "karunaratne" to "Sri Lanka",
                "chandimal" to "Sri Lanka",
                "perera" to "Sri Lanka",
                "fernando" to "Sri Lanka",
                "theekshana" to "Sri Lanka",
                "pathirana" to "Sri Lanka",
                "nissanka" to "Sri Lanka",
                "bandara" to "Sri Lanka",

                // 🇧🇩 Bangladesh
                "shanto" to "Bangladesh",
                "liton" to "Bangladesh",
                "sakib" to "Bangladesh",
                "mushfiq" to "Bangladesh",
                "afif" to "Bangladesh",
                "mahmudullah" to "Bangladesh",
                "tamim" to "Bangladesh",
                "mustafizur" to "Bangladesh",
                "taskin" to "Bangladesh",
                "mehidy" to "Bangladesh",
                "soumya" to "Bangladesh",
                "yasin" to "Bangladesh",
                "e badot" to "Bangladesh",
                "shoriful" to "Bangladesh",
                "mosaddek" to "Bangladesh",

                // 🇦🇫 Afghanistan
                "gurbaz" to "Afghanistan",
                "ibrahim" to "Afghanistan",
                "najib" to "Afghanistan",
                "rashid" to "Afghanistan",
                "nabi" to "Afghanistan",
                "rahmanullah" to "Afghanistan",
                "mujeeb" to "Afghanistan",
                "shahidi" to "Afghanistan",
                "noor" to "Afghanistan",
                "fazal" to "Afghanistan",
                "karim" to "Afghanistan",
                "zadran" to "Afghanistan",
                "ullah" to "Afghanistan",
                "safi" to "Afghanistan",
                "janat" to "Afghanistan",

                // 🇮🇪 Ireland
                "stirling" to "Ireland",
                "balbirnie" to "Ireland",
                "tucker" to "Ireland",
                "dockrell" to "Ireland",
                "campher" to "Ireland",
                "ad air" to "Ireland",
                "mccarthy" to "Ireland",
                "little" to "Ireland",
                "delany" to "Ireland",
                "hand" to "Ireland",
                "white" to "Ireland",
                "young" to "Ireland",
                "getkate" to "Ireland",
                "rock" to "Ireland",
                "humphreys" to "Ireland",

                // 🇿🇼 Zimbabwe
                "er vine" to "Zimbabwe",
                "kaia" to "Zimbabwe",
                "madhevere" to "Zimbabwe",
                "burl" to "Zimbabwe",
                "ryan" to "Zimbabwe",
                "myers" to "Zimbabwe",
                "williams" to "Zimbabwe",
                "chakabva" to "Zimbabwe",
                "raza" to "Zimbabwe",
                "chatara" to "Zimbabwe",
                "muzarabani" to "Zimbabwe",
                "marumani" to "Zimbabwe",
                "masakadza" to "Zimbabwe",
                "mavuta" to "Zimbabwe",
                "bennett" to "Zimbabwe",

                // 🇳🇱 Netherlands
                "edwards" to "Netherlands",
                "max o'dowd" to "Netherlands",
                "barresi" to "Netherlands",
                "klaassen" to "Netherlands",
                "ackermann" to "Netherlands",
                "van der merwe" to "Netherlands",
                "levitt" to "Netherlands",
                "engelbrecht" to "Netherlands",
                "de leede" to "Netherlands",
                "van beek" to "Netherlands",
                "dutt" to "Netherlands",
                "vijay" to "Netherlands",
                "klein" to "Netherlands",
                "kingma" to "Netherlands",
                "brand" to "Netherlands",

                // 🇵🇹 Scotland
                "munsey" to "Scotland",
                "berrington" to "Scotland",
                "cross" to "Scotland",
                "leask" to "Scotland",
                "greaves" to "Scotland",
                "sharif" to "Scotland",
                "wheal" to "Scotland",
                "sole" to "Scotland",
                "mcallister" to "Scotland",
                "tahir" to "Scotland",
                "jarvis" to "Scotland",
                "hain" to "Scotland",
                "machan" to "Scotland",
                "coetzer" to "Scotland",
                "evans" to "Scotland",

                // 🇦🇪 UAE (additional 16th team)
                "waseem" to "UAE",
                "kashif" to "UAE",
                "meiyappan" to "UAE",
                "sharma" to "UAE",
                "naveed" to "UAE",
                "raza" to "UAE",
                "mustafa" to "UAE",
                "lakra" to "UAE",
                "basil" to "UAE",
                "kaleem" to "UAE",
                "zaheer" to "UAE",
                "chand" to "UAE",
                "ali" to "UAE",
                "ahmed" to "UAE",
                "farooqi" to "UAE"
            )
        }
    }
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

        adapter = StatsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter


        loadTeamMapFromPreferences()
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
        var team1Overs = extractOvers(lines[team1Index])
        var team2Overs = extractOvers(lines[team2Index])
        val team1Wickets = extractWickets(lines[team2Index])
        val team2Wickets = extractWickets(lines[team2Index])

        // Determine winner
        val winLine = lines.firstOrNull { "won the game" in it } ?: ""
        val (winner, loser) = if ("Computer won" in winLine) {
            Pair(team2Name, team1Name)
        } else {
            Pair(team1Name, team2Name)
        }
        if(team1Wickets == 10)
        {
            team1Overs = 10.0
        }

        if(team2Wickets == 10)
        {
            team2Overs = 10.0
        }




        // Update team stats
        teams[team1Name]?.let { team ->
            team.matches++
            if (winner == team1Name) team.wins++ else team.losses++
            team.runsFor += team1Runs
            team.oversFaced += team1Overs
            team.runsAgainst += team2Runs
            team.oversBowled += team2Overs
            team.wicketsTaken += team2Wickets
            team.wicketsLost += team1Wickets
        }

        teams[team2Name]?.let { team ->
            team.matches++
            if (winner == team2Name) team.wins++ else team.losses++
            team.runsFor += team2Runs
            team.oversFaced += team2Overs
            team.runsAgainst += team1Runs
            team.oversBowled += team1Overs
            team.wicketsTaken += team1Wickets
            team.wicketsLost += team2Wickets
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

        val batsmen = mutableListOf<String>()
        var inBattingTable = false

        for (i in start until lines.size) {
            val line = lines[i].trim()

            // Start batting table
            if (line.startsWith("Batter ID", true)) {
                inBattingTable = true
                continue
            }

            // Stop when batting ends
            if (
                line.startsWith("Fall of Wickets", true) ||
                line.endsWith("Bowling", true)
            ) break

            if (inBattingTable && line.isNotEmpty()) {
                val parts = line.split(",")

                if (parts.size >= 2) {
                    val batterName = parts[1]
                        .substringBefore("(")   // remove bowler name
                        .lowercase()
                        .trim()

                    batsmen.add(batterName)
                }
            }
        }

        // Partial + case-insensitive match
        teamMap.forEach { (key, team) ->
            if (batsmen.any { it.contains(key.lowercase()) }) {
                return team
            }
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

        if (matchId != null) {
            player.matches++
        }

        // Store match performance
        if (matchId != null) {
            matchPerformances.add(
                MatchPerformance(name, matchId, runs, balls, fours, sixes, wickets, overs, runsConceded)
            )
        }
    }

    private fun calculateStatistics() {
        // Calculate NRR for teams - FIXED
        teams.values.forEach { team ->
            if (team.matches > 0) {
                // Calculate runs per over for batting
                val runsPerOverFor = if (team.oversFaced > 0) {
                    team.runsFor / team.oversFaced
                } else 0.0

                // Calculate runs per over against
                val runsPerOverAgainst = if (team.oversBowled > 0) {
                    team.runsAgainst / team.oversBowled
                } else 0.0

                // Calculate NRR
                team.nrr = runsPerOverFor - runsPerOverAgainst
            }
        }

        // Calculate player stats
        players.values.forEach { player ->
            // Batting stats
            player.strikeRate = if (player.balls > 0) {
                (player.runs.toDouble() / player.balls * 100)
            } else 0.0

            // Batting average (runs per dismissal - simplified)
            val dismissals = player.matches.coerceAtLeast(1) // Simplified - real cricket would need actual dismissals
            player.battingAverage = player.runs.toDouble() / dismissals

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

        // 1. Team Standings
        val sortedTeams = teams.values
            .sortedWith(compareByDescending<TeamStats> { it.wins }
                .thenByDescending { it.nrr }
                .thenByDescending { it.runsFor - it.runsAgainst })

        val allTeamStandings = sortedTeams.mapIndexed { index, team ->
            TopPlayer(
                "${index + 1}. ${team.name}",
                "${team.wins}W - ${team.losses}L",
                "Runs: ${team.runsFor}/${team.runsAgainst} • NRR: ${"%.3f".format(team.nrr)}"
            )
        }

        statCategories.add(
            StatCategory(
                "📊 Team Standings",
                "Win-Loss record with Net Run Rate",
                allTeamStandings.take(10),      // Show 10 in main view
                allTeamStandings                // All teams in detailed view
            )
        )

        // 2. Top Run Scorers
        val allTopScorers = players.values
            .filter { it.runs > 0 }
            .sortedByDescending { it.runs }
            .map {
                val avg = "%.1f".format(it.battingAverage)
                val sr = "%.1f".format(it.strikeRate)
                TopPlayer(
                    it.name,
                    "${it.runs} runs (${it.balls} balls)",
                    "Avg: $avg • SR: $sr • HS: ${it.highestScore}"
                )
            }

        statCategories.add(
            StatCategory(
                "🏆 Top Run Scorers",
                "Most runs in tournament",
                allTopScorers.take(8),          // Show 8 in main view
                allTopScorers                   // All players in detailed view
            )
        )

        // 3. Most Centuries & Half-Centuries
        val allCenturyMakers = players.values
            .filter { it.centuries + it.halfCenturies > 0 }
            .sortedWith(compareByDescending<PlayerStats> { it.centuries }
                .thenByDescending { it.halfCenturies }
                .thenByDescending { it.runs })
            .map {
                TopPlayer(
                    it.name,
                    "${it.centuries}x100s, ${it.halfCenturies}x50s",
                    "${it.runs} runs • HS: ${it.highestScore}"
                )
            }

        statCategories.add(
            StatCategory(
                "💯 Centuries & Half-Centuries",
                "50+ scores in tournament",
                allCenturyMakers.take(8),
                allCenturyMakers
            )
        )

        // 4. Most Wickets
        val allTopWickets = players.values
            .filter { it.wickets > 0 }
            .sortedWith(compareByDescending<PlayerStats> { it.wickets }
                .thenBy { it.runsGiven }
                .thenBy { it.overs })
            .map {
                val avg = "%.2f".format(it.bowlingAverage)
                val econ = "%.2f".format(it.economy)
                TopPlayer(
                    it.name,
                    "${it.wickets} wickets",
                    "Avg: $avg • Econ: $econ • Best: ${it.bestBowlingWickets}/${it.bestBowlingRuns}"
                )
            }

        statCategories.add(
            StatCategory(
                "🎯 Most Wickets",
                "Highest wicket-takers",
                allTopWickets.take(8),
                allTopWickets
            )
        )

        // 5. Best Strike Rate
        val allTopStrikeRate = players.values
            .filter { it.balls >= 30 }
            .sortedByDescending { it.strikeRate }
            .map {
                TopPlayer(
                    it.name,
                    "SR: ${"%.2f".format(it.strikeRate)}",
                    "${it.runs} runs in ${it.balls} balls • ${it.fours}x4s, ${it.sixes}x6s"
                )
            }

        statCategories.add(
            StatCategory(
                "⚡ Best Strike Rate",
                "Min 30 balls faced",
                allTopStrikeRate.take(8),
                allTopStrikeRate
            )
        )

        // 6. Most Boundaries
        val allTopBoundaries = players.values
            .sortedByDescending { it.fours + it.sixes }
            .map {
                TopPlayer(
                    it.name,
                    "${it.fours}x4s, ${it.sixes}x6s",
                    "Total: ${it.fours + it.sixes} boundaries • ${it.runs} runs"
                )
            }

        statCategories.add(
            StatCategory(
                "💥 Boundary Hitters",
                "Most fours and sixes",
                allTopBoundaries.take(8),
                allTopBoundaries
            )
        )

        // 7. Best Economy Rate (Bowling)
        val allBestEconomy = players.values
            .filter { it.overs >= 5 }
            .sortedBy { it.economy }
            .map {
                TopPlayer(
                    it.name,
                    "Econ: ${"%.2f".format(it.economy)}",
                    "${it.wickets} wickets • ${it.runsGiven} runs in ${it.overs} overs"
                )
            }

        statCategories.add(
            StatCategory(
                "💰 Best Economy",
                "Min 5 overs bowled",
                allBestEconomy.take(8),
                allBestEconomy
            )
        )

        // 8. Fantasy Points Leaders
        val allFantasyLeaders = players.values
            .sortedByDescending { it.fantasyPoints }
            .map {
                TopPlayer(
                    it.name,
                    "${it.fantasyPoints} pts",
                    "${it.runs}R • ${it.wickets}W • ${it.centuries}x100s • ${it.halfCenturies}x50s"
                )
            }

        statCategories.add(
            StatCategory(
                "🏅 Fantasy Points",
                "Top all-round performers",
                allFantasyLeaders.take(10),
                allFantasyLeaders
            )
        )
    }

    private fun updateUI() {
        if (teams.isEmpty() && players.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE

        // Update summary stats
        binding.tvTeamsCount.text = teams.size.toString()
        binding.tvPlayersCount.text = players.size.toString()

        // Calculate total matches properly
        val totalMatches = if (teams.isNotEmpty()) {
            teams.values.sumOf { it.matches } / 2
        } else 0
        binding.tvMatchesCount.text = totalMatches.toString()

        // Update top performers
        val topBatsman = players.values.maxByOrNull { it.runs }
        val topBowler = players.values.maxByOrNull { it.wickets }
        val topTeam = teams.values.maxByOrNull { it.wins }

        binding.tvTopBatsman.text = "🏏 Top Batsman: ${topBatsman?.name ?: "N/A"} (${topBatsman?.runs ?: 0} runs)"
        binding.tvTopBowler.text = "🎯 Top Bowler: ${topBowler?.name ?: "N/A"} (${topBowler?.wickets ?: 0} wickets)"
        binding.tvTopTeam.text = "🏆 Leading Team: ${topTeam?.name ?: "N/A"} (${topTeam?.wins ?: 0} wins)"

        // Show tournament summary
        binding.tvStatsCount.text = "${teams.size} Teams • ${players.size} Players • $totalMatches Matches"

        // Show total runs in tournament
        val totalRuns = teams.values.sumOf { it.runsFor }
        binding.tvTotalRuns.text = "Total Runs Scored: $totalRuns"

        adapter.submitList(statCategories)
    }

    private fun loadSampleData() {

        addSamplePlayers()
    }



    private fun addSamplePlayers() {
        val samplePlayers = listOf(
            PlayerStats("Virat Kohli", 450, 300, 45, 12, 132, 1, 3, 2, 10.0, 85, 2, 35, 5),
            PlayerStats("David Warner", 380, 250, 38, 15, 128, 1, 2, 0, 0.0, 0, 0, 0, 5),
            PlayerStats("Jos Buttler", 320, 180, 28, 18, 110, 1, 2, 0, 0.0, 0, 0, 0, 5),
            PlayerStats("Saim Ayub", 280, 200, 32, 8, 95, 0, 2, 5, 20.0, 150, 3, 25, 5),
            PlayerStats("Jasprit Bumrah", 30, 20, 4, 1, 25, 0, 0, 15, 25.0, 180, 5, 22, 5),
            PlayerStats("Pat Cummins", 45, 35, 5, 2, 28, 0, 0, 12, 22.0, 165, 4, 18, 5),
            PlayerStats("Kane Williamson", 280, 320, 25, 4, 88, 0, 2, 0, 0.0, 0, 0, 0, 5),
            PlayerStats("Trent Boult", 18, 15, 2, 1, 12, 0, 0, 14, 23.0, 190, 4, 28, 5)
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

    private fun extractWickets(line: String): Int {
        val pattern = """/\s*(\d+)""".toRegex()
        return pattern.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
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

            binding.btnViewAll.setOnClickListener {
                val intent = Intent(binding.root.context, DetailedStatsActivity::class.java).apply {
                    putExtra("CATEGORY_TITLE", category.title)
                    putExtra("CATEGORY_DESCRIPTION", category.description)
                    putParcelableArrayListExtra("TOP_PLAYERS", ArrayList(category.allPlayers)) // Pass allPlayers
                }
                binding.root.context.startActivity(intent)
            }

            // Add player items (showing limited list)
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