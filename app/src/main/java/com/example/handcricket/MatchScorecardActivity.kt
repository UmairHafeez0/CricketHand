package com.example.handcricket

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.handcricket.databinding.ActivityMatchScorecardBinding

class MatchScorecardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchScorecardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchScorecardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val matchId = intent.getIntExtra("MATCH_ID", -1)
        if (matchId < 1 || matchId > AppDataStore.matchSummaries.size) {
            finish(); return
        }

        val summary = AppDataStore.matchSummaries[matchId - 1]
        val perfs = AppDataStore.matchPerformances.filter { it.matchId == matchId }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "${summary.team1Name} vs ${summary.team2Name}"
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        populateHeader(summary)
        populateScorecard(summary, perfs)
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun populateHeader(ms: MatchSummary) {
        binding.tvTeam1Name.text = ms.team1Name
        binding.tvTeam2Name.text = ms.team2Name
        binding.tvTeam1Score.text = "${ms.team1Runs}/${ms.team1Wickets}"
        binding.tvTeam2Score.text = "${ms.team2Runs}/${ms.team2Wickets}"
        binding.tvTeam1Overs.text = "(${formatOvers(ms.team1Overs)} ov)"
        binding.tvTeam2Overs.text = "(${formatOvers(ms.team2Overs)} ov)"
        binding.tvWinner.text = if (ms.winner.isNotEmpty()) "🏆 ${ms.winner} won" else "No result"

        // Dim the loser's score
        val winColor = getColor(R.color.white)
        val loseColor = 0xAAFFFFFF.toInt()
        if (ms.winner == ms.team1Name) {
            binding.tvTeam1Score.setTextColor(winColor)
            binding.tvTeam2Score.setTextColor(loseColor)
        } else {
            binding.tvTeam1Score.setTextColor(loseColor)
            binding.tvTeam2Score.setTextColor(winColor)
        }
    }

    // ── Scorecard ─────────────────────────────────────────────────────────────

    private fun populateScorecard(ms: MatchSummary, perfs: List<MatchPerformance>) {
        // Separate perfs by team
        val team1Perfs = perfs.filter { AppDataStore.players[it.player]?.team == ms.team1Name }
        val team2Perfs = perfs.filter { AppDataStore.players[it.player]?.team == ms.team2Name }

        // Section titles
        binding.tvBatting1Title.text = "🏏 ${ms.team1Name} BATTING"
        binding.tvBatting2Title.text = "🏏 ${ms.team2Name} BATTING"
        binding.tvBowling2Title.text = "🎯 ${ms.team2Name} BOWLING"
        binding.tvBowling1Title.text = "🎯 ${ms.team1Name} BOWLING"

        // Team 1 batting (sorted by runs desc)
        val t1Batters = team1Perfs.filter { it.balls > 0 || it.runs > 0 }.sortedByDescending { it.runs }
        if (t1Batters.isNotEmpty()) {
            t1Batters.forEach { addBattingRow(binding.containerBatting1, it) }
        } else {
            binding.containerBatting1.addView(emptyMsg("No batting data"))
        }

        // Team 2 bowling (sorted by wickets desc)
        val t2Bowlers = team2Perfs.filter { it.overs > 0 || it.wickets > 0 }.sortedWith(
            compareByDescending<MatchPerformance> { it.wickets }.thenBy { it.runsConceded }
        )
        if (t2Bowlers.isNotEmpty()) {
            t2Bowlers.forEach { addBowlingRow(binding.containerBowling2, it) }
        } else {
            binding.containerBowling2.addView(emptyMsg("No bowling data"))
        }

        // Team 2 batting
        val t2Batters = team2Perfs.filter { it.balls > 0 || it.runs > 0 }.sortedByDescending { it.runs }
        if (t2Batters.isNotEmpty()) {
            t2Batters.forEach { addBattingRow(binding.containerBatting2, it) }
        } else {
            binding.containerBatting2.addView(emptyMsg("No batting data"))
        }

        // Team 1 bowling
        val t1Bowlers = team1Perfs.filter { it.overs > 0 || it.wickets > 0 }.sortedWith(
            compareByDescending<MatchPerformance> { it.wickets }.thenBy { it.runsConceded }
        )
        if (t1Bowlers.isNotEmpty()) {
            t1Bowlers.forEach { addBowlingRow(binding.containerBowling1, it) }
        } else {
            binding.containerBowling1.addView(emptyMsg("No bowling data"))
        }
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    private fun addBattingRow(container: LinearLayout, perf: MatchPerformance) {
        val sr = if (perf.balls > 0) perf.runs * 100.0 / perf.balls else 0.0
        val isHighScore = perf.runs >= 50

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(7), 0, dpToPx(7))
            isClickable = true
            isFocusable = true
            background = android.util.TypedValue().also {
                theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }.resourceId.let { getDrawable(it) }
            setOnClickListener {
                startActivity(
                    Intent(this@MatchScorecardActivity, PlayerProfileActivity::class.java)
                        .putExtra("PLAYER_NAME", perf.player)
                )
            }
        }

        val nameColor = if (isHighScore) getColor(R.color.purple_700) else getColor(R.color.text_primary)
        row.addView(makeText(perf.player, weight = 3f, bold = isHighScore, color = nameColor))
        row.addView(makeText("${perf.runs}", weight = 1f, bold = isHighScore,
            color = if (isHighScore) getColor(R.color.purple_700) else getColor(R.color.text_primary),
            gravity = Gravity.CENTER))
        row.addView(makeText("${perf.balls}", weight = 1f, color = getColor(R.color.gray_600),
            gravity = Gravity.CENTER))
        row.addView(makeText("${perf.fours}", weight = 1f, color = getColor(R.color.gray_600),
            gravity = Gravity.CENTER))
        row.addView(makeText("${perf.sixes}", weight = 1f, color = getColor(R.color.gray_600),
            gravity = Gravity.CENTER))
        row.addView(makeText("${"%.0f".format(sr)}", weight = 1f, color = getColor(R.color.gray_600),
            gravity = Gravity.END))

        container.addView(row)
        addThinDivider(container)
    }

    private fun addBowlingRow(container: LinearLayout, perf: MatchPerformance) {
        val econ = if (perf.overs > 0) perf.runsConceded / perf.overs else 0.0
        val goodFigures = perf.wickets >= 3

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(7), 0, dpToPx(7))
            isClickable = true
            isFocusable = true
            background = android.util.TypedValue().also {
                theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }.resourceId.let { getDrawable(it) }
            setOnClickListener {
                startActivity(
                    Intent(this@MatchScorecardActivity, PlayerProfileActivity::class.java)
                        .putExtra("PLAYER_NAME", perf.player)
                )
            }
        }

        val wicketColor = if (goodFigures) getColor(R.color.success) else getColor(R.color.text_primary)
        row.addView(makeText(perf.player, weight = 3f, bold = goodFigures,
            color = if (goodFigures) getColor(R.color.success) else getColor(R.color.text_primary)))
        row.addView(makeText(formatOvers(perf.overs), weight = 1f,
            color = getColor(R.color.gray_600), gravity = Gravity.CENTER))
        row.addView(makeText("${perf.runsConceded}", weight = 1f,
            color = getColor(R.color.gray_600), gravity = Gravity.CENTER))
        row.addView(makeText("${perf.wickets}", weight = 1f, bold = goodFigures,
            color = wicketColor, gravity = Gravity.CENTER))
        row.addView(makeText("${"%.2f".format(econ)}", weight = 1f,
            color = getColor(R.color.gray_600), gravity = Gravity.END))

        container.addView(row)
        addThinDivider(container)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeText(
        text: String,
        weight: Float = 1f,
        bold: Boolean = false,
        color: Int = getColor(R.color.text_primary),
        gravity: Int = Gravity.START,
        textSize: Float = 12f
    ): TextView = TextView(this).apply {
        this.text = text
        this.gravity = gravity
        this.textSize = textSize
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
    }

    private fun addThinDivider(container: LinearLayout) {
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(getColor(R.color.gray_200))
        })
    }

    private fun emptyMsg(msg: String) = TextView(this).apply {
        text = msg
        textSize = 12f
        setTextColor(getColor(R.color.gray_500))
        gravity = Gravity.CENTER
        setPadding(0, dpToPx(10), 0, dpToPx(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun formatOvers(overs: Double): String {
        val full = overs.toInt()
        val balls = ((overs - full) * 10).toInt()
        return if (balls == 0) "$full" else "$full.$balls"
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
