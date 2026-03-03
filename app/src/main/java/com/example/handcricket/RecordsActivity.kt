package com.example.handcricket

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.ActivityRecordsBinding
import com.example.handcricket.databinding.ItemRecordCardBinding
import com.google.android.material.chip.Chip

// ── Data model for one record entry ──────────────────────────────────────────

data class RecordItem(
    val section: String,         // e.g. "Batting • Innings"
    val emoji: String,
    val title: String,
    val topHolder: String,       // name of record holder
    val topValue: String,        // prominent value
    val details: String,         // context (vs team, match #)
    val allEntries: List<TopPlayer>   // full sorted list for "View All"
)

// ── Activity ──────────────────────────────────────────────────────────────────

class RecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordsBinding
    private lateinit var adapter: RecordCardAdapter
    private var allRecords = listOf<RecordItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        allRecords = buildRecords()

        if (allRecords.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            return
        }

        setupSectionChips()

        adapter = RecordCardAdapter { record ->
            startActivity(Intent(this, DetailedStatsActivity::class.java).apply {
                putExtra("CATEGORY_TITLE", record.title)
                putExtra("CATEGORY_DESCRIPTION", record.section)
                putExtra("CATEGORY_TYPE", "GENERIC")
                putParcelableArrayListExtra("TOP_PLAYERS", ArrayList(record.allEntries))
            })
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        adapter.submitList(allRecords)
    }

    // ── Section chips ─────────────────────────────────────────────────────────

    private fun setupSectionChips() {
        val sections = listOf("All") +
            allRecords.map { it.section }.distinct()

        sections.forEach { sec ->
            val chip = Chip(this).apply {
                text = sec
                isCheckable = true
                isChecked = (sec == "All")
            }
            binding.chipGroupSection.addView(chip)
        }

        binding.chipGroupSection.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selected = group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "All"
            val filtered = if (selected == "All") allRecords
            else allRecords.filter { it.section == selected }
            adapter.submitList(filtered)
        }
    }

    // ── Record computation ────────────────────────────────────────────────────

    private fun buildRecords(): List<RecordItem> {
        val records = mutableListOf<RecordItem>()
        val players = AppDataStore.players.values.toList()
        val performances = AppDataStore.matchPerformances
        val summaries = AppDataStore.matchSummaries

        if (players.isEmpty() && performances.isEmpty()) return records

        // Build matchId → MatchSummary map (matchId is 1-based)
        val matchMap = summaries.mapIndexed { idx, ms -> (idx + 1) to ms }.toMap()

        // Build per-player per-opponent fours/sixes from MatchPerformance
        val foursVsTeam  = mutableMapOf<String, MutableMap<String, Int>>()
        val sixesVsTeam  = mutableMapOf<String, MutableMap<String, Int>>()

        performances.forEach { mp ->
            val ms = matchMap[mp.matchId] ?: return@forEach
            val pTeam = AppDataStore.players[mp.player]?.team ?: return@forEach
            val opp = when (pTeam) {
                ms.team1Name -> ms.team2Name
                ms.team2Name -> ms.team1Name
                else -> return@forEach
            }
            foursVsTeam.getOrPut(mp.player) { mutableMapOf() }.merge(opp, mp.fours, Int::plus)
            sixesVsTeam.getOrPut(mp.player) { mutableMapOf() }.merge(opp, mp.sixes, Int::plus)
        }

        // ── GROUP 1 : Batting – Best Innings Performance ───────────────────────

        val sec1 = "🏏 Batting · Innings"

        // 1. Highest Individual Score
        performances.filter { it.runs > 0 }
            .sortedByDescending { it.runs }
            .takeIf { it.isNotEmpty() }?.let { list ->
                records += makeRecord(sec1, "🏅", "Highest Individual Score",
                    list.first(), matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.runs} runs (${mp.balls} balls)",
                            opponentOf(matchMap, mp.matchId, mp.player))
                    })
            }

        // 2. Most Fours in Single Innings
        performances.filter { it.fours > 0 }
            .sortedByDescending { it.fours }
            .takeIf { it.isNotEmpty() }?.let { list ->
                records += makeRecord(sec1, "4️⃣", "Most Fours in Single Innings",
                    list.first(), matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.fours} fours",
                            "${mp.runs} runs · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${list.first().fours} fours")
            }

        // 3. Most Sixes in Single Innings
        performances.filter { it.sixes > 0 }
            .sortedByDescending { it.sixes }
            .takeIf { it.isNotEmpty() }?.let { list ->
                records += makeRecord(sec1, "6️⃣", "Most Sixes in Single Innings",
                    list.first(), matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.sixes} sixes",
                            "${mp.runs} runs · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${list.first().sixes} sixes")
            }

        // 4. Most Boundaries (4s + 6s) in Single Innings
        performances.filter { it.fours + it.sixes > 0 }
            .sortedByDescending { it.fours + it.sixes }
            .takeIf { it.isNotEmpty() }?.let { list ->
                records += makeRecord(sec1, "💥", "Most Boundaries in Single Innings",
                    list.first(), matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.fours + mp.sixes} boundaries",
                            "${mp.fours}×4s, ${mp.sixes}×6s · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${list.first().fours + list.first().sixes} boundaries")
            }

        // 5. Fastest Half-Century (fewest balls for 50+)
        performances.filter { it.runs >= 50 && it.balls > 0 }
            .sortedBy { it.balls }
            .takeIf { it.isNotEmpty() }?.let { list ->
                records += makeRecord(sec1, "⚡", "Fastest Half-Century",
                    list.first(), matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.balls} balls for ${mp.runs} runs",
                            "SR: ${"%.1f".format(mp.runs * 100.0 / mp.balls)} · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${list.first().balls} balls")
            }

        // 6. Fastest Century (fewest balls for 100+)
        performances.filter { it.runs >= 100 && it.balls > 0 }
            .sortedBy { it.balls }
            .takeIf { it.isNotEmpty() }?.let { list ->
                records += makeRecord(sec1, "💯", "Fastest Century",
                    list.first(), matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.balls} balls for ${mp.runs} runs",
                            "SR: ${"%.1f".format(mp.runs * 100.0 / mp.balls)} · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${list.first().balls} balls")
            }

        // ── GROUP 2 : Batting – Tournament Aggregate ──────────────────────────

        val sec2 = "🏏 Batting · Tournament"

        // 7. Most Runs in Tournament
        players.filter { it.runs > 0 }.sortedByDescending { it.runs }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "🏆", "Most Runs in Tournament",
                    top.name, "${top.runs} runs",
                    "Avg: ${"%.1f".format(top.battingAverage)} · SR: ${"%.1f".format(top.strikeRate)}",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.runs} runs (${p.balls} balls)",
                            "Avg: ${"%.1f".format(p.battingAverage)} · SR: ${"%.1f".format(p.strikeRate)} · HS: ${p.highestScore}")
                    })
            }

        // 8. Most Fours in Tournament
        players.filter { it.fours > 0 }.sortedByDescending { it.fours }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "4️⃣", "Most Fours in Tournament",
                    top.name, "${top.fours} fours", "${top.runs} runs overall",
                    list.take(30).map { p -> TopPlayer(p.name, "${p.fours} fours", "${p.runs} runs · ${p.sixes} sixes") })
            }

        // 9. Most Sixes in Tournament
        players.filter { it.sixes > 0 }.sortedByDescending { it.sixes }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "6️⃣", "Most Sixes in Tournament",
                    top.name, "${top.sixes} sixes", "${top.runs} runs overall",
                    list.take(30).map { p -> TopPlayer(p.name, "${p.sixes} sixes", "${p.runs} runs · ${p.fours} fours") })
            }

        // 10. Most Centuries
        players.filter { it.centuries > 0 }.sortedByDescending { it.centuries }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "💯", "Most Centuries",
                    top.name, "${top.centuries}×100s", "HS: ${top.highestScore} · ${top.runs} runs",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.centuries}×100s, ${p.halfCenturies}×50s",
                            "HS: ${p.highestScore} · ${p.runs} runs")
                    })
            }

        // 11. Most Half-Centuries
        players.filter { it.halfCenturies > 0 }.sortedByDescending { it.halfCenturies }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "5️⃣", "Most Half-Centuries",
                    top.name, "${top.halfCenturies}×50s", "${top.runs} total runs",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.halfCenturies}×50s (${p.centuries}×100s)", "${p.runs} total runs")
                    })
            }

        // 12. Best Batting Average (min 3 innings)
        players.filter { it.innings >= 3 }.sortedByDescending { it.battingAverage }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "📊", "Best Batting Average (min 3 innings)",
                    top.name, "${"%.2f".format(top.battingAverage)} avg",
                    "${top.runs} runs in ${top.innings} innings",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "Avg: ${"%.2f".format(p.battingAverage)}",
                            "${p.runs} runs · ${p.innings} innings · HS: ${p.highestScore}")
                    })
            }

        // 13. Best Strike Rate (min 30 balls)
        players.filter { it.balls >= 30 }.sortedByDescending { it.strikeRate }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "⚡", "Best Batting Strike Rate (min 30 balls)",
                    top.name, "${"%.1f".format(top.strikeRate)} SR",
                    "${top.runs} runs in ${top.balls} balls",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "SR: ${"%.2f".format(p.strikeRate)}",
                            "${p.runs} runs in ${p.balls} balls · ${p.fours}×4s ${p.sixes}×6s")
                    })
            }

        // 14. Most Matches Played
        players.filter { it.matches > 0 }.sortedByDescending { it.matches }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "📅", "Most Matches Played",
                    top.name, "${top.matches} matches", "${top.runs} runs · ${top.wickets} wickets",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.matches} matches", "${p.runs}R · ${p.wickets}W")
                    })
            }

        // 15. Most Ducks
        players.filter { it.ducks > 0 }.sortedByDescending { it.ducks }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec2, "🦆", "Most Ducks (0 scores)",
                    top.name, "${top.ducks} ducks", "${top.innings} innings played",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.ducks} ducks", "${p.innings} innings · ${p.runs} runs")
                    })
            }

        // ── GROUP 3 : Batting – vs Team Records ───────────────────────────────

        val sec3 = "🏏 Batting · vs Team"

        // 16. Most Runs vs Single Team (career)
        buildMaxVsTeamList(players) { p -> p.runsAgainstTeam }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val (player, team, value) = list.first()
                records += RecordItem(sec3, "🎯", "Most Runs vs Single Team",
                    player, "$value runs", "vs $team",
                    list.take(30).map { (pl, tm, v) ->
                        TopPlayer(pl, "$v runs", "vs $tm")
                    })
            }

        // 17. Most Fours vs Single Team
        buildMaxVsTeamListFromPerf(foursVsTeam, "fours")
            .takeIf { it.isNotEmpty() }?.let { list ->
                val (player, team, value) = list.first()
                records += RecordItem(sec3, "4️⃣", "Most Fours vs Single Team",
                    player, "$value fours", "vs $team",
                    list.take(30).map { (pl, tm, v) -> TopPlayer(pl, "$v fours", "vs $tm") })
            }

        // 18. Most Sixes vs Single Team
        buildMaxVsTeamListFromPerf(sixesVsTeam, "sixes")
            .takeIf { it.isNotEmpty() }?.let { list ->
                val (player, team, value) = list.first()
                records += RecordItem(sec3, "6️⃣", "Most Sixes vs Single Team",
                    player, "$value sixes", "vs $team",
                    list.take(30).map { (pl, tm, v) -> TopPlayer(pl, "$v sixes", "vs $tm") })
            }

        // ── GROUP 4 : Bowling – Best Match Performance ────────────────────────

        val sec4 = "🎯 Bowling · Match"

        // 19. Most Wickets in Single Match
        performances.filter { it.wickets > 0 }
            .sortedWith(compareByDescending<MatchPerformance> { it.wickets }.thenBy { it.runsConceded })
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += makeRecord(sec4, "🔥", "Most Wickets in Single Match",
                    top, matchMap,
                    list.take(30).map { mp ->
                        TopPlayer(mp.player, "${mp.wickets}/${mp.runsConceded}",
                            "${"%.1f".format(mp.overs)} overs · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${top.wickets}/${top.runsConceded}")
            }

        // 20. Best Economy in Single Match (min 2 overs)
        performances.filter { it.overs >= 2 && it.runsConceded >= 0 }
            .sortedBy { if (it.overs > 0) it.runsConceded / it.overs else Double.MAX_VALUE }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                val econ = if (top.overs > 0) top.runsConceded / top.overs else 0.0
                records += makeRecord(sec4, "💨", "Best Economy in Single Match (min 2 overs)",
                    top, matchMap,
                    list.take(30).map { mp ->
                        val e = if (mp.overs > 0) mp.runsConceded / mp.overs else 0.0
                        TopPlayer(mp.player, "${"%.2f".format(e)} econ",
                            "${mp.wickets}W/${mp.runsConceded}R in ${"%.1f".format(mp.overs)} ovs · ${opponentOf(matchMap, mp.matchId, mp.player)}")
                    }, valueOverride = "${"%.2f".format(econ)} econ")
            }

        // ── GROUP 5 : Bowling – Tournament Aggregate ──────────────────────────

        val sec5 = "🎯 Bowling · Tournament"

        // 21. Most Wickets in Tournament
        players.filter { it.wickets > 0 }.sortedByDescending { it.wickets }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec5, "🏆", "Most Wickets in Tournament",
                    top.name, "${top.wickets} wickets",
                    "Avg: ${"%.2f".format(top.bowlingAverage)} · Econ: ${"%.2f".format(top.economy)}",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.wickets} wickets",
                            "Avg: ${"%.2f".format(p.bowlingAverage)} · Econ: ${"%.2f".format(p.economy)} · Best: ${p.bestBowlingWickets}/${p.bestBowlingRuns}")
                    })
            }

        // 22. Best Economy Rate (min 5 overs)
        players.filter { it.overs >= 5 }.sortedBy { it.economy }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec5, "💨", "Best Economy Rate (min 5 overs)",
                    top.name, "${"%.2f".format(top.economy)} econ",
                    "${top.wickets}W in ${"%.1f".format(top.overs)} overs",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "Econ: ${"%.2f".format(p.economy)}",
                            "${p.wickets}W · ${p.runsGiven}R in ${"%.1f".format(p.overs)} overs")
                    })
            }

        // 23. Best Bowling Average (min 5 wickets)
        players.filter { it.wickets >= 5 }.sortedBy { it.bowlingAverage }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec5, "📊", "Best Bowling Average (min 5 wickets)",
                    top.name, "${"%.2f".format(top.bowlingAverage)} avg",
                    "${top.wickets} wickets",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "Avg: ${"%.2f".format(p.bowlingAverage)}",
                            "${p.wickets}W · ${p.runsGiven}R · Econ: ${"%.2f".format(p.economy)}")
                    })
            }

        // 24. Best Bowling Strike Rate (min 5 wickets)
        players.filter { it.wickets >= 5 && it.bowlingStrikeRate > 0 }.sortedBy { it.bowlingStrikeRate }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec5, "⚡", "Best Bowling Strike Rate (min 5 wkts)",
                    top.name, "${"%.1f".format(top.bowlingStrikeRate)} BSR",
                    "${top.wickets} wickets in ${"%.1f".format(top.overs)} overs",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "BSR: ${"%.1f".format(p.bowlingStrikeRate)}",
                            "${p.wickets}W · ${"%.1f".format(p.overs)} overs · Avg: ${"%.2f".format(p.bowlingAverage)}")
                    })
            }

        // 25. Most 5-Wicket Hauls
        players.filter { it.fiveWicketHauls > 0 }.sortedByDescending { it.fiveWicketHauls }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec5, "🌟", "Most 5-Wicket Hauls",
                    top.name, "${top.fiveWicketHauls}×5W",
                    "${top.wickets} total wickets",
                    list.take(30).map { p ->
                        TopPlayer(p.name, "${p.fiveWicketHauls}×5W hauls",
                            "Also: ${p.fourWicketHauls}×4W, ${p.threeWicketHauls}×3W · ${p.wickets} total wickets")
                    })
            }

        // 26. Most 3-Wicket+ Hauls
        players.filter { it.threeWicketHauls + it.fourWicketHauls + it.fiveWicketHauls > 0 }
            .sortedByDescending { it.threeWicketHauls + it.fourWicketHauls + it.fiveWicketHauls }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                val total = top.threeWicketHauls + top.fourWicketHauls + top.fiveWicketHauls
                records += RecordItem(sec5, "🔥", "Most 3+ Wicket Hauls",
                    top.name, "$total hauls",
                    "${top.threeWicketHauls}×3W, ${top.fourWicketHauls}×4W, ${top.fiveWicketHauls}×5W",
                    list.take(30).map { p ->
                        val t = p.threeWicketHauls + p.fourWicketHauls + p.fiveWicketHauls
                        TopPlayer(p.name, "$t multi-wicket hauls",
                            "${p.threeWicketHauls}×3W · ${p.fourWicketHauls}×4W · ${p.fiveWicketHauls}×5W")
                    })
            }

        // 27. Most Wickets vs Single Team
        buildMaxVsTeamList(players) { p -> p.wicketsAgainstTeam }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val (player, team, value) = list.first()
                records += RecordItem(sec5, "🎯", "Most Wickets vs Single Team",
                    player, "$value wickets", "vs $team",
                    list.take(30).map { (pl, tm, v) -> TopPlayer(pl, "$v wickets", "vs $tm") })
            }

        // ── GROUP 6 : Team Records ────────────────────────────────────────────

        val sec6 = "🏳 Team Records"
        val teams = AppDataStore.teams.values.toList()

        // 28. Highest Team Total (single innings)
        teams.filter { it.highestTeamScore > 0 }
            .sortedByDescending { it.highestTeamScore }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec6, "🏏", "Highest Team Total",
                    top.name, "${top.highestTeamScore} runs", "${top.wins}W ${top.losses}L overall",
                    list.take(30).map { t ->
                        TopPlayer(t.name, "${t.highestTeamScore} (highest)",
                            "${t.wins}W ${t.losses}L · NRR: ${"%.3f".format(t.nrr)}")
                    })
            }

        // 29. Most Wins in Tournament
        teams.filter { it.wins > 0 }.sortedByDescending { it.wins }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec6, "🏆", "Most Wins in Tournament",
                    top.name, "${top.wins} wins", "from ${top.matches} matches",
                    list.take(30).map { t ->
                        TopPlayer(t.name, "${t.wins}W ${t.losses}L",
                            "Win%: ${"%.1f".format(if (t.matches > 0) t.wins * 100.0 / t.matches else 0.0)}% · NRR: ${"%.3f".format(t.nrr)}")
                    })
            }

        // 30. Best NRR
        teams.sortedByDescending { it.nrr }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec6, "📈", "Best Net Run Rate",
                    top.name, "${"%.3f".format(top.nrr)} NRR",
                    "${top.wins}W ${top.losses}L",
                    list.take(30).map { t ->
                        TopPlayer(t.name, "NRR: ${"%.3f".format(t.nrr)}",
                            "${t.wins}W ${t.losses}L · ${t.runsFor}R scored vs ${t.runsAgainst}R conceded")
                    })
            }

        // 31. Biggest Win by Runs
        teams.filter { it.highestMarginWinRuns > 0 }
            .sortedByDescending { it.highestMarginWinRuns }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec6, "💪", "Biggest Win by Run Margin",
                    top.name, "${top.highestMarginWinRuns} runs",
                    "vs ${top.highestMarginWinAgainst}",
                    list.take(30).map { t ->
                        TopPlayer(t.name, "${t.highestMarginWinRuns} run margin",
                            "vs ${t.highestMarginWinAgainst} · ${t.wins} total wins")
                    })
            }

        // 32. Best Win Percentage (min 2 matches)
        teams.filter { it.matches >= 2 }
            .sortedByDescending { it.wins.toDouble() / it.matches }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                val pct = top.wins * 100.0 / top.matches
                records += RecordItem(sec6, "📊", "Best Win Percentage (min 2 matches)",
                    top.name, "${"%.1f".format(pct)}%",
                    "${top.wins}W from ${top.matches} games",
                    list.take(30).map { t ->
                        val p = t.wins * 100.0 / t.matches
                        TopPlayer(t.name, "${"%.1f".format(p)}% wins",
                            "${t.wins}W ${t.losses}L from ${t.matches} matches")
                    })
            }

        // ── GROUP 7 : Match Records ───────────────────────────────────────────

        val sec7 = "📋 Match Records"

        // 33. Highest Scoring Match (combined runs)
        summaries.sortedByDescending { it.team1Runs + it.team2Runs }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                val total = top.team1Runs + top.team2Runs
                records += RecordItem(sec7, "💥", "Highest Scoring Match",
                    "${top.team1Name} vs ${top.team2Name}", "$total runs",
                    "${top.team1Runs} vs ${top.team2Runs} · Won: ${top.winner}",
                    list.take(30).mapIndexed { idx, ms ->
                        TopPlayer("Match ${idx + 1}: ${ms.team1Name} vs ${ms.team2Name}",
                            "${ms.team1Runs + ms.team2Runs} total",
                            "${ms.team1Runs} vs ${ms.team2Runs} · Won: ${ms.winner}")
                    })
            }

        // 34. Lowest Scoring Match (combined runs)
        summaries.sortedBy { it.team1Runs + it.team2Runs }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                val total = top.team1Runs + top.team2Runs
                records += RecordItem(sec7, "📉", "Lowest Scoring Match",
                    "${top.team1Name} vs ${top.team2Name}", "$total runs",
                    "${top.team1Runs} vs ${top.team2Runs} · Won: ${top.winner}",
                    list.take(30).mapIndexed { idx, ms ->
                        TopPlayer("Match ${idx + 1}: ${ms.team1Name} vs ${ms.team2Name}",
                            "${ms.team1Runs + ms.team2Runs} total",
                            "${ms.team1Runs} vs ${ms.team2Runs} · Won: ${ms.winner}")
                    })
            }

        // 35. Highest Successful Run Chase (team2 wins by batting 2nd)
        summaries.filter { ms ->
            // team2 batted second (Computer) and won
            ms.winner == ms.team2Name
        }.sortedByDescending { it.team2Runs }
            .takeIf { it.isNotEmpty() }?.let { list ->
                val top = list.first()
                records += RecordItem(sec7, "🏃", "Highest Successful Run Chase",
                    top.winner, "${top.team2Runs} runs",
                    "Chased ${top.team1Runs} vs ${top.team1Name}",
                    list.take(30).mapIndexed { idx, ms ->
                        TopPlayer(ms.winner, "${ms.team2Runs} chased",
                            "Target was ${ms.team1Runs + 1} · vs ${ms.team1Name}")
                    })
            }

        return records
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build a RecordItem from a MatchPerformance (uses runs as default value display). */
    private fun makeRecord(
        section: String,
        emoji: String,
        title: String,
        top: MatchPerformance,
        matchMap: Map<Int, MatchSummary>,
        allEntries: List<TopPlayer>,
        valueOverride: String? = null
    ): RecordItem {
        val value = valueOverride ?: "${top.runs} runs (${top.balls} balls)"
        val detail = opponentOf(matchMap, top.matchId, top.player)
        return RecordItem(section, emoji, title, top.player, value, detail, allEntries)
    }

    private fun opponentOf(matchMap: Map<Int, MatchSummary>, matchId: Int, playerName: String): String {
        val ms = matchMap[matchId] ?: return "Match $matchId"
        val pTeam = AppDataStore.players[playerName]?.team ?: return "Match $matchId"
        val opp = when (pTeam) {
            ms.team1Name -> ms.team2Name
            ms.team2Name -> ms.team1Name
            else -> "Match $matchId"
        }
        return "vs $opp · Match $matchId"
    }

    /** For each player find their max across all opponent buckets in a stat map. */
    private fun buildMaxVsTeamList(
        players: List<PlayerStats>,
        selector: (PlayerStats) -> Map<String, Int>
    ): List<Triple<String, String, Int>> {
        return players.flatMap { p ->
            selector(p).entries.map { (team, value) -> Triple(p.name, team, value) }
        }.filter { it.third > 0 }.sortedByDescending { it.third }
    }

    /** Build max vs-team list from a per-match accumulated map (e.g. foursVsTeam). */
    private fun buildMaxVsTeamListFromPerf(
        map: Map<String, Map<String, Int>>,
        label: String
    ): List<Triple<String, String, Int>> {
        return map.flatMap { (player, teamMap) ->
            teamMap.entries.map { (team, value) -> Triple(player, team, value) }
        }.filter { it.third > 0 }.sortedByDescending { it.third }
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class RecordCardAdapter(
    private val onViewAll: (RecordItem) -> Unit
) : RecyclerView.Adapter<RecordCardAdapter.ViewHolder>() {

    private val items = mutableListOf<RecordItem>()

    fun submitList(newItems: List<RecordItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(private val b: ItemRecordCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(record: RecordItem) {
            b.tvRecordEmoji.text   = record.emoji
            b.tvRecordTitle.text   = record.title
            b.tvRecordHolder.text  = record.topHolder
            b.tvRecordValue.text   = record.topValue
            b.tvRecordDetails.text = record.details
            b.btnViewAllRecord.setOnClickListener { onViewAll(record) }
        }
    }
}
