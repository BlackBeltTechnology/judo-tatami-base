package hu.blackbelt.judo.tatami.asm2query.feature;

/*-
 * #%L
 * JUDO Runtime Core :: Parent
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

import hu.blackbelt.judo.meta.expression.CollectionExpression;
import hu.blackbelt.judo.meta.expression.Expression;
import hu.blackbelt.judo.meta.expression.adapters.asm.AsmModelAdapter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates the iterator variables of collection expressions ahead of the feature conversion.
 *
 * <p>A build creates the iterator variable of a collection expression lazily, while it converts the expression, and keeps it
 * in the expression model. A restored query factory does not convert the getter expressions at boot, and the saved expression
 * model carries no iterators. A later conversion of a <em>detached copy</em> of such an expression (a dynamic filter over a
 * derived attribute) would have to create the iterator on a copy that has no resource. {@link #createMissing} reproduces the
 * side effect of the build at boot, so the copies inherit the iterators like they do after a build.</p>
 */
@Slf4j
public final class ExpressionIterators {

    /**
     * Name of the iterator variables created for collection expressions.
     */
    public static final String ITERATOR_NAME = "_iterator";

    private ExpressionIterators() {
    }

    /**
     * Create the missing iterator variable of every collection expression of the given expressions, each of them and everything
     * they contain.
     *
     * <p>A collection expression is only handled when it has no iterator variable yet and is contained in a resource (the
     * iterator is attached to the resource). The method is idempotent. It modifies the expression resources, so it must not run
     * while another thread converts expressions of the same resources (call it single-threaded, before the factory is
     * published).</p>
     *
     * @param modelAdapter model adapter of the expressions
     * @param expressions  expressions to walk
     * @return number of created iterator variables
     */
    public static int createMissing(@NonNull final AsmModelAdapter modelAdapter, @NonNull final Collection<? extends Expression> expressions) {
        final List<CollectionExpression> missing = new ArrayList<>();
        for (Expression expression : expressions) {
            if (expression == null) {
                continue;
            }
            collect(expression, missing);
            expression.eAllContents().forEachRemaining(o -> collect(o, missing));
        }

        int created = 0;
        for (CollectionExpression collectionExpression : missing) {
            // an earlier creation may have attached it already
            if (collectionExpression.getIteratorVariable() == null && collectionExpression.eResource() != null) {
                collectionExpression.createIterator(ITERATOR_NAME, modelAdapter, collectionExpression.eResource());
                created++;
            }
        }
        log.debug("Created {} missing iterator variable(s)", created);
        return created;
    }

    private static void collect(final EObject object, final List<CollectionExpression> target) {
        if (object instanceof CollectionExpression
                && ((CollectionExpression) object).getIteratorVariable() == null
                && object.eResource() != null) {
            target.add((CollectionExpression) object);
        }
    }
}
