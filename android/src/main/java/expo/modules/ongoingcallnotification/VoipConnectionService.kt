package expo.modules.ongoingcallnotification

import android.content.Intent
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class VoipConnectionService : ConnectionService() {

    companion object {
        var currentConnection: VoipConnection? = null
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d("VoipConnectionService", "onCreateIncomingConnection")
        try {
            currentConnection?.destroy()
        } catch (_: Exception) {}
        val connection = VoipConnection(applicationContext)
        connection.setRinging()
        currentConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e("VoipConnectionService", "onCreateIncomingConnectionFailed")
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d("VoipConnectionService", "onCreateOutgoingConnection")
        try {
            currentConnection?.destroy()
        } catch (_: Exception) {}
        val connection = VoipConnection(applicationContext)
        connection.setDialing()
        currentConnection = connection
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e("VoipConnectionService", "onCreateOutgoingConnectionFailed")
    }
}
