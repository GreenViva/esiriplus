package com.esiri.esiriplus.core.common.error

import android.content.Context
import com.esiri.esiriplus.core.common.result.Result

/**
 * Resolves localized error messages from [Result.Error].
 *
 * Priority:
 * 1. Localized message from [ErrorCode.messageResId] (if errorCode is available)
 * 2. Fallback to [Result.Error.message] (hardcoded English from ApiErrorMapper)
 * 3. Exception message
 * 4. Generic fallback
 */
fun Result.Error.localizedMessage(context: Context): String {
    errorCode?.let { code ->
        return context.getString(code.messageResId)
    }
    return message
        ?: exception.message
        ?: context.getString(ErrorCode.UNEXPECTED.messageResId)
}
