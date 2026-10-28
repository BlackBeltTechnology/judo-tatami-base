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
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Configuration options for {@link ModelChecksumCalculator}.
 * <p>
 * Allows ignoring specific features during checksum calculation and
 * customizing identifier extraction for EObjects.
 * </p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * CalculatorOptions options = new CalculatorOptions()
 *     .ignore("asm.EAnnotation#details")      // Ignore annotation details
 *     .ignore("*.EClass#instanceClassName")   // Ignore instanceClassName on all EClass
 *     .ignore("*.*#uuid")                     // Ignore uuid globally
 *     .withIdentifierResolver(obj -> {
 *         // Custom identifier extraction
 *         return obj.eClass().getName() + "_" + System.identityHashCode(obj);
 *     });
 * }</pre>
 */
public class CalculatorOptions {

    private final Set<FeaturePattern> ignoredFeatures = new HashSet<>();
    private Function<EObject, String> identifierResolver = CalculatorOptions::defaultIdentifier;

    /**
     * Ignore a feature during checksum calculation.
     *
     * @param pattern Feature pattern in format "package.Class#feature".
     *                Supports wildcards: *.Class#feature, package.*#feature, *.*#feature
     * @return this for fluent chaining
     */
    public CalculatorOptions ignore(String pattern) {
        ignoredFeatures.add(new FeaturePattern(pattern));
        return this;
    }

    /**
     * Ignore multiple features.
     *
     * @param patterns collection of feature patterns
     * @return this for fluent chaining
     */
    public CalculatorOptions ignoreAll(Collection<String> patterns) {
        patterns.forEach(this::ignore);
        return this;
    }

    /**
     * Set custom identifier resolver for EObjects.
     * <p>
     * The resolver extracts the identifier used in ModelNode paths.
     * </p>
     *
     * @param resolver function that extracts identifier from EObject
     * @return this for fluent chaining
     */
    public CalculatorOptions withIdentifierResolver(Function<EObject, String> resolver) {
        this.identifierResolver = resolver;
        return this;
    }

    /**
     * Returns the identifier resolver function.
     *
     * @return the identifier resolver
     */
    public Function<EObject, String> getIdentifierResolver() {
        return identifierResolver;
    }

    /**
     * Returns the set of ignored feature patterns.
     *
     * @return unmodifiable set of patterns
     */
    public Set<FeaturePattern> getIgnoredFeatures() {
        return Collections.unmodifiableSet(ignoredFeatures);
    }

    /**
     * Checks if a feature should be ignored.
     *
     * @param eClass the EClass containing the feature
     * @param feature the feature to check
     * @return true if the feature should be ignored
     */
    public boolean isIgnored(EClass eClass, EStructuralFeature feature) {
        for (FeaturePattern pattern : ignoredFeatures) {
            if (pattern.matches(eClass, feature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Default identifier resolver.
     * <p>
     * Uses 'name' attribute if available, otherwise EClass name + containment index.
     * </p>
     *
     * @param obj the EObject to get identifier for
     * @return the identifier string
     */
    public static String defaultIdentifier(EObject obj) {
        // Try 'name' attribute first
        EStructuralFeature nameFeature = obj.eClass().getEStructuralFeature("name");
        if (nameFeature != null) {
            Object name = obj.eGet(nameFeature);
            if (name != null && !name.toString().isEmpty()) {
                return String.valueOf(name);
            }
        }

        // Fall back to type + index in container
        EObject container = obj.eContainer();
        if (container != null) {
            EReference containmentRef = obj.eContainmentFeature();
            if (containmentRef != null && containmentRef.isMany()) {
                @SuppressWarnings("unchecked")
                List<EObject> siblings = (List<EObject>) container.eGet(containmentRef);
                int index = siblings.indexOf(obj);
                return obj.eClass().getName() + "_" + index;
            }
        }
        return obj.eClass().getName();
    }

    @Override
    public String toString() {
        return "CalculatorOptions{ignoredFeatures=" + ignoredFeatures.size() + "}";
    }
}
