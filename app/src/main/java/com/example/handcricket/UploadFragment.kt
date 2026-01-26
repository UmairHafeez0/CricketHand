package com.example.handcricket

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.handcricket.databinding.FragmentUploadBinding

class UploadFragment : Fragment() {
    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    private val defaultTeams = listOf(
        "Pakistan", "India", "Australia", "England",
        "South Africa", "New Zealand", "West Indies", "Sri Lanka",
        "Bangladesh", "Afghanistan", "Ireland", "Zimbabwe",
        "Netherlands", "Scotland", "UAE", "Namibia"
    )

    private val defaultPlayers = mapOf(
        "Pakistan" to "babar,rizwan,shaheen",
        "India" to "virat,rohit,bumrah",
        "Australia" to "warner,maxwell,starc",
        "England" to "buttler,stokes,bairstow",
        "South Africa" to "de kock,miller,rabada",
        "New Zealand" to "kane,boult,williamson",
        "West Indies" to "pollard,russell,narine",
        "Sri Lanka" to "mendis,shanaka,hasaranga",
        "Bangladesh" to "sakib,tamim,mushfiq",
        "Afghanistan" to "rashid,nabi,mujeeb",
        "Ireland" to "stirling,balbirnie,little",
        "Zimbabwe" to "williams,raza,chatara",
        "Netherlands" to "edwards,van der merwe,barresi",
        "Scotland" to "munsey,berrington,sharif",
        "UAE" to "waseem,kashif,meiyappan",
        "Namibia" to "green,wiese,loftie-eaton"
    )

    companion object {
        private const val PREFS_NAME = "TeamMappings"
        private const val KEY_TEAM_DATA = "team_player_data"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnUpload.setOnClickListener {
            (activity as? MainActivity)?.openFilePicker()
        }

        binding.btnSampleData.setOnClickListener {
            // Load sample data for demo
            val fragment = StatsFragment.newInstance(emptyList(), true)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.btnManagePlayers.setOnClickListener {
            showTeamManagementDialog()
        }
    }

    private fun showTeamManagementDialog() {
        val context = requireContext()
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Load saved data or use defaults
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedData = sharedPrefs.getString(KEY_TEAM_DATA, null)
        val teamData = if (savedData != null) {
            parseSavedData(savedData)
        } else {
            defaultTeams.associateWith { team ->
                defaultPlayers[team] ?: ""
            }
        }

        // Create input fields for each team
        val editTexts = mutableMapOf<String, EditText>()

        defaultTeams.forEach { team ->
            val teamLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 24)
            }

            val teamLabel = TextView(context).apply {
                text = team
                textSize = 18f
                setTextAppearance(android.R.style.TextAppearance_Material_Subhead)
            }

            val playerInput = EditText(context).apply {
                hint = "Enter player names (comma separated)"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setLines(2)
                setText(teamData[team] ?: "")
                editTexts[team] = this
            }

            teamLayout.addView(teamLabel)
            teamLayout.addView(playerInput)
            container.addView(teamLayout)
        }

        scrollView.addView(container)

        AlertDialog.Builder(context)
            .setTitle("Manage Player-Team Mappings")
            .setMessage("Enter player names for each team (comma separated). These will be used to detect teams from player names in CSV files.")
            .setView(scrollView)
            .setPositiveButton("Save") { dialog, _ ->
                saveTeamMappings(editTexts, sharedPrefs)
                dialog.dismiss()
            }
            .setNegativeButton("Reset to Default") { dialog, _ ->
                resetToDefaults(sharedPrefs)
                dialog.dismiss()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun parseSavedData(data: String): Map<String, String> {
        return data.split(";;").associate { teamData ->
            val parts = teamData.split("::")
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }.filter { it.key.isNotEmpty() }
    }

    private fun saveTeamMappings(editTexts: Map<String, EditText>, sharedPrefs: android.content.SharedPreferences) {
        val data = StringBuilder()

        defaultTeams.forEachIndexed { index, team ->
            val players = editTexts[team]?.text?.toString()?.trim() ?: ""
            data.append("${team}::${players}")
            if (index < defaultTeams.size - 1) {
                data.append(";;")
            }
        }

        sharedPrefs.edit {
            putString(KEY_TEAM_DATA, data.toString())
        }

        // Also update the global teamMap
        updateTeamMap(editTexts)

        // Show confirmation
        AlertDialog.Builder(requireContext())
            .setTitle("Success")
            .setMessage("Player-team mappings have been saved successfully.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateTeamMap(editTexts: Map<String, EditText>) {
        val newTeamMap = mutableMapOf<String, String>()

        editTexts.forEach { (team, editText) ->
            val playersText = editText.text.toString().trim()
            if (playersText.isNotEmpty()) {
                val players = playersText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                players.forEach { player ->
                    newTeamMap[player.lowercase()] = team
                }
            }
        }

        // Update the global teamMap (assuming it's accessible)
        // You might need to make teamMap a mutable variable in your main activity or repository
        try {
            // This is a placeholder - you'll need to implement this based on your app structure
            updateGlobalTeamMap(newTeamMap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateGlobalTeamMap(newMap: Map<String, String>) {
        // This method should update your global teamMap variable
        // Implementation depends on where you store teamMap
        // For example, if it's in a ViewModel or Repository:

        // Option 1: If teamMap is in a ViewModel
        // viewModel.updateTeamMap(newMap)

        // Option 2: If teamMap is in a singleton object
        // TeamRepository.updateTeamMap(newMap)

        // Option 3: If teamMap is in shared preferences (as shown above)
        // The data is already saved in shared preferences

        // For now, we'll just show a toast
        android.widget.Toast.makeText(
            requireContext(),
            "Team mappings updated (${newMap.size} entries)",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun resetToDefaults(sharedPrefs: android.content.SharedPreferences) {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset to Defaults")
            .setMessage("Are you sure you want to reset all team mappings to default values?")
            .setPositiveButton("Yes") { dialog, _ ->
                sharedPrefs.edit {
                    remove(KEY_TEAM_DATA)
                }
                dialog.dismiss()
                showTeamManagementDialog() // Refresh dialog with defaults
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}