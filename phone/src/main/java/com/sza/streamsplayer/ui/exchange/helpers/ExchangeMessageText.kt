package com.sza.streamsplayer.ui.exchange.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.streamsplayer.R
import com.sza.streamsplayer.domain.model.ExchangeCheckStep
import com.sza.streamsplayer.domain.model.ExchangeMessage

/**
 * Resolves a contract DEVICE-EXCHANGE failure row and check step to its string resource (S0031).
 */
object ExchangeMessageText {

    @StringRes
    fun what(message: ExchangeMessage): Int = when (message) {
        ExchangeMessage.BAD_CREDENTIALS -> R.string.exchange_failure_bad_credentials
        ExchangeMessage.BAD_CREDENTIALS_ONE_TIME_CODE -> R.string.exchange_failure_bad_credentials_one_time_code
        ExchangeMessage.DEVICE_REVOKED -> R.string.exchange_failure_device_revoked
        ExchangeMessage.SIGNED_OUT -> R.string.exchange_failure_signed_out
        ExchangeMessage.VERSION_UNSUPPORTED -> R.string.exchange_failure_version_unsupported
        ExchangeMessage.TLS_REQUIRED -> R.string.exchange_failure_tls_required
        ExchangeMessage.ID_COLLISION -> R.string.exchange_failure_id_collision
        ExchangeMessage.CAPACITY -> R.string.exchange_failure_capacity
        ExchangeMessage.PORT_UNAVAILABLE -> R.string.exchange_failure_port_unavailable
        ExchangeMessage.UNAVAILABLE -> R.string.exchange_failure_unavailable
        ExchangeMessage.RATE_LIMITED -> R.string.exchange_failure_rate_limited
        ExchangeMessage.PROTECTION_UNSUPPORTED -> R.string.exchange_failure_protection_unsupported
        ExchangeMessage.INVITATION_INVALID -> R.string.exchange_failure_invitation_invalid
        ExchangeMessage.LOGIN_TAKEN -> R.string.exchange_failure_login_taken
        ExchangeMessage.PASSWORD_POLICY -> R.string.exchange_failure_password_policy
        ExchangeMessage.LIMIT_REACHED -> R.string.exchange_failure_limit_reached
        ExchangeMessage.LIMIT_REACHED_CLIPBOARD -> R.string.exchange_failure_limit_reached_clipboard
        ExchangeMessage.NOT_PERMITTED -> R.string.exchange_failure_not_permitted
        ExchangeMessage.DECLINED -> R.string.exchange_failure_declined
        ExchangeMessage.TIMEOUT -> R.string.exchange_failure_timeout
        ExchangeMessage.UNSUPPORTED -> R.string.exchange_failure_unsupported
        ExchangeMessage.UNREACHABLE_SERVER -> R.string.exchange_failure_unreachable_server
        ExchangeMessage.UNREACHABLE_DEVICE -> R.string.exchange_failure_unreachable_device
        ExchangeMessage.UNANSWERED -> R.string.exchange_failure_unanswered
        ExchangeMessage.TLS_FAILED -> R.string.exchange_failure_tls_failed
        ExchangeMessage.CERTIFICATE_CHANGED -> R.string.exchange_failure_certificate_changed
        ExchangeMessage.EMPTY_CREDENTIAL -> R.string.exchange_failure_empty_credential
        ExchangeMessage.SERVER_OFFLINE -> R.string.exchange_failure_server_offline
        ExchangeMessage.RESOURCE_LOCKED -> R.string.exchange_failure_resource_locked
        ExchangeMessage.CLIPBOARD_EMPTY -> R.string.exchange_failure_clipboard_empty
        ExchangeMessage.REFUSED -> R.string.exchange_failure_refused
        ExchangeMessage.TOKEN_NOT_STORED -> R.string.exchange_failure_token_not_stored
        ExchangeMessage.E2E_MISSING -> R.string.exchange_failure_e2e_missing
        ExchangeMessage.KEY_CHANGED -> R.string.exchange_failure_key_changed
        ExchangeMessage.SERVER_NO_E2E -> R.string.exchange_failure_server_no_e2e
        ExchangeMessage.SEALED_FAILED -> R.string.exchange_failure_sealed_failed
        ExchangeMessage.FORMAT_UNSUPPORTED -> R.string.exchange_failure_format_unsupported
        ExchangeMessage.ADMIN_DISABLED -> R.string.exchange_failure_admin_disabled
    }

    @StringRes
    fun advice(step: ExchangeCheckStep): Int = when (step) {
        ExchangeCheckStep.PC_ASLEEP -> R.string.exchange_step_pc_asleep
        ExchangeCheckStep.ADDRESS_LAN -> R.string.exchange_step_address_lan
        ExchangeCheckStep.GUEST_NETWORK -> R.string.exchange_step_guest_network
        ExchangeCheckStep.VPN -> R.string.exchange_step_vpn
        ExchangeCheckStep.WINDOWS_PUBLIC_NETWORK -> R.string.exchange_step_windows_public_network
        ExchangeCheckStep.IP_FAMILY -> R.string.exchange_step_ip_family
        ExchangeCheckStep.SERVER_INTERNET -> R.string.exchange_step_server_internet
        ExchangeCheckStep.PORT_FORWARD -> R.string.exchange_step_port_forward
        ExchangeCheckStep.DEVICE_CLOCK -> R.string.exchange_step_device_clock
        ExchangeCheckStep.CERTIFICATE -> R.string.exchange_step_certificate
        ExchangeCheckStep.CREDENTIALS -> R.string.exchange_step_credentials
        ExchangeCheckStep.SIGN_IN_RATE -> R.string.exchange_step_sign_in_rate
        ExchangeCheckStep.INVITATION -> R.string.exchange_step_invitation
        ExchangeCheckStep.DEVICE_REMOVED -> R.string.exchange_step_device_removed
        ExchangeCheckStep.OPERATOR -> R.string.exchange_step_operator
    }

    /** The two-part text of contract item DA: what happened, a newline, and the advice of [message.step] when there is one. */
    fun twoParts(context: Context, message: ExchangeMessage): String {
        val whatText = context.getString(what(message))
        val step = message.step ?: return whatText
        val adviceText = context.getString(advice(step))
        return "$whatText\n$adviceText"
    }

    fun ofWire(reason: String?, isOneTimeCode: Boolean = false, clipboardSend: Boolean = false): ExchangeMessage =
        if (reason == null) ExchangeMessage.REFUSED else ExchangeMessage.ofWire(reason, isOneTimeCode, clipboardSend)
}
