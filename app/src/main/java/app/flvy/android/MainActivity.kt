package app.flvy.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var board: SplitFlapView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        board=SplitFlapView(this)
        MainActivityHolder.board=board
        setContentView(board)
        board.setOnLongClickListener {
            try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            Toast.makeText(this,"Enable FLVY notifications, then return here",Toast.LENGTH_LONG).show()
            true
        }
        tick()
    }
    override fun onResume(){ super.onResume(); hideSystemUi() }
    override fun onDestroy(){ MainActivityHolder.board=null; super.onDestroy() }
    private fun tick(){
        val now=Date()
        board.setClock(
            SimpleDateFormat("HH:mm",Locale.getDefault()).format(now),
            SimpleDateFormat("EEE  dd  MMM",Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        )
        board.postDelayed({ if (!isFinishing) tick() },1000)
    }
    private fun hideSystemUi(){
        window.decorView.systemUiVisibility=
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
