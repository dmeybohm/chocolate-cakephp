package com.daveme.chocolateCakePHP

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks modifications to composer.json and config/app.php files in the project root.
 *
 * This is used to invalidate the CakePHP autodetection cache when these files are
 * created or deleted, even if they didn't exist when the cache was first created.
 */
@Service(Service.Level.PROJECT)
class CakePhpFilesModificationTracker(private val project: Project) : ModificationTracker {

    private val modificationCount = AtomicLong(0)

    override fun getModificationCount(): Long = modificationCount.get()

    init {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val projectDir = project.guessProjectDir() ?: return
                val projectPath = projectDir.path

                for (e in events) {
                    if (affectsTarget(e, projectPath)) {
                        modificationCount.incrementAndGet()
                        break
                    }
                }
            }
        })
    }

    private fun affectsTarget(e: VFileEvent, projectPath: String): Boolean {
        // Handle create/delete by path
        when (e) {
            is VFileCreateEvent, is VFileDeleteEvent -> {
                val p = e.path
                return p == "$projectPath/composer.json" ||
                        p == "$projectPath/config/app.php"
            }
        }

        // For events with a file object (content change, rename, move)
        val file = when (e) {
            is VFileContentChangeEvent -> e.file
            is VFilePropertyChangeEvent -> if (e.isRename) e.file else e.file // rename or other prop
            is VFileMoveEvent -> e.file
            else -> null
        } ?: return false

        val path = file.path
        if (path == "$projectPath/composer.json" || path == "$projectPath/config/app.php") {
            return true
        }

        // Also handle rename/move where the *new* path becomes the target
        if (e is VFilePropertyChangeEvent && e.isRename) {
            val newName = e.newValue as? String ?: return false
            val parentPath = file.parent?.path ?: return false
            val newPath = "$parentPath/$newName"
            return newPath == "$projectPath/composer.json" ||
                    newPath == "$projectPath/config/app.php"
        }
        if (e is VFileMoveEvent) {
            val newPath = e.newParent.path + "/" + e.file.name
            return newPath == "$projectPath/composer.json" ||
                    newPath == "$projectPath/config/app.php"
        }

        return false
    }


}
