package com.powersync.demos.powersync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.androidexample.ui.CameraService
import com.powersync.attachments.AttachmentQueue
import com.powersync.db.getLongOptional
import com.powersync.db.getString
import com.powersync.db.getStringOptional
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class Todo(
    private val db: PowerSyncDatabase,
    private val attachmentsQueue: AttachmentQueue?,
    private val userId: String?,
) : ViewModel() {
    private val _inputText = MutableStateFlow<String>("")
    val inputText: StateFlow<String> = _inputText

    private val _editingItem = MutableStateFlow<TodoItem?>(null)
    val editingItem: StateFlow<TodoItem?> = _editingItem

    fun watchItems(listId: String?): Flow<List<TodoItem>> =
        db.watch<TodoItem>(
            """
                SELECT 
                    t.*, a.local_uri
                FROM
                    $TODOS_TABLE t
                    LEFT JOIN attachments a ON t.photo_id = a.id
                WHERE 
                    t.list_id = ?
                ORDER BY t.id;
            """,
            if (listId != null) listOf(listId) else null,
        ) { cursor ->
            TodoItem(
                id = cursor.getString("id"),
                createdAt = cursor.getStringOptional("created_at"),
                completedAt = cursor.getStringOptional("completed_at"),
                description = cursor.getString("description"),
                createdBy = cursor.getStringOptional("created_by"),
                completedBy = cursor.getStringOptional("completed_by"),
                completed = cursor.getLongOptional("completed") == 1L,
                listId = cursor.getString("list_id"),
                photoId = cursor.getStringOptional("photo_id"),
                photoURI = cursor.getStringOptional("local_uri"),
            )
        }

    fun onItemClicked(item: TodoItem) {
        _editingItem.value = item
    }

    fun onItemDoneChanged(
        item: TodoItem,
        isDone: Boolean,
    ) {
        updateItem(item = item) {
            it.copy(
                completed = isDone,
                completedBy = if (isDone) userId else null,
                completedAt = if (isDone) Clock.System.now().toString() else null,
            )
        }
    }

    fun onItemDeleteClicked(item: TodoItem) {
        viewModelScope.launch {
            if (item.photoId != null) {
                attachmentsQueue?.deleteFile(item.photoId) { _, _ -> }
            }
            db.writeTransaction { tx ->
                tx.execute("DELETE FROM $TODOS_TABLE WHERE id = ?", listOf(item.id))
            }
        }
    }

    fun onAddItemClicked(
        userId: String?,
        listId: String?,
    ) {
        if (_inputText.value.isBlank()) return

        if (userId == null || listId == null) {
            throw Exception("userId or listId is null")
        }

        viewModelScope.launch {
            db.writeTransaction { tx ->
                tx.execute(
                    "INSERT INTO $TODOS_TABLE (id, created_at, created_by, description, list_id) VALUES (uuid(), datetime(), ?, ?, ?)",
                    listOf(userId, _inputText.value, listId),
                )
            }
            _inputText.value = ""
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onEditorCloseClicked() {
        updateItem(item = requireNotNull(_editingItem.value)) { it.copy() }
        _editingItem.value = null
    }

    fun onEditorTextChanged(text: String) {
        updateEditingItem(item = requireNotNull(_editingItem.value)) {
            it.copy(description = text)
        }
    }

    fun onEditorDoneChanged(isDone: Boolean) {
        updateEditingItem(item = requireNotNull(_editingItem.value)) {
            it.copy(
                completed = isDone,
                completedBy = if (isDone) userId else null,
                completedAt = if (isDone) Clock.System.now().toString() else null,
            )
        }
    }

    fun onPhotoCapture(cameraService: CameraService) {
        viewModelScope.launch {
            val item = requireNotNull(_editingItem.value)
            val photoData =
                try {
                    cameraService.takePicture()
                } catch (ex: Exception) {
                    if (ex is CancellationException) {
                        throw ex
                    } else {
                        // otherwise ignore
                        return@launch
                    }
                }
            val attachment =
                attachmentsQueue!!.saveFile(data = flowOf(photoData), mediaType = "image/jped", fileExtension = "jpg") { tx, attachment ->
                    tx.execute("UPDATE $TODOS_TABLE SET photo_id = ? WHERE id = ?", listOf(attachment.id, item.id))
                }

            updateEditingItem(item = item) { it.copy(photoURI = attachment.localUri) }
        }
    }

    fun onPhotoDelete() {
        viewModelScope.launch {
            val item = requireNotNull(_editingItem.value)
            attachmentsQueue!!.deleteFile(item.photoId!!) { tx, _ ->
                tx.execute("UPDATE $TODOS_TABLE SET photo_id = NULL WHERE id = ?", listOf(item.id))
            }
            updateEditingItem(item = item) { it.copy(photoURI = null) }
        }
    }

    private fun updateEditingItem(
        item: TodoItem,
        transformer: (item: TodoItem) -> TodoItem,
    ) {
        _editingItem.value = transformer(item)
    }

    private fun updateItem(
        item: TodoItem,
        transformer: (item: TodoItem) -> TodoItem,
    ) {
        viewModelScope.launch {
            val updatedItem = transformer(item)
            Logger.i("Updating item: $updatedItem")
            db.writeTransaction { tx ->
                tx.execute(
                    "UPDATE $TODOS_TABLE SET description = ?, completed = ?, completed_by = ?, completed_at = ? WHERE id = ?",
                    listOf(
                        updatedItem.description,
                        updatedItem.completed,
                        updatedItem.completedBy,
                        updatedItem.completedAt,
                        item.id,
                    ),
                )
            }
        }
    }
}
