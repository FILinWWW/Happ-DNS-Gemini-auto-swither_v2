package ru.pixel.happgemini

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DnsPreset(
    val id: String,
    val title: String,
    val dohUrl: String,
    val ip: String,
    val dotHost: String,
    val domesticDoh: String = "https://common.dot.dns.yandex.net/dns-query",
    val domesticIp: String = "77.88.8.8"
)

object HappConfigManager {

    const val HAPP_PACKAGE = "com.happproxy"

    val dnsPresets = listOf(
        DnsPreset("cloudflare", "Cloudflare 1.1.1.1 (DoH)", "https://cloudflare-dns.com/dns-query", "1.1.1.1", "one.one.one.one"),
        DnsPreset("google", "Google Public DNS", "https://dns.google/dns-query", "8.8.8.8", "dns.google"),
        DnsPreset("quad9", "Quad9 Security DNS", "https://dns.quad9.net/dns-query", "9.9.9.9", "dns.quad9.net"),
        DnsPreset("adguard", "AdGuard DNS (AdBlock)", "https://dns.adguard-dns.com/dns-query", "94.140.14.14", "dns.adguard-dns.com")
    )

    /**
     * Строит JSON-профиль маршрутизации для Happ (Xray-core),
     * при котором Google Gemini и зарубежные AI-сервисы идут через туннель,
     * а российские банки, Госуслуги и локальные приложения работают напрямую без задержек.
     */
    fun buildSmartRoutingJson(
        dns: DnsPreset,
        splitRuDirect: Boolean = true,
        forceGeminiProxy: Boolean = true,
        enableSniffing: Boolean = true
    ): String {
        val root = JSONObject()
        root.put("Name", "Pixel-Gemini-RU-Smart")
        root.put("GlobalProxy", "true")
        root.put("RemoteDNSType", "DoH")
        root.put("RemoteDNSDomain", dns.dohUrl)
        root.put("RemoteDNSIP", dns.ip)
        root.put("DomesticDNSType", "DoH")
        root.put("DomesticDNSDomain", dns.domesticDoh)
        root.put("DomesticDNSIP", dns.domesticIp)
        root.put("Geoipurl", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat")
        root.put("Geositeurl", "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat")

        val dnsHosts = JSONObject()
        dnsHosts.put("cloudflare-dns.com", "1.1.1.1")
        dnsHosts.put("dns.google", "8.8.8.8")
        root.put("DnsHosts", dnsHosts)

        val directSites = JSONArray()
        if (splitRuDirect) {
            listOf(
                "geosite:category-ru",
                "geosite:yandex",
                "geosite:vk",
                "domain:gosuslugi.ru",
                "domain:sberbank.ru",
                "domain:tbank.ru",
                "domain:alfabank.ru",
                "domain:ozon.ru",
                "domain:wildberries.ru",
                "domain:avito.ru",
                "domain:rzd.ru"
            ).forEach { directSites.put(it) }
        }
        root.put("DirectSites", directSites)

        val directIp = JSONArray()
        if (splitRuDirect) {
            directIp.put("geoip:ru")
            directIp.put("geoip:private")
        }
        listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16").forEach { directIp.put(it) }
        root.put("DirectIp", directIp)

        val proxySites = JSONArray()
        if (forceGeminiProxy) {
            listOf(
                "geosite:google",
                "geosite:openai",
                "domain:gemini.google.com",
                "domain:bard.google.com",
                "domain:aistudio.google.com",
                "domain:generativelanguage.googleapis.com",
                "domain:makersuite.google.com",
                "domain:alkalimakersuite-pa.clients6.google.com",
                "domain:proactivebackend-pa.googleapis.com",
                "domain:youtube.com",
                "domain:googlevideo.com"
            ).forEach { proxySites.put(it) }
        }
        root.put("ProxySites", proxySites)

        val proxyIp = JSONArray()
        proxyIp.put("geoip:google")
        proxyIp.put("geoip:telegram")
        root.put("ProxyIp", proxyIp)

        root.put("BlockSites", JSONArray().put("geosite:category-ads-all"))
        root.put("BlockIp", JSONArray())
        root.put("DomainStrategy", if (enableSniffing) "IPIfNonMatch" else "AsIs")
        root.put("FakeDNS", "false")

        return root.toString(2)
    }

    /**
     * Генерирует официальный диплинк happ://routing/onadd/{base64},
     * который автоматически добавляет и активирует профиль маршрутизации и DNS в Happ.
     */
    fun buildHappRoutingDeepLink(
        dns: DnsPreset,
        splitRuDirect: Boolean = true,
        forceGeminiProxy: Boolean = true,
        enableSniffing: Boolean = true
    ): String {
        val json = buildSmartRoutingJson(dns, splitRuDirect, forceGeminiProxy, enableSniffing)
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "happ://routing/onadd/$encoded"
    }

    /**
     * Применяет оптимальный маршрут и DNS в Happ и запускает клиент.
     */
    fun activateOptimalHappTunnel(
        context: Context,
        dns: DnsPreset,
        splitRuDirect: Boolean = true,
        forceGeminiProxy: Boolean = true,
        enableSniffing: Boolean = true
    ) {
        val deepLink = buildHappRoutingDeepLink(dns, splitRuDirect, forceGeminiProxy, enableSniffing)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(HAPP_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Если пакет не перехватил прямой интент, открываем Happ или его страницу в Google Play
            val launchIntent = context.packageManager.getLaunchIntentForPackage(HAPP_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                val storeIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$HAPP_PACKAGE")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(storeIntent)
            }
        }
    }

    /**
     * Копирует DoT-адрес DNS в буфер обмена и открывает настройки сети Pixel 9 Pro XL.
     */
    fun applySystemPrivateDnsOnPixel(context: Context, dns: DnsPreset) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Private DNS DoT", dns.dotHost))

        val settingsIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(settingsIntent)
    }

    /**
     * Измеряет TCP-задержку (мс) до выбранного DNS/узла для выбора оптимального маршрута.
     */
    suspend fun measureLatencyMs(host: String = "1.1.1.1", port: Int = 443): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }
}
