package com.sza.streamsplayer.buildlogic

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * PACKAGE-VERSIONING conformance ladder, step 2: render rules 1-3 at a pinned instant and compare the
 * pair against the expected strings. The reference instant is the contract's own example,
 * 2026-09-26 23:22.
 */
class VersionStampTest {
    private val referenceInstant = "2609262322"

    @Test
    fun `versionName of the contract example`() {
        assertEquals("2.60.9262.322", VersionStamp.versionName(referenceInstant))
    }

    @Test
    fun `versionCode of the contract example in every family`() {
        // yyMMddHH = 26092623; minute 22 -> phone floor(22/10) = 2, watch 6 + floor(22/15) = 7.
        assertEquals(260926232, VersionStamp.versionCode(PackageFamily.PHONE, referenceInstant))
        assertEquals(260926237, VersionStamp.versionCode(PackageFamily.WATCH, referenceInstant))
        assertEquals(260926232, VersionStamp.versionCode(PackageFamily.WATCH_FACE, referenceInstant))
    }

    @Test
    fun `band edges of the partition`() {
        fun code(family: PackageFamily, minute: Int) =
            VersionStamp.versionCode(family, "26092623" + "%02d".format(minute)) % 10

        assertEquals(0, code(PackageFamily.PHONE, 0))
        assertEquals(0, code(PackageFamily.PHONE, 9))
        assertEquals(1, code(PackageFamily.PHONE, 10))
        assertEquals(5, code(PackageFamily.PHONE, 59))
        assertEquals(6, code(PackageFamily.WATCH, 0))
        assertEquals(6, code(PackageFamily.WATCH, 14))
        assertEquals(7, code(PackageFamily.WATCH, 15))
        assertEquals(9, code(PackageFamily.WATCH, 59))
    }

    @Test
    fun `phone and watch never repeat a code under their shared application id`() {
        var instant = LocalDateTime.of(2026, 10, 2, 0, 0)
        repeat(3 * 24 * 60) {
            val raw = instant.format(DateTimeFormatter.ofPattern("yyMMddHHmm"))
            assertNotEquals(
                raw,
                VersionStamp.versionCode(PackageFamily.PHONE, raw),
                VersionStamp.versionCode(PackageFamily.WATCH, raw),
            )
            instant = instant.plusMinutes(1)
        }
    }

    @Test
    fun `P2 a later slot orders above an earlier one inside every family`() {
        for (family in PackageFamily.entries) {
            var previous = -1
            var instant = LocalDateTime.of(2026, 12, 30, 22, 0)
            // Across an hour, a day and a year boundary.
            repeat(4 * 24 * 60) {
                val raw = instant.format(DateTimeFormatter.ofPattern("yyMMddHHmm"))
                val code = VersionStamp.versionCode(family, raw)
                assertTrue("$family went backwards at $raw: $previous -> $code", code >= previous)
                previous = code
                instant = instant.plusMinutes(1)
            }
        }
    }

    @Test
    fun `versionCode stays nine digits and fits a signed int through the century`() {
        assertEquals(9, VersionStamp.versionCode(PackageFamily.WATCH, "2612312359").toString().length)
        assertTrue(VersionStamp.versionCode(PackageFamily.WATCH, "9912312359") < Int.MAX_VALUE)
    }

    @Test
    fun `an instant that is not a real date or time is refused`() {
        for (bad in listOf("2602300000", "2613010000", "2609262400", "2609262360", "26092623", "26x9262322")) {
            try {
                VersionStamp.versionName(bad)
                fail("accepted $bad")
            } catch (expected: IllegalArgumentException) {
                // refused, as P1 requires
            }
        }
    }

    @Test
    fun `the name decodes back to its instant`() {
        for (raw in listOf(referenceInstant, "2601010000", "2612312359", VersionStamp.SENTINEL_INSTANT)) {
            assertEquals(raw, VersionStamp.instantOfName(VersionStamp.versionName(raw)))
        }
    }

    @Test
    fun `a pinned pair wins over a stamp`() {
        val resolved = VersionStamp.resolve(PackageFamily.PHONE, "2.60.9262.322", "260926232", "2701010000")
        assertEquals(VersionSource.PINNED, resolved.source)
        assertEquals("2.60.9262.322", resolved.name)
        assertEquals(260926232, resolved.code)
    }

