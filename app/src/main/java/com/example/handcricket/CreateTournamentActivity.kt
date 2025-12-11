package com.example.handcricket

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.handcricket.data.*
import com.example.handcricket.databinding.ActivityCreateTournamentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateTournamentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTournamentBinding
    private lateinit var sharedPrefs: SharedPreferences

    private val allTeams = arrayOf(
        "India", "Australia", "Pakistan", "England", "New Zealand",
        "South Africa", "Sri Lanka", "Bangladesh", "West Indies", "Afghanistan",
        "Ireland", "Netherlands", "Zimbabwe", "Scotland", "Nepal",
        "UAE", "Kenya", "Oman", "USA", "Canada"
    )

    private var selectedTeams = mutableListOf<String>()
    private var totalTeams = 0
    private lateinit var tournamentNameStr: String
    private var tournamentFormat: String = "RoundRobin"

    // Keys for SharedPreferences
    private companion object {
        const val PREFS_NAME = "TournamentPrefs"
        const val KEY_LAST_FORMAT = "last_tournament_format"
        const val KEY_LAST_TEAM_COUNT = "last_team_count"
        const val KEY_LAST_TEAMS = "last_selected_teams"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTournamentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SharedPreferences
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupUI()
        loadRecentFormat()
    }

    private fun setupUI() {
        binding.btnNext.setOnClickListener {
            tournamentNameStr = binding.etTournamentName.text.toString().trim()
            if (tournamentNameStr.isEmpty()) {
                Toast.makeText(this, "Enter tournament name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            askNumberOfTeams()
        }

        binding.btnQuickCreate.setOnClickListener {
            tournamentNameStr = binding.etTournamentName.text.toString().trim()
            if (tournamentNameStr.isEmpty()) {
                Toast.makeText(this, "Enter tournament name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createWithRecentFormat()
        }
    }

    private fun loadRecentFormat() {
        val lastFormat = sharedPrefs.getString(KEY_LAST_FORMAT, null)
        val lastTeamCount = sharedPrefs.getInt(KEY_LAST_TEAM_COUNT, 0)
        val lastTeams = sharedPrefs.getString(KEY_LAST_TEAMS, null)

        if (lastFormat != null && lastTeamCount > 0 && lastTeams != null) {
            binding.tvRecentFormat.text = "Last used: $lastFormat with $lastTeamCount teams"
            binding.tvRecentFormat.visibility = android.view.View.VISIBLE
            binding.btnQuickCreate.visibility = android.view.View.VISIBLE
        } else {
            binding.tvRecentFormat.visibility = android.view.View.GONE
            binding.btnQuickCreate.visibility = android.view.View.GONE
        }
    }

    private fun saveRecentFormat(format: String, teamCount: Int, teams: List<String>) {
        sharedPrefs.edit {
            putString(KEY_LAST_FORMAT, format)
            putInt(KEY_LAST_TEAM_COUNT, teamCount)
            putString(KEY_LAST_TEAMS, teams.joinToString(","))
        }
    }

    private fun createWithRecentFormat() {
        val lastFormat = sharedPrefs.getString(KEY_LAST_FORMAT, "RoundRobin") ?: "RoundRobin"
        val lastTeamCount = sharedPrefs.getInt(KEY_LAST_TEAM_COUNT, 2)
        val lastTeamsStr = sharedPrefs.getString(KEY_LAST_TEAMS, "")

        tournamentFormat = lastFormat
        totalTeams = lastTeamCount

        if (lastTeamsStr.isNullOrEmpty()) {
            // If no saved teams, ask to select teams
            chooseTeams()
        } else {
            // Use saved teams
            selectedTeams = lastTeamsStr.split(",").take(totalTeams).toMutableList()
            if (selectedTeams.size < totalTeams) {
                // If not enough saved teams, ask to select teams
                Toast.makeText(this, "Select teams to complete the tournament", Toast.LENGTH_SHORT).show()
                chooseTeams()
            } else {
                // Directly create with saved teams
                if (tournamentFormat == "RoundRobin") {
                    generateRoundRobin()
                } else {
                    askGroups()
                }
            }
        }
    }

    private fun askNumberOfTeams() {
        val numbers = (2..20).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Number of Teams")
            .setItems(numbers) { _, which ->
                totalTeams = numbers[which].toInt()
                chooseTeams()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseTeams() {
        val checkedItems = BooleanArray(allTeams.size)
        // Pre-check previously selected teams
        selectedTeams.forEach { team ->
            val index = allTeams.indexOf(team)
            if (index != -1) checkedItems[index] = true
        }

        val tempSelected = selectedTeams.toMutableList()

        AlertDialog.Builder(this)
            .setTitle("Select $totalTeams Teams")
            .setMultiChoiceItems(allTeams, checkedItems) { dialog, which, isChecked ->
                if (isChecked) {
                    if (tempSelected.size < totalTeams) {
                        tempSelected.add(allTeams[which])
                    } else {
                        (dialog as AlertDialog).listView.setItemChecked(which, false)
                        Toast.makeText(this, "You can select only $totalTeams teams", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tempSelected.remove(allTeams[which])
                }
            }
            .setPositiveButton("OK") { _, _ ->
                if (tempSelected.size != totalTeams) {
                    Toast.makeText(this, "Select exactly $totalTeams teams", Toast.LENGTH_SHORT).show()
                    chooseTeams()
                } else {
                    selectedTeams = tempSelected
                    chooseTournamentFormat()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseTournamentFormat() {
        val formats = arrayOf("Round Robin", "Groups")
        AlertDialog.Builder(this)
            .setTitle("Choose Tournament Format")
            .setItems(formats) { _, which ->
                tournamentFormat = if (which == 0) "RoundRobin" else "Groups"
                saveRecentFormat(tournamentFormat, totalTeams, selectedTeams)
                if (tournamentFormat == "RoundRobin") {
                    generateRoundRobin()
                } else {
                    askGroups()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -----------------------------
    // ROUND ROBIN TOURNAMENT
    // -----------------------------
    private fun generateRoundRobin() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@CreateTournamentActivity)
            val tournamentId = db.tournamentDao().insertTournament(
                Tournament(name = tournamentNameStr, format = tournamentFormat)
            ).toInt()

            // Insert teams and get generated IDs
            val teams = selectedTeams.map { Team(tournamentId = tournamentId, name = it) }
            val teamIds = db.tournamentDao().insertTeams(teams)
            val teamsWithIds = teams.mapIndexed { index, team -> team.copy(id = teamIds[index].toInt()) }

            val matches = mutableListOf<Match>()

            // Round Robin scheduling using circle method
            val teamList = teamsWithIds.toMutableList()
            val isOdd = teamList.size % 2 != 0
            if (isOdd) teamList.add(Team(id = -1, tournamentId = tournamentId, name = "BYE")) // add dummy team

            val numRounds = teamList.size - 1
            val numMatchesPerRound = teamList.size / 2

            val fixed = teamList[0]
            for (round in 0 until numRounds) {
                for (i in 0 until numMatchesPerRound) {
                    val teamA = if (i == 0) fixed else teamList[i]
                    val teamB = teamList[teamList.size - 1 - i]
                    if (teamA.id != -1 && teamB.id != -1) { // skip BYE
                        matches.add(
                            Match(
                                tournamentId = tournamentId,
                                teamAId = teamA.id,
                                teamBId = teamB.id,
                                matchType = "RoundRobin"
                            )
                        )
                    }
                }
                // Rotate teams except the fixed team
                val last = teamList.removeAt(teamList.size - 1)
                teamList.add(1, last)
            }

            db.tournamentDao().insertMatches(matches)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CreateTournamentActivity, "Round Robin tournament created!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // -----------------------------
    // GROUP TOURNAMENT
    // -----------------------------
    private fun askGroups() {
        val numbers = (2..totalTeams).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Number of Groups")
            .setItems(numbers) { _, which ->
                val groups = numbers[which].toInt()
                generateGroups(groups)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateGroups(groups: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@CreateTournamentActivity)
            val tournamentId = db.tournamentDao().insertTournament(
                Tournament(name = tournamentNameStr, format = tournamentFormat)
            ).toInt()

            val groupNames = (1..groups).map { "Group $it" }

            // Insert teams with group assignment
            val teams = selectedTeams.mapIndexed { index, name ->
                Team(tournamentId = tournamentId, name = name, groupName = groupNames[index % groups])
            }
            val teamIds = db.tournamentDao().insertTeams(teams)
            val teamsWithIds = teams.mapIndexed { index, team -> team.copy(id = teamIds[index].toInt()) }

            // Generate group matches
            val matches = mutableListOf<Match>()
            groupNames.forEach { group ->
                val groupTeams = teamsWithIds.filter { it.groupName == group }
                for (i in groupTeams.indices) {
                    for (j in i + 1 until groupTeams.size) {
                        matches.add(
                            Match(
                                tournamentId = tournamentId,
                                teamAId = groupTeams[i].id,
                                teamBId = groupTeams[j].id,
                                matchType = "Group"
                            )
                        )
                    }
                }
            }
            db.tournamentDao().insertMatches(matches)

            // Save to preferences
            saveRecentFormat(tournamentFormat, totalTeams, selectedTeams)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CreateTournamentActivity, "Groups and matches created!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}