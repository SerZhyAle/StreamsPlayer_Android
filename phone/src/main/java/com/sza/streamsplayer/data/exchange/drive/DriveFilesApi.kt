package com.sza.streamsplayer.data.exchange.drive

import java.io.IOException

/** One file of the app folder: its Drive id, its name and the time Drive last modified it. */
data class DriveFileInfo(val id: String, val name: String, val modifiedTimeMs: Long)

/**
 * A listing of the app folder with the instant Drive itself put on its answer (the `Date` header). Item C
 * judges a record's age on Drive's clock, so [driveNowMs] - not the phone's clock - is what a reader compares
 * [DriveFileInfo.modifiedTimeMs] with.
 */
data class DriveListing(val files: List<DriveFileInfo>, val driveNowMs: Long)

/**
 * The few Drive calls the announcement channel needs, over the `drive.appdata` scope only (item CM). No call
 * here ever names a file outside the app folder, and none reads or writes media.
 */
interface DriveFilesApi {

    /** The files of the app folder whose name starts with [namePrefix]. */
    suspend fun list(namePrefix: String): DriveListing

    /** The text of file [fileId], or null when it is gone. */
    suspend fun read(fileId: String): String?

    /** Creates a file in the app folder and returns its id. */
    suspend fun create(name: String, content: String): String

    /** Replaces the content of [fileId]; Drive stamps a new `modifiedTime`. */
    suspend fun update(fileId: String, content: String)

    /** Deletes [fileId] for good; a file that is already gone is not an error. */
    suspend fun delete(fileId: String)
}

/** Why a Drive call failed, in the words the channel acts on. */
sealed class DriveException(message: String, cause: Throwable? = null) : IOException(message, cause) {

    /** True for a failure no repetition helps with - the announcer stops writing and says so once. */
    open val final: Boolean get() = false

    /** No Google sign-in, or the sign-in was withdrawn: item CT, the GD path is absent. */
    class NotSignedIn : DriveException("not signed in") {
        override val final: Boolean get() = true
    }

    /** The token was refused (401) after a fresh one was asked for, or the scope is no longer granted. */
    class Unauthorized : DriveException("unauthorized") {
        override val final: Boolean get() = true
    }

    /** The folder or the scope is refused for a reason that is not a rate limit (403). */
    class Forbidden(val reason: String) : DriveException("forbidden: $reason") {
        override val final: Boolean get() = true
    }

    /** Item CS: the person's Drive has no room for the write. */
    class DriveFull : DriveException("drive-full") {
        override val final: Boolean get() = true
    }

    /** Item CS: Google's daily upload limit - never retried in a loop. */
    class DailyLimit : DriveException("daily-limit") {
        override val final: Boolean get() = true
    }

    /** Item CQ: 403 with a rate-limit reason, or 429 - back off. */
    class RateLimited : DriveException("rate-limited")

    /** Item CQ: a 5xx - back off. */
    class Server(val code: Int) : DriveException("server error $code")

    /** The file is not there (404). */
    class NotFound : DriveException("not found")

    /** Any other answer. */
    class Failure(val code: Int) : DriveException("drive answered $code")

    /** The request never got an answer (no network, a timeout). */
    class Network(cause: Throwable) : DriveException("network: ${cause.javaClass.simpleName}", cause)
}
