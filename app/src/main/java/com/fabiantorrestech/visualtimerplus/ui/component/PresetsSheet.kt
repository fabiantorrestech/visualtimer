package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.backup.AutoBackupManager
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.PresetEntity
import com.fabiantorrestech.visualtimerplus.db.PresetFolderEntity
import com.fabiantorrestech.visualtimerplus.timer.ClockPosition
import com.fabiantorrestech.visualtimerplus.timer.FinishedVibrationMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsSheet(
    db: AppDatabase,
    currentDurationMillis: Long,
    onAction: (TimerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dao = db.appDao()
    val context = LocalContext.current

    val folders by dao.observeFolders().collectAsState(emptyList())
    val allPresets by dao.observePresets().collectAsState(emptyList())

    var searchQuery by remember { mutableStateOf("") }
    val searchResults by dao.searchPresets(searchQuery).collectAsState(emptyList())
    val displayPresets = if (searchQuery.isBlank()) allPresets else searchResults

    var editMode by remember { mutableStateOf(false) }
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<PresetEntity?>(null) }
    var folderToRename by remember { mutableStateOf<PresetFolderEntity?>(null) }
    var presetToChangeDuration by remember { mutableStateOf<PresetEntity?>(null) }
    var presetToEditSettings by remember { mutableStateOf<PresetEntity?>(null) }
    var presetToMoveToFolder by remember { mutableStateOf<PresetEntity?>(null) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var pickerInitialMillis by remember { mutableStateOf(currentDurationMillis) }
    var pendingPresetName by remember { mutableStateOf("") }
    var pendingPresetFolder by remember { mutableStateOf<Long?>(null) }
    var pendingPresetMillis by remember { mutableStateOf(currentDurationMillis) }

    // Drag-to-reorder state
    val presetLists = remember { mutableStateMapOf<Long?, SnapshotStateList<PresetEntity>>() }
    var draggedPresetId by remember { mutableStateOf<Long?>(null) }
    var dragFolderId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Sync per-folder lists from DB when not dragging
    LaunchedEffect(allPresets) {
        if (draggedPresetId == null) {
            val byFolder = allPresets.groupBy { it.folderId }
            (folders.map { it.id as Long? } + listOf(null)).forEach { folderId ->
                val items = byFolder[folderId] ?: emptyList()
                val existing = presetLists[folderId]
                if (existing == null) {
                    presetLists[folderId] = items.toMutableStateList()
                } else {
                    existing.clear()
                    existing.addAll(items)
                }
            }
        }
    }

    val itemHeightPx = with(LocalDensity.current) { 65.dp.toPx() }

    if (showDurationPicker) {
        DurationPickerSheet(
            initialMillis = pickerInitialMillis,
            onDurationSet = { millis ->
                pendingPresetMillis = millis
                showDurationPicker = false
                showAddPresetDialog = true
            },
            onDismiss = {
                showDurationPicker = false
                showAddPresetDialog = true
            },
        )
    }

    presetToChangeDuration?.let { preset ->
        DurationPickerSheet(
            initialMillis = preset.durationMillis,
            onDurationSet = { millis ->
                scope.launch {
                    dao.updatePreset(preset.copy(durationMillis = millis))
                    AutoBackupManager.scheduleBackup(context)
                }
                presetToChangeDuration = null
            },
            onDismiss = { presetToChangeDuration = null },
        )
    }

    presetToEditSettings?.let { preset ->
        PresetSettingsSheet(
            preset = preset,
            onSave = { updated ->
                scope.launch {
                    dao.updatePreset(updated)
                    AutoBackupManager.scheduleBackup(context)
                }
                presetToEditSettings = null
            },
            onDismiss = { presetToEditSettings = null },
        )
    }

    presetToMoveToFolder?.let { preset ->
        MoveFolderDialog(
            preset = preset,
            folders = folders,
            onConfirm = { targetFolderId ->
                scope.launch {
                    dao.updatePreset(preset.copy(folderId = targetFolderId))
                    AutoBackupManager.scheduleBackup(context)
                }
                presetToMoveToFolder = null
            },
            onDismiss = { presetToMoveToFolder = null },
        )
    }

    if (showAddPresetDialog) {
        AddPresetDialog(
            initialName = pendingPresetName,
            initialMillis = pendingPresetMillis,
            folders = folders,
            selectedFolderId = pendingPresetFolder,
            onFolderSelected = { pendingPresetFolder = it },
            onPickDuration = {
                pendingPresetName = it
                showAddPresetDialog = false
                pickerInitialMillis = pendingPresetMillis
                showDurationPicker = true
            },
            onConfirm = { name, millis, folderId ->
                scope.launch {
                    dao.insertPreset(
                        PresetEntity(
                            name = name.trim(),
                            durationMillis = millis,
                            folderId = folderId,
                        ),
                    )
                    AutoBackupManager.scheduleBackup(context)
                }
                showAddPresetDialog = false
                pendingPresetName = ""
                pendingPresetFolder = null
                pendingPresetMillis = currentDurationMillis
            },
            onDismiss = {
                showAddPresetDialog = false
                pendingPresetName = ""
                pendingPresetFolder = null
                pendingPresetMillis = currentDurationMillis
            },
        )
    }

    presetToRename?.let { preset ->
        RenameDialog(
            title = stringResource(R.string.preset_rename),
            initialValue = preset.name,
            onConfirm = { newName ->
                scope.launch {
                    dao.updatePreset(preset.copy(name = newName.trim()))
                    AutoBackupManager.scheduleBackup(context)
                }
                presetToRename = null
            },
            onDismiss = { presetToRename = null },
        )
    }

    folderToRename?.let { folder ->
        RenameDialog(
            title = stringResource(R.string.folder_rename),
            initialValue = folder.name,
            onConfirm = { newName ->
                scope.launch {
                    dao.updateFolder(folder.copy(name = newName.trim()))
                    AutoBackupManager.scheduleBackup(context)
                }
                folderToRename = null
            },
            onDismiss = { folderToRename = null },
        )
    }

    if (showAddFolderDialog) {
        RenameDialog(
            title = stringResource(R.string.folder_add),
            initialValue = "",
            onConfirm = { name ->
                scope.launch {
                    dao.insertFolder(PresetFolderEntity(name = name.trim()))
                    AutoBackupManager.scheduleBackup(context)
                }
                showAddFolderDialog = false
            },
            onDismiss = { showAddFolderDialog = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.presets),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // + Add is always visible so presets can be added in edit mode too
                    TextButton(onClick = {
                        pendingPresetMillis = currentDurationMillis
                        pendingPresetName = ""
                        pendingPresetFolder = null
                        showAddPresetDialog = true
                    }) {
                        Text(stringResource(R.string.preset_add_short))
                    }
                    if (!editMode) {
                        TextButton(onClick = { showAddFolderDialog = true }) {
                            Text(stringResource(R.string.folder_add_short))
                        }
                    }
                    TextButton(onClick = { editMode = !editMode }) {
                        Text(if (editMode) stringResource(R.string.done) else stringResource(R.string.edit))
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.presets_search)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (displayPresets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.presets_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    folders.forEach { folder ->
                        val folderPresets = presetLists[folder.id] ?: emptyList<PresetEntity>()
                        if (folderPresets.isNotEmpty() || editMode) {
                            item(key = "folder_${folder.id}") {
                                FolderHeader(
                                    folder = folder,
                                    editMode = editMode,
                                    onRename = { folderToRename = folder },
                                    onDelete = {
                                        scope.launch {
                                            dao.orphanPresetsInFolder(folder.id)
                                            dao.deleteFolder(folder)
                                            AutoBackupManager.scheduleBackup(context)
                                        }
                                    },
                                    onAddPreset = if (editMode) {
                                        {
                                            pendingPresetFolder = folder.id
                                            pendingPresetName = ""
                                            pendingPresetMillis = currentDurationMillis
                                            showAddPresetDialog = true
                                        }
                                    } else null,
                                )
                            }
                            items(folderPresets, key = { "preset_${it.id}" }) { preset ->
                                val isDragged = preset.id == draggedPresetId
                                PresetRowDraggable(
                                    preset = preset,
                                    editMode = editMode,
                                    isDragged = isDragged,
                                    dragOffsetY = if (isDragged) dragOffsetY else 0f,
                                    onSelect = {
                                        onAction(TimerAction.SetDurationExact(preset.durationMillis))
                                        onAction(TimerAction.SetActiveTimerName(preset.name))
                                        onAction(TimerAction.SetActivePresetId(preset.id))
                                        onDismiss()
                                    },
                                    onRename = { presetToRename = preset },
                                    onChangeDuration = { presetToChangeDuration = preset },
                                    onEditSettings = { presetToEditSettings = preset },
                                    onMoveToFolder = { presetToMoveToFolder = preset },
                                    onDelete = {
                                        scope.launch {
                                            dao.deletePreset(preset)
                                            AutoBackupManager.scheduleBackup(context)
                                        }
                                    },
                                    onDragStart = {
                                        draggedPresetId = preset.id
                                        dragFolderId = folder.id
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { delta ->
                                        dragOffsetY += delta
                                        val list = presetLists[dragFolderId]
                                        val idx = list?.indexOfFirst { it.id == draggedPresetId } ?: -1
                                        if (list != null && idx >= 0) {
                                            when {
                                                dragOffsetY > itemHeightPx / 2 && idx < list.size - 1 -> {
                                                    list.add(idx + 1, list.removeAt(idx))
                                                    dragOffsetY -= itemHeightPx
                                                }
                                                dragOffsetY < -itemHeightPx / 2 && idx > 0 -> {
                                                    list.add(idx - 1, list.removeAt(idx))
                                                    dragOffsetY += itemHeightPx
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val fId = dragFolderId
                                        val list = presetLists[fId]
                                        if (list != null) {
                                            scope.launch {
                                                list.forEachIndexed { index, p ->
                                                    dao.updatePreset(p.copy(sortOrder = index))
                                                }
                                                AutoBackupManager.scheduleBackup(context)
                                            }
                                        }
                                        draggedPresetId = null
                                        dragOffsetY = 0f
                                    },
                                )
                            }
                        }
                    }

                    // Uncategorized
                    val uncategorized = presetLists[null] ?: emptyList<PresetEntity>()
                    if (uncategorized.isNotEmpty() || editMode) {
                        item(key = "folder_uncategorized") {
                            FolderHeader(
                                label = stringResource(R.string.folder_uncategorized),
                                editMode = false,
                                onRename = {},
                                onDelete = {},
                                onAddPreset = if (editMode) {
                                    {
                                        pendingPresetFolder = null
                                        pendingPresetName = ""
                                        pendingPresetMillis = currentDurationMillis
                                        showAddPresetDialog = true
                                    }
                                } else null,
                            )
                        }
                        items(uncategorized, key = { "preset_${it.id}" }) { preset ->
                            val isDragged = preset.id == draggedPresetId
                            PresetRowDraggable(
                                preset = preset,
                                editMode = editMode,
                                isDragged = isDragged,
                                dragOffsetY = if (isDragged) dragOffsetY else 0f,
                                onSelect = {
                                    onAction(TimerAction.SetDurationExact(preset.durationMillis))
                                    onAction(TimerAction.SetActiveTimerName(preset.name))
                                    onAction(TimerAction.SetActivePresetId(preset.id))
                                    onDismiss()
                                },
                                onRename = { presetToRename = preset },
                                onChangeDuration = { presetToChangeDuration = preset },
                                onEditSettings = { presetToEditSettings = preset },
                                onMoveToFolder = { presetToMoveToFolder = preset },
                                onDelete = {
                                    scope.launch {
                                        dao.deletePreset(preset)
                                        AutoBackupManager.scheduleBackup(context)
                                    }
                                },
                                onDragStart = {
                                    draggedPresetId = preset.id
                                    dragFolderId = null
                                    dragOffsetY = 0f
                                },
                                onDrag = { delta ->
                                    dragOffsetY += delta
                                    val list = presetLists[null]
                                    val idx = list?.indexOfFirst { it.id == draggedPresetId } ?: -1
                                    if (list != null && idx >= 0) {
                                        when {
                                            dragOffsetY > itemHeightPx / 2 && idx < list.size - 1 -> {
                                                list.add(idx + 1, list.removeAt(idx))
                                                dragOffsetY -= itemHeightPx
                                            }
                                            dragOffsetY < -itemHeightPx / 2 && idx > 0 -> {
                                                list.add(idx - 1, list.removeAt(idx))
                                                dragOffsetY += itemHeightPx
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val list = presetLists[null]
                                    if (list != null) {
                                        scope.launch {
                                            list.forEachIndexed { index, p ->
                                                dao.updatePreset(p.copy(sortOrder = index))
                                            }
                                            AutoBackupManager.scheduleBackup(context)
                                        }
                                    }
                                    draggedPresetId = null
                                    dragOffsetY = 0f
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(
    label: String,
    editMode: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddPreset: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode) {
            DeleteButton(onClick = onDelete)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        onAddPreset?.let { addFn ->
            TextButton(onClick = addFn) {
                Text(stringResource(R.string.preset_add_short))
            }
        }
        if (editMode) {
            TextButton(onClick = onRename) {
                Text(stringResource(R.string.rename))
            }
        }
    }
}

@Composable
private fun FolderHeader(
    folder: PresetFolderEntity,
    editMode: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddPreset: (() -> Unit)? = null,
) {
    FolderHeader(
        label = folder.name,
        editMode = editMode,
        onRename = onRename,
        onDelete = onDelete,
        onAddPreset = onAddPreset,
    )
}

@Composable
private fun PresetRowDraggable(
    preset: PresetEntity,
    editMode: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onChangeDuration: () -> Unit,
    onEditSettings: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = if (editMode) ({}) else onSelect,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                if (isDragged) {
                    shadowElevation = 8f
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editMode) {
                DeleteButton(onClick = onDelete)
                Spacer(modifier = Modifier.width(8.dp))
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "⠿",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (editMode) {
                Box {
                    Surface(
                        onClick = { showMenu = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "⚙",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { showMenu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.preset_change_duration)) },
                            onClick = { showMenu = false; onChangeDuration() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.preset_move_to_folder)) },
                            onClick = { showMenu = false; onMoveToFolder() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = { showMenu = false; onEditSettings() },
                        )
                    }
                }
            } else {
                Text(
                    text = preset.durationMillis.formatClockTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "−",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onError,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSettingsSheet(
    preset: PresetEntity,
    onSave: (PresetEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(preset.id) { mutableStateOf(preset) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun saveAndDismiss() {
        onSave(current)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::saveAndDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.preset_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = ::saveAndDismiss) {
                    Text(stringResource(R.string.done))
                }
            }

            HorizontalDivider()

            // Sound
            PresetSettingsSectionLabel(stringResource(R.string.sound))
            NullableBoolRow(
                label = stringResource(R.string.sound),
                value = current.soundEnabled,
                onChange = { current = current.copy(soundEnabled = it) },
            )
            if (current.soundEnabled == true) {
                NullableVolumeRow(
                    value = current.finishedSoundVolumePercent,
                    onChange = { current = current.copy(finishedSoundVolumePercent = it) },
                )
                NullableBoolRow(
                    label = stringResource(R.string.ignore_silent_mode),
                    value = current.ignoreSilentMode,
                    onChange = { current = current.copy(ignoreSilentMode = it) },
                )
                NullableBoolRow(
                    label = stringResource(R.string.override_muted_system_volume),
                    value = current.overrideSystemVolume,
                    onChange = { current = current.copy(overrideSystemVolume = it) },
                )
            }

            HorizontalDivider()

            // Vibration
            PresetSettingsSectionLabel(stringResource(R.string.vibrate))
            NullableVibrationSelector(
                value = current.finishedVibrationModeName,
                onChange = { current = current.copy(finishedVibrationModeName = it) },
            )

            HorizontalDivider()

            // Display
            PresetSettingsSectionLabel(stringResource(R.string.preset_settings_display))
            NullableBoolRow(
                label = stringResource(R.string.keep_screen_awake),
                value = current.keepScreenAwake,
                onChange = { current = current.copy(keepScreenAwake = it) },
            )
            NullableBoolRow(
                label = stringResource(R.string.show_current_time),
                value = current.showCurrentTime,
                onChange = { current = current.copy(showCurrentTime = it) },
            )
            if (current.showCurrentTime == true) {
                NullableBoolRow(
                    label = stringResource(R.string.show_seconds),
                    value = current.showSeconds,
                    onChange = { current = current.copy(showSeconds = it) },
                )
                NullableClockPositionSelector(
                    value = current.clockPositionName,
                    onChange = { current = current.copy(clockPositionName = it) },
                )
                NullableClockSizeSelector(
                    value = current.clockSizeName,
                    onChange = { current = current.copy(clockSizeName = it) },
                )
            }
        }
    }
}

@Composable
private fun PresetSettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun NullableBoolRow(
    label: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderChip(
                label = stringResource(R.string.preset_settings_default),
                selected = value == null,
                onClick = { onChange(null) },
            )
            FolderChip(
                label = stringResource(R.string.preset_settings_off),
                selected = value == false,
                onClick = { onChange(false) },
            )
            FolderChip(
                label = stringResource(R.string.preset_settings_on),
                selected = value == true,
                onClick = { onChange(true) },
            )
        }
    }
}

@Composable
private fun NullableVolumeRow(
    value: Int?,
    onChange: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (value != null)
                    stringResource(R.string.finished_sound_volume, value.coerceIn(0, 100))
                else
                    stringResource(R.string.preset_settings_volume_default),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = value != null,
                onCheckedChange = { if (it) onChange(100) else onChange(null) },
            )
        }
        if (value != null) {
            Slider(
                value = value.coerceIn(0, 100).toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
            )
        }
    }
}

@Composable
private fun NullableVibrationSelector(
    value: String?,
    onChange: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.finished_vibration),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderChip(
                label = stringResource(R.string.preset_settings_default),
                selected = value == null,
                onClick = { onChange(null) },
            )
            FolderChip(
                label = stringResource(R.string.finished_vibration_off),
                selected = value == FinishedVibrationMode.Off.name,
                onClick = { onChange(FinishedVibrationMode.Off.name) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderChip(
                label = stringResource(R.string.finished_vibration_1m),
                selected = value == FinishedVibrationMode.OneMinute.name,
                onClick = { onChange(FinishedVibrationMode.OneMinute.name) },
            )
            FolderChip(
                label = stringResource(R.string.finished_vibration_2m),
                selected = value == FinishedVibrationMode.TwoMinutes.name,
                onClick = { onChange(FinishedVibrationMode.TwoMinutes.name) },
            )
            FolderChip(
                label = stringResource(R.string.finished_vibration_5m),
                selected = value == FinishedVibrationMode.FiveMinutes.name,
                onClick = { onChange(FinishedVibrationMode.FiveMinutes.name) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderChip(
                label = stringResource(R.string.finished_vibration_10m),
                selected = value == FinishedVibrationMode.TenMinutes.name,
                onClick = { onChange(FinishedVibrationMode.TenMinutes.name) },
            )
            FolderChip(
                label = stringResource(R.string.finished_vibration_forever),
                selected = value == FinishedVibrationMode.Forever.name,
                onClick = { onChange(FinishedVibrationMode.Forever.name) },
            )
        }
    }
}

@Composable
private fun NullableClockPositionSelector(
    value: String?,
    onChange: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.clock_position),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderChip(
                label = stringResource(R.string.preset_settings_default),
                selected = value == null,
                onClick = { onChange(null) },
            )
            FolderChip(
                label = stringResource(R.string.clock_left),
                selected = value == ClockPosition.Left.name,
                onClick = { onChange(ClockPosition.Left.name) },
            )
            FolderChip(
                label = stringResource(R.string.clock_center),
                selected = value == ClockPosition.Center.name,
                onClick = { onChange(ClockPosition.Center.name) },
            )
            FolderChip(
                label = stringResource(R.string.clock_right),
                selected = value == ClockPosition.Right.name,
                onClick = { onChange(ClockPosition.Right.name) },
            )
        }
    }
}

@Composable
private fun NullableClockSizeSelector(
    value: String?,
    onChange: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.clock_size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderChip(
                label = stringResource(R.string.preset_settings_default),
                selected = value == null,
                onClick = { onChange(null) },
            )
            FolderChip(
                label = stringResource(R.string.clock_size_small),
                selected = value == "Small",
                onClick = { onChange("Small") },
            )
            FolderChip(
                label = stringResource(R.string.clock_size_medium),
                selected = value == "Medium",
                onClick = { onChange("Medium") },
            )
            FolderChip(
                label = stringResource(R.string.clock_size_large),
                selected = value == "Large",
                onClick = { onChange("Large") },
            )
        }
    }
}

@Composable
private fun AddPresetDialog(
    initialName: String,
    initialMillis: Long,
    folders: List<PresetFolderEntity>,
    selectedFolderId: Long?,
    onFolderSelected: (Long?) -> Unit,
    onPickDuration: (currentName: String) -> Unit,
    onConfirm: (name: String, millis: Long, folderId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var millis by remember(initialMillis) { mutableStateOf(initialMillis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    onClick = { onPickDuration(name) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.preset_duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = millis.formatClockTime(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (folders.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.preset_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FolderChip(
                            label = stringResource(R.string.folder_uncategorized),
                            selected = selectedFolderId == null,
                            onClick = { onFolderSelected(null) },
                        )
                    }
                    folders.chunked(2).forEach { rowFolders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowFolders.forEach { folder ->
                                FolderChip(
                                    label = folder.name,
                                    selected = selectedFolderId == folder.id,
                                    onClick = { onFolderSelected(folder.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, millis, selectedFolderId) },
                enabled = name.isNotBlank() && millis > 0L,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoveFolderDialog(
    preset: PresetEntity,
    folders: List<PresetFolderEntity>,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedFolderId by remember { mutableStateOf(preset.folderId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_move_to_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderChip(
                        label = stringResource(R.string.folder_uncategorized),
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                    )
                }
                folders.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { folder ->
                            FolderChip(
                                label = folder.name,
                                selected = selectedFolderId == folder.id,
                                onClick = { selectedFolderId = folder.id },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedFolderId) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RenameDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onConfirm(value) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
