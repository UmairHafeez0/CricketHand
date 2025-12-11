// TournamentDetailsPagerAdapter.kt
package com.example.handcricket

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class TournamentDetailsPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val tournamentId: Int
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ScheduleFragment.newInstance(tournamentId)
            1 -> StandingsFragment.newInstance(tournamentId)
            2 -> ImportFragment.newInstance(tournamentId)
            3 -> StatsFragment.newInstance(tournamentId)
            else -> ScheduleFragment.newInstance(tournamentId)
        }
    }
}