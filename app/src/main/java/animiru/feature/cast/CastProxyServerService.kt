package animiru.feature.cast

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import animiru.domain.player.service.PlayerPreferences
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.animiru.AMMR

class CastProxyServerService : Service() {

    private val graph: AppGraph by lazy { metroGraph() }

    private var server: CastProxyServer? = null

    @Inject private lateinit var networkHelper: NetworkHelper

    @Inject private lateinit var playerPreferences: PlayerPreferences

    @Inject private lateinit var json: Json

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        graph.inject(this)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (isRunning.value) return START_STICKY

        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        server = CastProxyServer(
            baseClient = networkHelper.client,
            contentResolver = contentResolver,
            ipAddress = address,
            port = playerPreferences.castProxyPort.get().toInt(),
            json = json,
        )

        try {
            server?.start()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to start cast server" }
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.update { _ -> true }

        val stopIntent = Intent(this, CastProxyServerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = notificationBuilder(Notifications.CHANNEL_CAST_SERVER) {
            setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_cast_24dp)
            addAction(
                R.drawable.ic_stop_circle_24dp,
                "Stop",
                pendingStopIntent,
            )
            setContentText(stringResource(AMMR.strings.pref_cast_server))
            setAutoCancel(false)
            setOngoing(true)
            setUsesChronometer(true)
        }

        ServiceCompat.startForeground(
            this,
            Notifications.ID_CAST_SERVER,
            builder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.update { _ -> false }
        server?.stop()
        super.onDestroy()
    }

    companion object {
        private const val NAME = "CastProxyServer"
        private val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.$NAME.ACTION_STOP"
        const val EXTRA_ADDRESS = "$NAME.EXTRA.ADDRESS"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }
}
