package com.daveme.chocolateCakePHP.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import java.io.File

/**
 * A light project with the real CakePHP framework source attached as a library.
 */
class RealCakeVendorProjectDescriptor(private val cakeMajorVersion: Int) : LightProjectDescriptor() {

    val vendorPath: String
        get() = System.getProperty("chocolateCakePHP.vendor.cake$cakeMajorVersion")
            ?: File("build/cakephp-vendor/$cakeMajorVersion").absolutePath

    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(vendorPath)
            ?: error("CakePHP vendor directory not found: $vendorPath; run ./gradlew fetchCake${cakeMajorVersion}Vendor")
        val library = model.moduleLibraryTable.modifiableModel.createLibrary("cakephp/cakephp")
        library.modifiableModel.apply {
            addRoot(root, OrderRootType.CLASSES)
            addRoot(root, OrderRootType.SOURCES)
            commit()
        }
    }

    override fun equals(other: Any?): Boolean =
        other is RealCakeVendorProjectDescriptor && other.cakeMajorVersion == cakeMajorVersion

    override fun hashCode(): Int = cakeMajorVersion

    companion object {
        private val descriptors = mutableMapOf<Int, RealCakeVendorProjectDescriptor>()

        fun forVersion(cakeMajorVersion: Int): RealCakeVendorProjectDescriptor =
            descriptors.getOrPut(cakeMajorVersion) { RealCakeVendorProjectDescriptor(cakeMajorVersion) }
    }
}
