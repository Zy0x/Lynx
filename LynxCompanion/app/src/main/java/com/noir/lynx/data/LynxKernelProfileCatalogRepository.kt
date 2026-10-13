package com.noir.lynx.data

import android.util.Log
import com.noir.lynx.profiles.KernelPerformanceProfile
import com.noir.lynx.profiles.KernelProfileCatalog
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persistent catalog shared by standalone and installed-module modes. */
internal object LynxKernelProfileCatalogRepository {
    private const val TAG = "LynxProfileCatalog"

    private suspend fun catalogPath(): String =
        LynxAppStateRepository.getActiveConfigPath().substringBeforeLast('/') + "/kernel_profiles.json"

    private fun standaloneCatalogPath(): String =
        LynxAppStateRepository.getLocalConfigPath().substringBeforeLast('/') + "/kernel_profiles.json"

    suspend fun read(): KernelProfileCatalog = withContext(Dispatchers.IO) {
        val path = catalogPath()
        val localPath = standaloneCatalogPath()
        val raw = readText(path).ifBlank { readText(localPath) }
        if (raw.isNotBlank() && path != localPath && !File(path).exists()) {
            LynxRepository.writeTextToFileSafely(path, raw, "660")
        }
        KernelProfileCatalog.parse(raw)
    }

    suspend fun write(catalog: KernelProfileCatalog): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = catalogPath()
            val encoded = catalog.toJson()
            val primarySaved = LynxRepository.writeTextToFileSafely(path, encoded, "660")
            val localPath = standaloneCatalogPath()
            val localSaved = if (path == localPath) true else LynxRepository.writeTextToFileSafely(localPath, encoded, "600")
            primarySaved && localSaved
        } catch (error: Exception) {
            Log.e(TAG, "Unable to persist catalog", error)
            false
        }
    }

    private suspend fun readText(path: String): String = try {
        val file = File(path)
        if (file.canRead()) file.readText(Charsets.UTF_8)
        else Shell.cmd("cat '$path' 2>/dev/null").exec().out.joinToString("\n")
    } catch (_: Exception) { "" }

    suspend fun save(
        profile: KernelPerformanceProfile,
        replaceBuiltIn: Boolean = false,
        replaceExistingCustom: Boolean = false,
    ): Result<KernelProfileCatalog> {
        profile.validate()?.let { return Result.failure(IllegalArgumentException(it)) }
        val current = read()
        val existing = current.find(profile.id)
        if (existing?.builtIn == true && !replaceBuiltIn) {
            return Result.failure(IllegalArgumentException("Profil bawaan perlu konfirmasi penggantian"))
        }
        if (existing != null && !existing.builtIn && !replaceExistingCustom) {
            return Result.failure(IllegalArgumentException("ID profil sudah digunakan; pilih profil lain atau edit profil tersebut"))
        }
        if (existing != null && !existing.builtIn && existing.id != profile.id) {
            return Result.failure(IllegalArgumentException("ID profil sudah digunakan"))
        }
        val saved = if (existing?.builtIn == true) profile.copy(builtIn = true) else profile.copy(builtIn = false)
        val updated = current.copy(profiles = current.profiles.filterNot { it.id == saved.id } + saved)
        return if (write(updated)) Result.success(updated) else Result.failure(IllegalStateException("Gagal menyimpan katalog profil"))
    }

    suspend fun delete(id: String): Boolean {
        val current = read()
        val item = current.find(id) ?: return false
        if (item.builtIn) return false
        val next = current.copy(
            profiles = current.profiles.filterNot { it.id == id },
            activeId = if (current.activeId == id) "balance" else current.activeId
        )
        return write(next)
    }

    suspend fun duplicate(id: String): Result<KernelProfileCatalog> {
        val current = read()
        val source = current.find(id) ?: return Result.failure(IllegalArgumentException("Profil tidak ditemukan"))
        val stem = source.id.take(32)
        var suffix = 2
        var newId = "${stem}_copy"
        while (current.find(newId) != null) {
            newId = "${stem}_$suffix"
            suffix++
        }
        val duplicate = source.copy(id = newId, name = "${source.name} (Salinan)".take(40), builtIn = false)
        val next = current.copy(profiles = current.profiles + duplicate)
        return if (write(next)) Result.success(next) else Result.failure(IllegalStateException("Gagal menyalin profil"))
    }

    suspend fun restoreBuiltIn(id: String): Boolean {
        val defaults = KernelPerformanceProfile.builtIns().firstOrNull { it.id == id } ?: return false
        val current = read()
        return write(current.copy(profiles = current.profiles.filterNot { it.id == id } + defaults))
    }

    suspend fun setActive(id: String): Boolean {
        val current = read()
        if (current.find(id) == null) return false
        return write(current.copy(activeId = id))
    }
}
