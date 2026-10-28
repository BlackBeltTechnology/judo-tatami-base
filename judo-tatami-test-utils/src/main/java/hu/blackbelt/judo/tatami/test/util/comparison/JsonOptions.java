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
 * Options for JSON export of ModelNode structures.
 */
public class JsonOptions {

    private boolean includeAttributes = true;
    private boolean includeChecksums = true;
    private boolean includeReferences = true;
    private int maxDepth = Integer.MAX_VALUE;
    private boolean prettyPrint = true;

    /**
     * Creates default JsonOptions with all features enabled.
     */
    public JsonOptions() {
    }

    /**
     * Set whether to include attributes in the JSON output.
     *
     * @param include true to include attributes
     * @return this for fluent chaining
     */
    public JsonOptions includeAttributes(boolean include) {
        this.includeAttributes = include;
        return this;
    }

    /**
     * Set whether to include checksums in the JSON output.
     *
     * @param include true to include checksums
     * @return this for fluent chaining
     */
    public JsonOptions includeChecksums(boolean include) {
        this.includeChecksums = include;
        return this;
    }

    /**
     * Set whether to include references in the JSON output.
     *
     * @param include true to include references
     * @return this for fluent chaining
     */
    public JsonOptions includeReferences(boolean include) {
        this.includeReferences = include;
        return this;
    }

    /**
     * Set the maximum depth to traverse.
     *
     * @param depth maximum containment depth
     * @return this for fluent chaining
     */
    public JsonOptions maxDepth(int depth) {
        this.maxDepth = depth;
        return this;
    }

    /**
     * Set whether to pretty print the JSON.
     *
     * @param pretty true for indented output
     * @return this for fluent chaining
     */
    public JsonOptions prettyPrint(boolean pretty) {
        this.prettyPrint = pretty;
        return this;
    }

    public boolean isIncludeAttributes() {
        return includeAttributes;
    }

    public boolean isIncludeChecksums() {
        return includeChecksums;
    }

    public boolean isIncludeReferences() {
        return includeReferences;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }
}
