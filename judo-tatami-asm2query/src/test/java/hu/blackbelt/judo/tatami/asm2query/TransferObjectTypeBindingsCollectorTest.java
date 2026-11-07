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

import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.expression.TypeName;
import hu.blackbelt.judo.meta.expression.binding.AttributeBinding;
import hu.blackbelt.judo.meta.expression.binding.FilterBinding;
import hu.blackbelt.judo.meta.expression.binding.ReferenceBinding;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * TP-59: the binding index answers every lookup exactly like the brute-force scan it replaced.
 */
public class TransferObjectTypeBindingsCollectorTest {

    /**
     * Test-only reference implementation: the per-call scan over {@code getAllContents()} of the previous collector.
     */
    static <T> List<T> referenceScan(final ResourceSet expressionResourceSet, final Class<T> clazz, final String typeFqn) {
        final Iterable<Notifier> contents = expressionResourceSet::getAllContents;
        return StreamSupport.stream(contents.spliterator(), false)
                .filter(e -> clazz.isAssignableFrom(e.getClass()))
                .map(e -> clazz.cast(e))
                .filter(e -> {
                    final TypeName typeName = e instanceof TypeName ? (TypeName) e : bindingTypeName(e);
                    return typeFqn.equals(typeName.getNamespace() + "::" + typeName.getName());
                })
                .collect(Collectors.toList());
    }

    private static TypeName bindingTypeName(final Object e) {
        if (e instanceof AttributeBinding) {
            return ((AttributeBinding) e).getTypeName();
        } else if (e instanceof ReferenceBinding) {
            return ((ReferenceBinding) e).getTypeName();
        } else {
            return ((FilterBinding) e).getTypeName();
        }
    }

    /**
     * A collector that answers every binding lookup with the reference scan.
     */
    static class ScanningCollector extends TransferObjectTypeBindingsCollector {
        private final ResourceSet expressions;

        ScanningCollector(final ResourceSet asm, final ResourceSet expressions) {
            super(asm, expressions);
            this.expressions = expressions;
        }

        @Override
        <T> List<T> getBindings(final Class<T> bindingClass, final String typeFqn) {
            return referenceScan(expressions, bindingClass, typeFqn);
        }
    }

    static NorthwindFixture fixture;
    static TransferObjectTypeBindingsCollector indexed;
    static ScanningCollector scanning;

    @BeforeAll
    static void setUp() {
        fixture = NorthwindFixture.shared();
        indexed = new TransferObjectTypeBindingsCollector(fixture.asmModel.getResourceSet(), fixture.expressionModel.getResourceSet());
        scanning = new ScanningCollector(fixture.asmModel.getResourceSet(), fixture.expressionModel.getResourceSet());
    }

    @Test
    void indexAnswersEveryLookupLikeTheScan() {
        final ResourceSet expressions = fixture.expressionModel.getResourceSet();
        int lookups = 0;
        int nonEmpty = 0;
        for (Class<?> bindingClass : TransferObjectTypeBindingsCollector.INDEXED_BINDING_CLASSES) {
            final Set<String> typeNames = new HashSet<>(indexed.getIndexedTypeNames(bindingClass));
            // every type name the scan can produce is in the index, and the other way around
            final Set<String> scanned = new HashSet<>();
            final Iterable<Notifier> contents = expressions::getAllContents;
            StreamSupport.stream(contents.spliterator(), false)
                    .filter(e -> bindingClass.isAssignableFrom(e.getClass()))
                    .forEach(e -> {
                        final TypeName typeName = e instanceof TypeName ? (TypeName) e : bindingTypeName(e);
                        scanned.add(typeName.getNamespace() + "::" + typeName.getName());
                    });
            assertThat(bindingClass.getSimpleName() + " type names", typeNames, equalTo(scanned));

            final List<String> lookupNames = new ArrayList<>(typeNames);
            lookupNames.add("no::SuchType");
            for (String typeFqn : lookupNames) {
                final List<?> expected = referenceScan(expressions, bindingClass, typeFqn);
                final List<?> actual = indexed.getBindings(bindingClass, typeFqn);
                assertThat(bindingClass.getSimpleName() + " " + typeFqn + " size", actual.size(), equalTo(expected.size()));
                for (int i = 0; i < expected.size(); i++) {
                    // identical elements in identical order
                    org.junit.jupiter.api.Assertions.assertSame(expected.get(i), actual.get(i),
                            bindingClass.getSimpleName() + " " + typeFqn + " #" + i);
                }
                lookups++;
                if (!expected.isEmpty()) {
                    nonEmpty++;
                }
            }
        }
        assertThat(lookups, greaterThan(10));
        assertThat(nonEmpty, greaterThan(10));
    }

