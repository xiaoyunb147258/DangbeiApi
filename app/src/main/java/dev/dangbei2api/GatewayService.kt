package dev.dangbei2api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import dev.dangbei2api.dangbei.DangbeiClient
import dev.dangbei2api.http.GatewayServer
import dev.dangbei2api.http.RequestHandler
import dev.dangbei2api.store.SettingsStore
import dev.dangbei2api.store.TokenStore
import dev.dangbei2api.util.Logger
import dev.dangbei2api.util.NetUtil

class GatewayService : Service() {

    private lateinit var settings: SettingsStore
    private lateinit var tokenStore: TokenStore
    private lateinit var client: DangbeiClient
    private var server: GatewayServer? = null

    companion object {
        const val ACTION_START = "dev.dangbei2api.START"
        const val ACTION_STOP = "dev.dangbei2api.STOP"
        const val ACTION_RESTART = "dev.dangbei2api.RESTART"
        private const val CHANNEL_ID = "dangbei_gateway"
        private const val NOTIFY_ID = 1001

        @Volatile
        var running = false
            private set

        @Volatile
        var currentPort = 9980
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable {
        if (running) {
            try {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFY_ID, buildNotification(currentPort))
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        tokenStore = TokenStore.get(this)
        client = DangbeiClient()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopGateway()
                startGateway()
            }
            else -> startGateway()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "当贝网关", NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun startGateway() {
        if (running) return
        val port = settings.port
        currentPort = port
        startForeground(NOTIFY_ID, buildNotification(port))
        try {
            val rh = RequestHandler(settings, tokenStore, client)
            server = GatewayServer(port, rh).also { it.start() }
            running = true
            currentPort = GatewayServer.runningPort
            Logger.log("服务启动，端口 ${GatewayServer.runningPort}")
            handler.removeCallbacks(notifyRunnable)
            handler.postDelayed(notifyRunnable, 30_000)
        } catch (e: Exception) {
            Logger.log("服务启动失败：${e.message}")
            running = false
        }
    }

    private fun stopGateway() {
        handler.removeCallbacks(notifyRunnable)
        server?.stop()
        server = null
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.log("服务已停止")
    }

    private fun buildNotification(port: Int): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("当贝 API 运行中")
            .setContentText(
                "地址 " + NetUtil.getLocalIp() + ":" + port +
                    "  已处理 " + GatewayServer.requestCount() + " 次请求"
            )
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(notifyRunnable)
        stopGateway()
        super.onDestroy()
    }

    fun refresh() {
        handler.removeCallbacks(notifyRunnable)
        handler.post(notifyRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
