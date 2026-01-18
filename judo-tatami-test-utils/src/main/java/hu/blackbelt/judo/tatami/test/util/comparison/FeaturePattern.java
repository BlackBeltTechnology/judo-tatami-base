package hu.blackbelt.judo.tatami.test.util.comparison;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.Objects;

/**
 * Parses and matches feature patterns like "package.Class#feature".
 * <p>
 * Supports wildcards for package, class, and feature names:
 * <ul>
 *   <li>{@code ecore.EClass#abstract} - exact match</li>
 *   <li>{@code *.EClass#abstract} - any package</li>
 *   <li>{@code ecore.*#name} - any class in package</li>
 *   <li>{@code *.*#uuid} - global (all features named uuid)</li>
 * </ul>
 * </p>
 */
public class FeaturePattern {

    private final String packagePattern;
    private final String classPattern;
    private final String featurePattern;

    /**
     * Parse pattern from string.
     *
     * @param pattern the pattern in format "package.Class#feature"
     * @throws IllegalArgumentException if pattern format is invalid
     */
    public FeaturePattern(String pattern) {
        int hashIndex = pattern.indexOf('#');
        if (hashIndex == -1) {
            throw new IllegalArgumentException(
                    "Invalid pattern: must contain '#'. Expected format: package.Class#feature, got: " + pattern);
        }

        String typePattern = pattern.substring(0, hashIndex);
        this.featurePattern = pattern.substring(hashIndex + 1);

        if (featurePattern.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid pattern: feature name cannot be empty. Expected format: package.Class#feature");
        }

        int dotIndex = typePattern.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException(
                    "Invalid pattern: must contain '.'. Expected format: package.Class#feature, got: " + pattern);
        }

        this.packagePattern = typePattern.substring(0, dotIndex);
        this.classPattern = typePattern.substring(dotIndex + 1);

        if (packagePattern.isEmpty() || classPattern.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid pattern: package and class cannot be empty. Expected format: package.Class#feature");
        }
    }

    /**
     * Checks if this pattern matches the given EClass and feature.
     *
     * @param eClass the EClass to match
     * @param feature the feature to match
     * @return true if the pattern matches
     */
    public boolean matches(EClass eClass, EStructuralFeature feature) {
        // Match package
        EPackage pkg = eClass.getEPackage();
        String pkgName = pkg != null ? pkg.getName() : "";
        if (!matchesWildcard(packagePattern, pkgName)) {
            return false;
        }

        // Match class
        if (!matchesWildcard(classPattern, eClass.getName())) {
            return false;
        }

        // Match feature
        return matchesWildcard(featurePattern, feature.getName());
    }

    private boolean matchesWildcard(String pattern, String value) {
        return "*".equals(pattern) || pattern.equals(value);
    }

    /**
     * Returns the package pattern.
     *
     * @return the package pattern ("*" for any)
     */
    public String getPackagePattern() {
        return packagePattern;
    }

    /**
     * Returns the class pattern.
     *
     * @return the class pattern ("*" for any)
     */
    public String getClassPattern() {
        return classPattern;
    }

    /**
     * Returns the feature pattern.
     *
     * @return the feature pattern ("*" for any)
     */
    public String getFeaturePattern() {
        return featurePattern;
    }

    @Override
    public String toString() {
        return packagePattern + "." + classPattern + "#" + featurePattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeaturePattern that = (FeaturePattern) o;
        return Objects.equals(packagePattern, that.packagePattern)
                && Objects.equals(classPattern, that.classPattern)
                && Objects.equals(featurePattern, that.featurePattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packagePattern, classPattern, featurePattern);
    }
}
