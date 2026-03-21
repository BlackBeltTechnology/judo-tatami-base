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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a node in the model's containment hierarchy.
 * Each node mirrors an EObject and stores its structural checksum.
 * <p>
 * The ModelNode tree is built by {@link ModelChecksumCalculator} and can be
 * compared using {@link StructuralModelComparator}.
 * </p>
 */
public class ModelNode {

    /** Direct reference to the source EObject */
    private final EObject eObject;

    /** EClass type name (e.g., "EClass", "EPackage") */
    private final String type;

    /** Element identifier (e.g., "MyClass", "myPackage") */
    private final String identifier;

    /** Containment path from root (e.g., "EPackage:root/EPackage:sub/EClass:MyClass") */
    private final String path;

    /** Structural checksum (type + attributes + children + references) */
    private String checksum;

    /** Attribute values keyed by attribute name */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /** Contained children keyed by containment reference name */
    private final Map<String, List<ModelNode>> containments = new LinkedHashMap<>();

    /** Non-containment references keyed by reference name */
    private final Map<String, List<ReferenceNode>> references = new LinkedHashMap<>();

    /**
     * Creates a new ModelNode for the given EObject.
     *
     * @param eObject the source EObject (can be null for root node)
     * @param path the containment path from root
     * @param identifier the element identifier
     */
    public ModelNode(EObject eObject, String path, String identifier) {
        this.eObject = eObject;
        this.type = eObject != null ? eObject.eClass().getName() : "Root";
        this.identifier = identifier != null ? identifier : "";
        this.path = path;
    }

    /**
     * Returns the source EObject.
     *
     * @return the EObject this node represents, or null for root
     */
    public EObject getEObject() {
        return eObject;
    }

    /**
     * Returns the EClass type name.
     *
     * @return the type name (e.g., "EClass", "EPackage")
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the element identifier.
     *
     * @return the identifier (e.g., "MyClass", "myPackage")
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Returns the containment path from root.
     *
     * @return the path (e.g., "EPackage:root/EPackage:sub/EClass:MyClass")
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the structural checksum.
     *
     * @return the checksum string
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * Sets the structural checksum.
     *
     * @param checksum the checksum to set
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    /**
     * Returns the attribute values map.
     *
     * @return map of attribute name to value
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Returns the containment children map.
     *
     * @return map of containment reference name to list of child nodes
     */
    public Map<String, List<ModelNode>> getContainments() {
        return containments;
    }

    /**
     * Returns the non-containment references map.
     *
     * @return map of reference name to list of reference nodes
     */
    public Map<String, List<ReferenceNode>> getReferences() {
        return references;
    }

    /**
     * Adds an attribute value.
     *
     * @param name the attribute name
     * @param value the attribute value
     */
    public void addAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * Adds a containment child.
     *
     * @param refName the containment reference name
     * @param child the child node
     */
    public void addContainment(String refName, ModelNode child) {
        containments.computeIfAbsent(refName, k -> new ArrayList<>()).add(child);
    }

    /**
     * Adds a non-containment reference.
     *
     * @param refName the reference name
     * @param ref the reference node
     */
    public void addReference(String refName, ReferenceNode ref) {
        references.computeIfAbsent(refName, k -> new ArrayList<>()).add(ref);
    }

    /**
     * Recursively finds a node by its full path.
     *
     * @param targetPath the path to find
     * @return the node at the given path, or null if not found
     */
    public ModelNode findByPath(String targetPath) {
        if (this.path.equals(targetPath)) {
            return this;
        }
        for (List<ModelNode> children : containments.values()) {
            for (ModelNode child : children) {
                ModelNode found = child.findByPath(targetPath);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Finds a child node by reference name and identifier.
     *
     * @param refName the containment reference name
     * @param childIdentifier the child's identifier
     * @return the child node, or null if not found
     */
    public ModelNode findChild(String refName, String childIdentifier) {
        List<ModelNode> children = containments.get(refName);
        if (children != null) {
            for (ModelNode child : children) {
                if (childIdentifier.equals(child.getIdentifier())) {
                    return child;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("ModelNode{type='%s', id='%s', path='%s', checksum='%s'}",
                type, identifier, path, checksum != null ? checksum.substring(0, Math.min(8, checksum.length())) + "..." : "null");
    }
}
