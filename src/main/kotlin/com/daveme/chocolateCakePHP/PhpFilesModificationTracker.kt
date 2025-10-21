package com.daveme.chocolateCakePHP

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Tracks modifications to PHP files in the project.
 *
 * This is used to invalidate caches that depend on PHP file contents,
 * such as the view variable cache which depends on controller files.
 *
 * The tracker uses lazy initialization - it only subscribes to VFS changes when
 * first needed, and only if the plugin is enabled for the project. This ensures
 * zero overhead for non-CakePHP projects.
 */
@Service(Service.Level.PROJECT)
class PhpFilesModificationTracker(private val project: Project) : SimpleModificationTracker(), Disposable {

    private val _isArmed: Boolean by lazy {
        val settings = Settings.getInstance(project)
        if (!settings.enabled) {
            // Not a CakePHP project - don't arm
            false
        } else {
            // CakePHP project detected - arm the VFS listener
            val connection = project.messageBus.connect(this)
            connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    for (e in events) {
                        if (affectsPhpFile(e)) {
                            incModificationCount()
                            break
                        }
                    }
                }
            })
            true
        }
    }

    /**
     * Returns true if the tracker is armed and actively tracking PHP file changes.
     * Returns false if the plugin is not enabled for this project.
     */
    val isArmed: Boolean
        get() = _isArmed

    /**
     * Ensures the tracker is armed if this is a CakePHP project.
     * This triggers lazy initialization which checks settings.enabled and
     * subscribes to VFS changes if appropriate. Safe to call multiple times.
     */
    fun ensureArmed() {
        // Access the property to trigger lazy initialization
        _isArmed
    }

    private fun affectsPhpFile(e: VFileEvent): Boolean {
        // Handle create/delete by path
        when (e) {
            is VFileCreateEvent, is VFileDeleteEvent -> {
                return e.path.endsWith(".php")
            }
        }

        // For events with a file object (content change, rename, move)
        val file = when (e) {
            is VFileContentChangeEvent -> e.file
            is VFilePropertyChangeEvent -> e.file
            is VFileMoveEvent -> e.file
            else -> null
        } ?: return false

        // Check if the file is a PHP file
        if (file.extension == "php") {
            return true
        }

        // Also handle rename where the file becomes a PHP file
        if (e is VFilePropertyChangeEvent && e.isRename) {
            val newName = e.newValue as? String ?: return false
            return newName.endsWith(".php")
        }

        return false
    }

    override fun dispose() {
        // Connection is automatically disposed via parent disposable
    }
}
