package expo.modules.ongoingcallnotification

import android.content.Context
import android.content.Intent
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

class VoipConnection(private val context: Context) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
    }

    override fun onAnswer(videoState: Int) {
        super.onAnswer(videoState)
        Log.d("VoipConnection", "onAnswer")
        setActive()
        // Broadcast to React Native that call was answered natively (e.g. from Bluetooth headset)
        context.sendBroadcast(Intent("ACTION_ANSWER").setPackage(context.packageName))
    }

    override fun onAnswer() {
        super.onAnswer()
        Log.d("VoipConnection", "onAnswer")
        setActive()
        context.sendBroadcast(Intent("ACTION_ANSWER").setPackage(context.packageName))
    }

    override fun onReject() {
        super.onReject()
        Log.d("VoipConnection", "onReject")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
        context.sendBroadcast(Intent("ACTION_DECLINE").setPackage(context.packageName))
    }

    override fun onDisconnect() {
        super.onDisconnect()
        Log.d("VoipConnection", "onDisconnect")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        context.sendBroadcast(Intent("ACTION_HANGUP").setPackage(context.packageName))
    }

    override fun onAbort() {
        super.onAbort()
        Log.d("VoipConnection", "onAbort")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
        context.sendBroadcast(Intent("ACTION_HANGUP").setPackage(context.packageName))
    }

    fun setDisconnectedByRemote() {
        setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
        destroy()
    }
}
