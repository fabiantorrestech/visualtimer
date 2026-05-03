package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.db.AppDao
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.PresetEntity
import com.fabiantorrestech.visualtimerplus.db.PresetFolderEntity
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.clampDuration
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsSheet(
    db: AppDatabase,
    currentDurationMillis: Long,
    onAction: (TimerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val dao = db.appDao()

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
    var showDurationPicker by remember { mutableStateOf(false) }
    var pickerInitialMillis by remember { mutableStateOf(currentDurationMillis) }
    var pendingPresetName by remember { mutableStateOf("") }
    var pendingPresetFolder by remember { mutableStateOf<Long?>(null) }
    var pendingPresetMillis by remember { mutableStateOf(currentDurationMillis) }

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
                scope.launch { dao.updatePreset(preset.copy(name = newName.trim())) }
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
                scope.launch { dao.updateFolder(folder.copy(name = newName.trim())) }
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
                scope.launch { dao.insertFolder(PresetFolderEntity(name = name.trim())) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    // Folders + their presets
                    folders.forEach { folder ->
                        val folderPresets = displayPresets.filter { it.folderId == folder.id }
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
                                        }
                                    },
                                )
                            }
                            items(folderPresets, key = { "preset_${it.id}" }) { preset ->
                                PresetRow(
                                    preset = preset,
                                    editMode = editMode,
                                    onSelect = {
                                        onAction(TimerAction.SetDurationExact(preset.durationMillis))
                                        onAction(TimerAction.SetActiveTimerName(preset.name))
                                        onAction(TimerAction.SetActivePresetId(preset.id))
                                        onDismiss()
                                    },
                                    onRename = { presetToRename = preset },
                                    onDelete = { scope.launch { dao.deletePreset(preset) } },
                                )
                            }
                        }
                    }

                    // Uncategorized
                    val uncategorized = displayPresets.filter { it.folderId == null }
                    if (uncategorized.isNotEmpty()) {
                        item(key = "folder_uncategorized") {
                            FolderHeader(
                                label = stringResource(R.string.folder_uncategorized),
                                editMode = false,
                                onRename = {},
                                onDelete = {},
                            )
                        }
                        items(uncategorized, key = { "preset_${it.id}" }) { preset ->
                            PresetRow(
                                preset = preset,
                                editMode = editMode,
                                onSelect = {
                                    onAction(TimerAction.SetDurationExact(preset.durationMillis))
                                    onAction(TimerAction.SetActiveTimerName(preset.name))
                                    onAction(TimerAction.SetActivePresetId(preset.id))
                                    onDismiss()
                                },
                                onRename = { presetToRename = preset },
                                onDelete = { scope.launch { dao.deletePreset(preset) } },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Add preset button
            TextButton(
                onClick = {
                    pendingPresetMillis = currentDurationMillis
                    pendingPresetName = ""
                    pendingPresetFolder = null
                    showAddPresetDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.preset_add),
                    style = MaterialTheme.typography.labelLarge,
                )
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
) {
    FolderHeader(
        label = folder.name,
        editMode = editMode,
        onRename = onRename,
        onDelete = onDelete,
    )
}

@Composable
private fun PresetRow(
    preset: PresetEntity,
    editMode: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = if (editMode) onRename else onSelect,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editMode) {
                DeleteButton(onClick = onDelete)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.durationMillis.formatClockTime(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
