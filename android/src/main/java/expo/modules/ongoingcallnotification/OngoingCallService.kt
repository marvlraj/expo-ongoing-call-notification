package expo.modules.ongoingcallnotification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

class OngoingCallService : Service() {

    companion object {
        const val CHANNEL_ID = "ongoing_call_channel"
        const val NOTIFICATION_ID = 4567

        const val ACTION_START_ONGOING = "START_ONGOING_CALL"
        const val ACTION_START_INCOMING = "START_INCOMING_CALL"
        const val ACTION_STOP_ONGOING = "STOP_ONGOING_CALL"
        const val ACTION_HANGUP_CALL = "ACTION_HANGUP_CALL"
        const val ACTION_ANSWER = "ACTION_ANSWER"
        const val ACTION_DECLINE = "ACTION_DECLINE"
        const val ACTION_DEMOTE_NOTIFICATION = "ACTION_DEMOTE_NOTIFICATION"

        /**
         * Enterprise Best Practice: Post ringing incoming call notification directly via
         * NotificationManagerCompat WITHOUT starting an illegal Background Foreground Service.
         */
        fun showIncomingCallNotification(
            context: Context,
            callerName: String,
            avatarUrl: String?,
            chatId: String?,
            callerId: String?
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createChannelSafely(context, manager)

            // Enable lock screen display on incoming call
            context.getSharedPreferences("call_flags", Context.MODE_PRIVATE)
                .edit().putBoolean("show_when_locked", true).apply()

            val notification = buildCallNotification(
                context = context,
                callType = "INCOMING",
                callerName = callerName,
                avatarBitmap = null,
                chatId = chatId,
                callerId = callerId,
                isRingingUIActive = false
            )

            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e("OngoingCallService", "Failed to post incoming call notification", e)
            }

            // Asynchronously resolve avatar and update notification if available
            if (!avatarUrl.isNullOrEmpty()) {
                Thread {
                    try {
                        val bitmap = downloadOrLoadBitmap(context, avatarUrl)
                        if (bitmap != null) {
                            val circular = getCircularBitmap(bitmap)
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    val updatedNotif = buildCallNotification(
                                        context = context,
                                        callType = "INCOMING",
                                        callerName = callerName,
                                        avatarBitmap = circular,
                                        chatId = chatId,
                                        callerId = callerId,
                                        isRingingUIActive = false
                                    )
                                    manager.notify(NOTIFICATION_ID, updatedNotif)
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("OngoingCallService", "Failed to load incoming avatar async", e)
                    }
                }.start()
            }
        }

        fun demoteIncomingNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = buildCallNotification(
                    context = context,
                    callType = "INCOMING",
                    callerName = "Incoming Call",
                    avatarBitmap = null,
                    chatId = null,
                    callerId = null,
                    isRingingUIActive = true
                )
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w("OngoingCallService", "Failed to demote incoming notification", e)
            }
        }

        fun cancelCallNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                context.getSharedPreferences("call_flags", Context.MODE_PRIVATE)
                    .edit().putBoolean("show_when_locked", false).apply()
            } catch (e: Exception) {
                Log.w("OngoingCallService", "Failed to cancel call notification", e)
            }
        }

        fun createChannelSafely(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ongoing Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for active and incoming calls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    enableLights(true)
                    // DND bypass must be safely wrapped to prevent SecurityExceptions on Samsung One UI
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            setBypassDnd(true)
                        } catch (_: Exception) {}
                    }
                }
                manager.createNotificationChannel(channel)
            }
        }

        private fun getSmallIconResId(context: Context): Int {
            val customIcon = context.resources.getIdentifier("ic_ongoing_call_notification", "drawable", context.packageName)
            if (customIcon != 0) return customIcon
            val appIcon = context.applicationInfo.icon
            if (appIcon != 0) return appIcon
            return android.R.drawable.ic_menu_call
        }

        fun buildCallNotification(
            context: Context,
            callType: String,
            callerName: String,
            avatarBitmap: Bitmap?,
            chatId: String?,
            callerId: String?,
            isRingingUIActive: Boolean
        ): Notification {
            val packageName = context.packageName

            // Broadcast PendingIntents for actions prevent Android 14 Background Service Start restrictions
            val hangupIntent = Intent("ACTION_HANGUP").setPackage(packageName).apply {
                chatId?.let { putExtra("chatId", it) }
                callerId?.let { putExtra("callerId", it) }
            }
            val hangupPendingIntent = PendingIntent.getBroadcast(
                context, 1, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = Intent("ACTION_DECLINE").setPackage(packageName).apply {
                chatId?.let { putExtra("chatId", it) }
                callerId?.let { putExtra("callerId", it) }
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                context, 5, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val scheme = try {
                val resId = context.resources.getIdentifier("ongoing_call_scheme", "string", context.packageName)
                if (resId != 0) context.getString(resId) else "call"
            } catch (_: Exception) {
                "call"
            }

            // Answer action MUST be an Activity PendingIntent to bring MainActivity to foreground over lock screen
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_VIEW).setPackage(packageName)

            val answerIntent = Intent(launchIntent).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("$scheme://call")
                putExtra("action", ACTION_ANSWER)
                putExtra("call_answered", true)
                putExtra("incoming_call", false)
                chatId?.let { putExtra("chatId", it) }
                callerId?.let { putExtra("callerId", it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val answerPendingIntent = PendingIntent.getActivity(
                context, 4, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Incoming call full-screen/tap intent
            val fullScreenIntent = Intent(launchIntent).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("$scheme://incoming")
                putExtra("incoming_call", true)
                chatId?.let { putExtra("chatId", it) }
                callerId?.let { putExtra("callerId", it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Ongoing call tap intent — opens active call screen directly
            val ongoingContentIntent = Intent(launchIntent).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("$scheme://call")
                putExtra("incoming_call", false)
                chatId?.let { putExtra("chatId", it) }
                callerId?.let { putExtra("callerId", it) }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val ongoingContentPendingIntent = PendingIntent.getActivity(
                context, 2, ongoingContentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultCallName = try {
                val resId = context.resources.getIdentifier("ongoing_call_default_name", "string", context.packageName)
                if (resId != 0) context.getString(resId) else context.applicationInfo.loadLabel(context.packageManager).toString()
            } catch (_: Exception) {
                "Call"
            }
            val displayName = if (callerName.isNotBlank()) callerName else defaultCallName

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            val personBuilder = Person.Builder().setName(displayName).setImportant(true)
            val effectiveAvatar = avatarBitmap ?: generateDefaultAvatar()
            personBuilder.setIcon(IconCompat.createWithBitmap(effectiveAvatar))
            val callerPerson = personBuilder.build()

            if (callType == "INCOMING") {
                val callStyle = NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    declinePendingIntent,
                    answerPendingIntent
                )
                builder.setStyle(callStyle)
                builder.setContentIntent(fullScreenPendingIntent)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    NotificationManagerCompat.from(context).canUseFullScreenIntent()) {
                    builder.setFullScreenIntent(fullScreenPendingIntent, true)
                }
            } else {
                val callStyle = NotificationCompat.CallStyle.forOngoingCall(callerPerson, hangupPendingIntent)
                builder.setStyle(callStyle)
                builder.setContentIntent(ongoingContentPendingIntent)
            }

            builder.setSmallIcon(getSmallIconResId(context))
            builder.color = getAccentColor(context)
            builder.setOngoing(true)
            builder.setCategory(NotificationCompat.CATEGORY_CALL)
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (isRingingUIActive) {
                builder.priority = NotificationCompat.PRIORITY_DEFAULT
            } else if (callType == "INCOMING") {
                builder.priority = NotificationCompat.PRIORITY_MAX
            }

            return builder.build()
        }

        private fun downloadOrLoadBitmap(context: Context, url: String): Bitmap? {
            return when {
                url.startsWith("file://") -> {
                    BitmapFactory.decodeFile(url.replace("file://", ""))
                }
                url.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(url))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                url.startsWith("http://") || url.startsWith("https://") -> {
                    var conn: java.net.HttpURLConnection? = null
                    try {
                        conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.doInput = true
                        conn.connect()
                        conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) {
                        null
                    } finally {
                        try { conn?.disconnect() } catch (_: Exception) {}
                    }
                }
                else -> {
                    BitmapFactory.decodeFile(url)
                }
            }
        }

        fun getCircularBitmap(bitmap: Bitmap): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)

            paint.color = android.graphics.Color.RED
            canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, bitmap.width / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }

        private fun generateDefaultAvatar(): Bitmap {
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val clipPath = Path().apply {
                addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3F3F46.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
            val personPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA1A1AA.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size * 0.35f, size * 0.18f, personPaint)
            val shoulderRect = RectF(size * 0.18f, size * 0.58f, size * 0.82f, size * 1.15f)
            canvas.drawRoundRect(shoulderRect, size * 0.32f, size * 0.32f, personPaint)
            return bitmap
        }

        private fun getAccentColor(context: Context): Int {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.resources.getColor(android.R.color.system_accent1_500, context.theme)
                } else {
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
                    if (typedValue.data != 0) typedValue.data else android.graphics.Color.parseColor("#007AFF")
                }
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#007AFF")
            }
        }
    }

    private var currentCallerName = "Ongoing Call"
    private var currentAvatarUrl: String? = null
    private var currentChatId: String? = null
    private var currentCallerId: String? = null
    private var cachedAvatarBitmap: Bitmap? = null
    private var isRingingUIActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ONGOING -> {
                currentCallerName = intent.getStringExtra("CALLER_NAME") ?: "Ongoing Call"
                val newAvatarUrl = intent.getStringExtra("AVATAR_URL")
                if (newAvatarUrl != null && newAvatarUrl != currentAvatarUrl) {
                    currentAvatarUrl = newAvatarUrl
                    cachedAvatarBitmap = null
                } else if (newAvatarUrl != null) {
                    currentAvatarUrl = newAvatarUrl
                }
                intent.getStringExtra("CHAT_ID")?.let { currentChatId = it }
                intent.getStringExtra("CALLER_ID")?.let { currentCallerId = it }
                startCallForeground()
            }
            ACTION_START_INCOMING -> {
                val caller = intent.getStringExtra("CALLER_NAME") ?: "Incoming Call"
                val avatar = intent.getStringExtra("AVATAR_URL")
                val chat = intent.getStringExtra("CHAT_ID")
                val callerUid = intent.getStringExtra("CALLER_ID")
                showIncomingCallNotification(this, caller, avatar, chat, callerUid)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_ONGOING -> {
                getSharedPreferences("call_flags", Context.MODE_PRIVATE)
                    .edit().putBoolean("show_when_locked", false).apply()
                cancelCallNotification(this)
                stopServiceForeground()
            }
            ACTION_DEMOTE_NOTIFICATION -> {
                isRingingUIActive = true
                updateNotification()
            }
            ACTION_HANGUP_CALL -> {
                val broadcast = Intent("ACTION_HANGUP").setPackage(packageName).apply {
                    currentChatId?.let { putExtra("chatId", it) }
                    currentCallerId?.let { putExtra("callerId", it) }
                }
                sendBroadcast(broadcast)
                getSharedPreferences("call_flags", Context.MODE_PRIVATE)
                    .edit().putBoolean("show_when_locked", false).apply()
                cancelCallNotification(this)
                stopServiceForeground()
            }
            ACTION_ANSWER -> {
                intent.getStringExtra("CHAT_ID")?.let { currentChatId = it }
                intent.getStringExtra("CALLER_ID")?.let { currentCallerId = it }
                val broadcast = Intent("ACTION_ANSWER").setPackage(packageName).apply {
                    currentChatId?.let { putExtra("chatId", it) }
                    currentCallerId?.let { putExtra("callerId", it) }
                }
                sendBroadcast(broadcast)
                startCallForeground()
            }
            ACTION_DECLINE -> {
                try {
                    VoipConnectionService.currentConnection?.onReject()
                    VoipConnectionService.currentConnection = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val broadcast = Intent("ACTION_DECLINE").setPackage(packageName).apply {
                    currentChatId?.let { putExtra("chatId", it) }
                    currentCallerId?.let { putExtra("callerId", it) }
                }
                sendBroadcast(broadcast)
                getSharedPreferences("call_flags", Context.MODE_PRIVATE)
                    .edit().putBoolean("show_when_locked", false).apply()
                cancelCallNotification(this)
                stopServiceForeground()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopServiceForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startCallForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannelSafely(this, manager)

        if (cachedAvatarBitmap == null && currentAvatarUrl != null) {
            val notification = buildCallNotification(
                context = this,
                callType = "ONGOING",
                callerName = currentCallerName,
                avatarBitmap = null,
                chatId = currentChatId,
                callerId = currentCallerId,
                isRingingUIActive = false
            )
            startForegroundInternal(notification)
            loadAvatarAsync()
        } else {
            val notification = buildCallNotification(
                context = this,
                callType = "ONGOING",
                callerName = currentCallerName,
                avatarBitmap = cachedAvatarBitmap,
                chatId = currentChatId,
                callerId = currentCallerId,
                isRingingUIActive = false
            )
            startForegroundInternal(notification)
        }
    }

    private fun startForegroundInternal(notification: Notification) {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // On Android 14+ (API 34), Ongoing calls use PHONE_CALL, and MICROPHONE if mic permission granted
                val fgsType = if (hasMicPermission) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (hasMicPermission) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
                if (fgsType != 0) {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("OngoingCallService", "Failed to startForeground with type. Gracefully degrading to plain notification.", e)
            try {
                // Enterprise Fail-Safe: Stop service so OS does NOT crash the process with CannotPostForegroundServiceException
                stopSelf()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            } catch (fallbackErr: Exception) {
                Log.e("OngoingCallService", "Fatal fallback suppressed", fallbackErr)
            }
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildCallNotification(
            context = this,
            callType = "ONGOING",
            callerName = currentCallerName,
            avatarBitmap = cachedAvatarBitmap,
            chatId = currentChatId,
            callerId = currentCallerId,
            isRingingUIActive = isRingingUIActive
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun loadAvatarAsync() {
        Thread {
            try {
                val url = currentAvatarUrl ?: return@Thread
                val bitmap = downloadOrLoadBitmap(this, url)
                if (bitmap != null) {
                    val finalBitmap = getCircularBitmap(bitmap)
                    Handler(Looper.getMainLooper()).post {
                        cachedAvatarBitmap = finalBitmap
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.w("OngoingCallService", "Failed to load ongoing avatar async", e)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("call_flags", Context.MODE_PRIVATE)
            .edit().putBoolean("show_when_locked", false).apply()
    }
}
