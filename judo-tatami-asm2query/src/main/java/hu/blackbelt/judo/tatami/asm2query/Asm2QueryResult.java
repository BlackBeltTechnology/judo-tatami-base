package hu.blackbelt.judo.tatami.asm2query;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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

import hu.blackbelt.judo.meta.query.QueryModelInfo;
import hu.blackbelt.judo.meta.query.runtime.QueryModel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EReference;

import java.util.Map;

/**
 * Result of {@link Asm2Query}: the query model (resource {@code <name>-query.model} holding the query graph and one
 * {@link QueryModelInfo} root), the index of the build, and the expression converter wired over the same ASM / measure
 * resource sets.
 */
@Getter
@RequiredArgsConstructor
public final class Asm2QueryResult {

    /**
     * Query model with the logical URI {@code <name>-query.model}.
     */
    @NonNull
    private final QueryModel queryModel;

    /**
     * Index of the build. Its counters are live: later expression conversions advance them.
     */
    @NonNull
    private final QueryIndex index;

    @NonNull
    private final DataExpressionConverter expressionConverter;

    /**
     * Custom join definitions used by the build (never null).
     */
    @NonNull
    private final Map<EReference, CustomJoinDefinition> customJoinDefinitions;

    /**
     * @return the single {@link QueryModelInfo} root of the query model
     */
    public QueryModelInfo getQueryModelInfo() {
        return QueryIndex.getQueryModelInfo(queryModel.getResource());
    }
}
