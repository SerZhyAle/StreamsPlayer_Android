package com.sza.streamsplayer.data.exchange.session

import com.sza.streamsplayer.R
import java.util.Locale

/**
 * Localized reason messages for contract DEVICE-EXCHANGE section 7.10 refusals in EN, RU, and UK.
 * Unknown reason values map to a generic refusal.
 * `id-collision` and `capacity` have distinct specific texts.
 */
object DeviceExchangeReasons {

    const val REASON_BAD_CREDENTIALS = "bad-credentials"
    const val REASON_DEVICE_REVOKED = "device-revoked"
    const val REASON_VERSION_UNSUPPORTED = "version-unsupported"
    const val REASON_TLS_REQUIRED = "tls-required"
    const val REASON_ID_COLLISION = "id-collision"
    const val REASON_CAPACITY = "capacity"
    const val REASON_PORT_UNAVAILABLE = "port-unavailable"
    const val REASON_UNAVAILABLE = "unavailable"
    const val REASON_RATE_LIMITED = "rate-limited"
    const val REASON_TOKEN_NOT_STORED = "token-not-stored"
    const val REASON_GENERIC = "generic"

    const val REASON_SIGNED_OUT = "signed-out"
    const val REASON_INVITATION_INVALID = "invitation-invalid"
    const val REASON_PASSWORD_POLICY = "password-policy"
    const val REASON_LIMIT_REACHED = "limit-reached"
    const val REASON_NOT_PERMITTED = "not-permitted"

    fun stringResId(reason: String): Int = when (reason) {
        REASON_BAD_CREDENTIALS -> R.string.exchange_refused_bad_credentials
        REASON_DEVICE_REVOKED -> R.string.exchange_refused_device_revoked
        REASON_VERSION_UNSUPPORTED -> R.string.exchange_refused_version_unsupported
        REASON_TLS_REQUIRED -> R.string.exchange_refused_tls_required
        REASON_ID_COLLISION -> R.string.exchange_refused_id_collision
        REASON_CAPACITY -> R.string.exchange_refused_capacity
        REASON_PORT_UNAVAILABLE -> R.string.exchange_refused_port_unavailable
        REASON_UNAVAILABLE -> R.string.exchange_refused_unavailable
        REASON_RATE_LIMITED -> R.string.exchange_refused_rate_limited
        REASON_TOKEN_NOT_STORED -> R.string.exchange_refused_token_not_stored
        REASON_SIGNED_OUT -> R.string.exchange_refused_signed_out
        REASON_INVITATION_INVALID -> R.string.exchange_refused_invitation_invalid
        REASON_PASSWORD_POLICY -> R.string.exchange_refused_password_policy
        REASON_LIMIT_REACHED -> R.string.exchange_refused_limit_reached
        REASON_NOT_PERMITTED -> R.string.exchange_refused_not_permitted
        else -> R.string.exchange_refused_generic
    }

    fun message(reason: String, locale: Locale = Locale.getDefault()): String {
        val lang = locale.language.lowercase(Locale.ROOT)
        return when (lang) {
            "ru" -> messageRu(reason)
            "uk" -> messageUk(reason)
            else -> messageEn(reason)
        }
    }

    // The texts below are the "what happened" column of item CZ of DEVICE-EXCHANGE, word for word (the vendored copy is
    // device-exchange/messages-item-cz.md; ExchangeTermsDictionaryTest compares them). `token-not-stored` is a local
    // class the table has no row for.
    private fun messageEn(reason: String): String = when (reason) {
        REASON_BAD_CREDENTIALS -> "The login or the password is wrong."
        REASON_DEVICE_REVOKED -> "This device was removed from the account."
        REASON_VERSION_UNSUPPORTED -> "This program and the server speak different versions of the exchange."
        REASON_TLS_REQUIRED -> "The server accepts this only over a protected connection, and this one is not protected."
        REASON_ID_COLLISION -> "The server already knows another device under this device's number. Remove this server from the list and add it again."
        REASON_CAPACITY -> "The server cannot take this: it has reached a limit of its own, or the item is too big to be listed."
        REASON_PORT_UNAVAILABLE -> "The server has no free port to open for this folder."
        REASON_UNAVAILABLE -> "The device or the server is not available right now."
        REASON_RATE_LIMITED -> "Too many attempts in a row."
        REASON_TOKEN_NOT_STORED -> "The sign-in of this device could not be stored securely."
        REASON_SIGNED_OUT -> "The password of this account was changed, so this device was signed out."
        REASON_INVITATION_INVALID -> "This invitation does not work."
        REASON_PASSWORD_POLICY -> "The password is too simple. Use at least 10 characters, with capital and small Latin letters and digits."
        REASON_LIMIT_REACHED -> "A limit set on the server has been reached."
        REASON_NOT_PERMITTED -> "A guest account cannot do this. If you need it, ask for an invitation of the kind \"own device or family\"."
        else -> "The server refused this."
    }

