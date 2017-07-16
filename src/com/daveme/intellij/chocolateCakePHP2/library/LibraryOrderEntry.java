package com.daveme.intellij.chocolateCakePHP.library;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LibraryOrderEntry implements com.intellij.openapi.roots.LibraryOrderEntry {
    @Nullable
    @Override
    public Library getLibrary() {
        return null;
    }

    @Override
    public boolean isModuleLevel() {
        return false;
    }

    @Override
    public String getLibraryLevel() {
        return null;
    }

    @Nullable
    @Override
    public String getLibraryName() {
        return null;
    }

    @Override
    public boolean isExported() {
        return false;
    }

    @Override
    public void setExported(boolean b) {

    }

    @NotNull
    @Override
    public DependencyScope getScope() {
        return null;
    }

    @Override
    public void setScope(@NotNull DependencyScope dependencyScope) {

    }

    @Override
    public VirtualFile[] getRootFiles(OrderRootType orderRootType) {
        return new VirtualFile[0];
    }

    @Override
    public String[] getRootUrls(OrderRootType orderRootType) {
        return new String[0];
    }

    @NotNull
    @Override
    public VirtualFile[] getFiles(OrderRootType orderRootType) {
        return new VirtualFile[0];
    }

    @NotNull
    @Override
    public String[] getUrls(OrderRootType orderRootType) {
        return new String[0];
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return null;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @NotNull
    @Override
    public Module getOwnerModule() {
        return null;
    }

    @Override
    public <R> R accept(RootPolicy<R> rootPolicy, @Nullable R r) {
        return null;
    }

    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     * <p>
     * <p>The implementor must ensure <tt>sgn(x.compareTo(y)) ==
     * -sgn(y.compareTo(x))</tt> for all <tt>x</tt> and <tt>y</tt>.  (This
     * implies that <tt>x.compareTo(y)</tt> must throw an exception iff
     * <tt>y.compareTo(x)</tt> throws an exception.)
     * <p>
     * <p>The implementor must also ensure that the relation is transitive:
     * <tt>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</tt> implies
     * <tt>x.compareTo(z)&gt;0</tt>.
     * <p>
     * <p>Finally, the implementor must ensure that <tt>x.compareTo(y)==0</tt>
     * implies that <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for
     * all <tt>z</tt>.
     * <p>
     * <p>It is strongly recommended, but <i>not</i> strictly required that
     * <tt>(x.compareTo(y)==0) == (x.equals(y))</tt>.  Generally speaking, any
     * class that implements the <tt>Comparable</tt> interface and violates
     * this condition should clearly indicate this fact.  The recommended
     * language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     * <p>
     * <p>In the foregoing description, the notation
     * <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
     * <i>signum</i> function, which is defined to return one of <tt>-1</tt>,
     * <tt>0</tt>, or <tt>1</tt> according to whether the value of
     * <i>expression</i> is negative, zero or positive.
     *
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     */
    @Override
    public int compareTo(OrderEntry o) {
        return 0;
    }

    @Override
    public boolean isSynthetic() {
        return false;
    }
}
