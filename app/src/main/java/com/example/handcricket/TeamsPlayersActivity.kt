package com.example.handcricket

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.ActivityTeamsPlayersBinding
import com.example.handcricket.databinding.ItemPlayerRowFullBinding
import com.google.android.material.chip.Chip

class TeamsPlayersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamsPlayersBinding
    private var allPlayers: List<PlayerStats> = emptyList()
    private var adapter: PlayersAdapter? = null

    private var selectedTeam: String = "All Teams"
    private var searchQuery: String = ""
    private var sortMode: SortMode = SortMode.RUNS

    enum class SortMode {
        RUNS, WICKETS, BAT_AVG, STRIKE_RATE, ECONOMY, MATCHES, FANTASY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamsPlayersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        allPlayers = AppDataStore.players.values.toList()

        setupTeamChips()
        setupSortChips()
        setupSearch()
        setupRecyclerView()
        applyFilters()
    }

    // ── Team Chips ────────────────────────────────────────────────────────────

    private fun setupTeamChips() {
        val teams = AppDataStore.teams.keys.sorted()

        teams.forEach { teamName ->
            val chip = Chip(this).apply {
                text = teamName
                isCheckable = true
                isCheckedIconVisible = true
                chipStartPadding = 8f
                chipEndPadding = 8f
            }
            binding.chipGroupTeams.addView(chip)
        }

        binding.chipGroupTeams.setOnCheckedStateChangeListener { group, _ ->
            val checkedId = group.checkedChipId
            if (checkedId == binding.chipAllTeams.id) {
                selectedTeam = "All Teams"
            } else {
                val chip = group.findViewById<Chip>(checkedId)
                selectedTeam = chip?.text?.toString() ?: "All Teams"
            }
            applyFilters()
        }
    }

    // ── Sort Chips ────────────────────────────────────────────────────────────

    private fun setupSortChips() {
        binding.chipGroupSort.setOnCheckedStateChangeListener { _, _ ->
            sortMode = when (binding.chipGroupSort.checkedChipId) {
                binding.chipSortRuns.id     -> SortMode.RUNS
                binding.chipSortWickets.id  -> SortMode.WICKETS
                binding.chipSortAvg.id      -> SortMode.BAT_AVG
                binding.chipSortSR.id       -> SortMode.STRIKE_RATE
                binding.chipSortEcon.id     -> SortMode.ECONOMY
                binding.chipSortMatches.id  -> SortMode.MATCHES
                binding.chipSortFantasy.id  -> SortMode.FANTASY
                else                        -> SortMode.RUNS
            }
            applyFilters()
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = PlayersAdapter { player ->
            startActivity(
                Intent(this, PlayerProfileActivity::class.java)
                    .putExtra("PLAYER_NAME", player.name)
            )
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recyclerView.adapter = adapter
    }

    // ── Filter + Sort ─────────────────────────────────────────────────────────

    private fun applyFilters() {
        var filtered = allPlayers

        // Team filter
        if (selectedTeam != "All Teams") {
            filtered = filtered.filter { it.team == selectedTeam }
        }

        // Search filter
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }

        // Sort
        filtered = when (sortMode) {
            SortMode.RUNS        -> filtered.sortedByDescending { it.runs }
            SortMode.WICKETS     -> filtered.sortedByDescending { it.wickets }
            SortMode.BAT_AVG     -> filtered.sortedByDescending { it.battingAverage }
            SortMode.STRIKE_RATE -> filtered.sortedByDescending { it.strikeRate }
            SortMode.ECONOMY     -> filtered.filter { it.overs > 0 }.sortedBy { it.economy } +
                                    filtered.filter { it.overs == 0.0 }
            SortMode.MATCHES     -> filtered.sortedByDescending { it.matches }
            SortMode.FANTASY     -> filtered.sortedByDescending { it.fantasyPoints }
        }

        val count = filtered.size
        val teamStr = if (selectedTeam == "All Teams") "all teams" else selectedTeam
        binding.tvResultCount.text = "$count player${if (count != 1) "s" else ""} · $teamStr"

        if (filtered.isEmpty()) {
            binding.recyclerView.visibility = android.view.View.GONE
            binding.emptyState.visibility = android.view.View.VISIBLE
        } else {
            binding.recyclerView.visibility = android.view.View.VISIBLE
            binding.emptyState.visibility = android.view.View.GONE
        }

        adapter?.submitList(filtered)
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class PlayersAdapter(
        private val onClick: (PlayerStats) -> Unit
    ) : RecyclerView.Adapter<PlayersAdapter.VH>() {

        private var items: List<PlayerStats> = emptyList()

        fun submitList(list: List<PlayerStats>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemPlayerRowFullBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemPlayerRowFullBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            val b = holder.binding

            b.tvRank.text = "${position + 1}"
            b.tvPlayerName.text = p.name
            b.tvTeamBadge.text = p.team
            b.tvRuns.text = "${p.runs}"
            b.tvWickets.text = "${p.wickets}"
            b.tvAvg.text = "${"%.1f".format(p.battingAverage)}"
            b.tvSR.text = "${"%.0f".format(p.strikeRate)}"

            // Highlight the sort column
            val highlight = getColor(R.color.purple_700)
            val normal = getColor(R.color.text_primary)
            val normalSub = getColor(R.color.gray_600)
            b.tvRuns.setTextColor(if (sortMode == SortMode.RUNS) highlight else normal)
            b.tvWickets.setTextColor(if (sortMode == SortMode.WICKETS) highlight else normal)
            b.tvAvg.setTextColor(if (sortMode == SortMode.BAT_AVG) highlight else normalSub)
            b.tvSR.setTextColor(if (sortMode == SortMode.STRIKE_RATE) highlight else normalSub)

            b.root.setOnClickListener { onClick(p) }
        }
    }
}
