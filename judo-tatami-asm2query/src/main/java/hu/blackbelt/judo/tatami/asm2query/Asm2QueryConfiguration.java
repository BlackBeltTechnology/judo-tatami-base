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

import org.eclipse.emf.ecore.EReference;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional context entry of {@link Asm2QueryWork}.
 *
 * <p>The query model is built over the expression model of the same run, extracted by {@code Asm2ExpressionWork} with
 * {@code resolveOnlyCurrentLambdaScope = false} by default, so there is no expression setting here.</p>
 */
public class Asm2QueryConfiguration {

    /**
     * Custom join definitions of derived transfer relations, keyed by the live ASM reference (default: none). They exist
     * only in an in-process build; a persisted query model is never built with custom joins by the workflows.
     */
    private Map<EReference, CustomJoinDefinition> customJoinDefinitions = new ConcurrentHashMap<>();

    public Map<EReference, CustomJoinDefinition> getCustomJoinDefinitions() {
        return customJoinDefinitions;
    }

    public void setCustomJoinDefinitions(final Map<EReference, CustomJoinDefinition> customJoinDefinitions) {
        this.customJoinDefinitions = customJoinDefinitions != null ? customJoinDefinitions : new ConcurrentHashMap<>();
    }
}