    @Test
    fun `a one-sided pin is refused`() {
        for ((name, code) in listOf("2.60.9262.322" to null, null to "260926232")) {
            try {
                VersionStamp.resolve(PackageFamily.PHONE, name, code, null)
                fail("accepted a one-sided pin")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("together or neither"))
            }
        }
    }

    @Test
    fun `a pin whose halves disagree, or that carries another family's code, is refused`() {
        // The watch's code under the phone's name.
        try {
            VersionStamp.resolve(PackageFamily.PHONE, "2.60.9262.322", "260926237", null)
            fail("accepted the watch code for the phone")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("does not agree"))
        }
        // A code of another instant.
        try {
            VersionStamp.resolve(PackageFamily.WATCH, "2.60.9262.322", "260926300", null)
            fail("accepted a code of another instant")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("does not agree"))
        }
    }

    @Test
    fun `a module this invocation does not build ignores the code of a pin made for another family`() {
        // The phone's pair, seen by the watch module that Gradle configures anyway.
        val watch = VersionStamp.resolve(PackageFamily.WATCH, "2.60.9262.322", "260926232", null, built = false)
        assertEquals(VersionSource.SENTINEL, watch.source)

        // The mistakes that belong to the invocation are refused for every module, built or not.
        for ((name, code) in listOf("2.60.9262.322" to null, "9.99.9999.999" to "260926232")) {
            try {
                VersionStamp.resolve(PackageFamily.WATCH, name, code, null, built = false)
                fail("accepted a broken pin for a module that is not built")
            } catch (expected: IllegalArgumentException) {
                // refused
            }
        }
    }

    @Test
    fun `an invocation builds the modules its task names reach`() {
        val phoneOnly = listOf(":phone:assembleStoreDebug")
        assertTrue(StampPolicy.targetsModule(phoneOnly, "phone"))
        assertEquals(false, StampPolicy.targetsModule(phoneOnly, "wear"))
        assertEquals(false, StampPolicy.targetsModule(phoneOnly, "watchface"))

        // An unqualified task runs in every module.
        for (module in listOf("phone", "wear", "watchface")) {
            assertTrue(StampPolicy.targetsModule(listOf("assembleRelease"), module))
        }
        // The prefix match is on the whole project name: ':watchface:' is not ':watch'.
        assertEquals(false, StampPolicy.targetsModule(listOf(":watchface:assembleDebug"), "wear"))
        // No task, no build.
        assertEquals(false, StampPolicy.targetsModule(emptyList(), "phone"))
    }

    @Test
    fun `without a pin the stamp is used, and without a stamp the sentinel`() {
        val stamped = VersionStamp.resolve(PackageFamily.WATCH, null, null, referenceInstant)
        assertEquals(VersionSource.STAMPED, stamped.source)
        assertEquals("2.60.9262.322", stamped.name)
        assertEquals(260926237, stamped.code)

        val sentinel = VersionStamp.resolve(PackageFamily.WATCH, null, null, null)
        assertEquals(VersionSource.SENTINEL, sentinel.source)
        assertEquals(VersionStamp.versionName(VersionStamp.SENTINEL_INSTANT), sentinel.name)
        assertEquals(VersionStamp.versionCode(PackageFamily.WATCH, VersionStamp.SENTINEL_INSTANT), sentinel.code)
    }

    @Test
    fun `the sentinel pair is fixed and sits before every real stamp of this product`() {
        // 2026-10-02 00:00, the day this repository's build was written.
        assertEquals("2.61.0020.000", VersionStamp.versionName(VersionStamp.SENTINEL_INSTANT))
        assertEquals(261002000, VersionStamp.versionCode(PackageFamily.PHONE, VersionStamp.SENTINEL_INSTANT))
        assertEquals(261002006, VersionStamp.versionCode(PackageFamily.WATCH, VersionStamp.SENTINEL_INSTANT))
        assertEquals(261002000, VersionStamp.versionCode(PackageFamily.WATCH_FACE, VersionStamp.SENTINEL_INSTANT))
    }

    @Test
    fun `packaging is told from compile-only work by the requested task names`() {
        assertTrue(StampPolicy.packagingRequested(listOf(":phone:assembleStoreDebug")))
        assertTrue(StampPolicy.packagingRequested(listOf("bundleNoLegalRelease")))
        assertTrue(StampPolicy.packagingRequested(listOf(":wear:installStoreDebug")))
        assertTrue(StampPolicy.packagingRequested(listOf("connectedStoreDebugAndroidTest")))
        assertEquals(false, StampPolicy.packagingRequested(emptyList()))
        assertEquals(false, StampPolicy.packagingRequested(listOf(":phone:testStoreDebugUnitTest")))
        assertEquals(false, StampPolicy.packagingRequested(listOf(":phone:uninstallAll")))
        // A test filter value that merely contains "install" is not a task.
        assertEquals(
            false,
            StampPolicy.packagingRequested(listOf(":phone:testStoreDebugUnitTest", "--tests", "*InstallTest*")),
        )
    }

    @Test
    fun `family lookup by module name`() {
        assertEquals(PackageFamily.PHONE, PackageFamily.forModule("phone"))
        assertEquals(PackageFamily.WATCH, PackageFamily.forModule("wear"))
        assertEquals(PackageFamily.WATCH_FACE, PackageFamily.forModule("watchface"))
        try {
            PackageFamily.forModule("app")
            fail("accepted an unregistered module")
        } catch (expected: IllegalArgumentException) {
            // refused
        }
    }
}
