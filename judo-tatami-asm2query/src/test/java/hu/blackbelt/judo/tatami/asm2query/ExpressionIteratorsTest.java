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

import hu.blackbelt.judo.meta.expression.CollectionExpression;
import hu.blackbelt.judo.meta.expression.Expression;
import hu.blackbelt.judo.meta.expression.adapters.asm.AsmModelAdapter;
import hu.blackbelt.judo.tatami.asm2query.feature.ExpressionIterators;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * {@link ExpressionIterators#createMissing}: the iterators of the collection expressions that a restored query factory must have
 * (the build creates them lazily and keeps them), over the Northwind expressions without any iterator.
 * See change: persisted-query-model
 */
public class ExpressionIteratorsTest {

    private NorthwindFixture fixture;
    private AsmModelAdapter modelAdapter;
    private List<Expression> topLevel;
    private Map<CollectionExpression, Object> existing;

    @BeforeEach
    void iteratorFreeExpressions() {
        fixture = NorthwindFixture.create();
        modelAdapter = new AsmModelAdapter(fixture.asmModel.getResourceSet(), fixture.measureModel.getResourceSet());
        topLevel = new ArrayList<>();
        for (Resource resource : fixture.expressionModel.getResourceSet().getResources()) {
            for (EObject root : resource.getContents()) {
                if (root instanceof Expression) {
                    topLevel.add((Expression) root);
                }
            }
        }
        // the expression model as asm2expression saves it: the lambda iterators are part of it, the others are missing
        existing = new IdentityHashMap<>();
        collections(topLevel).forEach(c -> existing.put(c, c.getIteratorVariable()));
    }

    private static List<CollectionExpression> collections(final List<? extends Expression> expressions) {
        final List<CollectionExpression> result = new ArrayList<>();
        for (Expression expression : expressions) {
            if (expression instanceof CollectionExpression) {
                result.add((CollectionExpression) expression);
            }
            for (TreeIterator<EObject> it = expression.eAllContents(); it.hasNext(); ) {
                final EObject o = it.next();
                if (o instanceof CollectionExpression) {
                    result.add((CollectionExpression) o);
                }
            }
        }
        return result;
    }

    @Test
    void createsTheMissingIteratorsOfEveryCollectionExpression() {
        final List<CollectionExpression> before = collections(topLevel);
        assertThat(before.size(), greaterThan(0));
        assertThat(before.stream().filter(c -> c.getIteratorVariable() == null).count(), greaterThan(0L));

        final int created = ExpressionIterators.createMissing(modelAdapter, topLevel);

        assertThat(created, greaterThan(0));
        // the creation may reach the collections nested in the created ones: every collection of the walk has an iterator
        collections(topLevel).forEach(c -> assertThat(c.getIteratorVariable(), notNullValue()));
        // an iterator the model already has (lambda variable, referenced by the lambda body) is never replaced
        existing.forEach((c, iterator) -> {
            if (iterator != null) {
                assertThat(c.getIteratorVariable(), sameInstance(iterator));
            }
        });
    }

    @Test
    void secondCallIsANoOp() {
        ExpressionIterators.createMissing(modelAdapter, topLevel);
        final Map<CollectionExpression, Object> iterators = new IdentityHashMap<>();
        collections(topLevel).forEach(c -> iterators.put(c, c.getIteratorVariable()));

        assertThat(ExpressionIterators.createMissing(modelAdapter, topLevel), equalTo(0));

        collections(topLevel).forEach(c -> assertThat(c.getIteratorVariable(), sameInstance(iterators.get(c))));
    }

    @Test
    void expressionsOutsideAResourceAreSkipped() {
        final List<Expression> detached = new ArrayList<>();
        for (Expression expression : topLevel) {
            if (collections(Collections.singletonList(expression)).stream().anyMatch(c -> c.getIteratorVariable() == null)) {
                detached.add(EcoreUtil.copy(expression));
            }
        }
        assertThat(detached.size(), greaterThan(0));
        detached.forEach(e -> assertThat(e.eResource(), nullValue()));
        final long missingBefore = collections(detached).stream().filter(c -> c.getIteratorVariable() == null).count();
        assertThat(missingBefore, greaterThan(0L));

        assertThat(ExpressionIterators.createMissing(modelAdapter, detached), equalTo(0));

        assertThat(collections(detached).stream().filter(c -> c.getIteratorVariable() == null).count(), equalTo(missingBefore));
    }

    @Test
    void iteratorNameIsTheOneOfTheConverter() {
        assertThat(ExpressionIterators.ITERATOR_NAME, equalTo("_iterator"));
    }
}
