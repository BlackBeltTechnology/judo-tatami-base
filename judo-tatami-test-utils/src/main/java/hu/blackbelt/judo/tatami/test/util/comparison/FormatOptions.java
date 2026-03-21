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

/**
 * Options for formatting comparison results for LLM consumption.
 * <p>
 * Controls which elements are included in the output and limits
 * for large result sets.
 * </p>
 */
public class FormatOptions {

    private boolean includeSuggestions = true;
    private boolean includeAnalysis = true;
    private int maxDifferences = Integer.MAX_VALUE;
    private boolean prettyPrint = true;

    /**
     * Creates default FormatOptions with all features enabled.
     */
    public FormatOptions() {
    }

    /**
     * Set whether to include suggestions for fixes.
     *
     * @param include true to include suggestions
     * @return this for fluent chaining
     */
    public FormatOptions includeSuggestions(boolean include) {
        this.includeSuggestions = include;
        return this;
    }

    /**
     * Set whether to include analysis context.
     *
     * @param include true to include analysis
     * @return this for fluent chaining
     */
    public FormatOptions includeAnalysis(boolean include) {
        this.includeAnalysis = include;
        return this;
    }

    /**
     * Set the maximum number of differences to include.
     *
     * @param max the maximum count (must be non-negative)
     * @return this for fluent chaining
     * @throws IllegalArgumentException if max is negative
     */
    public FormatOptions maxDifferences(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("maxDifferences cannot be negative");
        }
        this.maxDifferences = max;
        return this;
    }

    /**
     * Set whether to pretty print the output.
     *
     * @param pretty true for indented output
     * @return this for fluent chaining
     */
    public FormatOptions prettyPrint(boolean pretty) {
        this.prettyPrint = pretty;
        return this;
    }

    public boolean isIncludeSuggestions() {
        return includeSuggestions;
    }

    public boolean isIncludeAnalysis() {
        return includeAnalysis;
    }

    public int getMaxDifferences() {
        return maxDifferences;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }
}
