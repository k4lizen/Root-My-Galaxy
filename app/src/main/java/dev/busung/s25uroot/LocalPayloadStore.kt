package dev.busung.s25uroot

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import androidx.annotation.StringRes
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

enum class PayloadKind(
    val storedKey: String,
    val fileName: String,
    @StringRes val labelRes: Int,
) {
    Exploit("exploit", "cve-2026-43499-app.so", R.string.artifact_exploit),
    KernelSu("kernelsu", "ksud-s25u-kdp", R.string.artifact_kernelsu),
}

data class LocalPayload(
    val kind: PayloadKind,
    val sourceName: String,
    val size: Long,
    val sha256: String,
) {
    val shortSha256: String
        get() = sha256.take(16)
}

/**
 * Payloads supplied from local storage instead of GitHub.
 *
 * An offline install has no support manifest to check a transfer against, so
 * the import records the digest of exactly what was copied in and surfaces it
 * for the user to compare against the file they pushed. The recorded size is
 * re-checked every time an entry is read: an import interrupted partway would
 * otherwise leave a truncated file in the store still looking importable.
 */
class LocalPayloadStore(private val context: Context) {

    fun file(kind: PayloadKind): File = File(directory(), kind.fileName)

    fun load(kind: PayloadKind): LocalPayload? {
        val stored = preferences()
        val sha256 = stored.getString(key(kind, SHA_SUFFIX), null) ?: return null
        val sourceName = stored.getString(key(kind, NAME_SUFFIX), null) ?: return null
        val size = stored.getLong(key(kind, SIZE_SUFFIX), -1L)
        val file = file(kind)
        if (!file.isFile || file.length() != size) return null
        return LocalPayload(kind, sourceName, size, sha256)
    }

    fun import(kind: PayloadKind, uri: Uri): LocalPayload {
        val destination = file(kind)
        val temporary = File(destination.parentFile, "${kind.fileName}.part")
        // Drop the old record first: if the copy dies after the rename but
        // before the new record lands, a stale digest must not stay attached
        // to a file it no longer describes.
        forget(kind)
        val payload = try {
            copyIn(kind, uri, temporary, destination)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        preferences().edit()
            .putString(key(kind, NAME_SUFFIX), payload.sourceName)
            .putLong(key(kind, SIZE_SUFFIX), payload.size)
            .putString(key(kind, SHA_SUFFIX), payload.sha256)
            .apply()
        return payload
    }

    fun clear(kind: PayloadKind) {
        forget(kind)
        file(kind).delete()
    }

    /**
     * Resolves the imported pair for an install run. Throws with the missing
     * artifact named, so a half-finished import fails in the support check
     * rather than partway through the exploit.
     */
    fun requirePayloads(): VerifiedPayloads {
        PayloadKind.entries.forEach { kind ->
            requireNotNull(load(kind)) {
                context.getString(R.string.local_payload_missing, context.getString(kind.labelRes))
            }
        }
        return VerifiedPayloads(
            profileId = LOCAL_PROFILE_ID,
            exploit = file(PayloadKind.Exploit),
            kernelSu = file(PayloadKind.KernelSu),
        )
    }

    private fun copyIn(
        kind: PayloadKind,
        uri: Uri,
        temporary: File,
        destination: File,
    ): LocalPayload {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: error(context.getString(R.string.local_payload_unreadable))
        input.use { source ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    total += count
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(total > 0) { context.getString(R.string.local_payload_empty) }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, context.getString(kind.labelRes))
        }
        // Matches the permissions a downloaded payload is staged with; the
        // bootstrap helper reads these, it does not exec them directly.
        Os.chmod(destination.absolutePath, 0b100100100)
        return LocalPayload(
            kind = kind,
            sourceName = displayName(uri) ?: kind.fileName,
            size = total,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    private fun displayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                cursor.getString(0)?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
    }

    private fun forget(kind: PayloadKind) {
        preferences().edit()
            .remove(key(kind, NAME_SUFFIX))
            .remove(key(kind, SIZE_SUFFIX))
            .remove(key(kind, SHA_SUFFIX))
            .apply()
    }

    private fun directory(): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun preferences() =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun key(kind: PayloadKind, suffix: String) = "${kind.storedKey}_$suffix"

    companion object {
        const val LOCAL_PROFILE_ID = "local"
        private const val DIRECTORY = "local-payloads"
        private const val PREFERENCES = "local_payloads"
        private const val NAME_SUFFIX = "name"
        private const val SIZE_SUFFIX = "size"
        private const val SHA_SUFFIX = "sha256"
    }
}
