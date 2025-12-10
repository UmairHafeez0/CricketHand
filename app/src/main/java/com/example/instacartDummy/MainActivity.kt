package com.example.instacartDummy

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.instacartDummy.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var windowManager: WindowManager
    private var overlayButton: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        // Set an OnClickListener for the root view to capture click positions
        binding.root.setOnClickListener { view ->
            val x = view.width / 2 // Center of the view
            val y = view.height / 2 // Center of the view
 //           Toast.makeText(this, "Clicked at position: ($x, $y)", Toast.LENGTH_SHORT).show()
        }

        // Alternatively, you can use OnTouchListener to get exact touch positions
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt() // Get X coordinate of touch
                val y = event.y.toInt() // Get Y coordinate of touch
      //          Toast.makeText(this, "Clicked at position: ($x, $y)", Toast.LENGTH_SHORT).show()
            }
            false // Return false to allow other touch events to be handled
        }
    }

    private fun showToastWithCardViewPosition(cardView: CardView) {
        val position = IntArray(2)
        cardView.getLocationOnScreen(position)
        val x = position[0]
        val y = position[1]
            //     Toast.makeText(this, "CardView is clicked at position: ($x, $y)", Toast.LENGTH_SHORT).show()
    }


    private fun hideOverlayButton() {
        overlayButton?.let {
            windowManager.removeView(it)
            overlayButton = null
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlayButton()
    }
}
