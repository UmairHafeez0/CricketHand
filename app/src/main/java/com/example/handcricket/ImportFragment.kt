// ImportFragment.kt (Complete implementation)
package com.example.handcricket

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import java.text.SimpleDateFormat
import java.util.*

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

                    // Extract team names from CSV
                    extractTeamNamesFromCSV(lines)

                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text =
                            "CSV parsed successfully!\n" +
                                    "Found ${parsedData!!.batters.size} batters and ${parsedData!!.bowlers.size} bowlers\n" +
                                    "Teams detected: $csvTeam1Name vs $csvTeam2Name"
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

        for (line in lines) {
            when {
                line.contains("Batting") -> {
                    section = "batting"
                    // Extract team name from batting header
                    val teamMatch = Regex("""(.+?) Batting""").find(line)
                    currentTeam = teamMatch?.groupValues?.get(1)?.trim() ?: "Team"
                }
                line.contains("Bowling") -> {
                    section = "bowling"
                    // Extract team name from bowling header
                    val teamMatch = Regex("""(.+?) Bowling""").find(line)
                    currentTeam = teamMatch?.groupValues?.get(1)?.trim() ?: "Team"
                }
                line.contains("Batter ID") -> continue
                line.contains("Bowler ID") -> continue
                line.contains("/") && line.contains("(") -> {
                    // This is a score line like "186 / 10 (9.1)"
                    parseScoreLine(line, section, currentTeam, matchInfo)
                }
                line.contains("won the game by") -> {
                    parseResultLine(line, matchInfo)
                }
                line.contains("player of the match") -> {
                    parsePlayerOfMatch(line, matchInfo)
                }
                line.contains("Played on") -> {
                    parseDateLine(line, matchInfo)
                }
                line.isBlank() -> continue
                else -> {
                    val parts = line.split(",")
                    if (section == "batting" && parts.size >= 7) {
                        val batter = parseBatterData(parts, currentTeam)
                        batters.add(batter)
                    } else if (section == "bowling" && parts.size >= 6) {
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

    private fun parseScoreLine(line: String, section: String, team: String, matchInfo: MatchInfo) {
        val regex = """(\d+)\s*/\s*(\d+)\s*\(([\d\.]+)\)""".toRegex()
        val match = regex.find(line)
        if (match != null) {
            val runs = match.groupValues[1].toInt()
            val wickets = match.groupValues[2].toInt()
            val overs = match.groupValues[3]

            if (section == "batting") {
                if (matchInfo.team1Name.isEmpty()) {
                    matchInfo.team1Name = team
                    matchInfo.team1Score = "$runs/$wickets ($overs)"
                } else {
                    matchInfo.team2Name = team
                    matchInfo.team2Score = "$runs/$wickets ($overs)"
                }
            }
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

    private fun extractTeamNamesFromCSV(lines: List<String>) {
        var team1 = ""
        var team2 = ""

        for (line in lines) {
            if (line.contains("Batting")) {
                val match = Regex("""(.+?) Batting""").find(line)
                val team = match?.groupValues?.get(1)?.trim()
                if (team != null) {
                    if (team1.isEmpty()) {
                        team1 = team
                    } else if (team2.isEmpty() && team != team1) {
                        team2 = team
                    }
                }
            }
        }

        csvTeam1Name = team1
        csvTeam2Name = team2
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

                // Update match with winner
                if (winnerTeamId != null) {
                    db.tournamentDao().updateMatchWinner(matchId, winnerTeamId)
                }

                // Save match result
                val matchResult = MatchResult(
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
                db.tournamentDao().insertMatchResult(matchResult)

                // Save player performances
                val playerPerformances = mutableListOf<PlayerPerformance>()

                // Process batters
                parsedData.batters.forEach { batter ->
                    val teamId = when (batter.team) {
                        csvTeam1Name -> team1Id
                        csvTeam2Name -> team2Id
                        else -> team1Id
                    }

                    playerPerformances.add(PlayerPerformance(
                        matchId = matchId,
                        tournamentId = tournamentId,
                        teamId = teamId,
                        playerName = batter.name,
                        role = "batter",
                        runs = batter.runs,
                        balls = batter.balls,
                        fours = batter.fours,
                        sixes = batter.sixes,
                        strikeRate = batter.strikeRate,
                        isOut = batter.isOut,
                        date = parsedData.matchInfo.date
                    ))
                }

                // Process bowlers
                parsedData.bowlers.forEach { bowler ->
                    val teamId = when (bowler.team) {
                        csvTeam1Name -> team1Id
                        csvTeam2Name -> team2Id
                        else -> team2Id // Bowlers are from opposite team
                    }

                    playerPerformances.add(PlayerPerformance(
                        matchId = matchId,
                        tournamentId = tournamentId,
                        teamId = teamId,
                        playerName = bowler.name,
                        role = "bowler",
                        wickets = bowler.wickets,
                        overs = bowler.overs,
                        runsConceded = bowler.runs,
                        economy = bowler.economy,
                        date = parsedData.matchInfo.date
                    ))
                }

                // Insert all performances
                db.tournamentDao().insertPlayerPerformances(playerPerformances)

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text =
                        "✅ Data imported successfully!\n" +
                                "• Match result saved\n" +
                                "• ${playerPerformances.size} player performances saved\n" +
                                "• Winner: ${parsedData.matchInfo.winner}"
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Error importing data: ${e.message}"
                }
            }
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