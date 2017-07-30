package com.daveme.intellij.chocolateCakePHP;

import com.daveme.intellij.chocolateCakePHP.icons.CakeIcons;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
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
    private LineMarkerInfo getRelatedFiles(@NotNull VirtualFile file, @NotNull String controllerName, @NotNull PsiElement element) {
        if (!(element instanceof Method)) {
            return null;
        }
        Method method = (Method)element;
        if (!method.getAccess().isPublic()) {
            return null;
        }
        String methodName = method.getName();

        String path = file.getCanonicalPath();
        String appDir = StringUtil.lastOccurrenceOf(path, "app");
        if (appDir == null) {
            return null;
        }
        String elementPath = String.format("%s/View/%s/%s.ctp", appDir, controllerName, methodName);
        VirtualFileManager vfManager = VirtualFileManager.getInstance();
        VirtualFile fileByUrl = vfManager.findFileByUrl(VirtualFileManager.constructUrl("file", elementPath));
        if (fileByUrl == null) {
            return null;
        }

        PsiFile targetFile = PsiUtil.convertVirtualFileToPsiFile(element.getProject(), fileByUrl);
        PsiElement targetElement = targetFile.getFirstChild();
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(CakeIcons.LOGO).setTarget(targetElement);
        return builder.createLineMarkerInfo(element);
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
            VirtualFile file = element.getContainingFile().getVirtualFile();
            String controllerName = StringUtil.controllerBaseNameFromControllerFileName(file.getNameWithoutExtension());
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
