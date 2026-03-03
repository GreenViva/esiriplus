package com.esiri.esiriplus.call

import android.content.Context
import android.content.Intent
import android.os.Build
import com.esiri.esiriplus.core.domain.service.CallServiceController
import com.esiri.esiriplus.service.CallForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: CallForegroundServiceStateHolder,
) : CallServiceController {

    override fun startCallService(consultationId: String, callType: String, isVideo: Boolean) {
        stateHolder.start(consultationId, callType, isVideo)
        val intent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_START
            putExtra(CallForegroundService.EXTRA_CONSULTATION_ID, consultationId)
            putExtra(CallForegroundService.EXTRA_CALL_TYPE, callType)
            putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stopCallService() {
        stateHolder.stop()
        val intent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    override fun updateCallDuration(seconds: Int) {
        stateHolder.updateDuration(seconds)
    }
}
