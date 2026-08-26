package dev.busung.s25uroot

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalPayloadStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = LocalPayloadStore(context)

    @Before
    fun clearStore() {
        PayloadKind.entries.forEach(store::clear)
    }

    @Test
    fun importRecordsDigestAndSurvivesReload() {
        val source = sourceFile("payload-source", 2048)
        val imported = store.import(PayloadKind.Exploit, Uri.fromFile(source))

        assertEquals(2048L, imported.size)
        assertEquals(64, imported.sha256.length)

        val reloaded = LocalPayloadStore(context).load(PayloadKind.Exploit)
        assertNotNull(reloaded)
        assertEquals(imported.sha256, reloaded!!.sha256)
        assertEquals(imported.size, reloaded.size)
    }

    @Test
    fun truncatedStagedFileIsNotReported() {
        store.import(PayloadKind.KernelSu, Uri.fromFile(sourceFile("payload-truncated", 4096)))

        // Stand in for an import that died partway: the record is present but
        // the bytes behind it are not the ones it describes.
        store.file(PayloadKind.KernelSu).apply {
            setWritable(true)
            writeBytes(ByteArray(16))
        }

        assertNull(store.load(PayloadKind.KernelSu))
    }

    @Test
    fun requirePayloadsFailsUntilBothKindsAreImported() {
        val source = sourceFile("payload-pair", 512)
        store.import(PayloadKind.Exploit, Uri.fromFile(source))

        assertThrows(IllegalArgumentException::class.java) { store.requirePayloads() }

        store.import(PayloadKind.KernelSu, Uri.fromFile(source))
        assertEquals(LocalPayloadStore.LOCAL_PROFILE_ID, store.requirePayloads().profileId)
    }

    private fun sourceFile(name: String, size: Int): File =
        File(context.cacheDir, name).apply {
            writeBytes(ByteArray(size) { (it % 251).toByte() })
        }
}
