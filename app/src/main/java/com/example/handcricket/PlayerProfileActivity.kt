package com.example.handcricket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.handcricket.databinding.ActivityPlayerProfileBinding
import com.example.handcricket.databinding.ItemStatRowBinding
import com.example.handcricket.databinding.ItemVsTeamRowBinding

class PlayerProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val playerName = intent.getStringExtra("PLAYER_NAME") ?: return

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = playerName
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        val player = AppDataStore.players[playerName]
        if (player == null) {
            finish()
            return
        }

        populateHeader(player)
        populateBattingStats(player)
        populateBowlingStats(player)
        populateVsTeams(player)
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun populateHeader(p: PlayerStats) {
        binding.tvPlayerName.text = p.name
        binding.tvTeam.text = if (p.team != "Unknown") "🏳 ${p.team}" else "Team Unknown"

        val battingRank = AppDataStore.battingRanks[p.name]
        val bowlingRank = AppDataStore.bowlingRanks[p.name]

        binding.tvBattingRank.text =
            if (battingRank != null) "🏏 #$battingRank Batting" else "🏏 Not Ranked"
        binding.tvBowlingRank.text =
            if (bowlingRank != null) "🎯 #$bowlingRank Bowling" else "🎯 Not Ranked"
    }

    // ── Batting ───────────────────────────────────────────────────────────────

    private fun populateBattingStats(p: PlayerStats) {
        val c = binding.containerBatting
        addRow(c, "Matches",        "${p.matches}")
        addRow(c, "Innings",        "${p.innings}")
        addRow(c, "Not Outs",       "N/A")
        addRow(c, "Total Runs",     "${p.runs}")
        addRow(c, "Highest Score",  "${p.highestScore}")
        addRow(c, "Batting Average","${"%.2f".format(p.battingAverage)}")
        addRow(c, "Strike Rate",    "${"%.2f".format(p.strikeRate)}")
        addDivider(c)
        addRow(c, "100s",           "${p.centuries}")
        addRow(c, "50s",            "${p.halfCenturies}")
        addRow(c, "30s",            "${p.thirties}")
        addRow(c, "Ducks (0s)",     "${p.ducks}")
        addDivider(c)
        addRow(c, "Fours",          "${p.fours}")
        addRow(c, "Sixes",          "${p.sixes}")
        addRow(c, "Balls Faced",    "${p.balls}")
    }

    // ── Bowling ───────────────────────────────────────────────────────────────

    private fun populateBowlingStats(p: PlayerStats) {
        val c = binding.containerBowling
        addRow(c, "Matches",           "${p.matches}")
        addRow(c, "Overs Bowled",      "${"%.1f".format(p.overs)}")
        addRow(c, "Maidens",           "N/A")
        addRow(c, "Runs Conceded",     "${p.runsGiven}")
        addRow(c, "Wickets",           "${p.wickets}")
        addRow(c, "Best Bowling",
            if (p.bestBowlingWickets > 0) "${p.bestBowlingWickets}/${p.bestBowlingRuns}" else "–")
        addDivider(c)
        addRow(c, "Bowling Average",
            if (p.wickets > 0) "${"%.2f".format(p.bowlingAverage)}" else "–")
        addRow(c, "Economy Rate",
            if (p.overs > 0) "${"%.2f".format(p.economy)}" else "–")
        addRow(c, "Bowling Strike Rate",
            if (p.wickets > 0) "${"%.2f".format(p.bowlingStrikeRate)}" else "–")
        addDivider(c)
        addRow(c, "3-Wicket Hauls",    "${p.threeWicketHauls}")
        addRow(c, "4-Wicket Hauls",    "${p.fourWicketHauls}")
        addRow(c, "5-Wicket Hauls",    "${p.fiveWicketHauls}")
    }

    // ── vs Teams ──────────────────────────────────────────────────────────────

    private fun populateVsTeams(p: PlayerStats) {
        val allOpponents = (p.runsAgainstTeam.keys + p.wicketsAgainstTeam.keys + p.matchesAgainstTeam.keys).toSortedSet()

        if (allOpponents.isEmpty()) {
            binding.tvNoVsTeams.visibility = View.VISIBLE
            return
        }

        allOpponents.forEach { opponent ->
            val runs     = p.runsAgainstTeam[opponent] ?: 0
            val wickets  = p.wicketsAgainstTeam[opponent] ?: 0
            val matches  = p.matchesAgainstTeam[opponent] ?: 0

            val rowBinding = ItemVsTeamRowBinding.inflate(layoutInflater, binding.containerVsTeams, false)
            rowBinding.tvTeam.text    = opponent
            rowBinding.tvRuns.text    = "$runs"
            rowBinding.tvWickets.text = "$wickets"
            rowBinding.tvMatches.text = "$matches"
            binding.containerVsTeams.addView(rowBinding.root)

            addThinDivider(binding.containerVsTeams)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addRow(container: LinearLayout, label: String, value: String) {
        val b = ItemStatRowBinding.inflate(layoutInflater, container, false)
        b.tvLabel.text = label
        b.tvValue.text = value
        container.addView(b.root)
    }

    private fun addDivider(container: LinearLayout) {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 4, 0, 4) }
            setBackgroundColor(getColor(R.color.gray_200))
        }
        container.addView(divider)
    }

    private fun addThinDivider(container: LinearLayout) {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            setBackgroundColor(getColor(R.color.gray_200))
        }
        container.addView(divider)
    }
}
