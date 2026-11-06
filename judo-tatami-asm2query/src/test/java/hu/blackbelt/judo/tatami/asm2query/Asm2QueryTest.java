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
import hu.blackbelt.judo.meta.expression.DataExpression;
import hu.blackbelt.judo.meta.expression.Expression;
import hu.blackbelt.judo.meta.expression.ReferenceExpression;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionEvaluator;
import hu.blackbelt.judo.meta.query.EntityTypeExpressionsEntry;
import hu.blackbelt.judo.meta.query.QueryModelInfo;
import hu.blackbelt.judo.meta.query.util.builder.QueryModelInfoBuilder;
import hu.blackbelt.judo.tatami.asm2expression.Asm2ExpressionConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class Asm2QueryTest {

    static NorthwindFixture fixture;
    static Asm2QueryResult built;

    @BeforeAll
    static void build() {
        fixture = NorthwindFixture.shared();
        built = Asm2Query.executeAsm2Query(fixture.asmModel, fixture.measureModel, fixture.expressionModel);
    }

    /**
     * TP-10: default asm2expression lambda scope is false, and the query resource has the logical URI.
     */
    @Test
    void defaultLambdaScopeIsFalseAndQueryUriIsLogical() {
        // the expression model of the fixture was extracted with the default configuration
        assertThat(new Asm2ExpressionConfiguration().isResolveOnlyCurrentLambdaScope(), equalTo(false));
        assertThat(fixture.context.getByClass(Asm2ExpressionConfiguration.class).isPresent(), equalTo(false));

        // from the models: <asm model name>-query.model, never query:in-memory
        assertThat(built.getQueryModel().getResource().getURI().toString(), equalTo(fixture.asmModel.getName() + "-query.model"));
        assertThat(built.getQueryModel().isValid(), equalTo(true));

        // explicit model name (the platform's model name, e.g. NorthwindDemo)
        final Asm2QueryResult named = Asm2Query.executeAsm2Query(Asm2Query.Asm2QueryParameter.asm2QueryParameter()
                .asmResourceSet(fixture.asmModel.getResourceSet())
                .measureResourceSet(fixture.measureModel.getResourceSet())
                .expressionResourceSet(fixture.expressionModel.getResourceSet())
                .modelName("NorthwindDemo"));
        assertThat(named.getQueryModel().getResource().getURI().toString(), equalTo("NorthwindDemo-query.model"));
        assertThat(named.getQueryModelInfo().getModelName(), equalTo("NorthwindDemo"));
    }

    /**
     * Every QueryModelInfo field equals the built index.
     */
    @Test
    void queryModelInfoMatchesBuiltIndex() {
        final QueryIndex index = built.getIndex();
        final QueryModelInfo info = built.getQueryModelInfo();

        final List<QueryModelInfo> roots = built.getQueryModel().getResource().getContents().stream()
                .filter(QueryModelInfo.class::isInstance).map(QueryModelInfo.class::cast).collect(Collectors.toList());
        assertThat(roots.size(), equalTo(1));

        assertThat(info.getFormatVersion(), equalTo(Asm2Query.FORMAT_VERSION));
        assertThat(info.getModelName(), equalTo(fixture.asmModel.getName()));
        assertThat(info.getNextSourceIndex(), equalTo(index.getNextSourceIndex().get()));
        assertThat(info.getNextTargetIndex(), equalTo(index.getNextTargetIndex().get()));
        assertThat(info.getNextSourceIndex(), greaterThan(0));
        assertThat(info.getNextTargetIndex(), greaterThan(0));

        // stamps are set by save
        assertThat(info.getGenerator(), nullValue());
        assertThat(info.getAsmDigest(), nullValue());
        assertThat(info.getMeasureDigest(), nullValue());
        assertThat(info.getExpressionDigest(), nullValue());

        assertThat(info.getQueries().size(), equalTo(index.getQueryTypes().size()));
        assertThat(index.getQueryTypes().isEmpty(), equalTo(false));
        info.getQueries().forEach(e -> assertThat(index.getQuery(e.getType()).orElse(null), sameInstance(e.getSelect())));
        assertThat(info.getQueries().stream().map(e -> e.getType()).collect(Collectors.toSet()), equalTo(index.getQueryTypes()));

        assertThat(info.getNavigations().size(), equalTo(index.getNavigationReferences().size()));
        info.getNavigations().forEach(e -> assertThat(index.getNavigation(e.getReference()).orElse(null), sameInstance(e.getSubSelect())));
        assertThat(info.getNavigations().stream().map(e -> e.getReference()).collect(Collectors.toSet()), equalTo(index.getNavigationReferences()));

        assertThat(info.getDataQueries().size(), equalTo(index.getDataQueryAttributes().size()));
        info.getDataQueries().forEach(e -> assertThat(index.getDataQuery(e.getAttribute()).orElse(null), sameInstance(e.getSubSelect())));
        assertThat(info.getDataQueries().stream().map(e -> e.getAttribute()).collect(Collectors.toSet()), equalTo(index.getDataQueryAttributes()));

        assertThat(new HashSet<>(info.getOrderedTransferRelations()), equalTo(index.getOrderedTransferRelations()));
        assertThat(info.getOrderedTransferRelations().size(), equalTo(index.getOrderedTransferRelations().size()));
        assertThat(new HashSet<>(info.getStaticEntityAttributes()), equalTo(index.getStaticEntityAttributes()));
        assertThat(info.getStaticEntityAttributes().size(), equalTo(index.getStaticEntityAttributes().size()));
        assertThat(new HashSet<>(info.getStaticEntityReferences()), equalTo(index.getStaticEntityReferences()));
        assertThat(info.getStaticEntityReferences().size(), equalTo(index.getStaticEntityReferences().size()));

        // entity expressions: every entity type, empty entries included
        final Map<EClass, EntityTypeExpressions> map = index.getEntityTypeExpressionsMap();
        assertThat(info.getEntityTypeExpressions().size(), equalTo(map.size()));
        assertThat(fixture.asmUtils.all(EClass.class).filter(AsmUtils::isEntityType).count(), equalTo((long) map.size()));
        assertThat(info.getEntityTypeExpressions().stream().filter(e -> e.getGetterAttributes().isEmpty() && e.getGetterReferences().isEmpty()).count(),
                greaterThan(0L));
        for (EntityTypeExpressionsEntry entry : info.getEntityTypeExpressions()) {
            final EntityTypeExpressions expressions = map.get(entry.getEntityType());
            assertThat(expressions, not(nullValue()));
            assertThat(entry.getGetterAttributes().size(), equalTo(expressions.getGetterAttributeExpressions().size()));
            entry.getGetterAttributes().forEach(g -> assertThat(expressions.getGetterAttributeExpressions().get(g.getAttribute()), sameInstance((EObject) g.getExpression())));
            assertThat(entry.getGetterReferences().size(), equalTo(expressions.getGetterReferenceExpressions().size()));
            entry.getGetterReferences().forEach(g -> assertThat(expressions.getGetterReferenceExpressions().get(g.getReference()), sameInstance((EObject) g.getExpression())));
        }
        assertThat(info.getEntityTypeExpressions().stream().mapToInt(e -> e.getGetterAttributes().size() + e.getGetterReferences().size()).sum(),
                greaterThan(0));
    }

    /**
     * The info keeps entries in a deterministic (sorted) order.
     */
    @Test
    void queryModelInfoEntriesAreSorted() {
        final QueryModelInfo info = built.getQueryModelInfo();
        final List<String> names = info.getQueries().stream().map(e -> AsmUtils.getClassifierFQName(e.getType())).collect(Collectors.toList());
        final List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        assertThat(names, equalTo(sorted));
    }

    /**
     * TP-18 (build side): static sets hold exactly the entity features evaluated with an empty variable list.
     */
    @Test
    void staticSetsHoldOnlyEvaluatedFeaturesWithEmptyVariableLists() {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator();
        evaluator.init(fixture.expressionModel.getResourceSet().getResources().get(0).getContents().stream()
                .filter(c -> c instanceof Expression).map(c -> (Expression) c).collect(Collectors.toList()));

        final QueryIndex index = built.getIndex();
        final Set<EAttribute> evaluatedAttributes = new HashSet<>();
        final Set<EReference> evaluatedReferences = new HashSet<>();

        for (Map.Entry<EClass, EntityTypeExpressions> entry : index.getEntityTypeExpressionsMap().entrySet()) {
            for (Map.Entry<EAttribute, DataExpression> getter : entry.getValue().getGetterAttributeExpressions().entrySet()) {
                evaluatedAttributes.add(getter.getKey());
                assertThat("static attribute: " + AsmUtils.getAttributeFQName(getter.getKey()),
                        index.getStaticEntityAttributes().contains(getter.getKey()),
                        equalTo(evaluator.getVariablesOfScope(getter.getValue()).isEmpty()));
            }
            for (Map.Entry<EReference, ReferenceExpression> getter : entry.getValue().getGetterReferenceExpressions().entrySet()) {
                evaluatedReferences.add(getter.getKey());
                assertThat("static reference: " + AsmUtils.getReferenceFQName(getter.getKey()),
                        index.getStaticEntityReferences().contains(getter.getKey()),
                        equalTo(evaluator.getVariablesOfScope(getter.getValue()).isEmpty()));
            }
        }

        // never evaluated features are in neither set
        assertThat(evaluatedAttributes.containsAll(index.getStaticEntityAttributes()), equalTo(true));
        assertThat(evaluatedReferences.containsAll(index.getStaticEntityReferences()), equalTo(true));
        assertThat(evaluatedAttributes.isEmpty(), equalTo(false));
        assertThat(evaluatedReferences.isEmpty(), equalTo(false));

        // and the data holds both kinds: static and non-static ones
        assertThat(Stream.concat(index.getStaticEntityAttributes().stream(), index.getStaticEntityReferences().stream()).count(), greaterThan(0L));
        assertThat(evaluatedAttributes.size() + evaluatedReferences.size()
                        - index.getStaticEntityAttributes().size() - index.getStaticEntityReferences().size(),
                greaterThan(0));
    }

    /**
     * TP-16 (in-memory part) and TP-18: restored index equals the built one, isStatic* for every ASM feature.
     * The save -> load leg of TP-16 belongs to the QueryModelPersistence tests.
     */
    @Test
    void restoredIndexEqualsBuiltIndex() {
        final QueryIndex index = built.getIndex();
        final QueryIndex restored = QueryIndex.restore(fixture.asmModel.getResourceSet(), built.getQueryModel());

        // queries of every mapped transfer type
        final List<EClass> classes = fixture.asmUtils.all(EClass.class).collect(Collectors.toList());
        int mapped = 0;
        for (EClass c : classes) {
            if (fixture.asmUtils.isMappedTransferObjectType(c)) {
                mapped++;
                assertThat(AsmUtils.getClassifierFQName(c), restored.getQuery(c).orElse(null), sameInstance(index.getQuery(c).orElse(null)));
            }
        }
        assertThat(mapped, greaterThan(0));
        assertThat(restored.getQueryTypes(), equalTo(index.getQueryTypes()));

        // navigations, data queries, ordering for every ASM reference / attribute
        final List<EReference> references = fixture.asmUtils.all(EReference.class).collect(Collectors.toList());
        final List<EAttribute> attributes = fixture.asmUtils.all(EAttribute.class).collect(Collectors.toList());
        for (EReference r : references) {
            assertThat(AsmUtils.getReferenceFQName(r), restored.getNavigation(r).orElse(null), sameInstance(index.getNavigation(r).orElse(null)));
            assertThat(AsmUtils.getReferenceFQName(r), restored.isOrdered(r), equalTo(index.isOrdered(r)));
        }
        for (EAttribute a : attributes) {
            assertThat(AsmUtils.getAttributeFQName(a), restored.getDataQuery(a).orElse(null), sameInstance(index.getDataQuery(a).orElse(null)));
        }
        assertThat(restored.getNavigationReferences(), equalTo(index.getNavigationReferences()));
        assertThat(restored.getDataQueryAttributes(), equalTo(index.getDataQueryAttributes()));
        assertThat(restored.getOrderedTransferRelations(), equalTo(index.getOrderedTransferRelations()));

        // counters
        assertThat(restored.getNextSourceIndex().get(), equalTo(index.getNextSourceIndex().get()));
        assertThat(restored.getNextTargetIndex().get(), equalTo(index.getNextTargetIndex().get()));

        // entity expressions: entry count and expression identity
        assertThat(restored.getEntityTypeExpressionsMap().size(), equalTo(index.getEntityTypeExpressionsMap().size()));
        for (Map.Entry<EClass, EntityTypeExpressions> entry : index.getEntityTypeExpressionsMap().entrySet()) {
            final EntityTypeExpressions other = restored.getEntityTypeExpressionsMap().get(entry.getKey());
            assertThat(other, not(nullValue()));
            assertThat(other.getGetterAttributeExpressions().size(), equalTo(entry.getValue().getGetterAttributeExpressions().size()));
            entry.getValue().getGetterAttributeExpressions().forEach((a, e) -> assertThat(other.getGetterAttributeExpressions().get(a), sameInstance(e)));
            assertThat(other.getGetterReferenceExpressions().size(), equalTo(entry.getValue().getGetterReferenceExpressions().size()));
            entry.getValue().getGetterReferenceExpressions().forEach((r, e) -> assertThat(other.getGetterReferenceExpressions().get(r), sameInstance(e)));
        }

        // TP-18: isStatic* for every declared ASM attribute / reference
        int mismatches = 0;
        for (EAttribute a : attributes) {
            if (restored.isStaticAttribute(a) != index.isStaticAttribute(a)) {
                mismatches++;
            }
        }
        for (EReference r : references) {
            if (restored.isStaticReference(r) != index.isStaticReference(r)) {
                mismatches++;
            }
        }
        assertThat("isStatic* mismatches", mismatches, equalTo(0));
        assertThat(attributes.size() + references.size(), greaterThan(0));
        assertThat(restored.getStaticEntityAttributes(), equalTo(index.getStaticEntityAttributes()));
        assertThat(restored.getStaticEntityReferences(), equalTo(index.getStaticEntityReferences()));
    }

    /**
     * Restore rule: transfer features without an entity binding are static, mapped ones follow the static set.
     */
    @Test
    void staticRuleIsMappedFeatureInSetOrTrue() {
        final QueryIndex index = built.getIndex();
        for (EAttribute a : fixture.asmUtils.all(EAttribute.class).collect(Collectors.toList())) {
            final boolean expected = fixture.asmUtils.getMappedAttribute(a).map(index.getStaticEntityAttributes()::contains).orElse(true);
            assertThat(AsmUtils.getAttributeFQName(a), index.isStaticAttribute(a), equalTo(expected));
        }
        for (EReference r : fixture.asmUtils.all(EReference.class).collect(Collectors.toList())) {
            final boolean expected = fixture.asmUtils.getMappedReference(r).map(index.getStaticEntityReferences()::contains).orElse(true);
            assertThat(AsmUtils.getReferenceFQName(r), index.isStaticReference(r), equalTo(expected));
        }
    }

    /**
     * An unset format-version stamp reads as a mismatch, never as a match.
     */
    @Test
    void restoreRejectsUnsetOrForeignFormatVersion() {
        final QueryModelInfo unset = QueryModelInfoBuilder.create().withModelName("x").build();
        assertThrows(IllegalStateException.class, () -> QueryIndex.restore(fixture.asmUtils, unset));

        final QueryModelInfo foreign = QueryModelInfoBuilder.create().withModelName("x").withFormatVersion(Asm2Query.FORMAT_VERSION + 1).build();
        assertThrows(IllegalStateException.class, () -> QueryIndex.restore(fixture.asmUtils, foreign));
    }

    /**
     * Restore fails (never picks the first) when the resource has no or several QueryModelInfo roots.
     */
    @Test
    void restoreFailsOnZeroOrManyInfoRoots() {
        final Asm2QueryResult own = Asm2Query.executeAsm2Query(fixture.asmModel, fixture.measureModel, fixture.expressionModel);

        final QueryModelInfo info = own.getQueryModelInfo();
        final QueryModelInfo second = QueryModelInfoBuilder.create()
                .withFormatVersion(Asm2Query.FORMAT_VERSION)
                .withModelName(info.getModelName())
                .build();
        own.getQueryModel().getResource().getContents().add(second);
        assertThrows(IllegalStateException.class, () -> QueryIndex.restore(fixture.asmModel.getResourceSet(), own.getQueryModel()));
        assertThrows(IllegalStateException.class, own::getQueryModelInfo);

        own.getQueryModel().getResource().getContents().remove(second);
        own.getQueryModel().getResource().getContents().remove(info);
        assertThat(own.getQueryModel().getResource().getContents().stream().noneMatch(QueryModelInfo.class::isInstance), equalTo(true));
        assertThrows(IllegalStateException.class, () -> QueryIndex.restore(fixture.asmModel.getResourceSet(), own.getQueryModel()));
    }

    @Test
    void builtModelHasQueriesNavigationsAndOrderedRelationsAsRuntimeCoreDid() {
        final QueryIndex index = built.getIndex();
        assertThat(index.getQueryTypes(), not(empty()));
        // every mapped entity transfer type has a query
        fixture.asmUtils.all(EClass.class)
                .filter(c -> fixture.asmUtils.getMappedEntityType(c).isPresent())
                .forEach(c -> assertThat(AsmUtils.getClassifierFQName(c), index.getQuery(c).isPresent(), equalTo(true)));
    }
}
