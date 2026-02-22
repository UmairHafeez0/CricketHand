package com.example.handcricket

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
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

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            this.title = title
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvDescription.text = description
        binding.tvTotalCount.text = "${allPlayers.size} entries"

        // Setup RecyclerView
        adapter = DetailedStatsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        adapter.submitList(allPlayers)

        setupFilters()

        // Search listener
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
                // Primary value IS wickets — use the numeric range filter labelled for wickets
                binding.layoutNumericFilter.visibility = View.VISIBLE
                binding.etMinValue.hint = "Min Wickets"
                binding.etMaxValue.hint = "Max Wickets"
                binding.etMinValue.addTextChangedListener(watcher)
                binding.etMaxValue.addTextChangedListener(watcher)
            }

            "GENERIC" -> { /* no extra filters */ }

            else -> {
                // All other player stat sections: value range + optional wickets
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

    // Build team chips by parsing the "PAK ... vs IND ..." value strings
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

        // "All" chip
        val allChip = Chip(this).apply {
            text = "All"
            isCheckable = true
            isChecked = true
        }
        binding.chipGroupTeams.addView(allChip)

        teams.sorted().forEach { abbr ->
            val chip = Chip(this).apply {
                text = abbr
                isCheckable = true
            }
            binding.chipGroupTeams.addView(chip)
        }

        binding.chipGroupTeams.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                selectedTeamFilter = "All"
            } else {
                val checkedChip = group.findViewById<Chip>(checkedIds[0])
                selectedTeamFilter = checkedChip?.text?.toString() ?: "All"
            }
            applyFilters()
        }
    }

    // ── Filter application ────────────────────────────────────────────────────

    private fun applyFilters() {
        var list = allPlayers

        // Text search
        val query = binding.searchView.query?.toString() ?: ""
        if (query.isNotEmpty()) {
            list = list.filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                p.value.contains(query, ignoreCase = true) ||
                p.details.contains(query, ignoreCase = true)
            }
        }

        // Category-specific filters
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

            "GENERIC" -> { /* only text search applies */ }

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

    // ── Value extraction helpers ──────────────────────────────────────────────

    /** Extract the primary numeric value for range filtering */
    private fun extractPrimaryValue(player: TopPlayer): Double? = when (categoryType) {
        // "450 runs (300 balls)" → 450
        "RUN_SCORERS"  -> Regex("^(\\d+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        // "15 wickets" → 15
        "WICKETS"      -> Regex("^(\\d+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        // "SR: 150.00" → 150.00
        "STRIKE_RATE"  -> Regex("SR:\\s*([\\d.]+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        // details = "Total: 57 boundaries • …" → 57
        "BOUNDARIES"   -> Regex("Total:\\s*(\\d+)").find(player.details)?.groupValues?.get(1)?.toDoubleOrNull()
        // "Econ: 6.50" → 6.50
        "ECONOMY"      -> Regex("Econ:\\s*([\\d.]+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        // "450 pts" → 450
        "FANTASY"      -> Regex("^(\\d+)").find(player.value)?.groupValues?.get(1)?.toDoubleOrNull()
        // "1x100s, 3x50s" → total milestones (centuries + half-centuries)
        "CENTURIES"    -> {
            val c = Regex("(\\d+)x100s").find(player.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val h = Regex("(\\d+)x50s").find(player.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            (c + h).toDouble()
        }
        else -> null
    }

    /** Extract wickets count from value or details for cross-category filtering */
    private fun extractWicketsValue(player: TopPlayer): Int? {
        // "15 wickets" (Wickets section)
        Regex("^(\\d+)\\s*wickets?", RegexOption.IGNORE_CASE)
            .find(player.value)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        // "450R • 15W • …" (Fantasy details)
        Regex("\\b(\\d+)W\\b")
            .find(player.details)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    // ── Clear filters ─────────────────────────────────────────────────────────

    private fun clearFilters() {
        binding.searchView.setQuery("", false)
        selectedTeamFilter = "All"

        // Reset team chips to "All"
        for (i in 0 until binding.chipGroupTeams.childCount) {
            val chip = binding.chipGroupTeams.getChildAt(i) as? Chip
            chip?.isChecked = (i == 0)
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

class DetailedStatsAdapter : RecyclerView.Adapter<DetailedStatsAdapter.ViewHolder>() {

    private val items = mutableListOf<TopPlayer>()

    fun submitList(newItems: List<TopPlayer>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopPlayerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemTopPlayerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(player: TopPlayer, rank: Int) {
            binding.tvRank.text = rank.toString()
            binding.tvPlayerName.text = player.name
            binding.tvPlayerValue.text = player.value
            binding.tvPlayerDetails.text = player.details
        }
    }
}
