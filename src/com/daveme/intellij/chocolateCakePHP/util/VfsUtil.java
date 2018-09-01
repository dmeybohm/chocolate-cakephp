package com.daveme.intellij.chocolateCakePHP.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VfsUtil {

    @Nullable
    public static VirtualFile findRelativeFile(@Nullable PsiDirectory dir, @NotNull String childPath) {
        if (dir == null) {
            return null;
        }
        return com.intellij.openapi.vfs.VfsUtil.findRelativeFile(dir.getVirtualFile(), childPath.split("/"));
    }
}
