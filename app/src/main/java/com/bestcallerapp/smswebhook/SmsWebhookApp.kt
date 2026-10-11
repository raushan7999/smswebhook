package com.bestcallerapp.smswebhook

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bestcallerapp.smswebhook.core.utils.ApplicationScope
import com.bestcallerapp.smswebhook.domain.port.SmsListenerController
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.worker.WorkerNotifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmsWebhookApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var scheduler: WorkScheduler

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var listenerController: SmsListenerController

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        WorkerNotifications.createChannel(this)
        scheduler.ensurePeriodicMaintenance()
        scheduler.scheduleReconcile()
        appScope.launch {
            // Keep the receiver component state aligned with the persisted user choice
            // (e.g. after an app update re-enabled manifest components).
            listenerController.setListening(settingsRepository.current().listenerEnabled)
        }
    }
}
