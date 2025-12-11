// StandingsFragment.kt
package com.example.handcricket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.data.Match
import com.example.handcricket.data.Team
import com.example.handcricket.databinding.FragmentStandingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StandingsFragment : Fragment() {

    private var _binding: FragmentStandingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StandingsAdapter
    private var tournamentId: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStandingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tournamentId = arguments?.getInt("TOURNAMENT_ID") ?: 0

        setupRecyclerView()
        loadStandings()
    }

    private fun setupRecyclerView() {
        adapter = StandingsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadStandings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val teams = db.tournamentDao().getTeams(tournamentId)
            val matches = db.tournamentDao().getMatches(tournamentId)

            // Calculate standings
            val standings = calculateStandings(teams, matches)

            withContext(Dispatchers.Main) {
                adapter.submitList(standings)
            }
        }
    }

    private fun calculateStandings(teams: List<Team>, matches: List<Match>): List<StandingItem> {
        // Implement your standings calculation logic here
        // This should include wins, losses, NRR, points, etc.
        return teams.map { team ->
            StandingItem(
                team.name,
                team.groupName ?: "",
                0, // matches
                0, // wins
                0, // losses
                0, // points
                0.0 // NRR
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tournamentId: Int): StandingsFragment {
            val fragment = StandingsFragment()
            val args = Bundle()
            args.putInt("TOURNAMENT_ID", tournamentId)
            fragment.arguments = args
            return fragment
        }
    }
}

data class StandingItem(
    val teamName: String,
    val group: String,
    val matches: Int,
    val wins: Int,
    val losses: Int,
    val points: Int,
    val nrr: Double
)