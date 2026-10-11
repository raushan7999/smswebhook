package com.bestcallerapp.smswebhook.data.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.bestcallerapp.smswebhook.domain.model.SmsMessage
import com.bestcallerapp.smswebhook.domain.port.DeviceInfoProvider
import com.bestcallerapp.smswebhook.domain.port.PowerStatusProvider
import com.bestcallerapp.smswebhook.domain.port.SmsDeleter
import com.bestcallerapp.smswebhook.domain.port.SmsDeletionResult
import com.bestcallerapp.smswebhook.domain.port.SmsListenerController
import com.bestcallerapp.smswebhook.receiver.SmsReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Since Android 4.4 only the default SMS app may write to (and therefore delete from) the SMS
 * provider. This app intentionally does not implement a full SMS client — that would require
 * handling MMS, sending and a complete messaging UI, and Google Play restricts the role — so it
 * never holds that role. Deletion is therefore reported as unsupported rather than attempted
 * through unsupported means; the settings screen explains this to the user.
 */
@Singleton
class AndroidSmsDeleter @Inject constructor() : SmsDeleter {

    override fun isSupported(): Boolean = false

    override suspend fun delete(message: SmsMessage): SmsDeletionResult = SmsDeletionResult.NotPermitted
}

@Singleton
class AndroidDeviceInfoProvider @Inject constructor() : DeviceInfoProvider {
    override fun description(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
            Build.MODEL
        } else {
            "$manufacturer ${Build.MODEL}"
        }
        return "$model (Android ${Build.VERSION.RELEASE})"
    }
}

/**
 * Enables/disables the manifest-declared [SmsReceiver] component. When disabled the system does
 * not even start the app for incoming SMS, which is cheaper and more honest than ignoring them.
 */
@Singleton
class AndroidSmsListenerController @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsListenerController {

    private val component get() = ComponentName(context, SmsReceiver::class.java)

    override fun hasReceivePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    override fun setListening(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    override fun isReceiverEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}

@Singleton
class AndroidPowerStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PowerStatusProvider {
    override fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
