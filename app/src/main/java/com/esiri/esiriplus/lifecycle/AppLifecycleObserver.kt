package com.esiri.esiriplus.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLifecycleObserver @Inject constructor(
    private val biometricLockStateHolder: BiometricLockStateHolder,
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        biometricLockStateHolder.setLocked(true)
    }
}
