package com.sza.streamsplayer.buildlogic

import java.io.File
import java.util.Properties

/** The four values of a release signing config, read from a properties file that is never tracked. */
internal class KeystoreProperties(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * Where the release key comes from. No key material is ever stored in the repository; the build reads
 * a path (and the passwords) from one of two places, in this order:
 *
 *  1. the file named by the environment variable [ENV_VAR], and failing that
 *  2. `keystore.properties` in the repository root, which `.gitignore` excludes.
 *
 * The file has four keys - `storeFile`, `storePassword`, `keyAlias`, `keyPassword` - and a relative
 * `storeFile` is resolved against the directory of the file that names it. The key itself lives outside
 * the repository, in `P:\ANDROID\StreamsPlayer_credentials`, which is the owner's to create.
 */
internal object ReleaseSigning {
    const val ENV_VAR = "STREAMSPLAYER_KEYSTORE_PROPERTIES"
    const val FILE_NAME = "keystore.properties"

    private val requiredKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

    /** The properties file, or null on a machine that has no release key (debug builds still work). */
    fun locate(rootDir: File, envValue: String?): File? {
        if (!envValue.isNullOrBlank()) {
            val named = File(envValue)
            require(named.isFile) { "$ENV_VAR names '$envValue', which is not a file." }
            return named
        }
        return File(rootDir, FILE_NAME).takeIf(File::isFile)
    }

    /** Messages name keys and paths, never values: a password must not reach a build log. */
    fun load(file: File): KeystoreProperties {
        val properties = Properties().apply { file.inputStream().use(::load) }
        val missing = requiredKeys.filter { properties.getProperty(it).isNullOrBlank() }
        require(missing.isEmpty()) { "${file.path} lacks: ${missing.joinToString()}." }
        val rawStoreFile = properties.getProperty("storeFile")
        val declared = File(rawStoreFile)
        return KeystoreProperties(
            storeFile = if (declared.isAbsolute) declared else File(file.parentFile, rawStoreFile).normalize(),
            storePassword = properties.getProperty("storePassword"),
            keyAlias = properties.getProperty("keyAlias"),
            keyPassword = properties.getProperty("keyPassword"),
        )
    }
}