    @Test
    void collectorsAgreeOnTheWholeModel() {
        final List<EClass> classes = fixture.asmUtils.all(EClass.class).collect(Collectors.toList());
        int mapped = 0;
        int unmapped = 0;
        for (EClass c : classes) {
            if (fixture.asmUtils.isMappedTransferObjectType(c)) {
                final var expected = scanning.getTransferObjectGraph(c);
                final var actual = indexed.getTransferObjectGraph(c);
                assertThat(AsmUtils.getClassifierFQName(c), actual.isPresent(), equalTo(expected.isPresent()));
                if (expected.isPresent()) {
                    mapped++;
                    final MappedTransferObjectTypeBindings e = expected.get();
                    final MappedTransferObjectTypeBindings a = actual.get();
                    assertThat(a.getEntityType(), equalTo(e.getEntityType()));
                    assertThat(a.getTransferObjectType(), equalTo(e.getTransferObjectType()));
                    assertThat(a.getGetterAttributeExpressions(), equalTo(e.getGetterAttributeExpressions()));
                    assertThat(a.getSetterAttributeExpressions(), equalTo(e.getSetterAttributeExpressions()));
                    assertThat(a.getGetterReferenceExpressions(), equalTo(e.getGetterReferenceExpressions()));
                    assertThat(a.getSetterReferenceExpressions(), equalTo(e.getSetterReferenceExpressions()));
                    assertThat(a.getFilter(), equalTo(e.getFilter()));
                    assertThat(a.getReferences().keySet(), equalTo(e.getReferences().keySet()));
                }
            } else if (!AsmUtils.isEntityType(c)) {
                final var expected = scanning.getTransferObjectBindings(c);
                final var actual = indexed.getTransferObjectBindings(c);
                assertThat(actual.isPresent(), equalTo(expected.isPresent()));
                if (expected.isPresent()) {
                    unmapped++;
                    assertThat(actual.get().getDataExpressions(), equalTo(expected.get().getDataExpressions()));
                    assertThat(actual.get().getNavigationExpressions(), equalTo(expected.get().getNavigationExpressions()));
                }
            }
        }
        assertThat(mapped, greaterThan(0));
        assertThat(unmapped, greaterThan(0));

        // static flags and entity expressions after the same graph walk
        for (EAttribute a : fixture.asmUtils.all(EAttribute.class).collect(Collectors.toList())) {
            assertThat(AsmUtils.getAttributeFQName(a), indexed.isStaticAttribute(a), equalTo(scanning.isStaticAttribute(a)));
        }
        for (EReference r : fixture.asmUtils.all(EReference.class).collect(Collectors.toList())) {
            assertThat(AsmUtils.getReferenceFQName(r), indexed.isStaticReference(r), equalTo(scanning.isStaticReference(r)));
        }
        assertThat(indexed.getStaticEntityAttributes(), equalTo(scanning.getStaticEntityAttributes()));
        assertThat(indexed.getStaticEntityReferences(), equalTo(scanning.getStaticEntityReferences()));
        assertThat(indexed.getEntityTypeExpressionsMap().keySet(), equalTo(scanning.getEntityTypeExpressionsMap().keySet()));
        indexed.getEntityTypeExpressionsMap().forEach((entity, expressions) -> {
            final EntityTypeExpressions other = scanning.getEntityTypeExpressionsMap().get(entity);
            assertThat(expressions.getGetterAttributeExpressions(), equalTo(other.getGetterAttributeExpressions()));
            assertThat(expressions.getGetterReferenceExpressions(), equalTo(other.getGetterReferenceExpressions()));
        });
    }
}
