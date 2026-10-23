package ru.pixel.happgemini

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFA8C7FA),
                    secondary = Color(0xFF6DD58C),
                    background = Color(0xFF0C0F14),
                    surface = Color(0xFF171B22)
                )
            ) {
                HappGeminiOptimizerScreen(
                    onRequestAddTile = { requestQuickSettingsTile() }
                )
            }
        }
    }

    /**
     * Использует API Android 13-16 (StatusBarManager.requestAddTileService)
     * для добавления кнопки Вкл/Выкл в шторку Pixel 9 Pro XL в один тап прямо из приложения.
     */
    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            statusBarManager?.requestAddTileService(
                ComponentName(this, HappQuickTileService::class.java),
                "Gemini + Happ",
                Icon.createWithResource(this, android.R.drawable.ic_lock_lock),
                mainExecutor
            ) { result ->
                Toast.makeText(this, "Статус плитки в шторке: $result", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Откройте шторку и нажмите карандаш для добавления плитки", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun HappGeminiOptimizerScreen(onRequestAddTile: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("happ_gemini_prefs", ComponentActivity.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(prefs.getBoolean("tunnel_active", false)) }
    var selectedDnsId by remember { mutableStateOf(prefs.getString("selected_dns", "cloudflare") ?: "cloudflare") }
    var splitRuDirect by remember { mutableStateOf(true) }
    var forceGeminiProxy by remember { mutableStateOf(true) }
    var enableSniffing by remember { mutableStateOf(true) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var pingMs by remember { mutableStateOf(38L) }

    val activeDns = HappConfigManager.dnsPresets.find { it.id == selectedDnsId }
        ?: HappConfigManager.dnsPresets.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F14))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Верхняя панель с кнопкой Настроек
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Happ & Gemini Hub", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Google Pixel 9 Pro XL • Smart Xray & DNS", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Button(
                onClick = { showSettingsModal = !showSettingsModal },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212731))
            ) {
                Text(if (showSettingsModal) "Закрыть" else "Настройки", color = Color.White, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Главная круглая кнопка Вкл / Выкл
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color(0xFF0A3818) else Color(0xFF212731))
                .clickable {
                    val next = !isConnected
                    isConnected = next
                    prefs.edit().putBoolean("tunnel_active", next).apply()
                    if (next) {
                        scope.launch {
                            val measured = HappConfigManager.measureLatencyMs(activeDns.ip)
                            if (measured > 0) pingMs = measured
                        }
                        HappConfigManager.activateOptimalHappTunnel(
                            context = context,
                            dns = activeDns,
                            splitRuDirect = splitRuDirect,
                            forceGeminiProxy = forceGeminiProxy,
                            enableSniffing = enableSniffing
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isConnected) "АКТИВЕН" else "ВКЛЮЧИТЬ",
                    color = if (isConnected) Color(0xFF6DD58C) else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Пинг: ${pingMs} мс",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка добавления плитки в верхнюю шторку Pixel 9 Pro XL
        Button(
            onClick = onRequestAddTile,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8C7FA))
        ) {
            Text(
                "Добавить кнопку Вкл/Выкл в шторку Pixel",
                color = Color(0xFF041E49),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Секция выбора DNS-сервера
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF171B22)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Смена DNS-сервера (Happ DoH + Pixel DoT)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))

                HappConfigManager.dnsPresets.forEach { preset ->
                    val isSelected = preset.id == selectedDnsId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedDnsId = preset.id
                                prefs.edit().putString("selected_dns", preset.id).apply()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF0842A0) else Color(0xFF212731)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(preset.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(preset.dohUrl, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                            Text(preset.ip, color = Color(0xFF6DD58C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { HappConfigManager.applySystemPrivateDnsOnPixel(context, activeDns) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Скопировать DoT (${activeDns.dotHost}) и открыть настройки DNS Pixel", color = Color(0xFFA8C7FA), fontSize = 12.sp)
                }
            }
        }

        // Модальная карточка настроек параметров
        if (showSettingsModal) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF171B22)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Параметры маршрутизации", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Прямой маршрут для банков РФ и Госуслуг", color = Color.White, fontSize = 13.sp)
                        Switch(checked = splitRuDirect, onCheckedChange = { splitRuDirect = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Принудительный туннель для Google Gemini", color = Color.White, fontSize = 13.sp)
                        Switch(checked = forceGeminiProxy, onCheckedChange = { forceGeminiProxy = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sniffing SNI (Защита от утечек DNS)", color = Color.White, fontSize = 13.sp)
                        Switch(checked = enableSniffing, onCheckedChange = { enableSniffing = it })
                    }
                }
            }
        }
    }
}
