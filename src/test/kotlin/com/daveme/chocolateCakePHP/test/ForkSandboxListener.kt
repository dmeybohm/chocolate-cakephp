package com.daveme.chocolateCakePHP.test

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Gives each Gradle test fork its own IDE sandbox so that `maxParallelForks > 1`
 * actually runs in parallel.
 *
 * The IntelliJ Platform Gradle Plugin passes the same `-Didea.system.path`,
 * `-Didea.config.path` and `-Didea.log.path` to every fork. The system path
 * holds the VFS caches and indexes, and concurrent platforms sharing it
 * contend on (or corrupt and rebuild) that storage. This listener runs before
 * any test class is loaded, so `PathManager` has not yet cached the values,
 * and redirects those three paths to per-fork sibling directories
 * (`system-test-fork-0`, `system-test-fork-1`, ...).
 *
 * Slots are claimed with an OS file lock held for the life of the JVM rather
 * than keyed by Gradle's worker id, because worker ids are not stable across
 * builds. Stable slots mean each fork reuses a warm index from the previous run.
 *
 * `idea.plugins.path` is read only and stays shared.
 *
 * Registered through `META-INF/services/org.junit.platform.launcher.LauncherSessionListener`.
 * This class must not reference any `com.intellij` class.
 */
class ForkSandboxListener : LauncherSessionListener {

    override fun launcherSessionOpened(session: LauncherSession) {
        // Set by Gradle in every test worker JVM; absent when run from the IDE.
        System.getProperty("org.gradle.test.worker") ?: return

        val systemDir = System.getProperty("idea.system.path")?.let(::File) ?: return
        val slot = claimSlot(systemDir.parentFile)

        redirect("idea.system.path", slot)
        redirect("idea.log.path", slot)
        redirect("idea.config.path", slot) { shared, fork ->
            // prepareTest writes bundled_plugins.txt, disabled_plugins.txt and
            // options/ into the shared config dir; the platform reads them at
            // startup, so each fork needs a copy.
            if (shared.isDirectory) {
                shared.copyRecursively(fork, overwrite = true)
            }
        }
    }

    private fun claimSlot(sandboxRoot: File): Int {
        sandboxRoot.mkdirs()
        for (slot in 0 until MAX_SLOTS) {
            val lockFile = File(sandboxRoot, "fork-$slot.lock")
            val channel = RandomAccessFile(lockFile, "rw").channel
            // A null lock or an overlapping lock means another fork holds the
            // slot. Any other failure means locking itself is broken here, and
            // is reported rather than treated as an occupied slot.
            val lock: FileLock? = try {
                channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            } catch (e: IOException) {
                channel.close()
                throw IllegalStateException("Could not lock $lockFile for the test fork sandbox", e)
            }
            if (lock != null) {
                heldLock = lock // keep the lock (and channel) alive until the JVM exits
                return slot
            }
            channel.close()
        }
        throw IllegalStateException(
            "No free test fork sandbox slot under $sandboxRoot (all $MAX_SLOTS are locked). " +
                "Run with -PtestForks=1 or stop the other test JVMs using this build directory."
        )
    }

    private fun redirect(
        property: String,
        slot: Int,
        seed: (shared: File, fork: File) -> Unit = { _, _ -> }
    ) {
        val shared = System.getProperty(property)?.let(::File) ?: return
        val fork = File(shared.parentFile, "${shared.name}-fork-$slot")
        fork.mkdirs()
        seed(shared, fork)
        System.setProperty(property, fork.absolutePath)
    }

    companion object {
        private const val MAX_SLOTS = 64

        @Suppress("unused")
        @Volatile
        private var heldLock: FileLock? = null
    }
}
