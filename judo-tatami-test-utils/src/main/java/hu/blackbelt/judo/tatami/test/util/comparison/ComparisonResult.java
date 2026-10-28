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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of comparing two model structures.
 * <p>
 * Contains the comparison outcome (match or not) and a list of all differences
 * found. Provides access to the original ModelNode roots for reference resolution.
 * </p>
 */
public class ComparisonResult {

    private final boolean match;
    private final List<Difference> differences;
    private final ModelNode expectedRoot;
    private final ModelNode actualRoot;

    /**
     * Creates a new ComparisonResult.
     *
     * @param expected the expected model's root node
     * @param actual the actual model's root node
     * @param differences the list of differences found
     */
    public ComparisonResult(ModelNode expected, ModelNode actual, List<Difference> differences) {
        this.expectedRoot = expected;
        this.actualRoot = actual;
        this.differences = differences;
        this.match = differences.isEmpty();
    }

    /**
     * Returns whether the models match (no differences).
     *
     * @return true if models are structurally identical
     */
    public boolean isMatch() {
        return match;
    }

    /**
     * Returns all differences found.
     *
     * @return unmodifiable list of differences
     */
    public List<Difference> getDifferences() {
        return Collections.unmodifiableList(differences);
    }

    /**
     * Returns the expected model's root node.
     *
     * @return the expected root
     */
    public ModelNode getExpectedRoot() {
        return expectedRoot;
    }

    /**
     * Returns the actual model's root node.
     *
     * @return the actual root
     */
    public ModelNode getActualRoot() {
        return actualRoot;
    }

    /**
     * Returns differences of a specific type.
     *
     * @param type the difference type to filter by
     * @return list of differences matching the type
     */
    public List<Difference> getDifferences(Difference.DifferenceType type) {
        return differences.stream()
                .filter(d -> d.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of differences.
     *
     * @return difference count
     */
    public int getDifferenceCount() {
        return differences.size();
    }

    @Override
    public String toString() {
        if (match) {
            return "ComparisonResult{match=true}";
        }
        return String.format("ComparisonResult{match=false, differences=%d}", differences.size());
    }

    /**
     * Returns a detailed summary of all differences.
     *
     * @return multi-line string with all differences
     */
    public String toDetailedString() {
        if (match) {
            return "Models are structurally identical.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(differences.size()).append(" difference(s):\n");
        for (Difference diff : differences) {
            sb.append("  ").append(diff.toString()).append("\n");
        }
        return sb.toString();
    }
}
