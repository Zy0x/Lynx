package com.noir.lynx.safety

import android.util.Log
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class ThermalTransactionRecord(
    val transactionId: String,
    val timestamp: Long,
    val mode: String,
    val targetNodesCount: Int,
    val verifiedAppliedCount: Int,
    val rollbackAvailable: Boolean,
    val status: String, // "COMMITTED", "FAILED", "ROLLED_BACK"
    val failureReason: String? = null
)

sealed class ThermalTransactionResult {
    data class Success(val record: ThermalTransactionRecord) : ThermalTransactionResult()
    data class Failure(val record: ThermalTransactionRecord, val reason: String) : ThermalTransactionResult()
}

/**
 * ThermalTransaction — Transactional engine with atomic checkpointing,
 * verified read-back validation, and automatic rollback on failure.
 */
object ThermalTransaction {
    private const val TAG = "ThermalTransaction"
    private const val BACKUP_DIR = "/data/local/tmp/lynx_backup/thermal"

    private val historyLock = Any()
    private val _history = mutableListOf<ThermalTransactionRecord>()
    val history: List<ThermalTransactionRecord>
        get() = synchronized(historyLock) { _history.toList() }

    private fun generateTransactionId(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "thermal_${sdf.format(Date())}_${(100..999).random()}"
    }

    /**
     * Executes a batch of node writes within an atomic transaction.
     * Pre-reads old values, backs them up, writes with verification, and commits or rolls back.
     */
    suspend fun execute(
        mode: String,
        targetWrites: List<Pair<String, String>>
    ): ThermalTransactionResult = withContext(Dispatchers.IO) {
        val txId = generateTransactionId()
        val timestamp = System.currentTimeMillis()
        Log.i(TAG, "Starting transaction $txId for mode $mode with ${targetWrites.size} operations")

        if (targetWrites.isEmpty()) {
            val record = ThermalTransactionRecord(
                transactionId = txId,
                timestamp = timestamp,
                mode = mode,
                targetNodesCount = 0,
                verifiedAppliedCount = 0,
                rollbackAvailable = false,
                status = "COMMITTED",
                failureReason = null
            )
            synchronized(historyLock) { _history.add(0, record) }
            return@withContext ThermalTransactionResult.Success(record)
        }

        // 1. Pre-read and backup original states
        val backupNodes = mutableListOf<ThermalNodeSnapshot>()
        try {
            val readSb = StringBuilder()
            readSb.append("mkdir -p '$BACKUP_DIR' 2>/dev/null\n")
            for ((path, _) in targetWrites) {
                readSb.append("if [ -e '$path' ]; then echo '$path|'\$(cat '$path' 2>/dev/null | head -n 1); fi\n")
            }
            val preReadLines = Shell.cmd(readSb.toString()).exec().out
            for (line in preReadLines) {
                val p = line.split("|")
                if (p.size >= 2) {
                    backupNodes.add(ThermalNodeSnapshot(p[0].trim(), p[1].trim()))
                }
            }

            // Save transaction backup file
            val backupJson = JSONObject().apply {
                put("transactionId", txId)
                put("timestamp", timestamp)
                put("mode", mode)
                val arr = JSONArray()
                for (b in backupNodes) {
                    arr.put(JSONObject().apply {
                        put("path", b.path)
                        put("value", b.value)
                    })
                }
                put("nodes", arr)
            }

            val backupPath = "$BACKUP_DIR/$txId.json"
            val saveScript = """
                cat << 'EOF' > '$backupPath'
${backupJson.toString(2)}
EOF
                chmod 644 '$backupPath' 2>/dev/null
            """.trimIndent()
            Shell.cmd(saveScript).exec()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pre-write backup: ${e.message}")
        }

        // 2. Verified Apply Loop
        var appliedCount = 0
        var failureNode: String? = null
        var failureMsg: String? = null

        for ((path, value) in targetWrites) {
            val res = NodeWriter.writeVerified(path, value)
            when (res) {
                is WriteResult.Applied -> {
                    appliedCount++
                }
                is WriteResult.Rejected -> {
                    failureNode = path
                    failureMsg = "Kernel rejected write on $path (read-back: ${res.readBack}, expected: $value)"
                    break
                }
                is WriteResult.Unsupported -> {
                    // Non-critical node may not exist on all kernels; log warning but don't fail immediately
                    Log.w(TAG, "Node unsupported on this kernel: $path")
                }
            }
        }

        // 3. Evaluate Result & Handle Rollback if Failed
        if (failureNode != null) {
            Log.e(TAG, "Transaction $txId failed on node $failureNode: $failureMsg. Rolling back...")
            rollbackTransaction(backupNodes)

            val failedRecord = ThermalTransactionRecord(
                transactionId = txId,
                timestamp = timestamp,
                mode = mode,
                targetNodesCount = targetWrites.size,
                verifiedAppliedCount = appliedCount,
                rollbackAvailable = false,
                status = "FAILED",
                failureReason = failureMsg
            )
            synchronized(historyLock) { _history.add(0, failedRecord) }
            return@withContext ThermalTransactionResult.Failure(failedRecord, failureMsg ?: "Transaction failed")
        }

        // 4. Commit Success
        val successRecord = ThermalTransactionRecord(
            transactionId = txId,
            timestamp = timestamp,
            mode = mode,
            targetNodesCount = targetWrites.size,
            verifiedAppliedCount = appliedCount,
            rollbackAvailable = backupNodes.isNotEmpty(),
            status = "COMMITTED",
            failureReason = null
        )
        synchronized(historyLock) { _history.add(0, successRecord) }
        Log.i(TAG, "Transaction $txId committed successfully ($appliedCount / ${targetWrites.size} nodes verified)")
        ThermalTransactionResult.Success(successRecord)
    }

    private suspend fun rollbackTransaction(backupNodes: List<ThermalNodeSnapshot>) {
        if (backupNodes.isEmpty()) return
        val sb = StringBuilder()
        for (n in backupNodes) {
            sb.append("if [ -e '${n.path}' ]; then chmod 644 '${n.path}' 2>/dev/null; echo '${n.value}' > '${n.path}' 2>/dev/null; fi\n")
        }
        Shell.cmd(sb.toString()).exec()
        Log.i(TAG, "Rollback executed for ${backupNodes.size} nodes.")
    }
}
