package eu.kanade.tachiyomi

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import animiru.core.cache.ThumbnailCache
import animiru.core.coil.EpisodeThumbnailFetcher
import animiru.core.coil.EpisodeThumbnailKeyer
import animiru.domain.player.service.PlayerPreferences
import animiru.domain.torrent.service.TorrentUtilsHolder
import animiru.domain.torrent.service.TorrentUtilsImpl
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.connection.SyncPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.tachiyomi.crash.CrashActivity
import eu.kanade.tachiyomi.crash.GlobalExceptionHandler
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.AnimeCoverKeyer
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher
import eu.kanade.tachiyomi.data.coil.AnimeKeyer
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.connection.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connection.syncmiru.SyncDataJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import mihon.app.di.AppGraph
import mihon.app.di.injekt.MetroInteropModule
import mihon.core.metro.GraphProvider
import mihon.core.migration.Migration
import mihon.core.migration.Migrator
import org.conscrypt.Conscrypt
import tachiyomi.cast.CastManager
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.CustomAnimeInfoHolder
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.WidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.security.Security

class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory, GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this, isDebugBuild = isDebugBuildType)
    }

    @Inject private lateinit var preferenceStore: PreferenceStore

    @Inject private lateinit var basePreferences: BasePreferences

    @Inject private lateinit var networkPreferences: NetworkPreferences

    @Inject private lateinit var uiPreferences: UiPreferences

    @Inject private lateinit var coverCache: CoverCache

    @Inject private lateinit var networkHelper: NetworkHelper

    @Inject private lateinit var sourceManager: SourceManager

    @Inject private lateinit var widgetManager: WidgetManager

    @Inject private lateinit var injektMetroInteropModule: MetroInteropModule

    @Inject private lateinit var migrations: Set<Migration>

    // AM -->
    @Inject private lateinit var backgroundCache: BackgroundCache

    @Inject private lateinit var thumbnailCache: ThumbnailCache
    // <-- AM

    // AM (SYNC) -->
    @Inject private lateinit var syncPreferences: SyncPreferences
    // <-- AM (SYNC)

    // AM (CAST) -->
    @Inject private lateinit var castManager: CastManager

    @Inject private lateinit var playerPreferences: PlayerPreferences
    // <-- AM (CAST)

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()

        // Must run before the graph is built, since injecting dependencies initializes WebView and the
        // suffix can't be set once a provider exists in the process. Secondary processes die otherwise.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        graph.inject(this)
        // AM -->
        TorrentUtilsHolder.init(graph.torrentUtilsImpl)
        // <-- AM
        // AM (CUSTOM_INFORMATION) -->
        CustomAnimeInfoHolder.init(graph.getCustomAnimeInfo)
        // <-- AM (CUSTOM_INFORMATION)
        setupInjekt()

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

        // Show notification to disable Incognito Mode when it's enabled
        basePreferences.incognitoMode.changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    notify(
                        Notifications.ID_INCOGNITO_MODE,
                        Notifications.CHANNEL_INCOGNITO_MODE,
                    ) {
                        setContentTitle(stringResource(MR.strings.pref_incognito_mode))
                        setContentText(stringResource(MR.strings.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE).setPackage(BuildConfig.APPLICATION_ID),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(scope)

        setAppCompatDelegateThemeMode(uiPreferences.themeMode.get())

        // Updates widget update
        widgetManager.init(scope)

        // AM (SYNC) -->
        startSyncJob(syncPreferences.getSyncTriggerOptions().syncOnAppStart)
        // <-- AM (SYNC)

        // AM --> (CAST)
        if (playerPreferences.enableCast.get()) {
            castManager.initialize(this)
        }
        // <-- AM (CAST)

        if (!LogcatLogger.isInstalled) {
            val minLogPriority = when {
                networkPreferences.verboseLogging.get() -> LogPriority.VERBOSE
                BuildConfig.DEBUG -> LogPriority.DEBUG
                else -> LogPriority.INFO
            }
            LogcatLogger.install()
            LogcatLogger.loggers += AndroidLogcatLogger(minLogPriority)
        }

        initializeMigrator()
    }

    private fun setupInjekt() {
        Injekt.addSingleton<Application>(this)
        Injekt.addSingleton<Context>(this)
        Injekt.importModule(injektMetroInteropModule)
    }

    private fun initializeMigrator() {
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        logcat {
            "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE} with ${migrations.size} migration(s)"
        }
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations.toList(),
            onMigrationComplete = {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { networkHelper.client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(AnimeImageFetcher.AnimeCoverFactory(callFactoryLazy, coverCache, sourceManager))
                add(AnimeImageFetcher.AnimeFactory(callFactoryLazy, coverCache, backgroundCache, sourceManager))
                // AM -->
                add(EpisodeThumbnailFetcher.EpisodeFactory(callFactoryLazy, thumbnailCache, sourceManager))
                // <-- AM
                // Keyer
                add(AnimeCoverKeyer(coverCache))
                add(AnimeKeyer())
                // AM -->
                add(EpisodeThumbnailKeyer())
                // <-- AM
            }

            memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build(),
            )

            crossfade((300 * this@App.animatorDurationScale).toInt())
            allowRgb565(DeviceUtil.isLowRamDevice(this@App))
            if (networkPreferences.verboseLogging.get()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStart(this)
        // AM (DISCORD_RPC) -->
        DiscordRPCService.start(applicationContext)
        // <-- AM (DISCORD_RPC)
        // AM (SYNC) -->
        startSyncJob(syncPreferences.getSyncTriggerOptions().syncOnAppResume)
        // <-- AM (SYNC)
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStopped(this)
        // AM (DISCORD_RPC) -->
        DiscordRPCService.stop(applicationContext, 10000L)
        // <-- AM (DISCORD_RPC)
        // AM (SYNC) -->
        startSyncJob(syncPreferences.getSyncTriggerOptions().syncOnAppStart)
        // <-- AM (SYNC)
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Thread.currentThread().stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.lowercase() in setOf("org.chromium.base.buildinfo", "org.chromium.base.apkinfo") &&
                    trace.methodName.lowercase() in setOf("getall", "getpackagename", "<init>")
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (_: Exception) {
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode.set(false)
        }

        fun register() {
            if (!registered) {
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }

    // AM (SYNC) -->
    private fun startSyncJob(syncTriggerOption: Boolean) {
        if (syncPreferences.isSyncEnabled() && syncTriggerOption) {
            SyncDataJob.startNow(this@App.applicationContext.workManager)
        }
    }
    // <-- AM (SYNC)
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
