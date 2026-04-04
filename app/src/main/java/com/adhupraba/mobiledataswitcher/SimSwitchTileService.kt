package com.adhupraba.mobiledataswitcher

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import rikka.shizuku.Shizuku

class SimSwitchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            updateTile(Tile.STATE_UNAVAILABLE, "No Perm", null)
            return
        }

        if (!Shizuku.pingBinder()) {
            var retries = 0
            while (!Shizuku.pingBinder() && retries < 15) {
                try { Thread.sleep(50) } catch (e: Exception) {}
                retries++
            }
            if (!Shizuku.pingBinder()) {
                updateTile(Tile.STATE_UNAVAILABLE, "Start Shizuku", null)
                return
            }
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            updateTile(Tile.STATE_UNAVAILABLE, "No Shizuku Perm", null)
            return
        }

        val sims = SimManager.getActiveSimCards(this)
        if (sims.size < 2) {
            updateTile(Tile.STATE_UNAVAILABLE, "Need 2 SIMs", null)
            return
        }

        val currentSubId = SimManager.getDefaultDataSubId()
        val targetSim = sims.firstOrNull { it.subscriptionId != currentSubId }

        if (targetSim != null) {
            val success = SimManager.switchMobileData(targetSim.subscriptionId)
            if (success) {
                // Briefly wait for system to process the update
                Handler(Looper.getMainLooper()).postDelayed({
                    updateTileState()
                }, 800)
            } else {
                Log.e("SimSwitchTileService", "Failed to switch data")
            }
        }
    }

    private fun updateTileState() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            updateTile(Tile.STATE_UNAVAILABLE, "No Perm", null)
            return
        }

        val sims = SimManager.getActiveSimCards(this)
        val currentSubId = SimManager.getDefaultDataSubId()
        val currentSim = sims.find { it.subscriptionId == currentSubId }

        if (currentSim != null) {
            val label = currentSim.displayName?.toString() ?: "SIM ${currentSim.simSlotIndex + 1}"
            val number = SimManager.getPhoneNumber(this, currentSim.subscriptionId)
            updateTile(Tile.STATE_ACTIVE, label, number)
        } else {
            updateTile(Tile.STATE_INACTIVE, "Switch Data", null)
        }
    }

    private fun updateTile(state: Int, label: String, subtitle: String?) {
        val tile = qsTile
        if (tile != null) {
            tile.state = state
            tile.label = label
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = subtitle
            }
            tile.updateTile()
        }
    }
}
