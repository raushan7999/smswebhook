package com.bestcallerapp.smswebhook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.bestcallerapp.smswebhook.BuildConfig
import com.bestcallerapp.smswebhook.core.constants.AppConstants
import com.bestcallerapp.smswebhook.core.logging.AndroidAppLogger
import com.bestcallerapp.smswebhook.core.logging.AppLogger
import com.bestcallerapp.smswebhook.core.network.AndroidConnectivityObserver
import com.bestcallerapp.smswebhook.core.network.ConnectivityObserver
import com.bestcallerapp.smswebhook.core.security.KeystoreSecretCipher
import com.bestcallerapp.smswebhook.core.security.SecretCipher
import com.bestcallerapp.smswebhook.core.utils.ApplicationScope
import com.bestcallerapp.smswebhook.core.utils.IoDispatcher
import com.bestcallerapp.smswebhook.core.utils.SystemTimeProvider
import com.bestcallerapp.smswebhook.core.utils.TimeProvider
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryAttemptDao
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryDao
import com.bestcallerapp.smswebhook.data.local.dao.SmsMessageDao
import com.bestcallerapp.smswebhook.data.local.dao.WebhookDao
import com.bestcallerapp.smswebhook.data.local.database.AppDatabase
import com.bestcallerapp.smswebhook.data.remote.OkHttpWebhookSender
import com.bestcallerapp.smswebhook.data.repository.DeliveryLogRepositoryImpl
import com.bestcallerapp.smswebhook.data.repository.DeliveryRepositoryImpl
import com.bestcallerapp.smswebhook.data.repository.SmsRepositoryImpl
import com.bestcallerapp.smswebhook.data.repository.WebhookRepositoryImpl
import com.bestcallerapp.smswebhook.data.serialization.JsonConfigCodec
import com.bestcallerapp.smswebhook.data.settings.DataStoreSettingsRepository
import com.bestcallerapp.smswebhook.data.system.AndroidDeviceInfoProvider
import com.bestcallerapp.smswebhook.data.system.AndroidPowerStatusProvider
import com.bestcallerapp.smswebhook.data.system.AndroidSmsDeleter
import com.bestcallerapp.smswebhook.data.system.AndroidSmsListenerController
import com.bestcallerapp.smswebhook.domain.engine.WebhookValidator
import com.bestcallerapp.smswebhook.domain.port.ConfigCodec
import com.bestcallerapp.smswebhook.domain.port.DeviceInfoProvider
import com.bestcallerapp.smswebhook.domain.port.PowerStatusProvider
import com.bestcallerapp.smswebhook.domain.port.SmsDeleter
import com.bestcallerapp.smswebhook.domain.port.SmsListenerController
import com.bestcallerapp.smswebhook.domain.port.WebhookSender
import com.bestcallerapp.smswebhook.domain.port.WorkScheduler
import com.bestcallerapp.smswebhook.domain.repository.DeliveryLogRepository
import com.bestcallerapp.smswebhook.domain.repository.DeliveryRepository
import com.bestcallerapp.smswebhook.domain.repository.SettingsRepository
import com.bestcallerapp.smswebhook.domain.repository.SmsRepository
import com.bestcallerapp.smswebhook.domain.repository.WebhookRepository
import com.bestcallerapp.smswebhook.worker.WorkManagerScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

    @Provides fun provideSmsMessageDao(db: AppDatabase): SmsMessageDao = db.smsMessageDao()

    @Provides fun provideWebhookDao(db: AppDatabase): WebhookDao = db.webhookDao()

    @Provides fun provideDeliveryDao(db: AppDatabase): DeliveryDao = db.deliveryDao()

    @Provides fun provideDeliveryAttemptDao(db: AppDatabase): DeliveryAttemptDao = db.deliveryAttemptDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "${AppConstants.USER_AGENT_PRODUCT}/${BuildConfig.VERSION_NAME}")
                        .build(),
                )
            }
            .build()

    private const val DEFAULT_TIMEOUT_SECONDS = 15L
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Named(WebhookValidator.ALLOW_CLEARTEXT)
    fun provideAllowCleartext(): Boolean = BuildConfig.ALLOW_CLEARTEXT_WEBHOOKS
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSmsRepository(impl: SmsRepositoryImpl): SmsRepository

    @Binds abstract fun bindWebhookRepository(impl: WebhookRepositoryImpl): WebhookRepository

    @Binds abstract fun bindDeliveryRepository(impl: DeliveryRepositoryImpl): DeliveryRepository

    @Binds abstract fun bindDeliveryLogRepository(impl: DeliveryLogRepositoryImpl): DeliveryLogRepository

    @Binds abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds abstract fun bindLogger(impl: AndroidAppLogger): AppLogger

    @Binds abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds abstract fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher

    @Binds abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds abstract fun bindWebhookSender(impl: OkHttpWebhookSender): WebhookSender

    @Binds abstract fun bindWorkScheduler(impl: WorkManagerScheduler): WorkScheduler

    @Binds abstract fun bindSmsDeleter(impl: AndroidSmsDeleter): SmsDeleter

    @Binds abstract fun bindDeviceInfoProvider(impl: AndroidDeviceInfoProvider): DeviceInfoProvider

    @Binds abstract fun bindListenerController(impl: AndroidSmsListenerController): SmsListenerController

    @Binds abstract fun bindConfigCodec(impl: JsonConfigCodec): ConfigCodec

    @Binds abstract fun bindPowerStatusProvider(impl: AndroidPowerStatusProvider): PowerStatusProvider
}
