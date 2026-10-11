package com.bestcallerapp.smswebhook.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryAttemptDao
import com.bestcallerapp.smswebhook.data.local.dao.DeliveryDao
import com.bestcallerapp.smswebhook.data.local.dao.SmsMessageDao
import com.bestcallerapp.smswebhook.data.local.dao.WebhookDao
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryAttemptEntity
import com.bestcallerapp.smswebhook.data.local.entity.DeliveryEntity
import com.bestcallerapp.smswebhook.data.local.entity.RegexExtractorEntity
import com.bestcallerapp.smswebhook.data.local.entity.SmsMessageEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookFilterEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookHeaderEntity
import com.bestcallerapp.smswebhook.data.local.entity.WebhookPayloadConfigEntity

@Database(
    entities = [
        SmsMessageEntity::class,
        WebhookEntity::class,
        WebhookHeaderEntity::class,
        WebhookFilterEntity::class,
        WebhookPayloadConfigEntity::class,
        RegexExtractorEntity::class,
        DeliveryEntity::class,
        DeliveryAttemptEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun webhookDao(): WebhookDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun deliveryAttemptDao(): DeliveryAttemptDao

    companion object {
        const val VERSION = 1
        const val NAME = "sms_webhook.db"

        /**
         * Explicit migrations, oldest first. Every schema change must bump [VERSION], add a
         * Migration here and commit the exported schema in app/schemas. Destructive fallback is
         * intentionally not enabled: losing queued SMS on upgrade is unacceptable.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
