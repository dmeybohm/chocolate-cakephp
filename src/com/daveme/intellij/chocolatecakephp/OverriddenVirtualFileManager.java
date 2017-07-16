package com.daveme.intellij.chocolateCakePHP;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OverriddenVirtualFileManager extends VirtualFileManager {

    private VirtualFileManager proxy;

    public OverriddenVirtualFileManager(VirtualFileManager proxy) {
        System.out.println("OveriddenFileManager(): proxy: "+proxy);
        this.proxy = proxy;
    }

    @Override
    public VirtualFileSystem getFileSystem(String s) {
        return proxy.getFileSystem(s);
    }

    @Override
    public long syncRefresh() {
        return proxy.syncRefresh();
    }

    @Override
    public long asyncRefresh(@Nullable Runnable runnable) {
        return proxy.asyncRefresh(runnable);
    }

    @Override
    public void refreshWithoutFileWatcher(boolean b) {
        proxy.refreshWithoutFileWatcher(b);
    }

    @Nullable
    @Override
    public VirtualFile findFileByUrl(@NonNls @NotNull String s) {
        System.out.println("findFileByUrl: "+s);
        return proxy.findFileByUrl(s);
    }

    @Nullable
    @Override
    public VirtualFile refreshAndFindFileByUrl(@NotNull String s) {
        return proxy.refreshAndFindFileByUrl(s);
    }

    @Override
    public void addVirtualFileListener(@NotNull VirtualFileListener virtualFileListener) {
        proxy.addVirtualFileListener(virtualFileListener);
    }

    @Override
    public void addVirtualFileListener(@NotNull VirtualFileListener virtualFileListener, @NotNull Disposable disposable) {
        proxy.addVirtualFileListener(virtualFileListener, disposable);
    }

    @Override
    public void removeVirtualFileListener(@NotNull VirtualFileListener virtualFileListener) {
        proxy.removeVirtualFileListener(virtualFileListener);
    }

    @Override
    public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener virtualFileManagerListener) {
        proxy.addVirtualFileManagerListener(virtualFileManagerListener);
    }

    @Override
    public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener virtualFileManagerListener, @NotNull Disposable disposable) {
        proxy.addVirtualFileManagerListener(virtualFileManagerListener, disposable);
    }

    @Override
    public void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener virtualFileManagerListener) {
        proxy.removeVirtualFileManagerListener(virtualFileManagerListener);
    }

    @Override
    public void notifyPropertyChanged(@NotNull VirtualFile virtualFile, @NotNull String s, Object o, Object o1) {
        proxy.notifyPropertyChanged(virtualFile, s, o, o1);
    }

    @Override
    public long getModificationCount() {
        return proxy.getModificationCount();
    }

    @Override
    public long getStructureModificationCount() {
        return proxy.getStructureModificationCount();
    }
}
