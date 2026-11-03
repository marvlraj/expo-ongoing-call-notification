package expo.modules.ongoingcallnotification

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class OngoingCallNotificationModule : Module() {
  
  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("React Context is null")

  private var phoneAccountHandle: PhoneAccountHandle? = null
  private var telecomManager: TelecomManager? = null

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      val eventData = mutableMapOf<String, Any?>()
      intent?.getStringExtra("chatId")?.let { eventData["chatId"] = it }
      intent?.getStringExtra("callerId")?.let { eventData["callerId"] = it }

      when (intent?.action) {
        "ACTION_HANGUP" -> {
          context?.let { OngoingCallService.cancelCallNotification(it) }
          sendEvent("onHangUp", eventData)
        }
        "ACTION_ANSWER" -> {
          context?.let { OngoingCallService.cancelCallNotification(it) }
          sendEvent("onAnswer", eventData)
        }
        "ACTION_DECLINE" -> {
          context?.let { OngoingCallService.cancelCallNotification(it) }
          sendEvent("onDecline", eventData)
        }
      }
    }
  }

  private fun registerTelecomAccount() {
    try {
      telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
      val componentName = ComponentName(context, VoipConnectionService::class.java)
      phoneAccountHandle = PhoneAccountHandle(componentName, "ongoing_call_account")
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val defaultAppLabel = try {
          context.applicationInfo.loadLabel(context.packageManager).toString()
        } catch (_: Exception) {
          "Call"
        }

        val label = try {
          val resId = context.resources.getIdentifier("ongoing_call_default_name", "string", context.packageName)
          if (resId != 0) context.getString(resId) else defaultAppLabel
        } catch (_: Exception) {
          defaultAppLabel
        }

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, label)
          .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
          .build()
        telecomManager?.registerPhoneAccount(phoneAccount)
      }
    } catch (e: Exception) {
      Log.w("OngoingCallModule", "Telecom registerPhoneAccount non-fatal warning", e)
    }
  }

  override fun definition() = ModuleDefinition {
    Name("OngoingCallNotification")

    Events("onHangUp", "onAnswer", "onDecline")

    OnCreate {
      val filter = IntentFilter().apply {
        addAction("ACTION_HANGUP")
        addAction("ACTION_ANSWER")
        addAction("ACTION_DECLINE")
      }
      try {
        ContextCompat.registerReceiver(
          context,
          receiver,
          filter,
          ContextCompat.RECEIVER_NOT_EXPORTED
        )
      } catch (e: Exception) {
        Log.w("OngoingCallModule", "Failed to register receiver", e)
      }
      registerTelecomAccount()
    }

    OnDestroy {
      try {
        appContext.reactContext?.unregisterReceiver(receiver)
      } catch (_: Exception) {}
      VoipConnectionService.currentConnection?.destroy()
      VoipConnectionService.currentConnection = null
    }

    Function("startOngoingCallNotification") { callerName: String, avatarUrl: String? ->
      // Start foreground service ONLY when the call is actively ongoing / answered
      try {
        val intent = Intent(context, OngoingCallService::class.java).apply {
          action = OngoingCallService.ACTION_START_ONGOING
          putExtra("CALLER_NAME", callerName)
          putExtra("AVATAR_URL", avatarUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        Log.e("OngoingCallModule", "Failed to start ongoing call foreground service", e)
      }
    }

    Function("startIncomingCallNotification") { callerName: String, avatarUrl: String?, chatId: String?, callerId: String? ->
      // 1. Telecom Manager Incoming (Self-managed)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && phoneAccountHandle != null) {
        val extras = Bundle().apply {
          putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.parse("tel:$callerName"))
        }
        try {
          val canRegister = telecomManager?.isIncomingCallPermitted(phoneAccountHandle) ?: true
          if (canRegister) {
            telecomManager?.addNewIncomingCall(phoneAccountHandle, extras)
          }
        } catch (e: Exception) {
          Log.w("OngoingCallModule", "Telecom addNewIncomingCall non-fatal warning", e)
        }
      }

      // 2. Enterprise Standard: Post high-priority incoming CallStyle notification via NotificationManager directly
      // Avoid starting a Foreground Service while merely ringing to comply with Android 14 background restrictions
      OngoingCallService.showIncomingCallNotification(context, callerName, avatarUrl, chatId, callerId)
    }

    Function("stopOngoingCallNotification") {
      VoipConnectionService.currentConnection?.setDisconnectedByRemote()
      VoipConnectionService.currentConnection = null
      
      OngoingCallService.cancelCallNotification(context)

      try {
        val intent = Intent(context, OngoingCallService::class.java).apply {
          action = OngoingCallService.ACTION_STOP_ONGOING
        }
        context.startService(intent)
      } catch (e: Exception) {
        Log.w("OngoingCallModule", "stopOngoingCallNotification non-fatal stopService error", e)
      }
    }

    Function("onRingingScreenMounted") {
      // Signals that the FullScreenIntent React Native UI is now active.
      OngoingCallService.demoteIncomingNotification(context)
    }
  }
}
