package com.example.handcricket

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.handcricket.data.*
import com.example.handcricket.databinding.FragmentImportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ImportFragment : Fragment() {

    private var _binding: FragmentImportBinding? = null
    private val binding get() = _binding!!
    private var tournamentId: Int = 0
    private var selectedMatchId: Int = -1
    private var parsedData: ParsedMatchData? = null
    private lateinit var matches: List<Match>
    private lateinit var teams: Map<Int, Team>
    private var csvTeam1Name: String = ""
    private var csvTeam2Name: String = ""

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                binding.tvSelectedFile.text = "File: ${uri.lastPathSegment}"
                parseCSVFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tournamentId = arguments?.getInt("TOURNAMENT_ID") ?: 0

        setupUI()
        loadMatches()
    }

    private fun setupUI() {
        binding.btnSelectFile.setOnClickListener {
            openFilePicker()
        }

        binding.btnImport.setOnClickListener {
            if (selectedMatchId == -1) {
                binding.tvStatus.text = "Please select a match first"
            } else if (parsedData == null) {
                binding.tvStatus.text = "Please select a CSV file first"
            } else {
                showTeamSelectionDialog()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*" // For CSV files
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            binding.tvStatus.text = "Error opening file picker: ${e.message}"
        }
    }

    private fun parseCSVFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val lines = reader.readLines()
                    reader.close()

                    // Parse CSV data
                    parsedData = parseMatchDataFromCSV(lines)

                    withContext(Dispatchers.Main) {
                        if (parsedData != null) {
                            binding.tvStatus.text =
                                "CSV parsed successfully!\n" +
                                        "Found ${parsedData!!.batters.size} batters and ${parsedData!!.bowlers.size} bowlers\n" +
                                        "Teams detected: $csvTeam1Name vs $csvTeam2Name"
                        } else {
                            binding.tvStatus.text = "Error: Could not parse CSV file"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Error parsing CSV: ${e.message}"
                }
            }
        }
    }

    private fun parseMatchDataFromCSV(lines: List<String>): ParsedMatchData {
        val batters = mutableListOf<BatterData>()
        val bowlers = mutableListOf<BowlerData>()
        var matchInfo = MatchInfo()

        var section = ""
        var currentTeam = ""

        // Reset team names
        csvTeam1Name = ""
        csvTeam2Name = ""

        // First pass: Extract team names and scores from batting headers with scores
        for (line in lines) {
            if (line.contains("Batting") && line.contains("/") && line.contains("(")) {
                // This is a batting header with score line like "Babar Azam Batting,,,,,186 / 10 (9.1)"
                val teamMatch = Regex("""(.+?) Batting""").find(line)
                val team = teamMatch?.groupValues?.get(1)?.trim() ?: ""

                // Extract score from the line
                val scoreMatch = Regex("""(\d+)\s*/\s*(\d+)\s*\(([\d\.]+)\)""").find(line)
                val score = scoreMatch?.value?.trim() ?: ""

                if (matchInfo.team1Name.isEmpty()) {
                    matchInfo.team1Name = team
                    matchInfo.team1Score = score
                    csvTeam1Name = team
                } else if (matchInfo.team2Name.isEmpty() && team != matchInfo.team1Name) {
                    matchInfo.team2Name = team
                    matchInfo.team2Score = score
                    csvTeam2Name = team
                }
            }
        }

        // Second pass: Parse player data and other match info
        for (line in lines) {
            when {
                line.contains("won the game by") -> {
                    parseResultLine(line, matchInfo)
                }
                line.contains("player of the match") -> {
                    parsePlayerOfMatch(line, matchInfo)
                }
                line.contains("Played on") -> {
                    parseDateLine(line, matchInfo)
                }
                line.contains(" Batting,") -> {
                    section = "batting"
                    // Extract team name from batting header without score
                    val teamMatch = Regex("""(.+?) Batting,""").find(line)
                    currentTeam = teamMatch?.groupValues?.get(1)?.trim() ?: "Team"

                    // Update team names if not already set
                    if (csvTeam1Name.isEmpty()) {
                        csvTeam1Name = currentTeam
                    } else if (csvTeam2Name.isEmpty() && currentTeam != csvTeam1Name) {
                        csvTeam2Name = currentTeam
                    }
                }
                line.contains(" Bowling") -> {
                    section = "bowling"
                    // Extract team name from bowling header
                    val teamMatch = Regex("""(.+?) Bowling,""").find(line)
                    currentTeam = teamMatch?.groupValues?.get(1)?.trim() ?: "Team"
                }
                line.contains("Batter ID") -> continue
                line.contains("Bowler ID") -> continue
                line.contains("Fall of Wickets") -> continue
                line.isBlank() -> continue
                else -> {
                    val parts = line.split(",")
                    if (section == "batting" && parts.size >= 7 && parts[0].trim().isNotEmpty()) {
                        val batter = parseBatterData(parts, currentTeam)
                        batters.add(batter)
                    } else if (section == "bowling" && parts.size >= 6 && parts[0].trim().isNotEmpty()) {
                        val bowler = parseBowlerData(parts, currentTeam)
                        bowlers.add(bowler)
                    }
                }
            }
        }

        return ParsedMatchData(batters, bowlers, matchInfo)
    }

    private fun parseBatterData(parts: List<String>, team: String): BatterData {
        val nameWithBowler = parts[1].trim()
        val name = nameWithBowler.split("(").first().trim()

        return BatterData(
            id = parts[0].trim(),
            name = name,
            team = team,
            runs = parts[2].toIntOrNull() ?: 0,
            balls = parts[3].toIntOrNull() ?: 0,
            fours = parts[4].toIntOrNull() ?: 0,
            sixes = parts[5].toIntOrNull() ?: 0,
            strikeRate = parts[6].toFloatOrNull() ?: 0f,
            isOut = !nameWithBowler.contains("not out", ignoreCase = true)
        )
    }

    private fun parseBowlerData(parts: List<String>, team: String): BowlerData {
        return BowlerData(
            id = parts[0].trim(),
            name = parts[1].trim(),
            team = team,
            overs = parseOvers(parts[2]),
            runs = parts[3].toIntOrNull() ?: 0,
            wickets = parts[4].toIntOrNull() ?: 0,
            economy = parts[5].toFloatOrNull() ?: 0f
        )
    }

    private fun parseOvers(oversStr: String): Float {
        return try {
            if (oversStr.contains(".")) {
                val parts = oversStr.split(".")
                parts[0].toFloat() + (parts[1].toFloat() / 6)
            } else {
                oversStr.toFloat()
            }
        } catch (e: Exception) {
            0f
        }
    }

    private fun parseResultLine(line: String, matchInfo: MatchInfo) {
        val winnerRegex = """(.+?) won the game by""".toRegex()
        val winnerMatch = winnerRegex.find(line)
        matchInfo.winner = winnerMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parsePlayerOfMatch(line: String, matchInfo: MatchInfo) {
        val pomRegex = """(.+?) is player of the match""".toRegex()
        val pomMatch = pomRegex.find(line)
        matchInfo.playerOfMatch = pomMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parseDateLine(line: String, matchInfo: MatchInfo) {
        val dateRegex = """Played on (.+)""".toRegex()
        val dateMatch = dateRegex.find(line)
        matchInfo.date = dateMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun loadMatches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            matches = db.tournamentDao().getMatches(tournamentId)
            val teamList = db.tournamentDao().getTeams(tournamentId)
            teams = teamList.associateBy { it.id }

            val matchItems = matches.map { match ->
                "${match.id}: ${teams[match.teamAId]?.name} vs ${teams[match.teamBId]?.name} (${match.matchType})"
            }

            withContext(Dispatchers.Main) {
                setupMatchSpinner(matchItems)
            }
        }
    }

    private fun setupMatchSpinner(matchItems: List<String>) {
        // Using a simple dialog for match selection
        binding.btnSelectMatch.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Match")
                .setItems(matchItems.toTypedArray()) { _, which ->
                    selectedMatchId = matches[which].id
                    binding.tvSelectedMatch.text = "Match: ${matchItems[which]}"
                }
                .show()
        }
    }

    private fun showTeamSelectionDialog() {
        // Check if we have the data
        val match = matches.find { it.id == selectedMatchId }
        if (match == null) {
            binding.tvStatus.text = "Error: No match selected"
            return
        }

        val team1Name = teams[match.teamAId]?.name ?: "Team A"
        val team2Name = teams[match.teamBId]?.name ?: "Team B"

        // Use default names if CSV names are empty
        val csv1 = if (csvTeam1Name.isBlank()) "CSV Team 1" else csvTeam1Name
        val csv2 = if (csvTeam2Name.isBlank()) "CSV Team 2" else csvTeam2Name

        // Create custom dialog
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_team_mapping, null)

        val tvTitle: TextView = dialogView.findViewById(R.id.tvTitle)
        val tvMessage: TextView = dialogView.findViewById(R.id.tvMessage)
        val option1: RadioButton = dialogView.findViewById(R.id.option1)
        val option2: RadioButton = dialogView.findViewById(R.id.option2)
        val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)
        val btnImport: Button = dialogView.findViewById(R.id.btnImport)

        // Set text
        tvTitle.text = "Map CSV Teams to Tournament Teams"
        tvMessage.text = "Select how teams should be mapped:"
        option1.text = "$csv1 → $team1Name  AND  $csv2 → $team2Name"
        option2.text = "$csv1 → $team2Name  AND  $csv2 → $team1Name"

        // Select first option by default
        option1.isChecked = true

        // Create and show dialog
        val dialog = AlertDialog.Builder(requireActivity())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Button listeners
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnImport.setOnClickListener {
            dialog.dismiss()
            if (option1.isChecked) {
                // Option 1: csv1 = team1, csv2 = team2
                importDataToDatabase(match.teamAId, match.teamBId)
            } else {
                // Option 2: csv1 = team2, csv2 = team1
                importDataToDatabase(match.teamBId, match.teamAId)
            }
        }

        dialog.show()
    }

    private fun importDataToDatabase(team1Id: Int, team2Id: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                val parsedData = parsedData ?: return@launch
                val matchId = selectedMatchId

                // Determine winner
                val winnerTeamId = when (parsedData.matchInfo.winner) {
                    csvTeam1Name -> team1Id
                    csvTeam2Name -> team2Id
                    else -> null
                }

                // Update match with winner and player of match
                if (winnerTeamId != null) {
                    db.tournamentDao().updateMatchWinner(matchId, winnerTeamId)
                }
                if (parsedData.matchInfo.playerOfMatch.isNotEmpty()) {
                    db.tournamentDao().updatePlayerOfMatch(matchId, parsedData.matchInfo.playerOfMatch)
                }

                // Check if match result already exists
                val existingResult = db.tournamentDao().getMatchResult(matchId)
                val matchResult = if (existingResult == null) {
                    MatchResult(
                        matchId = matchId,
                        tournamentId = tournamentId,
                        team1Id = team1Id,
                        team2Id = team2Id,
                        team1Score = parsedData.matchInfo.team1Score,
                        team2Score = parsedData.matchInfo.team2Score,
                        winnerTeamId = winnerTeamId ?: -1,
                        playerOfMatch = parsedData.matchInfo.playerOfMatch,
                        date = parsedData.matchInfo.date,
                        matchType = matches.find { it.id == matchId }?.matchType ?: "Group"
                    )
                } else {
                    // Update existing result
                    existingResult.copy(
                        team1Score = parsedData.matchInfo.team1Score,
                        team2Score = parsedData.matchInfo.team2Score,
                        winnerTeamId = winnerTeamId ?: existingResult.winnerTeamId,
                        playerOfMatch = parsedData.matchInfo.playerOfMatch,
                        date = parsedData.matchInfo.date
                    )
                }

                // Save or update match result
                if (existingResult == null) {
                    db.tournamentDao().insertMatchResult(matchResult)
                } else {
                    db.tournamentDao().updateMatchResult(matchResult)
                }

                // Get existing performances for this match and create a map for quick lookup
                val existingPerformances = db.tournamentDao().getPlayerPerformancesByMatch(matchId)
                val existingPerformanceMap = existingPerformances.associateBy { it.playerName }

                // First, process all players to create/update records
                val allPlayers = mutableMapOf<String, PlayerDataForUpdate>()

                // Add batters to the map
                parsedData.batters.forEach { batter ->
                    val teamId = when (batter.team) {
                        csvTeam1Name -> team1Id
                        csvTeam2Name -> team2Id
                        else -> team1Id
                    }

                    allPlayers[batter.name] = PlayerDataForUpdate(
                        name = batter.name,
                        teamId = teamId,
                        batterData = batter,
                        bowlerData = null
                    )
                }

                // Add/update bowlers in the map (some players might be both batter and bowler)
                parsedData.bowlers.forEach { bowler ->
                    val teamId = when (bowler.team) {
                        csvTeam1Name -> team2Id  // Bowlers bowl for opposite team
                        csvTeam2Name -> team1Id
                        else -> team2Id
                    }

                    if (allPlayers.containsKey(bowler.name)) {
                        // Player already exists as batter, add bowler data
                        allPlayers[bowler.name] = allPlayers[bowler.name]!!.copy(bowlerData = bowler)
                    } else {
                        // New player, only bowler data
                        allPlayers[bowler.name] = PlayerDataForUpdate(
                            name = bowler.name,
                            teamId = teamId,
                            batterData = null,
                            bowlerData = bowler
                        )
                    }
                }

                // Now process each player, creating or updating their performance
                var updatedCount = 0
                var insertedCount = 0

                allPlayers.values.forEach { playerData ->
                    val existing = existingPerformanceMap[playerData.name]

                    if (existing != null) {
                        // Update existing performance
                        val updatedPerformance = mergePlayerPerformance(
                            existing = existing,
                            batter = playerData.batterData,
                            bowler = playerData.bowlerData,
                            date = parsedData.matchInfo.date
                        )
                        db.tournamentDao().updatePlayerPerformance(updatedPerformance)
                        updatedCount++
                    } else {
                        // Create new performance
                        val newPerformance = createPlayerPerformance(
                            matchId = matchId,
                            tournamentId = tournamentId,
                            teamId = playerData.teamId,
                            playerName = playerData.name,
                            batter = playerData.batterData,
                            bowler = playerData.bowlerData,
                            date = parsedData.matchInfo.date
                        )
                        db.tournamentDao().insertPlayerPerformance(newPerformance)
                        insertedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text =
                        "✅ Data imported successfully!\n" +
                                "• Match result saved\n" +
                                "• $updatedCount performances updated\n" +
                                "• $insertedCount performances added\n" +
                                "• Winner: ${parsedData.matchInfo.winner}"
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Error importing data: ${e.message}\n${e.stackTraceToString()}"
                }
            }
        }
    }

    private fun mergePlayerPerformance(
        existing: PlayerPerformance,
        batter: BatterData?,
        bowler: BowlerData?,
        date: String
    ): PlayerPerformance {
        val newRuns = (existing.runs + (batter?.runs ?: 0))
        val newBalls = (existing.balls + (batter?.balls ?: 0))
        val newStrikeRate = if (newBalls > 0) {
            (newRuns.toFloat() / newBalls) * 100
        } else {
            existing.strikeRate
        }

        val newOvers = (existing.overs + (bowler?.overs ?: 0f))
        val newRunsConceded = (existing.runsConceded + (bowler?.runs ?: 0))
        val newEconomy = if (newOvers > 0) {
            newRunsConceded.toFloat() / newOvers
        } else {
            existing.economy
        }

        return existing.copy(
            // Batting stats
            runs = newRuns,
            balls = newBalls,
            fours = (existing.fours + (batter?.fours ?: 0)),
            sixes = (existing.sixes + (batter?.sixes ?: 0)),
            strikeRate = newStrikeRate,
            isOut = batter?.isOut ?: existing.isOut,

            // Bowling stats
            wickets = (existing.wickets + (bowler?.wickets ?: 0)),
            overs = newOvers,
            runsConceded = newRunsConceded,
            economy = newEconomy,

            // Determine role
            role = determineRole(batter, bowler, existing.role),

            date = date
        )
    }

    private fun createPlayerPerformance(
        matchId: Int,
        tournamentId: Int,
        teamId: Int,
        playerName: String,
        batter: BatterData?,
        bowler: BowlerData?,
        date: String
    ): PlayerPerformance {
        val strikeRate = if (batter?.balls ?: 0 > 0) {
            ((batter?.runs ?: 0).toFloat() / (batter?.balls ?: 1)) * 100
        } else 0f

        val economy = if (bowler?.overs ?: 0f > 0) {
            (bowler?.runs ?: 0).toFloat() / (bowler?.overs ?: 1f)
        } else 0f

        return PlayerPerformance(
            matchId = matchId,
            tournamentId = tournamentId,
            teamId = teamId,
            playerName = playerName,
            role = determineRole(batter, bowler, "player"),
            runs = batter?.runs ?: 0,
            balls = batter?.balls ?: 0,
            fours = batter?.fours ?: 0,
            sixes = batter?.sixes ?: 0,
            wickets = bowler?.wickets ?: 0,
            overs = bowler?.overs ?: 0f,
            runsConceded = bowler?.runs ?: 0,
            economy = economy,
            strikeRate = strikeRate,
            isOut = batter?.isOut ?: false,
            date = date
        )
    }

    private fun determineRole(batter: BatterData?, bowler: BowlerData?, existingRole: String): String {
        return when {
            batter != null && bowler != null -> "all-rounder"
            batter != null -> "batter"
            bowler != null -> "bowler"
            else -> existingRole
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tournamentId: Int): ImportFragment {
            val fragment = ImportFragment()
            val args = Bundle()
            args.putInt("TOURNAMENT_ID", tournamentId)
            fragment.arguments = args
            return fragment
        }
    }
}

// Data classes for parsing
data class ParsedMatchData(
    val batters: List<BatterData>,
    val bowlers: List<BowlerData>,
    val matchInfo: MatchInfo = MatchInfo()
)

data class MatchInfo(
    var team1Name: String = "",
    var team2Name: String = "",
    var team1Score: String = "",
    var team2Score: String = "",
    var winner: String = "",
    var playerOfMatch: String = "",
    var date: String = ""
)

data class BatterData(
    val id: String,
    val name: String,
    val team: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Float,
    val isOut: Boolean
)

data class BowlerData(
    val id: String,
    val name: String,
    val team: String,
    val overs: Float,
    val runs: Int,
    val wickets: Int,
    val economy: Float
)

// Helper data class for player updates
data class PlayerDataForUpdate(
    val name: String,
    val teamId: Int,
    val batterData: BatterData?,
    val bowlerData: BowlerData?
)