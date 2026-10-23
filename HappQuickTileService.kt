package ru.pixel.happgemini

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Системная плитка Вкл/Выкл в шторке быстрых настроек Google Pixel 9 Pro XL.
 */
class HappQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("happ_gemini_prefs", MODE_PRIVATE)
        val currentlyActive = prefs.getBoolean("tunnel_active", false)
        val nextState = !currentlyActive
        prefs.edit().putBoolean("tunnel_active", nextState).apply()

        syncTileState()

        val selectedDnsId = prefs.getString("selected_dns", "cloudflare") ?: "cloudflare"
        val dns = HappConfigManager.dnsPresets.find { it.id == selectedDnsId }
            ?: HappConfigManager.dnsPresets.first()

        if (nextState) {
            HappConfigManager.activateOptimalHappTunnel(applicationContext, dns)
        } else {
            val launchIntent = packageManager.getLaunchIntentForPackage(HappConfigManager.HAPP_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    startActivity(launchIntent)
                }
            }
        }
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("happ_gemini_prefs", MODE_PRIVATE)
        val isActive = prefs.getBoolean("tunnel_active", false)
        val dnsId = prefs.getString("selected_dns", "cloudflare") ?: "cloudflare"

        tile.label = "Gemini + Happ"
        if (isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "Активен ($dnsId)"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Отключено"
        }
        tile.updateTile()
    }
}
