// ScheduleFragment.kt
package com.example.handcricket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.databinding.FragmentScheduleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MatchesAdapter
    private var tournamentId: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tournamentId = arguments?.getInt("TOURNAMENT_ID") ?: 0

        setupRecyclerView()
        loadMatches()
    }

    private fun setupRecyclerView() {
        adapter = MatchesAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadMatches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val matches = db.tournamentDao().getMatches(tournamentId)
            val teams = db.tournamentDao().getTeams(tournamentId).associateBy { it.id }

            val matchList = matches.map { match ->
                MatchItem(
                    match.id,
                    teams[match.teamAId]?.name ?: "Team A",
                    teams[match.teamBId]?.name ?: "Team B",
                    match.matchType,
                    match.winnerTeamId?.let { teams[it]?.name } ?: "Not Played"
                )
            }

            withContext(Dispatchers.Main) {
                adapter.submitList(matchList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tournamentId: Int): ScheduleFragment {
            val fragment = ScheduleFragment()
            val args = Bundle()
            args.putInt("TOURNAMENT_ID", tournamentId)
            fragment.arguments = args
            return fragment
        }
    }
}

data class MatchItem(
    val id: Int,
    val teamA: String,
    val teamB: String,
    val matchType: String,
    val result: String
)