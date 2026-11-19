package ru.mobilegateway.app

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var runButton: Button
    private var lastReport = "Диагностика ещё не запускалась."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFFF4F7F6.toInt())
        }
        fun text(value: String, size: Float, color: Int): TextView =
            TextView(this).apply { text = value; textSize = size; setTextColor(color) }

        layout.addView(text("Mobile Gateway", 28f, 0xFF102A27.toInt()))
        layout.addView(text("Диагностика мобильной сети • версия 0.1", 14f, 0xFF52635F.toInt()))
        status = text("Определение подключения…", 16f, 0xFF173D36.toInt())
        status.setPadding(16, 16, 16, 16)
        status.setBackgroundColor(0xFFE3EFEB.toInt())
        layout.addView(status)
        runButton = Button(this).apply {
            text = "Запустить диагностику"
            setOnClickListener { runDiagnostics() }
        }
        layout.addView(runButton)
        layout.addView(text("Результаты проверки", 20f, 0xFF102A27.toInt()))
        results = text("Нажмите кнопку. Приложение проверит DNS и HTTPS-доступность нескольких контрольных адресов.", 14f, 0xFF263A36.toInt())
        results.setPadding(16, 16, 16, 16)
        results.setBackgroundColor(0xFFFFFFFF.toInt())
        layout.addView(results)
        val share = Button(this).apply {
            text = "Поделиться отчётом"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Mobile Gateway — отчёт")
                    putExtra(Intent.EXTRA_TEXT, lastReport)
                }
                startActivity(Intent.createChooser(intent, "Отправить отчёт"))
            }
        }
        layout.addView(share)
        layout.addView(text("Важно: приложение только диагностирует соединение. Оно не отключает ограничения оператора и не гарантирует доступ к заблокированным ресурсам.", 12f, 0xFF66736F.toInt()))
        scroll.addView(layout)
        setContentView(scroll)
        showNetworkState()
    }

    private fun showNetworkState(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val transport = when {
            caps == null -> "Нет активной сети"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Другая сеть"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val line = "Подключение: $transport\nПроверка Android: " +
            if (validated) "интернет подтверждён" else "интернет не подтверждён"
        status.text = line
        return line
    }

    private fun runDiagnostics() {
        runButton.isEnabled = false
        results.text = "Выполняется проверка…"
        val networkInfo = showNetworkState()
        executor.execute {
            val lines = mutableListOf<String>()
            lines += "MOBILE GATEWAY — ОТЧЁТ"
            lines += "Время: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
            lines += networkInfo
            lines += ""
            for (host in listOf("example.com", "cloudflare.com", "ya.ru")) {
                lines += "Узел: $host"
                try {
                    val start = System.currentTimeMillis()
                    val addresses = InetAddress.getAllByName(host)
                    val elapsed = System.currentTimeMillis() - start
                    val v4 = addresses.filterIsInstance<Inet4Address>().map { it.hostAddress }.distinct()
                    val v6 = addresses.filterIsInstance<Inet6Address>().map { it.hostAddress }.distinct()
                    lines += "DNS: успешно (${elapsed} мс)"
                    lines += "IPv4: ${if (v4.isEmpty()) "нет" else v4.joinToString()}"
                    lines += "IPv6: ${if (v6.isEmpty()) "нет" else v6.joinToString()}"
                } catch (e: Exception) {
                    lines += "DNS: ошибка — ${e.javaClass.simpleName}: ${e.message ?: "нет подробностей"}"
                }
                try {
                    val start = System.currentTimeMillis()
                    val conn = (URL("https://$host/").openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                    }
                    val code = conn.responseCode
                    val elapsed = System.currentTimeMillis() - start
                    lines += "HTTPS: ответ HTTP $code (${elapsed} мс)"
                    conn.disconnect()
                } catch (e: Exception) {
                    lines += "HTTPS: ошибка — ${e.javaClass.simpleName}: ${e.message ?: "нет подробностей"}"
                }
                lines += ""
            }
            lines += "Примечание: результаты зависят от сети, DNS и доступности контрольных узлов."
            val report = lines.joinToString("\n")
            lastReport = report
            runOnUiThread {
                results.text = report
                runButton.isEnabled = true
                showNetworkState()
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
