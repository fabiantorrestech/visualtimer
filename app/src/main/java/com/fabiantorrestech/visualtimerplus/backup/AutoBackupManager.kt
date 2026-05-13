package com.fabiantorrestech.visualtimerplus.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutoBackupManager {

    private const val LOCATION_PREF_KEY = "auto_backup_location_uri"
    private const val DEBOUNCE_DELAY_MS = 2 * 60 * 1000L

    private var debounceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scheduleBackup(context: Context) {
        val appCtx = context.applicationContext
        if (!TimerRepository.getState().autoBackupEnabled) return
        val uriString = getSavedLocationUri(appCtx) ?: return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            performBackup(appCtx, uriString)
        }
    }

    fun cancelPending() {
        debounceJob?.cancel()
    }

    fun saveLocationUri(context: Context, uri: Uri) {
        context.getSharedPreferences("visual_timer_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(LOCATION_PREF_KEY, uri.toString())
            .apply()
    }

    fun getSavedLocationUri(context: Context): String? =
        context.getSharedPreferences("visual_timer_prefs", Context.MODE_PRIVATE)
            .getString(LOCATION_PREF_KEY, null)

    private suspend fun performBackup(context: Context, uriString: String) {
        try {
            val dao = AppDatabase.getInstance(context).appDao()
            val folders = dao.getAllFolders()
            val presets = dao.getAllPresets()
            val json = BackupManager.buildBackup(context, folders, presets).toString(2)

            val treeUri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val cr = context.contentResolver
            var existingDocId: String? = null
            cr.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "visualtimer_autobackup.json") {
                        existingDocId = cursor.getString(0)
                        break
                    }
                }
            }

            val targetUri = if (existingDocId != null)
                DocumentsContract.buildDocumentUriUsingTree(treeUri, existingDocId!!)
            else
                DocumentsContract.createDocument(cr, parentDocUri, "application/json", "visualtimer_autobackup")
                    ?: return

            cr.openOutputStream(targetUri, "wt")?.use { it.write(json.toByteArray()) }
        } catch (_: Exception) {
            // Silent fail — auto-backup should never disrupt the user
        }
    }
}
