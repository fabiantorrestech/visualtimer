package com.fabiantorrestech.visualtimerplus.backup

import android.content.Context
import com.fabiantorrestech.visualtimerplus.db.AppDao
import com.fabiantorrestech.visualtimerplus.db.PresetEntity
import com.fabiantorrestech.visualtimerplus.db.PresetFolderEntity
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val PREFS_NAME = "visual_timer_prefs"
    private const val BACKUP_VERSION = 1

    fun buildBackup(
        context: Context,
        folders: List<PresetFolderEntity>,
        presets: List<PresetEntity>,
    ): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("created_at", System.currentTimeMillis())

        root.put("appearance", JSONObject().apply {
            put("oled_mode", prefs.getBoolean("oled_mode", false))
            put("theme_mode", prefs.getString("theme_mode", "System") ?: "System")
            put("hide_status_bar_enabled", prefs.getBoolean("hide_status_bar_enabled", false))
            put("hide_status_bar_only_when_running", prefs.getBoolean("hide_status_bar_only_when_running", false))
            put("hide_page_dots_in_clean_mode", prefs.getBoolean("hide_page_dots_in_clean_mode", true))
        })

        root.put("notification", JSONObject().apply {
            put("notification_mode", prefs.getString("notification_mode", "Consolidated") ?: "Consolidated")
            put("notification_update_interval", prefs.getInt("notification_update_interval", 15))
        })

        root.put("overlay", JSONObject().apply {
            put("overlay_enabled", prefs.getBoolean("overlay_enabled", true))
            put("overlay_size", prefs.getString("overlay_size", "Medium") ?: "Medium")
            put("overlay_style", prefs.getString("overlay_style", "Ring") ?: "Ring")
            put("overlay_show_on_lockscreen", prefs.getBoolean("overlay_show_on_lockscreen", false))
        })

        root.put("default_timer_settings", JSONObject().apply {
            put("sound_enabled", prefs.getBoolean("default_sound_enabled", true))
            put("finished_sound_route", prefs.getString("default_finished_sound_route", "Default") ?: "Default")
            put("finished_sound_volume", prefs.getInt("default_finished_sound_volume", 100))
            put("override_muted_system_volume", prefs.getBoolean("default_override_muted_system_volume", false))
            put("ignore_silent_mode", prefs.getBoolean("default_ignore_silent_mode", false))
            put("full_clock_mode", prefs.getBoolean("default_full_clock_mode", false))
            put("finished_vibration_mode", prefs.getString("default_finished_vibration_mode", "OneMinute") ?: "OneMinute")
            put("keep_screen_awake", prefs.getBoolean("default_keep_screen_awake", false))
            put("show_current_time_enabled", prefs.getBoolean("default_show_current_time_enabled", false))
            put("show_clock_seconds_enabled", prefs.getBoolean("default_show_clock_seconds_enabled", false))
            put("clock_position", prefs.getString("default_clock_position", "Left") ?: "Left")
            put("clock_text_size_sp", prefs.getFloat("default_clock_text_size_sp", 32f).toDouble())
            put("clockwise_mode_enabled", prefs.getBoolean("default_clockwise_mode_enabled", true))
            put("clean_mode_enabled", prefs.getBoolean("default_clean_mode_enabled", false))
            put("clean_mode_auto_dismiss_enabled", prefs.getBoolean("default_clean_mode_auto_dismiss_enabled", true))
            put("clean_mode_auto_dismiss_seconds", prefs.getInt("default_clean_mode_auto_dismiss_seconds", 3))
            put("hide_clock_in_clean_mode", prefs.getBoolean("default_hide_clock_in_clean_mode", false))
            put("timer_title_enabled", prefs.getBoolean("default_timer_title_enabled", false))
            put("timer_title_hide_in_clean_mode", prefs.getBoolean("default_timer_title_hide_in_clean_mode", false))
            put("timer_title_position", prefs.getString("default_timer_title_position", "Center") ?: "Center")
            put("timer_title_text_size_sp", prefs.getFloat("default_timer_title_text_size_sp", 16f).toDouble())
            put("center_time_size_sp", prefs.getFloat("default_center_time_size_sp", 36f).toDouble())
            put("prompt_before_start", prefs.getBoolean("default_prompt_before_start", false))
            put("show_end_time_enabled", prefs.getBoolean("default_show_end_time_enabled", false))
            put("show_end_time_seconds_enabled", prefs.getBoolean("default_show_end_time_seconds_enabled", false))
            put("end_time_size_sp", prefs.getFloat("default_end_time_size_sp", 32f).toDouble())
        })

        val foldersArray = JSONArray()
        folders.forEach { folder ->
            foldersArray.put(JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("sort_order", folder.sortOrder)
            })
        }
        root.put("preset_folders", foldersArray)

        val presetsArray = JSONArray()
        presets.forEach { preset ->
            presetsArray.put(JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("duration_millis", preset.durationMillis)
                put("folder_id", preset.folderId ?: JSONObject.NULL)
                put("sort_order", preset.sortOrder)
                put("sound_enabled", preset.soundEnabled ?: JSONObject.NULL)
                put("finished_sound_volume_percent", preset.finishedSoundVolumePercent ?: JSONObject.NULL)
                put("override_system_volume", preset.overrideSystemVolume ?: JSONObject.NULL)
                put("finished_vibration_mode_name", preset.finishedVibrationModeName ?: JSONObject.NULL)
                put("keep_screen_awake", preset.keepScreenAwake ?: JSONObject.NULL)
                put("show_current_time", preset.showCurrentTime ?: JSONObject.NULL)
                put("show_seconds", preset.showSeconds ?: JSONObject.NULL)
                put("clock_position_name", preset.clockPositionName ?: JSONObject.NULL)
                put("clock_size_name", preset.clockSizeName ?: JSONObject.NULL)
                put("ignore_silent_mode", preset.ignoreSilentMode ?: JSONObject.NULL)
            })
        }
        root.put("presets", presetsArray)

        return root
    }

    fun parseBackup(json: String): JSONObject {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        if (version < 1) throw IllegalArgumentException("Invalid backup file.")
        if (version > BACKUP_VERSION) {
            throw IllegalArgumentException("Backup version $version requires a newer app version.")
        }
        return root
    }

    suspend fun applyBackup(context: Context, root: JSONObject, dao: AppDao) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        root.optJSONObject("appearance")?.let { a ->
            editor.putBoolean("oled_mode", a.optBoolean("oled_mode", false))
            editor.putString("theme_mode", a.optString("theme_mode", "System"))
            editor.putBoolean("hide_status_bar_enabled", a.optBoolean("hide_status_bar_enabled", false))
            editor.putBoolean("hide_status_bar_only_when_running", a.optBoolean("hide_status_bar_only_when_running", false))
            editor.putBoolean("hide_page_dots_in_clean_mode", a.optBoolean("hide_page_dots_in_clean_mode", true))
        }

        root.optJSONObject("notification")?.let { n ->
            editor.putString("notification_mode", n.optString("notification_mode", "Consolidated"))
            editor.putInt("notification_update_interval", n.optInt("notification_update_interval", 15))
        }

        root.optJSONObject("overlay")?.let { o ->
            editor.putBoolean("overlay_enabled", o.optBoolean("overlay_enabled", true))
            editor.putString("overlay_size", o.optString("overlay_size", "Medium"))
            editor.putString("overlay_style", o.optString("overlay_style", "Ring"))
            editor.putBoolean("overlay_show_on_lockscreen", o.optBoolean("overlay_show_on_lockscreen", false))
        }

        root.optJSONObject("default_timer_settings")?.let { d ->
            editor.putBoolean("default_sound_enabled", d.optBoolean("sound_enabled", true))
            editor.putString("default_finished_sound_route", d.optString("finished_sound_route", "Default"))
            editor.putInt("default_finished_sound_volume", d.optInt("finished_sound_volume", 100))
            editor.putBoolean("default_override_muted_system_volume", d.optBoolean("override_muted_system_volume", false))
            editor.putBoolean("default_ignore_silent_mode", d.optBoolean("ignore_silent_mode", false))
            editor.putBoolean("default_full_clock_mode", d.optBoolean("full_clock_mode", false))
            editor.putString("default_finished_vibration_mode", d.optString("finished_vibration_mode", "OneMinute"))
            editor.putBoolean("default_keep_screen_awake", d.optBoolean("keep_screen_awake", false))
            editor.putBoolean("default_show_current_time_enabled", d.optBoolean("show_current_time_enabled", false))
            editor.putBoolean("default_show_clock_seconds_enabled", d.optBoolean("show_clock_seconds_enabled", false))
            editor.putString("default_clock_position", d.optString("clock_position", "Left"))
            editor.putFloat("default_clock_text_size_sp", d.optDouble("clock_text_size_sp", 32.0).toFloat())
            editor.putBoolean("default_clockwise_mode_enabled", d.optBoolean("clockwise_mode_enabled", true))
            editor.putBoolean("default_clean_mode_enabled", d.optBoolean("clean_mode_enabled", false))
            editor.putBoolean("default_clean_mode_auto_dismiss_enabled", d.optBoolean("clean_mode_auto_dismiss_enabled", true))
            editor.putInt("default_clean_mode_auto_dismiss_seconds", d.optInt("clean_mode_auto_dismiss_seconds", 3))
            editor.putBoolean("default_hide_clock_in_clean_mode", d.optBoolean("hide_clock_in_clean_mode", false))
            editor.putBoolean("default_timer_title_enabled", d.optBoolean("timer_title_enabled", false))
            editor.putBoolean("default_timer_title_hide_in_clean_mode", d.optBoolean("timer_title_hide_in_clean_mode", false))
            editor.putString("default_timer_title_position", d.optString("timer_title_position", "Center"))
            editor.putFloat("default_timer_title_text_size_sp", d.optDouble("timer_title_text_size_sp", 16.0).toFloat())
            editor.putFloat("default_center_time_size_sp", d.optDouble("center_time_size_sp", 36.0).toFloat())
            editor.putBoolean("default_prompt_before_start", d.optBoolean("prompt_before_start", false))
            editor.putBoolean("default_show_end_time_enabled", d.optBoolean("show_end_time_enabled", false))
            editor.putBoolean("default_show_end_time_seconds_enabled", d.optBoolean("show_end_time_seconds_enabled", false))
            editor.putFloat("default_end_time_size_sp", d.optDouble("end_time_size_sp", 32.0).toFloat())
        }

        editor.apply()
        TimerRepository.reloadFromPrefs()

        dao.deleteAllPresets()
        dao.deleteAllFolders()

        val folderIdMap = mutableMapOf<Long, Long>()
        val foldersArray = root.optJSONArray("preset_folders") ?: JSONArray()
        for (i in 0 until foldersArray.length()) {
            val obj = foldersArray.getJSONObject(i)
            val oldId = obj.getLong("id")
            val newId = dao.insertFolder(
                PresetFolderEntity(
                    name = obj.getString("name"),
                    sortOrder = obj.optInt("sort_order", 0),
                )
            )
            folderIdMap[oldId] = newId
        }

        val presetsArray = root.optJSONArray("presets") ?: JSONArray()
        for (i in 0 until presetsArray.length()) {
            val obj = presetsArray.getJSONObject(i)
            val oldFolderId = if (!obj.isNull("folder_id")) obj.getLong("folder_id").takeIf { it >= 0 } else null
            val newFolderId = oldFolderId?.let { folderIdMap[it] }
            dao.insertPreset(
                PresetEntity(
                    name = obj.getString("name"),
                    durationMillis = obj.getLong("duration_millis"),
                    folderId = newFolderId,
                    sortOrder = obj.optInt("sort_order", 0),
                    soundEnabled = if (!obj.isNull("sound_enabled")) obj.optBoolean("sound_enabled") else null,
                    finishedSoundVolumePercent = if (!obj.isNull("finished_sound_volume_percent")) obj.optInt("finished_sound_volume_percent") else null,
                    overrideSystemVolume = if (!obj.isNull("override_system_volume")) obj.optBoolean("override_system_volume") else null,
                    finishedVibrationModeName = if (!obj.isNull("finished_vibration_mode_name")) obj.optString("finished_vibration_mode_name") else null,
                    keepScreenAwake = if (!obj.isNull("keep_screen_awake")) obj.optBoolean("keep_screen_awake") else null,
                    showCurrentTime = if (!obj.isNull("show_current_time")) obj.optBoolean("show_current_time") else null,
                    showSeconds = if (!obj.isNull("show_seconds")) obj.optBoolean("show_seconds") else null,
                    clockPositionName = if (!obj.isNull("clock_position_name")) obj.optString("clock_position_name") else null,
                    clockSizeName = if (!obj.isNull("clock_size_name")) obj.optString("clock_size_name") else null,
                    ignoreSilentMode = if (!obj.isNull("ignore_silent_mode")) obj.optBoolean("ignore_silent_mode") else null,
                )
            )
        }
    }
}
