package com.sza.streamsplayer.data.exchange

import java.util.Locale

/** The Android apps route this seam through Timber; the core has no Android dependency. */
object ExchangeLog {
    @Volatile
    var sink: ((priority: Int, cause: Throwable?, message: String) -> Unit)? = null

    fun v(message: String, vararg args: Any?) = emit(2, null, message, args)
    fun v(cause: Throwable?, message: String, vararg args: Any?) = emit(2, cause, message, args)
    fun d(message: String, vararg args: Any?) = emit(3, null, message, args)
    fun d(cause: Throwable?, message: String, vararg args: Any?) = emit(3, cause, message, args)
    fun i(message: String, vararg args: Any?) = emit(4, null, message, args)
    fun i(cause: Throwable?, message: String, vararg args: Any?) = emit(4, cause, message, args)
    fun w(message: String, vararg args: Any?) = emit(5, null, message, args)
    fun w(cause: Throwable?, message: String, vararg args: Any?) = emit(5, cause, message, args)

    private fun emit(priority: Int, cause: Throwable?, message: String, args: Array<out Any?>) {
        sink?.invoke(priority, cause, if (args.isEmpty()) message else String.format(Locale.ROOT, message, *args))
    }
}
