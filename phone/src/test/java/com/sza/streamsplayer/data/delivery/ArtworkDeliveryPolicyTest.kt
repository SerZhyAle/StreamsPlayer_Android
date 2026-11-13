package com.sza.streamsplayer.data.delivery

import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.PayloadFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S1483: artwork is verified structurally and by size, not by a pin. These tests are the guard on that
 * policy: an edit that lets a truncated tile pack install would otherwise pass every mechanical gate
 * this repo has.
 */
class ArtworkDeliveryPolicyTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val verifier = PayloadIntegrityVerifier()

    @Test
    fun `artwork payloads are published unpinned and under stable names`() {
        val artwork = DeliverableDescriptorCatalog.channelPreviewAtlas().files +
            DeliverableDescriptorCatalog.streamLogoAtlas().files

        assertTrue("artwork descriptors exist", artwork.isNotEmpty())
        artwork.forEach { file ->
            assertTrue("${file.fileName} must not be pinned", file.sha256.isBlank())
            val url = file.sources.single()
            assertTrue(
                "${file.fileName} must be fetched under its stable name, was $url",
                url.endsWith("/${file.fileName}"),
            )
        }
    }

    @Test
    fun `a healthy tile pack verifies`() {
        val pack = tilePack("pack-ok.zip", entryNames = listOf("0", "1", "1948"))
        assertTrue(verifier.verify(pack, unpinnedZip(pack.name)) is PayloadIntegrityVerifier.Result.Verified)
    }

    @Test
    fun `a truncated download is rejected`() {
        val pack = tilePack("pack-truncated.zip", entryNames = listOf("0", "1"))
        val bytes = pack.readBytes()
        pack.writeBytes(bytes.copyOf(bytes.size / 2))

        val result = verifier.verify(pack, unpinnedZip(pack.name, minSize = 1))
        assertTrue("truncated pack must fail", result is PayloadIntegrityVerifier.Result.Failed)
    }

    @Test
    fun `a zip of ordinary files is not a tile pack`() {
        val pack = tilePack("pack-wrong-names.zip", entryNames = listOf("0", "cover.webp"))
        val result = verifier.verify(pack, unpinnedZip(pack.name))
        assertTrue("named entries must fail", result is PayloadIntegrityVerifier.Result.Failed)
    }

    @Test
    fun `an unpinned non-zip resource is still accepted on size alone`() {
        val resource = folder.newFile("coords.json").apply { writeBytes(ByteArray(2048)) }
        val expected = PayloadFile(
            fileName = resource.name,
            sources = listOf("https://example.invalid/${resource.name}"),
            sha256 = "",
            minSize = 1024,
        )
        assertTrue(verifier.verify(resource, expected) is PayloadIntegrityVerifier.Result.Verified)
    }

    @Test
    fun `artwork sets are exactly the two the manifest names`() {
        // The manifest keys and the descriptor sets are one contract; drifting them apart is how the
        // freshness signal would silently stop matching the payload it describes.
        assertFalse(DeliverableSet.CHANNEL_PREVIEW_ATLAS == DeliverableSet.STREAM_LOGO_ATLAS)
        assertEquals(DeliverableSet.CHANNEL_PREVIEW_ATLAS, DeliverableDescriptorCatalog.channelPreviewAtlas().set)
        assertEquals(DeliverableSet.STREAM_LOGO_ATLAS, DeliverableDescriptorCatalog.streamLogoAtlas().set)
    }

    private fun unpinnedZip(name: String, minSize: Long = 1): PayloadFile = PayloadFile(
        fileName = name,
        sources = listOf("https://example.invalid/$name"),
        sha256 = "",
        minSize = minSize,
    )

    private fun tilePack(name: String, entryNames: List<String>): File {
        val file = folder.newFile(name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entryNames.forEach { entryName ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(ByteArray(64) { it.toByte() })
                zip.closeEntry()
            }
        }
        return file
    }
}