    private fun messageRu(reason: String): String = when (reason) {
        REASON_BAD_CREDENTIALS -> "Логин или пароль неверны."
        REASON_DEVICE_REVOKED -> "Это устройство удалено из аккаунта."
        REASON_VERSION_UNSUPPORTED -> "Эта программа и сервер работают по разным версиям обмена."
        REASON_TLS_REQUIRED -> "Сервер принимает это только по защищённому соединению, а это соединение не защищено."
        REASON_ID_COLLISION -> "Сервер уже знает другое устройство под номером этого. Удалите этот сервер из списка и добавьте его снова."
        REASON_CAPACITY -> "Сервер не может это принять: он достиг своего предела или объект слишком большой, чтобы показать его в списке."
        REASON_PORT_UNAVAILABLE -> "У сервера нет свободного порта для этой папки."
        REASON_UNAVAILABLE -> "Сейчас устройство или сервер недоступны."
        REASON_RATE_LIMITED -> "Слишком много попыток подряд."
        REASON_TOKEN_NOT_STORED -> "Не удалось надёжно сохранить вход этого устройства."
        REASON_SIGNED_OUT -> "Пароль этого аккаунта изменили, поэтому это устройство вышло из аккаунта."
        REASON_INVITATION_INVALID -> "Это приглашение не подходит."
        REASON_PASSWORD_POLICY -> "Пароль слишком простой. Нужно не меньше 10 знаков: заглавные и строчные латинские буквы и цифры."
        REASON_LIMIT_REACHED -> "На сервере достигнут установленный предел."
        REASON_NOT_PERMITTED -> "Гостевой аккаунт не может это делать. Если это нужно, попросите приглашение «своё устройство или семья»."
        else -> "Сервер отказал в этом."
    }

    private fun messageUk(reason: String): String = when (reason) {
        REASON_BAD_CREDENTIALS -> "Логін або пароль неправильні."
        REASON_DEVICE_REVOKED -> "Цей пристрій видалено з акаунта."
        REASON_VERSION_UNSUPPORTED -> "Ця програма і сервер працюють за різними версіями обміну."
        REASON_TLS_REQUIRED -> "Сервер приймає це лише через захищене з'єднання, а це з'єднання не захищене."
        REASON_ID_COLLISION -> "Сервер уже знає інший пристрій під таким самим номером. Видаліть цей сервер зі списку й додайте його знову."
        REASON_CAPACITY -> "Сервер не може це прийняти: він досяг свого ліміту або об'єкт завеликий, щоб показати його в списку."
        REASON_PORT_UNAVAILABLE -> "У сервера немає вільного порту для цієї папки."
        REASON_UNAVAILABLE -> "Зараз пристрій або сервер недоступні."
        REASON_RATE_LIMITED -> "Забагато спроб поспіль."
        REASON_TOKEN_NOT_STORED -> "Не вдалося надійно зберегти вхід цього пристрою."
        REASON_SIGNED_OUT -> "Пароль цього акаунта змінили, тому цей пристрій вийшов з акаунта."
        REASON_INVITATION_INVALID -> "Це запрошення не підходить."
        REASON_PASSWORD_POLICY -> "Пароль надто простий. Потрібно щонайменше 10 символів: великі й малі латинські літери та цифри."
        REASON_LIMIT_REACHED -> "На сервері досягнуто встановленого ліміту."
        REASON_NOT_PERMITTED -> "Гостьовий акаунт не може цього робити. Якщо це потрібно, попросіть запрошення «свій пристрій або сім'я»."
        else -> "Сервер відмовив у цьому."
    }
}
