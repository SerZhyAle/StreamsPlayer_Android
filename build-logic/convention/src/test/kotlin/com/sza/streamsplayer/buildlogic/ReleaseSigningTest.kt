package com.sza.streamsplayer.buildlogic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseSigningTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `no key on the machine is not an error`() {
        assertNull(ReleaseSigning.locate(temp.root, null))
        assertNull(ReleaseSigning.locate(temp.root, ""))
    }

    @Test
    fun `the root keystore properties file is found`() {
        val file = temp.newFile(ReleaseSigning.FILE_NAME)
        assertEquals(file, ReleaseSigning.locate(temp.root, null))
    }

    @Test
    fun `the environment variable wins over the root file`() {
        temp.newFile(ReleaseSigning.FILE_NAME)
        val elsewhere = temp.newFile("elsewhere.properties")
        assertEquals(elsewhere, ReleaseSigning.locate(temp.root, elsewhere.path))
    }

    @Test
    fun `an environment variable that names nothing is refused instead of falling back`() {
        temp.newFile(ReleaseSigning.FILE_NAME)
        try {
            ReleaseSigning.locate(temp.root, File(temp.root, "missing.properties").path)
            fail("fell back silently")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains(ReleaseSigning.ENV_VAR))
        }
    }

    @Test
    fun `a relative storeFile is resolved against the properties file`() {
        val file = temp.newFile("keys.properties")
        file.writeText(
            "storeFile=store/release.jks\nstorePassword=sp\nkeyAlias=ka\nkeyPassword=kp\n",
        )
        val loaded = ReleaseSigning.load(file)
        assertEquals(File(temp.root, "store/release.jks").normalize(), loaded.storeFile)
        assertEquals("ka", loaded.keyAlias)
    }

    @Test
    fun `an absolute storeFile is kept as it is`() {
        val file = temp.newFile("keys.properties")
        val absolute = File(temp.root, "abs/release.jks").absoluteFile
        file.writeText(
            "storeFile=${absolute.path.replace("\\", "/")}\nstorePassword=sp\nkeyAlias=ka\nkeyPassword=kp\n",
        )
        assertEquals(absolute.normalize(), ReleaseSigning.load(file).storeFile.normalize())
    }

    @Test
    fun `a missing key is named and no value reaches the message`() {
        val file = temp.newFile("keys.properties")
        file.writeText("storeFile=a.jks\nstorePassword=TOPSECRET\n")
        try {
            ReleaseSigning.load(file)
            fail("accepted an incomplete file")
        } catch (expected: IllegalArgumentException) {
            val message = expected.message!!
            assertTrue(message.contains("keyAlias"))
            assertTrue(message.contains("keyPassword"))
            assertFalse(message.contains("TOPSECRET"))
        }
    }
}
