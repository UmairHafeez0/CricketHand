package com.example.handcricket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.handcricket.databinding.ActivityTeamProfileBinding
import com.example.handcricket.databinding.ItemStatRowBinding

class TeamProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val teamName = intent.getStringExtra("TEAM_NAME") ?: return

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = teamName
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        val team = AppDataStore.teams[teamName]
        if (team == null) {
            finish()
            return
        }

        populateHeader(team)
        populateOverallStats(team)
        populateHeadToHead(team)
        populateTopPlayers(teamName)
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun populateHeader(t: TeamStats) {
        binding.tvTeamName.text = t.name
        binding.tvRecord.text   = "${t.wins}W  –  ${t.losses}L  (${t.matches} matches)"

        val rank = AppDataStore.teamRanks[t.name]
        binding.tvTeamRank.text = if (rank != null) "🏆 Rank #$rank" else "🏆 Unranked"
        binding.tvNrr.text      = "NRR: ${"%.3f".format(t.nrr)}"
    }

    // ── Overall Stats ─────────────────────────────────────────────────────────

    private fun populateOverallStats(t: TeamStats) {
        val c = binding.containerOverall
        addRow(c, "Matches Played",      "${t.matches}")
        addRow(c, "Wins",                "${t.wins}")
        addRow(c, "Losses",              "${t.losses}")
        addRow(c, "Runs Scored",         "${t.runsFor}")
        addRow(c, "Runs Conceded",       "${t.runsAgainst}")
        addRow(c, "Net Run Rate (NRR)",  "${"%.3f".format(t.nrr)}")
        addDivider(c)
        addRow(c, "Highest Team Score",  "${t.highestTeamScore}")
        val biggestWin = if (t.highestMarginWinRuns > 0)
            "${t.highestMarginWinRuns} runs (vs ${t.highestMarginWinAgainst})" else "–"
        addRow(c, "Biggest Win Margin",  biggestWin)
        addDivider(c)
        addRow(c, "Wickets Taken",       "${t.wicketsTaken}")
        addRow(c, "Wickets Lost",        "${t.wicketsLost}")
        val winPct = if (t.matches > 0) "${"%.1f".format(t.wins * 100.0 / t.matches)}%" else "–"
        addRow(c, "Win %",               winPct)
    }

    // ── Head-to-Head ──────────────────────────────────────────────────────────

    private fun populateHeadToHead(t: TeamStats) {
        val allOpponents = (t.winsAgainstTeam.keys + t.lossesAgainstTeam.keys).toSortedSet()

        if (allOpponents.isEmpty()) {
            binding.tvNoH2H.visibility = View.VISIBLE
            return
        }

        val totalLabel = makeText("TOTAL", weight = 1f,
            color = getColor(R.color.gray_500), gravity = android.view.Gravity.CENTER)
        totalLabel.textSize = 10f
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }
        headerRow.addView(makeText("", weight = 2f))
        headerRow.addView(makeText("W", weight = 1f, color = getColor(R.color.gray_500),
            gravity = android.view.Gravity.CENTER).also { it.textSize = 10f })
        headerRow.addView(makeText("L", weight = 1f, color = getColor(R.color.gray_500),
            gravity = android.view.Gravity.CENTER).also { it.textSize = 10f })
        headerRow.addView(makeText("▶", weight = 0.6f, color = getColor(R.color.gray_500),
            gravity = android.view.Gravity.CENTER).also { it.textSize = 10f })

        allOpponents.forEach { opponent ->
            val w = t.winsAgainstTeam[opponent] ?: 0
            val l = t.lossesAgainstTeam[opponent] ?: 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(dpToPx(4), dpToPx(9), dpToPx(4), dpToPx(9))
                isClickable = true
                isFocusable = true
                background = android.util.TypedValue().also { tv ->
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                }.resourceId.let { getDrawable(it) }
                setOnClickListener {
                    startActivity(
                        android.content.Intent(this@TeamProfileActivity, TeamVsTeamActivity::class.java)
                            .putExtra("TEAM1_NAME", t.name)
                            .putExtra("TEAM2_NAME", opponent)
                    )
                }
            }

            row.addView(makeText(opponent, weight = 2f, bold = true))
            row.addView(makeText("$w", weight = 1f, color = getColor(R.color.success), gravity = android.view.Gravity.CENTER))
            row.addView(makeText("$l", weight = 1f, color = getColor(R.color.error),   gravity = android.view.Gravity.CENTER))
            row.addView(makeText("›", weight = 0.6f, color = getColor(R.color.purple_500),
                gravity = android.view.Gravity.CENTER).also { it.textSize = 16f })

            binding.containerH2H.addView(row)
            addThinDivider(binding.containerH2H)
        }
    }

    // ── Top Players ───────────────────────────────────────────────────────────

    private fun populateTopPlayers(teamName: String) {
        val teamPlayers = AppDataStore.players.values.filter { it.team == teamName }

        if (teamPlayers.isEmpty()) {
            val msg = TextView(this).apply {
                text = "No player data available"
                setTextColor(getColor(R.color.gray_500))
                textSize = 13f
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
            }
            binding.containerTopPlayers.addView(msg)
            return
        }

        val c = binding.containerTopPlayers

        // Top batsmen (top 5 by runs)
        val batsmen = teamPlayers.filter { it.runs > 0 }.sortedByDescending { it.runs }.take(5)
        if (batsmen.isNotEmpty()) {
            addSectionLabel(c, "🏏 Top Batsmen")
            batsmen.forEachIndexed { idx, p ->
                addClickableRow(c, "${idx + 1}. ${p.name}",
                    "${p.runs} runs (Avg: ${"%.1f".format(p.battingAverage)})", p.name)
            }
            addDivider(c)
        }

        // Top bowlers (top 5 by wickets)
        val bowlers = teamPlayers.filter { it.wickets > 0 }.sortedByDescending { it.wickets }.take(5)
        if (bowlers.isNotEmpty()) {
            addSectionLabel(c, "🎯 Top Bowlers")
            bowlers.forEachIndexed { idx, p ->
                addClickableRow(c, "${idx + 1}. ${p.name}",
                    "${p.wickets} wkts (Econ: ${"%.2f".format(p.economy)})", p.name)
            }
        }

        // "View all team players" button
        addDivider(c)
        val viewAllBtn = com.google.android.material.button.MaterialButton(this,
            null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "👥 View All Team Players"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpToPx(4) }
            setOnClickListener {
                startActivity(
                    android.content.Intent(this@TeamProfileActivity, TeamsPlayersActivity::class.java)
                )
            }
        }
        c.addView(viewAllBtn)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addRow(container: LinearLayout, label: String, value: String) {
        val b = ItemStatRowBinding.inflate(layoutInflater, container, false)
        b.tvLabel.text = label
        b.tvValue.text = value
        container.addView(b.root)
    }

    private fun addSectionLabel(container: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.gray_500))
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(4))
        }
        container.addView(tv)
    }

    private fun addDivider(container: LinearLayout) {
        val d = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                .also { it.setMargins(0, 4, 0, 4) }
            setBackgroundColor(getColor(R.color.gray_200))
        }
        container.addView(d)
    }

    private fun addThinDivider(container: LinearLayout) {
        val d = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(getColor(R.color.gray_200))
        }
        container.addView(d)
    }

    private fun makeText(
        text: String,
        weight: Float = 1f,
        bold: Boolean = false,
        color: Int = getColor(R.color.text_primary),
        gravity: Int = android.view.Gravity.START
    ): TextView = TextView(this).apply {
        this.text = text
        this.gravity = gravity
        textSize = 13f
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
    }

    private fun addClickableRow(container: LinearLayout, label: String, value: String, playerName: String) {
        val b = ItemStatRowBinding.inflate(layoutInflater, container, false)
        b.tvLabel.text = label
        b.tvValue.text = value
        b.root.isClickable = true
        b.root.isFocusable = true
        b.root.background = android.util.TypedValue().also { tv ->
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        }.resourceId.let { getDrawable(it) }
        b.root.setOnClickListener {
            startActivity(
                android.content.Intent(this, PlayerProfileActivity::class.java)
                    .putExtra("PLAYER_NAME", playerName)
            )
        }
        container.addView(b.root)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
