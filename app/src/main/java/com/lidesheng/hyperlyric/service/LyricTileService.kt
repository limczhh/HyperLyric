package com.lidesheng.hyperlyric.service
import com.lidesheng.hyperlyric.Constants


import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit

class LyricTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
        val isPaused = prefs.getBoolean(Constants.KEY_PAUSE_LISTENING, Constants.DEFAULT_PAUSE_LISTENING)
        
        val tile = qsTile ?: return
        tile.label = "HyperLyric媒体信息监听"
        if (isPaused) {
            tile.state = Tile.STATE_INACTIVE
        } else {
            tile.state = Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
        val isPaused = prefs.getBoolean(Constants.KEY_PAUSE_LISTENING, Constants.DEFAULT_PAUSE_LISTENING)
        val nextState = !isPaused
        
        prefs.edit { putBoolean(Constants.KEY_PAUSE_LISTENING, nextState) }
        
        val intent = Intent(this, ForegroundLyricService::class.java).apply {
            action = if (nextState) ACTION_PAUSE_TOGGLED else ACTION_RESUME_TOGGLED
        }
        startService(intent)
        
        updateTileState()
    }

    companion object {
        const val ACTION_PAUSE_TOGGLED = "com.lidesheng.hyperlyric.ACTION_PAUSE_TOGGLED"
        const val ACTION_RESUME_TOGGLED = "com.lidesheng.hyperlyric.ACTION_RESUME_TOGGLED"
    }
}
