package hu.blackbelt.judo.tatami.asm2expression;

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

/**
 * Records, in the {@code TransformationContext}, whether the {@code ExpressionModel} used by
 * {@link Asm2ExpressionWork} was created in this run or was already present (preloaded, for example from a saved
 * {@code -expression.model}). A preloaded model keeps the bindings of whatever setting built it, so downstream stages
 * that must reproduce a fresh extraction (the query model stage) use this to tell the two apart.
 */
public enum ExpressionModelOrigin {
    /**
     * The expression model was created by {@link Asm2ExpressionWork} in this run.
     */
    CREATED_IN_THIS_RUN,

    /**
     * The expression model was already in the context when {@link Asm2ExpressionWork} ran.
     */
    PRELOADED
}
