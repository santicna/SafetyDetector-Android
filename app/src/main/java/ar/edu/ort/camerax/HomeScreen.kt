package ar.edu.ort.camerax

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeScreen : AppCompatActivity() {

    private lateinit var cameraButton : FloatingActionButton
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)
    }

    override fun onStart() {
        super.onStart()
        cameraButton = findViewById(R.id.goToCameraButton)

        cameraButton.setOnClickListener {
            val intention = Intent(this, MainActivity::class.java)
            startActivity(intention)
        }
    }
}