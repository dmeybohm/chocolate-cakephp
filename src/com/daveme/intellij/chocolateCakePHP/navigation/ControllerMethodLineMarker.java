package com.daveme.intellij.chocolateCakePHP.navigation;

import com.daveme.intellij.chocolateCakePHP.icons.CakeIcons;
import com.daveme.intellij.chocolateCakePHP.util.CakeUtil;
import com.daveme.intellij.chocolateCakePHP.util.PsiUtil;
import com.daveme.intellij.chocolateCakePHP.util.StringUtil;
import com.daveme.intellij.chocolateCakePHP.util.VfsUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class ControllerMethodLineMarker implements LineMarkerProvider {
    @Nullable
    @Override
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement psiElement) {
        return null;
    }

    @Nullable
    private LineMarkerInfo getRelatedFiles(@NotNull PsiFile file, @NotNull String controllerName, @NotNull PsiElement element) {
        if (!(element instanceof Method)) {
            return null;
        }
        Method method = (Method)element;
        if (!method.getAccess().isPublic()) {
            return null;
        }
        String methodName = method.getName();
        PsiDirectory appDir = PsiUtil.getAppDirectoryFromFile(file);
        String templatePath = String.format("View/%s/%s.ctp", controllerName, methodName);
        VirtualFile relativeFile = VfsUtil.findRelativeFile(appDir, templatePath);
        if (relativeFile == null) {
            return null;
        }

        PsiFile targetFile = PsiUtil.convertVirtualFileToPsiFile(method.getProject(), relativeFile);
        if (targetFile == null) {
            return null;
        }
        PsiElement targetElement = targetFile.getFirstChild();
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(CakeIcons.LOGO).setTarget(targetElement);
        return builder.createLineMarkerInfo(method);
    }

    private void addLineMarkerUnique(@NotNull Collection<LineMarkerInfo> collection, @Nullable LineMarkerInfo newMarker) {
        if (newMarker == null) {
            return;
        }
        for (LineMarkerInfo lineMarkerInfo : collection) {
            PsiElement element = lineMarkerInfo.getElement();
            if (element == null) {
                return;
            }
            if (element.equals(newMarker.getElement())) {
                return;
            }
        }
        collection.add(newMarker);
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> list, @NotNull Collection<LineMarkerInfo> collection) {
        for (PsiElement element : list) {
            PsiFile file = element.getContainingFile();
            if (file == null) {
                continue;
            }
            VirtualFile virtualFile  = file.getVirtualFile();
            if (virtualFile == null) {
                continue;
            }
            String controllerName = CakeUtil.controllerBaseNameFromControllerFileName(virtualFile.getNameWithoutExtension());
            if (controllerName == null) {
                continue;
            }
            LineMarkerInfo info = getRelatedFiles(file, controllerName, element);
            addLineMarkerUnique(collection, info);
            PsiElement[] children = element.getChildren();
            for (PsiElement child : children) {
                info = getRelatedFiles(file, controllerName, child);
                addLineMarkerUnique(collection, info);
            }
        }
    }
}
