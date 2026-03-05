package com.example.handcricket

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.handcricket.databinding.ActivityTeamVsTeamBinding

class TeamVsTeamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamVsTeamBinding
    private lateinit var team1: String
    private lateinit var team2: String

    // Aggregated per-player stats in this H2H fixture
    data class H2HBatsman(
        val name: String,
        val team: String,
        var runs: Int = 0,
        var balls: Int = 0,
        var fours: Int = 0,
        var sixes: Int = 0,
        var innings: Int = 0
    )

    data class H2HBowler(
        val name: String,
        val team: String,
        var wickets: Int = 0,
        var runsConceded: Int = 0,
        var overs: Double = 0.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamVsTeamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        team1 = intent.getStringExtra("TEAM1_NAME") ?: return
        team2 = intent.getStringExtra("TEAM2_NAME") ?: return

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "$team1 vs $team2"
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        val data = buildH2HData()
        populateHeroCard(data)
        populateComparison(data)
        populateMatchHistory(data)
        populateTopBatsmen(data)
        populateTopBowlers(data)
        populatePlayersToWatch(data)
    }

    // ── Data Computation ──────────────────────────────────────────────────────

    private data class H2HData(
        val team1Wins: Int,
        val team2Wins: Int,
        val totalMatches: Int,
        // aggregate team stats across H2H matches
        val t1Runs: Int, val t1Wickets: Int, val t1Overs: Double,
        val t2Runs: Int, val t2Wickets: Int, val t2Overs: Double,
        val matches: List<Pair<Int, MatchSummary>>,   // (matchId, summary)
        val batsmen: List<H2HBatsman>,
        val bowlers: List<H2HBowler>
    )

    private fun buildH2HData(): H2HData {
        val summaries = AppDataStore.matchSummaries
        val perfs = AppDataStore.matchPerformances

        // Find all match indices (1-based) where these two teams played
        val h2hMatchesWithIds: List<Pair<Int, MatchSummary>> = summaries.mapIndexedNotNull { idx, ms ->
            val isH2H = (ms.team1Name == team1 && ms.team2Name == team2) ||
                        (ms.team1Name == team2 && ms.team2Name == team1)
            if (isH2H) (idx + 1) to ms else null
        }
        val h2hMatchIndices = h2hMatchesWithIds.map { it.first }.toSet()
        val h2hSummaries = h2hMatchesWithIds.map { it.second }

        // Count wins
        var t1Wins = 0; var t2Wins = 0
        var t1Runs = 0; var t2Runs = 0
        var t1Wickets = 0; var t2Wickets = 0
        var t1Overs = 0.0; var t2Overs = 0.0

        h2hSummaries.forEach { ms ->
            if (ms.winner == team1) t1Wins++ else if (ms.winner == team2) t2Wins++
            // Map runs/wickets to team1/team2 direction
            if (ms.team1Name == team1) {
                t1Runs += ms.team1Runs; t1Wickets += ms.team1Wickets; t1Overs += ms.team1Overs
                t2Runs += ms.team2Runs; t2Wickets += ms.team2Wickets; t2Overs += ms.team2Overs
            } else {
                t1Runs += ms.team2Runs; t1Wickets += ms.team2Wickets; t1Overs += ms.team2Overs
                t2Runs += ms.team1Runs; t2Wickets += ms.team1Wickets; t2Overs += ms.team1Overs
            }
        }

        // Aggregate per-player H2H stats from MatchPerformance
        val batsmenMap = mutableMapOf<String, H2HBatsman>()
        val bowlersMap = mutableMapOf<String, H2HBowler>()

        perfs.filter { it.matchId in h2hMatchIndices }.forEach { perf ->
            val playerTeam = AppDataStore.players[perf.player]?.team ?: "Unknown"
            if (playerTeam != team1 && playerTeam != team2) return@forEach

            // Batting
            if (perf.balls > 0 || perf.runs > 0) {
                val b = batsmenMap.getOrPut(perf.player) { H2HBatsman(perf.player, playerTeam) }
                b.runs += perf.runs
                b.balls += perf.balls
                b.fours += perf.fours
                b.sixes += perf.sixes
                b.innings++
            }
            // Bowling
            if (perf.overs > 0 || perf.wickets > 0) {
                val bw = bowlersMap.getOrPut(perf.player) { H2HBowler(perf.player, playerTeam) }
                bw.wickets += perf.wickets
                bw.runsConceded += perf.runsConceded
                bw.overs += perf.overs
            }
        }

        return H2HData(
            team1Wins = t1Wins, team2Wins = t2Wins,
            totalMatches = h2hSummaries.size,
            t1Runs = t1Runs, t1Wickets = t1Wickets, t1Overs = t1Overs,
            t2Runs = t2Runs, t2Wickets = t2Wickets, t2Overs = t2Overs,
            matches = h2hMatchesWithIds,
            batsmen = batsmenMap.values.sortedByDescending { it.runs },
            bowlers = bowlersMap.values.sortedByDescending { it.wickets }
        )
    }

    // ── Hero Card ────────────────────────────────────────────────────────────

    private fun populateHeroCard(d: H2HData) {
        binding.tvTeam1Name.text = team1
        binding.tvTeam2Name.text = team2
        binding.tvTeam1Wins.text = "${d.team1Wins}W"
        binding.tvTeam2Wins.text = "${d.team2Wins}W"
        binding.tvTotalMatches.text = "${d.totalMatches} Matches"

        binding.tvSeriesLeader.text = when {
            d.totalMatches == 0 -> "No matches yet"
            d.team1Wins > d.team2Wins -> "🏅 $team1 leads the series"
            d.team2Wins > d.team1Wins -> "🏅 $team2 leads the series"
            else -> "🤝 Series is tied"
        }
    }

    // ── Comparison Stats ─────────────────────────────────────────────────────

    private fun populateComparison(d: H2HData) {
        binding.tvCompHeader1.text = team1
        binding.tvCompHeader2.text = team2

        val t1Avg = if (d.t1Overs > 0) d.t1Runs / d.t1Overs else 0.0
        val t2Avg = if (d.t2Overs > 0) d.t2Runs / d.t2Overs else 0.0
        val t1WinPct = if (d.totalMatches > 0) d.team1Wins * 100.0 / d.totalMatches else 0.0
        val t2WinPct = if (d.totalMatches > 0) d.team2Wins * 100.0 / d.totalMatches else 0.0

        val rows = listOf(
            Triple("Wins", "${d.team1Wins}", "${d.team2Wins}"),
            Triple("Win %", "${"%.0f".format(t1WinPct)}%", "${"%.0f".format(t2WinPct)}%"),
            Triple("Runs Scored", "${d.t1Runs}", "${d.t2Runs}"),
            Triple("Wickets Taken", "${d.t1Wickets}", "${d.t2Wickets}"),
            Triple("Run Rate", "${"%.2f".format(t1Avg)}", "${"%.2f".format(t2Avg)}"),
            Triple("Avg Runs/Match",
                if (d.totalMatches > 0) "${"%.0f".format(d.t1Runs.toDouble() / d.totalMatches)}" else "–",
                if (d.totalMatches > 0) "${"%.0f".format(d.t2Runs.toDouble() / d.totalMatches)}" else "–")
        )

        rows.forEach { (stat, v1, v2) ->
            addComparisonRow(binding.containerComparison, v1, stat, v2,
                highlight1 = compareValues(v1, v2, stat))
        }
    }

    /** Returns true if v1 is "better" for the given stat label */
    private fun compareValues(v1: String, v2: String, stat: String): Boolean {
        val n1 = v1.replace("%", "").toDoubleOrNull() ?: return false
        val n2 = v2.replace("%", "").toDoubleOrNull() ?: return false
        return n1 > n2
    }

    private fun addComparisonRow(
        container: LinearLayout, v1: String, label: String, v2: String,
        highlight1: Boolean
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(4), dpToPx(7), dpToPx(4), dpToPx(7))
        }

        val green = getColor(R.color.success)
        val normal = getColor(R.color.text_primary)

        row.addView(makeText(v1, weight = 2f, bold = highlight1,
            color = if (highlight1) green else normal, gravity = Gravity.CENTER))
        row.addView(makeText(label, weight = 2f, color = getColor(R.color.gray_500),
            gravity = Gravity.CENTER))
        row.addView(makeText(v2, weight = 2f, bold = !highlight1,
            color = if (!highlight1) green else normal, gravity = Gravity.CENTER))

        container.addView(row)
        addThinDivider(container)
    }

    // ── Match History ─────────────────────────────────────────────────────────

    private var showAllH2HMatches = false

    private fun populateMatchHistory(d: H2HData) {
        if (d.matches.isEmpty()) {
            binding.tvNoMatches.visibility = View.VISIBLE
            return
        }

        binding.containerMatchHistory.removeAllViews()

        val displayList = if (!showAllH2HMatches && d.matches.size > 10)
            d.matches.take(10) else d.matches

        displayList.forEachIndexed { idx, (matchId, ms) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(10))
                isClickable = true
                isFocusable = true
                background = android.util.TypedValue().also { tv ->
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                }.resourceId.let { getDrawable(it) }
                setOnClickListener {
                    startActivity(
                        Intent(this@TeamVsTeamActivity, MatchScorecardActivity::class.java)
                            .putExtra("MATCH_ID", matchId)
                    )
                }
            }

            val matchNumLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val matchLabel = makeText("Match $matchId  ›", weight = 0f, textSize = 10f,
                color = getColor(R.color.purple_500)).apply { layoutParams = matchNumLp }

            val scoreRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val winnerColor = getColor(R.color.success)
            val loserColor = getColor(R.color.text_primary)

            scoreRow.addView(makeText("${ms.team1Name}: ${ms.team1Runs}/${ms.team1Wickets}", weight = 1f,
                color = if (ms.winner == ms.team1Name) winnerColor else loserColor,
                bold = ms.winner == ms.team1Name))
            scoreRow.addView(makeText("${ms.team2Name}: ${ms.team2Runs}/${ms.team2Wickets}", weight = 1f,
                color = if (ms.winner == ms.team2Name) winnerColor else loserColor,
                bold = ms.winner == ms.team2Name))

            val resultLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(2) }
            val resultLabel = makeText("🏆 ${ms.winner} won", weight = 0f, textSize = 11f,
                color = getColor(R.color.success)).apply { layoutParams = resultLp }

            row.addView(matchLabel)
            row.addView(scoreRow)
            row.addView(resultLabel)

            binding.containerMatchHistory.addView(row)
            addThinDivider(binding.containerMatchHistory)
        }

        // "Show all" button if limited
        if (!showAllH2HMatches && d.matches.size > 10) {
            val showAllBtn = makeText("Show all ${d.matches.size} matches →", weight = 0f,
                textSize = 13f, color = getColor(R.color.purple_700)).apply {
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(12), 0, dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                isClickable = true
                isFocusable = true
                background = android.util.TypedValue().also { tv ->
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                }.resourceId.let { getDrawable(it) }
                setOnClickListener {
                    showAllH2HMatches = true
                    populateMatchHistory(d)
                }
            }
            binding.containerMatchHistory.addView(showAllBtn)
        }
    }

    // ── Top Batsmen ───────────────────────────────────────────────────────────

    private fun populateTopBatsmen(d: H2HData) {
        d.batsmen.take(8).forEach { b ->
            val avg = if (b.innings > 0) b.runs.toDouble() / b.innings else 0.0
            val sr = if (b.balls > 0) b.runs * 100.0 / b.balls else 0.0
            addPlayerRow(
                container = binding.containerTopBatsmen,
                name = b.name,
                team = b.team,
                col1 = "${b.runs}",
                col2 = "${"%.1f".format(avg)}",
                col3 = "${"%.0f".format(sr)}"
            )
        }
        if (d.batsmen.isEmpty()) {
            binding.containerTopBatsmen.addView(makeEmptyMsg("No batting data"))
        }
    }

    // ── Top Bowlers ───────────────────────────────────────────────────────────

    private fun populateTopBowlers(d: H2HData) {
        d.bowlers.take(8).forEach { bw ->
            val econ = if (bw.overs > 0) bw.runsConceded / bw.overs else 0.0
            addPlayerRow(
                container = binding.containerTopBowlers,
                name = bw.name,
                team = bw.team,
                col1 = "${bw.wickets}",
                col2 = "${bw.runsConceded}",
                col3 = "${"%.2f".format(econ)}"
            )
        }
        if (d.bowlers.isEmpty()) {
            binding.containerTopBowlers.addView(makeEmptyMsg("No bowling data"))
        }
    }

    private fun addPlayerRow(
        container: LinearLayout, name: String, team: String,
        col1: String, col2: String, col3: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
            isClickable = true
            isFocusable = true
            background = android.util.TypedValue().also {
                theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }.resourceId.let { getDrawable(it) }
            setOnClickListener {
                startActivity(Intent(this@TeamVsTeamActivity, PlayerProfileActivity::class.java)
                    .putExtra("PLAYER_NAME", name))
            }
        }

        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        }
        nameCol.addView(makeText(name, weight = 0f, bold = true, textSize = 13f))
        nameCol.addView(makeText(team, weight = 0f, textSize = 10f,
            color = getColor(R.color.purple_500)).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(1) }
        })

        row.addView(nameCol)
        row.addView(makeText(col1, weight = 1f, bold = true, gravity = Gravity.CENTER))
        row.addView(makeText(col2, weight = 1f, gravity = Gravity.CENTER,
            color = getColor(R.color.gray_600)))
        row.addView(makeText(col3, weight = 1f, gravity = Gravity.CENTER,
            color = getColor(R.color.gray_600)))

        container.addView(row)
        addThinDivider(container)
    }

    // ── Players to Watch ─────────────────────────────────────────────────────

    private fun populatePlayersToWatch(d: H2HData) {
        binding.tvWatch1TeamLabel.text = team1.uppercase()
        binding.tvWatch2TeamLabel.text = team2.uppercase()

        // Team 1 key player: best batsman or bowler from team1 in H2H
        val t1Batsman = d.batsmen.firstOrNull { it.team == team1 }
        val t1Bowler = d.bowlers.firstOrNull { it.team == team1 }
        val t1Star = t1Batsman?.takeIf { it.runs >= (t1Bowler?.wickets ?: 0) * 20 } ?: run {
            // pick better performer
            if ((t1Batsman?.runs ?: 0) > 0) t1Batsman else null
        }
        if (t1Star != null) {
            binding.tvWatch1Name.text = t1Star.name
            val sr = if (t1Star.balls > 0) t1Star.runs * 100.0 / t1Star.balls else 0.0
            binding.tvWatch1Stat.text = "${t1Star.runs} runs • SR ${"%.0f".format(sr)}"
        } else if (t1Bowler != null) {
            binding.tvWatch1Name.text = t1Bowler.name
            val econ = if (t1Bowler.overs > 0) t1Bowler.runsConceded / t1Bowler.overs else 0.0
            binding.tvWatch1Stat.text = "${t1Bowler.wickets} wkts • Econ ${"%.2f".format(econ)}"
        } else {
            binding.tvWatch1Name.text = "–"
        }

        // Team 2 key player
        val t2Batsman = d.batsmen.firstOrNull { it.team == team2 }
        val t2Bowler = d.bowlers.firstOrNull { it.team == team2 }
        val t2Star = t2Batsman?.takeIf { it.runs >= (t2Bowler?.wickets ?: 0) * 20 } ?: run {
            if ((t2Batsman?.runs ?: 0) > 0) t2Batsman else null
        }
        if (t2Star != null) {
            binding.tvWatch2Name.text = t2Star.name
            val sr = if (t2Star.balls > 0) t2Star.runs * 100.0 / t2Star.balls else 0.0
            binding.tvWatch2Stat.text = "${t2Star.runs} runs • SR ${"%.0f".format(sr)}"
        } else if (t2Bowler != null) {
            binding.tvWatch2Name.text = t2Bowler.name
            val econ = if (t2Bowler.overs > 0) t2Bowler.runsConceded / t2Bowler.overs else 0.0
            binding.tvWatch2Stat.text = "${t2Bowler.wickets} wkts • Econ ${"%.2f".format(econ)}"
        } else {
            binding.tvWatch2Name.text = "–"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeText(
        text: String,
        weight: Float = 1f,
        bold: Boolean = false,
        color: Int = getColor(R.color.text_primary),
        gravity: Int = Gravity.START,
        textSize: Float = 13f
    ): TextView = TextView(this).apply {
        this.text = text
        this.gravity = gravity
        this.textSize = textSize
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = if (weight > 0f)
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        else
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
    }

    private fun addThinDivider(container: LinearLayout) {
        val d = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(getColor(R.color.gray_200))
        }
        container.addView(d)
    }

    private fun makeEmptyMsg(msg: String): TextView = TextView(this).apply {
        text = msg
        textSize = 13f
        setTextColor(getColor(R.color.gray_500))
        gravity = Gravity.CENTER
        setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
