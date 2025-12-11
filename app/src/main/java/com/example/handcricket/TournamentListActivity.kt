package com.example.handcricket

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.handcricket.data.AppDatabase
import com.example.handcricket.data.Tournament
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TournamentListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var fragmentContainer: View
    private val tournaments = mutableListOf<Tournament>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_list)

        listView = findViewById(R.id.listView)
        fragmentContainer = findViewById(R.id.fragment_container)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        loadTournaments()

        listView.setOnItemClickListener { _, _, position, _ ->
            val tournament = tournaments[position]

            // Hide ListView and show Fragment container
            listView.visibility = View.GONE
            fragmentContainer.visibility = View.VISIBLE

            // Navigate to Tournament Details Fragment
            val fragment = TournamentDetailsFragment.newInstance(tournament.id)

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack("tournament_details")
                .commit()
        }
    }

    override fun onBackPressed() {
        // Check if fragment is currently showing
        if (listView.visibility == View.GONE) {
            // Go back to ListView
            supportFragmentManager.popBackStack()
            listView.visibility = View.VISIBLE
            fragmentContainer.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }

    private fun loadTournaments() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@TournamentListActivity)
            val data = db.tournamentDao().getAllTournaments()
            tournaments.clear()
            tournaments.addAll(data)

            val names = data.map { "${it.name} (${it.format})" }

            withContext(Dispatchers.Main) {
                if (data.isEmpty()) {
                    Toast.makeText(
                        this@TournamentListActivity,
                        "No tournaments found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                adapter.clear()
                adapter.addAll(names)
                adapter.notifyDataSetChanged()
            }
        }
    }
}