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

import org.eclipse.emf.ecore.EObject;

/**
 * Represents a single difference between two model structures.
 * <p>
 * Each difference has a type, path, description, and optional references
 * to the expected and actual ModelNodes. The EObjects can be directly
 * accessed for debugging purposes.
 * </p>
 */
public class Difference {

    /**
     * Types of structural differences that can be detected.
     */
    public enum DifferenceType {
        /** Element exists in expected but not in actual */
        MISSING,
        /** Element exists in actual but not in expected */
        EXTRA,
        /** Attribute values differ between expected and actual */
        ATTRIBUTE_MISMATCH,
        /** Non-containment reference targets differ */
        REFERENCE_MISMATCH,
        /** Element types (EClass) differ */
        TYPE_MISMATCH
    }

    private final String path;
    private final DifferenceType type;
    private final String description;
    private final ModelNode expectedNode;
    private final ModelNode actualNode;

    /**
     * Creates a new Difference.
     *
     * @param path the containment path where the difference was found
     * @param type the type of difference
     * @param description a human-readable description of the difference
     * @param expected the expected ModelNode (may be null for EXTRA differences)
     * @param actual the actual ModelNode (may be null for MISSING differences)
     */
    public Difference(String path, DifferenceType type, String description,
                      ModelNode expected, ModelNode actual) {
        this.path = path;
        this.type = type;
        this.description = description;
        this.expectedNode = expected;
        this.actualNode = actual;
    }

    /**
     * Returns the containment path where the difference was found.
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the type of difference.
     *
     * @return the difference type
     */
    public DifferenceType getType() {
        return type;
    }

    /**
     * Returns a human-readable description of the difference.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the expected ModelNode.
     *
     * @return the expected node, or null for EXTRA differences
     */
    public ModelNode getExpectedNode() {
        return expectedNode;
    }

    /**
     * Returns the actual ModelNode.
     *
     * @return the actual node, or null for MISSING differences
     */
    public ModelNode getActualNode() {
        return actualNode;
    }

    /**
     * Returns the expected EObject directly.
     *
     * @return the expected EObject, or null if not available
     */
    public EObject getExpectedObject() {
        return expectedNode != null ? expectedNode.getEObject() : null;
    }

    /**
     * Returns the actual EObject directly.
     *
     * @return the actual EObject, or null if not available
     */
    public EObject getActualObject() {
        return actualNode != null ? actualNode.getEObject() : null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", type, path, description);
    }
}
