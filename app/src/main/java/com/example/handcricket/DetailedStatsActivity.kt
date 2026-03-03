package com.example.handcricket

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.ActivityDetailedStatsBinding
import com.example.handcricket.databinding.ItemTopPlayerBinding
import com.google.android.material.chip.Chip

class DetailedStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailedStatsBinding
    private lateinit var adapter: DetailedStatsAdapter
    private var allPlayers = listOf<TopPlayer>()
    private var categoryType = "GENERIC"
    private var selectedTeamFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailedStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("CATEGORY_TITLE") ?: "Stats"
        val description = intent.getStringExtra("CATEGORY_DESCRIPTION") ?: ""
        allPlayers = intent.getParcelableArrayListExtra<TopPlayer>("TOP_PLAYERS") ?: emptyList()
        categoryType = intent.getStringExtra("CATEGORY_TYPE") ?: "GENERIC"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            this.title = title
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvDescription.text = description
        binding.tvTotalCount.text = "${allPlayers.size} entries"

        // Adapter with row-click routing
        adapter = DetailedStatsAdapter { player ->
            when (categoryType) {
                "MATCH_RESULTS" -> { /* scorecards — no profile */ }
                "TEAM_STANDINGS" -> {
                    // name is "1. Pakistan" → extract team name
                    val teamName = player.name.substringAfter(". ").trim()
                    if (teamName.isNotEmpty()) {
                        startActivity(Intent(this, TeamProfileActivity::class.java).apply {
                            putExtra("TEAM_NAME", teamName)
                        })
                    }
                }
                else -> {
                    if (player.name.isNotEmpty()) {
                        startActivity(Intent(this, PlayerProfileActivity::class.java).apply {
                            putExtra("PLAYER_NAME", player.name)
                        })
                    }
                }
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        adapter.submitList(allPlayers)

        setupFilters()

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { applyFilters(); return true }
            override fun onQueryTextChange(newText: String?): Boolean { applyFilters(); return true }
        })

        updateEmptyState(false)
    }

    // ── Filter setup ─────────────────────────────────────────────────────────

    private fun setupFilters() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilters()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        when (categoryType) {
            "MATCH_RESULTS" -> {
                binding.scrollTeams.visibility = View.VISIBLE
                setupTeamChips()
            }
            "TEAM_STANDINGS" -> {
                binding.layoutWinsFilter.visibility = View.VISIBLE
                binding.etMinWins.addTextChangedListener(watcher)
            }
            "WICKETS" -> {
                binding.layoutNumericFilter.visibility = View.VISIBLE
                binding.etMinValue.hint = "Min Wickets"
                binding.etMaxValue.hint = "Max Wickets"
                binding.etMinValue.addTextChangedListener(watcher)
                binding.etMaxValue.addTextChangedListener(watcher)
            }
            "GENERIC" -> { /* no extra filters */ }
            else -> {
                binding.layoutNumericFilter.visibility = View.VISIBLE
                binding.layoutWicketsFilter.visibility = View.VISIBLE
                val hint = valueHintFor(categoryType)
                binding.etMinValue.hint = "Min $hint"
                binding.etMaxValue.hint = "Max $hint"
                binding.etMinValue.addTextChangedListener(watcher)
                binding.etMaxValue.addTextChangedListener(watcher)
                binding.etMinWickets.addTextChangedListener(watcher)
            }
        }

        binding.btnClearFilter.setOnClickListener { clearFilters() }
    }

    private fun valueHintFor(type: String) = when (type) {
        "RUN_SCORERS"  -> "Runs"
        "STRIKE_RATE"  -> "Strike Rate"
        "BOUNDARIES"   -> "Boundaries"
        "ECONOMY"      -> "Economy"
        "FANTASY"      -> "Points"
        "CENTURIES"    -> "Milestones"
        else           -> "Value"
    }

    private fun setupTeamChips() {
        val teams = mutableSetOf<String>()
        allPlayers.forEach { player ->
            player.value.split(" vs ").forEach { part ->
                val abbr = part.trim().split(" ").firstOrNull()
                if (!abbr.isNullOrEmpty() && abbr.length in 2..4 && abbr.all { it.isLetter() }) {
                    teams.add(abbr)
                }
            }
        }

        val allChip = Chip(this).apply { text = "All"; isCheckable = true; isChecked = true }
        binding.chipGroupTeams.addView(allChip)

        teams.sorted().forEach { abbr ->
            binding.chipGroupTeams.addView(Chip(this).apply { text = abbr; isCheckable = true })
        }

        binding.chipGroupTeams.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedTeamFilter = if (checkedIds.isEmpty()) "All"
            else group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "All"
            applyFilters()
        }
    }

    // ── Filter application ────────────────────────────────────────────────────

    private fun applyFilters() {
        var list = allPlayers

        val query = binding.searchView.query?.toString() ?: ""
        if (query.isNotEmpty()) {
            list = list.filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                p.value.contains(query, ignoreCase = true) ||
                p.details.contains(query, ignoreCase = true)
            }
        }

        when (categoryType) {
            "MATCH_RESULTS" -> {
                if (selectedTeamFilter != "All") {
                    list = list.filter { it.value.contains(selectedTeamFilter, ignoreCase = true) }
                }
            }
            "TEAM_STANDINGS" -> {
                val minWins = binding.etMinWins.text?.toString()?.toIntOrNull()
                if (minWins != null) {
                    list = list.filter { p ->
                        val wins = Regex("(\\d+)W").find(p.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        wins >= minWins
                    }
                }
            }
            "WICKETS" -> {
                val minV = binding.etMinValue.text?.toString()?.toDoubleOrNull()
                val maxV = binding.etMaxValue.text?.toString()?.toDoubleOrNull()
                if (minV != null || maxV != null) {
                    list = list.filter { p ->
                        val v = extractPrimaryValue(p) ?: return@filter true
                        (minV == null || v >= minV) && (maxV == null || v <= maxV)
                    }
                }
            }
            "GENERIC" -> {}
            else -> {
                val minV = binding.etMinValue.text?.toString()?.toDoubleOrNull()
                val maxV = binding.etMaxValue.text?.toString()?.toDoubleOrNull()
                val minW = binding.etMinWickets.text?.toString()?.toIntOrNull()

                if (minV != null || maxV != null) {
                    list = list.filter { p ->
                        val v = extractPrimaryValue(p) ?: return@filter true
                        (minV == null || v >= minV) && (maxV == null || v <= maxV)
                    }
                }
                if (minW != null) {
                    list = list.filter { p ->
                        val w = extractWicketsValue(p) ?: return@filter true
                        w >= minW
                    }
                }
            }
        }

        adapter.submitList(list)
        binding.tvTotalCount.text = "${list.size} of ${allPlayers.size} entries"
        updateEmptyState(list.isEmpty())
    }

    private fun extractPrimaryValue(player: TopPlayer): Double? = when (categoryType) {
        "RUN_SCORERS"  -> Regex("^(\\d+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        "WICKETS"      -> Regex("^(\\d+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        "STRIKE_RATE"  -> Regex("SR:\\s*([\\d.]+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        "BOUNDARIES"   -> Regex("Total:\\s*(\\d+)").find(player.details)?.groupValues?.get(1)?.toDoubleOrNull()
        "ECONOMY"      -> Regex("Econ:\\s*([\\d.]+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        "FANTASY"      -> Regex("^(\\d+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        "CENTURIES"    -> {
            val c = Regex("(\\d+)x100s").find(player.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val h = Regex("(\\d+)x50s").find(player.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            (c + h).toDouble()
        }
        else -> null
    }

    private fun extractWicketsValue(player: TopPlayer): Int? {
        Regex("^(\\d+)\\s*wickets?", RegexOption.IGNORE_CASE)
            .find(player.value)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("\\b(\\d+)W\\b")
            .find(player.details)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun clearFilters() {
        binding.searchView.setQuery("", false)
        selectedTeamFilter = "All"
        for (i in 0 until binding.chipGroupTeams.childCount) {
            (binding.chipGroupTeams.getChildAt(i) as? Chip)?.isChecked = (i == 0)
        }
        binding.etMinWins.text?.clear()
        binding.etMinValue.text?.clear()
        binding.etMaxValue.text?.clear()
        binding.etMinWickets.text?.clear()

        adapter.submitList(allPlayers)
        binding.tvTotalCount.text = "${allPlayers.size} entries"
        updateEmptyState(false)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}

class DetailedStatsAdapter(
    private val onItemClick: (TopPlayer) -> Unit = {}
) : RecyclerView.Adapter<DetailedStatsAdapter.ViewHolder>() {

    private val items = mutableListOf<TopPlayer>()

    fun submitList(newItems: List<TopPlayer>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemTopPlayerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(player: TopPlayer, rank: Int) {
            binding.tvRank.text        = rank.toString()
            binding.tvPlayerName.text  = player.name
            binding.tvPlayerValue.text = player.value
            binding.tvPlayerDetails.text = player.details

            binding.root.setOnClickListener { onItemClick(player) }
        }
    }
}
