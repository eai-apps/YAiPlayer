// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import tv.own.owntv.core.R

/** Text for a server error code (docs/SYNC_API.md §2). */
object AccountErrors {
    fun messageRes(e: Throwable): Int = when ((e as? AccountApiException)?.code) {
        "network" -> R.string.account_err_network
        "bad_login", "unauthorized" -> R.string.account_err_bad_login
        "login_taken" -> R.string.account_err_login_taken
        "weak_password" -> R.string.account_err_weak_password
        "bad_login_format" -> R.string.account_err_bad_login_format
        "too_many_attempts" -> R.string.account_err_too_many
        "bad_recovery_code" -> R.string.account_err_bad_recovery
        "not_found" -> R.string.account_err_not_found
        "account_blocked" -> R.string.account_err_blocked
        "device_limit" -> R.string.account_err_device_limit
        else -> R.string.account_err_generic
    }
}
