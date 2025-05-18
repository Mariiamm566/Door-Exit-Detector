package scan.door.exitdetector

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog


@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        val detect: LinearLayout = findViewById(R.id.open);
        val des: LinearLayout = findViewById(R.id.des);
        val linkedin: LinearLayout = findViewById(R.id.linkedin);


        detect.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        des.setOnClickListener {
            val message = """
        🚪 Door Exit Detector with Obstacle Avoidance
        
        An Android application that helps visually impaired users navigate to doors while avoiding obstacles using real-time object detection and voice guidance.
        
        Features:
        • Real-time door detection using custom ML model
        • Obstacle detection and avoidance guidance
        • Voice feedback for navigation instructions
        • Visual bounding boxes for detected objects
        • Optimized performance with frame skipping
        • Guidance cooldown to prevent speech spamming
    """.trimIndent()
            AlertDialog.Builder(this)
                .setTitle("About Our App")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show()
        }
        linkedin.setOnClickListener {
            val url = "https://www.linkedin.com/in/mariam-hassan-7a7775328/"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            startActivity(intent)
        }


    }
}