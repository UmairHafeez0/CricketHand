package com.example.handcricket

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.handcricket.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start new tournament
        binding.btnNewTournament.setOnClickListener {
            val intent = Intent(this, CreateTournamentActivity::class.java)
            startActivity(intent)
        }

        // Show existing tournaments
        binding.btnExistingTournaments.setOnClickListener {
            val intent = Intent(this, TournamentListActivity::class.java)
            startActivity(intent)
        }
    }
}
