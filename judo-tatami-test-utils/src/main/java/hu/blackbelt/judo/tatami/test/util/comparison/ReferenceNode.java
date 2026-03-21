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
 * Represents a non-containment reference to another model element.
 * <p>
 * Can resolve to the actual ModelNode using the containment path.
 * Reference nodes store the target's checksum, path, and identifier for
 * comparison and resolution purposes.
 * </p>
 */
public class ReferenceNode {

    private final String targetChecksum;
    private final String targetPath;
    private final String targetIdentifier;
    private transient ModelNode resolved;

    /**
     * Creates a new ReferenceNode pointing to the given target.
     *
     * @param target the target ModelNode
     */
    public ReferenceNode(ModelNode target) {
        this.targetChecksum = target.getChecksum();
        this.targetPath = target.getPath();
        this.targetIdentifier = target.getIdentifier();
    }

    /**
     * Creates a new ReferenceNode with explicit values.
     *
     * @param targetChecksum the target's checksum
     * @param targetPath the target's containment path
     * @param targetIdentifier the target's identifier
     */
    public ReferenceNode(String targetChecksum, String targetPath, String targetIdentifier) {
        this.targetChecksum = targetChecksum;
        this.targetPath = targetPath;
        this.targetIdentifier = targetIdentifier;
    }

    /**
     * Returns the target's structural checksum.
     *
     * @return the checksum
     */
    public String getTargetChecksum() {
        return targetChecksum;
    }

    /**
     * Returns the target's containment path.
     *
     * @return the path
     */
    public String getTargetPath() {
        return targetPath;
    }

    /**
     * Returns the target's identifier.
     *
     * @return the identifier
     */
    public String getTargetIdentifier() {
        return targetIdentifier;
    }

    /**
     * Resolves this reference to the target ModelNode.
     * <p>
     * The result is cached for subsequent calls.
     * </p>
     *
     * @param root the root ModelNode to search from
     * @return the target ModelNode, or null if not found
     */
    public ModelNode resolve(ModelNode root) {
        if (resolved == null) {
            resolved = root.findByPath(targetPath);
        }
        return resolved;
    }

    /**
     * Resolves this reference to the target EObject.
     *
     * @param root the root ModelNode to search from
     * @return the target EObject, or null if not found
     */
    public EObject resolveEObject(ModelNode root) {
        ModelNode node = resolve(root);
        return node != null ? node.getEObject() : null;
    }

    /**
     * Clears the cached resolution.
     */
    public void clearResolution() {
        resolved = null;
    }

    @Override
    public String toString() {
        return String.format("ReferenceNode{path='%s', id='%s'}", targetPath, targetIdentifier);
    }
}
