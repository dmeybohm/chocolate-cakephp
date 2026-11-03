package com.daveme.chocolateCakePHP

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
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
 * Tracks modifications to the configured composer.json and the config/app.php beside it.
 *
 * This is used to invalidate the CakePHP autodetection cache when these files are
 * created or deleted, even if they didn't exist when the cache was first created.
 */
@Service(Service.Level.PROJECT)
class CakePhpFilesModificationTracker(private val project: Project) : SimpleModificationTracker(), Disposable {

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val targets = targetPaths() ?: return
                for (e in events) {
                    if (affectsTarget(e, targets)) {
                        incModificationCount()
                        break
                    }
                }
            }
        })
    }

    private fun targetPaths(): List<String>? {
        val projectPath = project.guessProjectDir()?.path ?: return null
        val composerJsonPath = Settings.getInstance(project).composerJsonPath
            .split('/').filter { it.isNotEmpty() }.joinToString("/")
        val rootPath = listOf(projectPath, composerJsonPath.substringBeforeLast('/', ""))
            .filter { it.isNotEmpty() }.joinToString("/")
        return listOf("$projectPath/$composerJsonPath", "$rootPath/config/app.php")
    }

    private fun affectsTarget(e: VFileEvent, targets: List<String>): Boolean {
        fun isTargetOrAncestor(path: String) = targets.any { it == path || it.startsWith("$path/") }

        // Handle create/delete by path, including a directory holding a target
        when (e) {
            is VFileCreateEvent, is VFileDeleteEvent -> return isTargetOrAncestor(e.path)
        }

        // For events with a file object (content change, rename, move)
        val file = when (e) {
            is VFileContentChangeEvent -> e.file
            is VFilePropertyChangeEvent -> e.file
            is VFileMoveEvent -> e.file
            else -> null
        } ?: return false

        if (isTargetOrAncestor(file.path)) {
            return true
        }

        // Also handle rename/move where the *new* path becomes the target
        if (e is VFilePropertyChangeEvent && e.isRename) {
            val newName = e.newValue as? String ?: return false
            val parentPath = file.parent?.path ?: return false
            return isTargetOrAncestor("$parentPath/$newName")
        }
        if (e is VFileMoveEvent) {
            return isTargetOrAncestor(e.newParent.path + "/" + e.file.name)
        }

        return false
    }

    override fun dispose() {
        // Connection is automatically disposed via parent disposable
    }
}
