package com.esiri.esiriplus.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.esiri.esiriplus.call.IncomingCallStateHolder
import com.esiri.esiriplus.service.IncomingCallService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CallDeclineBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var incomingCallStateHolder: IncomingCallStateHolder

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("CallDeclineReceiver", "Call declined via notification action")
        incomingCallStateHolder.dismiss()
        NotificationManagerCompat.from(context).cancel(EsiriplusFirebaseMessagingService.CALL_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(IncomingCallService.NOTIFICATION_ID)
        IncomingCallService.stop(context)
    }
}
