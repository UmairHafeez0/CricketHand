// StandingsAdapter.kt
package com.example.handcricket

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.handcricket.databinding.ItemStandingBinding

class StandingsAdapter : ListAdapter<StandingItem, StandingsAdapter.StandingViewHolder>(StandingDiffCallback()) {

    class StandingViewHolder(private val binding: ItemStandingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(standing: StandingItem) {
            binding.tvTeamName.text = standing.teamName
            binding.tvGroup.text = standing.group
            binding.tvMatches.text = standing.matches.toString()
            binding.tvWins.text = standing.wins.toString()
            binding.tvLosses.text = standing.losses.toString()
            binding.tvPoints.text = standing.points.toString()
            binding.tvNrr.text = String.format("%.2f", standing.nrr)

            // Set position number
            binding.tvPosition.text = (adapterPosition + 1).toString()

            // Highlight top positions
            when (adapterPosition) {
                0 -> binding.root.setBackgroundResource(R.color.gold_light)
                1 -> binding.root.setBackgroundResource(R.color.silver_light)
                2 -> binding.root.setBackgroundResource(R.color.bronze_light)
                else -> binding.root.setBackgroundResource(android.R.color.white)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StandingViewHolder {
        val binding = ItemStandingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StandingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StandingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class StandingDiffCallback : DiffUtil.ItemCallback<StandingItem>() {
    override fun areItemsTheSame(oldItem: StandingItem, newItem: StandingItem): Boolean {
        return oldItem.teamName == newItem.teamName
    }

    override fun areContentsTheSame(oldItem: StandingItem, newItem: StandingItem): Boolean {
        return oldItem == newItem
    }
}