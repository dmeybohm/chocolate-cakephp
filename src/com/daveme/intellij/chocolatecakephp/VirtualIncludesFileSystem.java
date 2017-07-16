package com.daveme.intellij.chocolateCakePHP;

import com.daveme.intellij.chocolateCakePHP.library.LibraryOrderEntry;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

public class VirtualIncludesFileSystem implements FileSystemInterface, ApplicationComponent {
    private static boolean overidden = false;

    @Override
    public void initComponent() {
        LibraryOrderEntry orderEntry = new LibraryOrderEntry();
        System.out.println("VirtualIncludesFileSystem.initComponent()");
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : openProjects) {

        }
//        ExternalLibrariesNode.addLibraryChildren(orderEntry, list, project, projectViewNode);
    }

    public static void overrideFileManager() {
        if (overidden) {
            return;
        }
        VirtualIncludesFileSystem.overidden = true;
        Application application = ApplicationManager.getApplication();
        MutablePicoContainer picoContainer = (MutablePicoContainer) application.getPicoContainer();
        VirtualFileManager virtualFileManager = application.getComponent(VirtualFileManager.class);

        picoContainer.unregisterComponent(VirtualFileManager.class);
        picoContainer.registerComponentInstance(VirtualFileManager.class, new OverriddenVirtualFileManager(virtualFileManager));
    }

    @Override
    public void disposeComponent() {
        System.out.println("VirtualIncludesFileSystem.disposeComponent()");
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "ChocolateCakePHP.VirtualIncludesFileSystem";
    }

    @Override
    public boolean exists(@NotNull VirtualFile virtualFile) {
        return false;
    }

    @NotNull
    @Override
    public String[] list(@NotNull VirtualFile virtualFile) {
        return new String[0];
    }

    @Override
    public boolean isDirectory(@NotNull VirtualFile virtualFile) {
        return false;
    }

    @Override
    public long getTimeStamp(@NotNull VirtualFile virtualFile) {
        return 0;
    }

    @Override
    public void setTimeStamp(@NotNull VirtualFile virtualFile, long l) throws IOException {

    }

    @Override
    public boolean isWritable(@NotNull VirtualFile virtualFile) {
        return false;
    }

    @Override
    public void setWritable(@NotNull VirtualFile virtualFile, boolean b) throws IOException {

    }

    @Override
    public boolean isSymLink(@NotNull VirtualFile virtualFile) {
        return false;
    }

    @Nullable
    @Override
    public String resolveSymLink(@NotNull VirtualFile virtualFile) {
        return null;
    }

    @NotNull
    @Override
    public VirtualFile createChildDirectory(@Nullable Object o, @NotNull VirtualFile virtualFile, @NotNull String s) throws IOException {
        return null;
    }

    @NotNull
    @Override
    public VirtualFile createChildFile(@Nullable Object o, @NotNull VirtualFile virtualFile, @NotNull String s) throws IOException {
        return null;
    }

    @Override
    public void deleteFile(Object o, @NotNull VirtualFile virtualFile) throws IOException {

    }

    @Override
    public void moveFile(Object o, @NotNull VirtualFile virtualFile, @NotNull VirtualFile virtualFile1) throws IOException {

    }

    @Override
    public void renameFile(Object o, @NotNull VirtualFile virtualFile, @NotNull String s) throws IOException {

    }

    @NotNull
    @Override
    public VirtualFile copyFile(Object o, @NotNull VirtualFile virtualFile, @NotNull VirtualFile virtualFile1, @NotNull String s) throws IOException {
        return null;
    }

    @NotNull
    @Override
    public byte[] contentsToByteArray(@NotNull VirtualFile virtualFile) throws IOException {
        return new byte[0];
    }

    @NotNull
    @Override
    public InputStream getInputStream(@NotNull VirtualFile virtualFile) throws IOException {
        return null;
    }

    @NotNull
    @Override
    public OutputStream getOutputStream(@NotNull VirtualFile virtualFile, Object o, long l, long l1) throws IOException {
        return null;
    }

    @Override
    public long getLength(@NotNull VirtualFile virtualFile) {
        return 0;
    }
}
