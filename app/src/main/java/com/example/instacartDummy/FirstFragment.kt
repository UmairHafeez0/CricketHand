package com.example.instacartDummy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class FirstFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var bigSpinner: ProgressBar
    private lateinit var accessMoreRow: LinearLayout
    private lateinit var lookingRow: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_first, container, false)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        bigSpinner = view.findViewById(R.id.big_spinner)
        accessMoreRow = view.findViewById(R.id.access_more_row)
        lookingRow = view.findViewById(R.id.looking_row)

        setupRefresh()

        return view
    }

    private fun setupRefresh() {
        swipeRefresh.setOnRefreshListener {
            // Show BIG spinner & hide access more row
            bigSpinner.visibility = View.VISIBLE
            accessMoreRow.visibility = View.GONE
            lookingRow.visibility = View.VISIBLE   // small spinner row

            // Simulate loading
            Handler(Looper.getMainLooper()).postDelayed({
                swipeRefresh.isRefreshing = false

                // After refresh done
                bigSpinner.visibility = View.GONE
                accessMoreRow.visibility = View.VISIBLE
                lookingRow.visibility = View.GONE   // hide small spinner
            }, 2000) // 2 seconds delay for demo
        }
    }
}
