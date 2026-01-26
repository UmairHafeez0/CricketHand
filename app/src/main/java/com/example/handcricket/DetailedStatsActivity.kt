package com.example.handcricket

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.ActivityDetailedStatsBinding
import com.example.handcricket.databinding.ItemTopPlayerBinding



class DetailedStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailedStatsBinding
    private lateinit var adapter: DetailedStatsAdapter
    private var allPlayers = listOf<TopPlayer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailedStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        val title = intent.getStringExtra("CATEGORY_TITLE") ?: "Stats"
        val description = intent.getStringExtra("CATEGORY_DESCRIPTION") ?: ""
        allPlayers = intent.getParcelableArrayListExtra<TopPlayer>("TOP_PLAYERS") ?: emptyList()

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            this.title = title
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Setup description
        binding.tvDescription.text = description
        binding.tvTotalCount.text = "${allPlayers.size} entries"

        // Setup RecyclerView
        adapter = DetailedStatsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Show all data initially
        adapter.submitList(allPlayers)

        // Setup search
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterPlayers(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterPlayers(newText)
                return true
            }
        })

        updateEmptyState()
    }

    private fun filterPlayers(query: String?) {
        val filteredList = if (query.isNullOrEmpty()) {
            allPlayers
        } else {
            allPlayers.filter { player ->
                player.name.contains(query, ignoreCase = true) ||
                        player.value.contains(query, ignoreCase = true) ||
                        player.details.contains(query, ignoreCase = true)
            }
        }

        adapter.submitList(filteredList)
        binding.tvTotalCount.text = "${filteredList.size} of ${allPlayers.size} entries"
        updateEmptyState(filteredList.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean = false) {
        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
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
            LayoutInflater.from(parent.context),
            parent,
            false
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