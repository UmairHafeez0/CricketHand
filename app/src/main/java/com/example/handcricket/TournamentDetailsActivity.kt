// TournamentDetailsFragment.kt (Updated)
package com.example.handcricket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.data.Match
import com.example.handcricket.data.Team
import com.example.handcricket.databinding.*
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TournamentDetailsFragment : Fragment() {

    private var _binding: FragmentTournamentDetailsBinding? = null
    private val binding get() = _binding!!
    private var tournamentId: Int = 0
    private lateinit var viewPagerAdapter: TournamentDetailsPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTournamentDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tournamentId = arguments?.getInt("TOURNAMENT_ID") ?: 0

        setupViewPager()
        loadTournamentInfo()
    }

    private fun setupViewPager() {
        // Use requireActivity() instead of this
        viewPagerAdapter = TournamentDetailsPagerAdapter(requireActivity(), tournamentId)
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Schedule"
                1 -> "Standings"
                2 -> "Import"
                3 -> "Stats"
                else -> null
            }
        }.attach()
    }

    private fun loadTournamentInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val tournament = db.tournamentDao().getAllTournaments().find { it.id == tournamentId }

            withContext(Dispatchers.Main) {
                tournament?.let {
                    binding.tournamentName.text = it.name
                    binding.tournamentFormat.text = "Format: ${it.format}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tournamentId: Int): TournamentDetailsFragment {
            val fragment = TournamentDetailsFragment()
            val args = Bundle()
            args.putInt("TOURNAMENT_ID", tournamentId)
            fragment.arguments = args
            return fragment
        }
    }
}