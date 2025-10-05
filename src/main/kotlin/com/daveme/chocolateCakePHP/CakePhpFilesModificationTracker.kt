package com.daveme.chocolateCakePHP

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
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

    init {
        // Subscribe to VFS changes to detect when composer.json or config/app.php are created/deleted
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val projectDir = project.guessProjectDir() ?: return
                val projectPath = projectDir.path

                for (event in events) {
                    if (isCakePhpConfigFile(event, projectPath)) {
                        modificationCount.incrementAndGet()
                        break
                    }
                }
            }
        })
    }

    /**
     * Checks if the event is for composer.json or config/app.php in the project root.
     */
    private fun isCakePhpConfigFile(event: VFileEvent, projectPath: String): Boolean {
        if (event !is VFileCreateEvent && event !is VFileDeleteEvent) {
            return false
        }

        val path = event.path
        return path == "$projectPath/composer.json" ||
               path == "$projectPath/config/app.php"
    }

    override fun getModificationCount(): Long {
        return modificationCount.get()
    }
}
