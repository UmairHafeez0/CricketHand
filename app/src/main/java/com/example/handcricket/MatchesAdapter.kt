package com.example.handcricket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.ItemMatchBinding

class MatchesAdapter : ListAdapter<MatchItem, MatchesAdapter.MatchViewHolder>(DiffCallback()) {

    class MatchViewHolder(private val binding: ItemMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(match: MatchItem) {
            binding.tvMatchTitle.text = "${match.teamA} vs ${match.teamB}"
            binding.tvMatchType.text = match.matchType

            // Show result and player of match
            if (match.result == "Not Played") {
                binding.tvResult.text = "Result: Not Played"
                binding.tvPlayerOfMatch.visibility = View.GONE
            } else {
                binding.tvResult.text = "Winner: ${match.result}"

                // Show Player of the Match if available
                if (match.playerOfMatch.isNotEmpty()) {
                    binding.tvPlayerOfMatch.visibility = View.VISIBLE
                    binding.tvPlayerOfMatch.text = "Player of Match: ${match.playerOfMatch}"
                } else {
                    binding.tvPlayerOfMatch.visibility = View.GONE
                }
            }

            // Set background color based on match type
            val bgColor = when (match.matchType) {
                "Final" -> R.color.red_light
                "Semi" -> R.color.orange_light
                else -> R.color.teal_light
            }
            binding.root.setBackgroundResource(bgColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class DiffCallback : DiffUtil.ItemCallback<MatchItem>() {
    override fun areItemsTheSame(oldItem: MatchItem, newItem: MatchItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MatchItem, newItem: MatchItem): Boolean {
        return oldItem == newItem
    }
}