package hu.blackbelt.judo.tatami.psm2asm;

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

import com.google.common.collect.ImmutableList;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.derived.*;
import hu.blackbelt.judo.meta.psm.measure.*;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newActorTypeBuilder;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.newEntityTypeBuilder;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.*;
import static hu.blackbelt.judo.meta.psm.measure.util.builder.MeasureBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.*;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmDerivedTest {

	public static final String MODEL_NAME = "Test";
	public static final String TARGET_TEST_CLASSES = "target/test-classes";

	public static final String CONSTRAINTS_SOURCE = AsmUtils.getAnnotationUri("constraints");
	public static final String EXPRESSION_SOURCE = AsmUtils.getAnnotationUri("expression");

	PsmModel psmModel;
	PsmUtils psmUtils;
	AsmModel asmModel;
	AsmUtils asmUtils;

	@BeforeEach
	public void setUp() {
		// Loading PSM to isolated ResourceSet, because in Tatami
		// there is no new namespace registration made.
		psmModel = buildPsmModel().build();
		psmUtils = new PsmUtils();
		// Create empty ASM model
		asmModel = buildAsmModel().build();
		asmUtils = new AsmUtils(asmModel.getResourceSet());
	}

	private void transform(final String testName) throws Exception {
		psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
				.file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model")).build());

		log.info(psmModel.getDiagnosticsAsString());
		assertTrue(psmModel.isValid());
		try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
			validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
		}

		executePsm2AsmTransformation(psm2AsmParameter()
				.psmModel(psmModel)
				.asmModel(asmModel));

		log.info(asmModel.getDiagnosticsAsString());
		assertTrue(asmModel.isValid());
		asmModel.saveAsmModel(asmSaveArgumentsBuilder()
				.file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model")).build());
	}

	@Test
	void testDerived() throws Exception {

		StringType strType = newStringTypeBuilder().withName("string").withMaxLength(256).build();
		NumericType intType = newNumericTypeBuilder().withName("int").withPrecision(6).withScale(0).build();
		BooleanType boolType = newBooleanTypeBuilder().withName("bool").build();
		CustomType custom = newCustomTypeBuilder().withName("object").build();

		Unit unit = newUnitBuilder().withName("u").build();
		Measure m = newMeasureBuilder().withName("measure").withUnits(unit).build();
		MeasuredType measuredType = newMeasuredTypeBuilder().withName("measuredType").withStoreUnit(unit)
				.withPrecision(5).withScale(3).build();

		TransferObjectType parameter = newUnmappedTransferObjectTypeBuilder().withName("Parameter")
				.withAttributes(newTransferAttributeBuilder()
						.withName("x")
						.withDataType(strType)
						.build())
				.build();

		EntityType entity1 = newEntityTypeBuilder().withName("entity1").build();
		EntityType entity2 = newEntityTypeBuilder().withName("entity2").build();
		EntityType entity3 = newEntityTypeBuilder().withName("entity3").withSuperEntityTypes(ImmutableList.of(entity2))
				.build();

		DataProperty stringDataProperty = newDataPropertyBuilder().withName("a1").withDataType(strType)
				.withGetterExpression(newDataExpressionTypeBuilder().withExpression("self.a1").withParameterType(parameter)).build();
		DataProperty customDataProperty = newDataPropertyBuilder().withName("a2").withDataType(custom)
				.withGetterExpression(newDataExpressionTypeBuilder().withExpression("self.a2")).build();
		DataProperty boolDataProperty = newDataPropertyBuilder().withName("a3").withDataType(boolType)
				.withGetterExpression(newDataExpressionTypeBuilder().withExpression("self.a3")).build();
		DataProperty intDataProperty = newDataPropertyBuilder().withName("a4").withDataType(intType)
				.withGetterExpression(newDataExpressionTypeBuilder().withExpression("self.a4")).build();
		DataProperty measuredDataProperty = newDataPropertyBuilder().withName("a5").withDataType(measuredType)
				.withGetterExpression(newDataExpressionTypeBuilder().withExpression("self.a5")).build();

		entity1.getDataProperties().addAll(ImmutableList.of(stringDataProperty, customDataProperty, boolDataProperty,
				intDataProperty, measuredDataProperty));

		NavigationProperty navProp = newNavigationPropertyBuilder().withName("navProp")
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(1)).withTarget(entity3)
				.withGetterExpression(newReferenceExpressionTypeBuilder().withExpression("self.navProp")).build();

		entity1.getNavigationProperties().addAll(ImmutableList.of(navProp));

		Model model = newModelBuilder().withName("M").withElements(
				ImmutableList.of(entity1, entity2, entity3, strType, intType, boolType, custom, measuredType, parameter, m))
				.build();

		psmModel.addContent(model);

		transform("testDerived");

		final EClass asmEntity1 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entity1.getName()))
				.findAny().get();
		final EClass asmEntity3 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entity2.getName()))
				.findAny().get();
		final EClass asmEntity4 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entity3.getName()))
				.findAny().get();

		final String CUSTOM_TYPE_VALUE = psmUtils.namespaceElementToString(custom).replace("::", ".");
		final String MEASURE_VALUE = psmUtils.namespaceElementToString(measuredType.getStoreUnit().getMeasure())
				.replace("::", ".");
		final String MEASURE_UNIT_VALUE = measuredType.getStoreUnit().getName();

		final EDataType asmStr = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(strType.getName()))
				.findAny().get();
		final EDataType asmInt = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(intType.getName()))
				.findAny().get();
		final EDataType asmBoolType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(boolType.getName()))
				.findAny().get();
		final EDataType asmCustom = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(custom.getName()))
				.findAny().get();
		final EDataType asmMeasured = asmUtils.all(EDataType.class)
				.filter(e -> e.getName().equals(measuredType.getName())).findAny().get();

		final Optional<EAttribute> asmStrDataProperty = asmUtils.all(EAttribute.class)
				.filter(a -> a.getName().equals(stringDataProperty.getName())).findAny();
		final Optional<EAttribute> asmCustomDataProperty = asmUtils.all(EAttribute.class)
				.filter(a -> a.getName().equals(customDataProperty.getName())).findAny();
		final Optional<EAttribute> asmBoolDataProperty = asmUtils.all(EAttribute.class)
				.filter(a -> a.getName().equals(boolDataProperty.getName())).findAny();
		final Optional<EAttribute> asmIntDataProperty = asmUtils.all(EAttribute.class)
				.filter(a -> a.getName().equals(intDataProperty.getName())).findAny();
		final Optional<EAttribute> asmMeasuredDataProperty = asmUtils.all(EAttribute.class)
				.filter(a -> a.getName().equals(measuredDataProperty.getName())).findAny();

		assertTrue(asmStrDataProperty.isPresent());
		assertTrue(asmCustomDataProperty.isPresent());
		assertTrue(asmBoolDataProperty.isPresent());
		assertTrue(asmIntDataProperty.isPresent());
		assertTrue(asmMeasuredDataProperty.isPresent());

		assertThat(asmStrDataProperty.get().getLowerBound(), IsEqual.equalTo(0));
		assertThat(asmCustomDataProperty.get().getLowerBound(), IsEqual.equalTo(0));
		assertThat(asmBoolDataProperty.get().getLowerBound(), IsEqual.equalTo(0));
		assertThat(asmIntDataProperty.get().getLowerBound(), IsEqual.equalTo(0));
		assertThat(asmMeasuredDataProperty.get().getLowerBound(), IsEqual.equalTo(0));

		assertTrue(asmStrDataProperty.get().isDerived());
		assertTrue(asmCustomDataProperty.get().isDerived());
		assertTrue(asmBoolDataProperty.get().isDerived());
		assertTrue(asmIntDataProperty.get().isDerived());
		assertTrue(asmMeasuredDataProperty.get().isDerived());
		assertTrue(asmStrDataProperty.get().isVolatile());
		assertTrue(asmCustomDataProperty.get().isVolatile());
		assertTrue(asmBoolDataProperty.get().isVolatile());
		assertTrue(asmIntDataProperty.get().isVolatile());
		assertTrue(asmMeasuredDataProperty.get().isVolatile());

		assertTrue(asmStrDataProperty.get().getEType().equals(asmStr));
		assertTrue(asmCustomDataProperty.get().getEType().equals(asmCustom));
		assertTrue(asmBoolDataProperty.get().getEType().equals(asmBoolType));
		assertTrue(asmIntDataProperty.get().getEType().equals(asmInt));
		assertTrue(asmMeasuredDataProperty.get().getEType().equals(asmMeasured));

		assertTrue(asmStrDataProperty.get().getEContainingClass().equals(asmEntity1));
		assertTrue(asmCustomDataProperty.get().getEContainingClass().equals(asmEntity1));
		assertTrue(asmBoolDataProperty.get().getEContainingClass().equals(asmEntity1));
		assertTrue(asmIntDataProperty.get().getEContainingClass().equals(asmEntity1));
		assertTrue(asmMeasuredDataProperty.get().getEContainingClass().equals(asmEntity1));

		assertFalse(asmStrDataProperty.get().isChangeable());
		assertFalse(asmCustomDataProperty.get().isChangeable());
		assertFalse(asmBoolDataProperty.get().isChangeable());
		assertFalse(asmIntDataProperty.get().isChangeable());
		assertFalse(asmMeasuredDataProperty.get().isChangeable());

		final EAnnotation asmStrConstraint = asmStrDataProperty.get().getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmStrConstraint, IsNull.notNullValue());
		assertThat(asmStrConstraint.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmStrConstraint.getDetails().containsKey("maxLength"));
		assertTrue(asmStrConstraint.getDetails().get("maxLength").equals(String.valueOf(strType.getMaxLength())));
		final EAnnotation asmStrExpr = asmStrDataProperty.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmStrExpr, IsNull.notNullValue());
		assertThat(asmStrExpr.getDetails().size(), IsEqual.equalTo(3));
		assertTrue(asmStrExpr.getDetails().containsKey("getter"));
		assertTrue(
				asmStrExpr.getDetails().get("getter").equals(stringDataProperty.getGetterExpression().getExpression()));
		assertTrue(asmStrExpr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmStrExpr.getDetails().get("getter.dialect")
				.equals(stringDataProperty.getGetterExpression().getDialect().toString()));
		assertThat(asmStrExpr.getDetails().get("getter.parameter"), IsEqual.equalTo(model.getName() + "." + parameter.getName()));

		final EAnnotation asmCustomConstraint = asmCustomDataProperty.get().getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmCustomConstraint, IsNull.notNullValue());
		assertThat(asmCustomConstraint.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmCustomConstraint.getDetails().containsKey("customType"));
		assertTrue(asmCustomConstraint.getDetails().get("customType").equals(CUSTOM_TYPE_VALUE));
		final EAnnotation asmCustomExpr = asmCustomDataProperty.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmCustomExpr, IsNull.notNullValue());
		assertThat(asmCustomExpr.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmCustomExpr.getDetails().containsKey("getter"));
		assertTrue(asmCustomExpr.getDetails().get("getter")
				.equals(customDataProperty.getGetterExpression().getExpression()));
		assertTrue(asmCustomExpr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmCustomExpr.getDetails().get("getter.dialect")
				.equals(customDataProperty.getGetterExpression().getDialect().toString()));

		final EAnnotation asmIntConstraint = asmIntDataProperty.get().getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmIntConstraint, IsNull.notNullValue());
		assertThat(asmIntConstraint.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmIntConstraint.getDetails().containsKey("precision"));
		assertTrue(asmIntConstraint.getDetails().containsKey("scale"));
		assertTrue(asmIntConstraint.getDetails().get("precision").equals(String.valueOf(intType.getPrecision())));
		assertTrue(asmIntConstraint.getDetails().get("scale").equals(String.valueOf(intType.getScale())));
		final EAnnotation asmIntExpr = asmIntDataProperty.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmIntExpr, IsNull.notNullValue());
		assertThat(asmIntExpr.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmIntExpr.getDetails().containsKey("getter"));
		assertTrue(asmIntExpr.getDetails().get("getter").equals(intDataProperty.getGetterExpression().getExpression()));
		assertTrue(asmIntExpr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmIntExpr.getDetails().get("getter.dialect")
				.equals(intDataProperty.getGetterExpression().getDialect().toString()));

		final EAnnotation asmMeasuredConstraint = asmMeasuredDataProperty.get().getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmMeasuredConstraint, IsNull.notNullValue());
		assertThat(asmMeasuredConstraint.getDetails().size(), IsEqual.equalTo(4));
		assertTrue(asmMeasuredConstraint.getDetails().containsKey("precision"));
		assertTrue(asmMeasuredConstraint.getDetails().containsKey("scale"));
		assertTrue(asmMeasuredConstraint.getDetails().get("precision")
				.equals(String.valueOf(measuredType.getPrecision())));
		assertTrue(asmMeasuredConstraint.getDetails().get("scale").equals(String.valueOf(measuredType.getScale())));
		assertTrue(asmMeasuredConstraint.getDetails().containsKey("measure"));
		assertTrue(asmMeasuredConstraint.getDetails().containsKey("unit"));
		assertTrue(asmMeasuredConstraint.getDetails().get("measure").equals(MEASURE_VALUE));
		assertTrue(asmMeasuredConstraint.getDetails().get("unit").equals(MEASURE_UNIT_VALUE));
		final EAnnotation asmMeasuredExpr = asmMeasuredDataProperty.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmMeasuredExpr, IsNull.notNullValue());
		assertThat(asmMeasuredExpr.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmMeasuredExpr.getDetails().containsKey("getter"));
		assertTrue(asmMeasuredExpr.getDetails().get("getter")
				.equals(measuredDataProperty.getGetterExpression().getExpression()));
		assertTrue(asmMeasuredExpr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmMeasuredExpr.getDetails().get("getter.dialect")
				.equals(measuredDataProperty.getGetterExpression().getDialect().toString()));

		final Optional<EReference> asmNavProp = asmUtils.all(EReference.class)
				.filter(r -> r.getName().equals(navProp.getName())).findAny();
		assertTrue(asmNavProp.isPresent());
		assertTrue(asmNavProp.get().getEContainingClass().equals(asmEntity1));
		assertThat(asmNavProp.get().getLowerBound(), IsEqual.equalTo(navProp.getCardinality().getLower()));
		assertThat(asmNavProp.get().getUpperBound(), IsEqual.equalTo(navProp.getCardinality().getUpper()));
		assertTrue(asmNavProp.get().getEType().equals(asmEntity4));
		assertTrue(asmNavProp.get().isDerived());
		assertTrue(asmNavProp.get().isVolatile());
		assertFalse(asmNavProp.get().isChangeable());
		final EAnnotation asmNavPropExpr = asmNavProp.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmNavPropExpr, IsNull.notNullValue());
		assertThat(asmNavPropExpr.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmNavPropExpr.getDetails().containsKey("getter"));
		assertTrue(asmNavPropExpr.getDetails().get("getter").equals(navProp.getGetterExpression().getExpression()));
		assertTrue(asmNavPropExpr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmNavPropExpr.getDetails().get("getter.dialect")
				.equals(navProp.getGetterExpression().getDialect().toString()));
	}
	
	@Test
	void testDerivedInUnmappedTransferObjectTypes() throws Exception {
		
		Package defaultTo = newPackageBuilder().withName("_default_transferobjecttypes").build();
		Package genNav = newPackageBuilder().withName("_generated_navigations").build();
		Package actorTypes = newPackageBuilder().withName("_actortypes").build();
		
		EntityType entity = newEntityTypeBuilder().withName("E").build();
		EntityType entity2 = newEntityTypeBuilder().withName("E2").build();
		StringType string = newStringTypeBuilder().withName("string").withMaxLength(256).build();
		MappedTransferObjectType mapped = newMappedTransferObjectTypeBuilder().withName("M").withEntityType(entity).build();
		MappedTransferObjectType defaultForEntity = newMappedTransferObjectTypeBuilder().withName("E").withEntityType(entity).build();
		MappedTransferObjectType defaultForEntity2 = newMappedTransferObjectTypeBuilder().withName("E2").withEntityType(entity2).build();
		UnmappedTransferObjectType unmapped = newUnmappedTransferObjectTypeBuilder().withName("U").build();
		
		UnmappedTransferObjectType ap = newUnmappedTransferObjectTypeBuilder().withName("AP").build();
		
		StaticData staticData = newStaticDataBuilder().withName("_derivedAttribute_binding_U").withDataType(string).withGetterExpression(newDataExpressionTypeBuilder().withExpression("text").build()).build();
		TransferAttribute attribute = newTransferAttributeBuilder().withName("derivedAttribute").withDataType(string).withBinding(staticData).build();
		
		useUnmappedTransferObjectType(unmapped).withAttributes(attribute).build();
		
		ActorType actorType = newActorTypeBuilder().withName("AP").withTransferObjectType(ap).build();
		
		useUnmappedTransferObjectType(ap).withActorType(actorType).build();
	    
	    StaticNavigation statNav = newStaticNavigationBuilder().withName("_accessToM_selector_AP")
	    	.withTarget(entity).withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build())
	    	.withGetterExpression(newReferenceExpressionTypeBuilder().withExpression("TestModel::E").build()).build();
		
		TransferObjectRelation rel = newTransferObjectRelationBuilder().withName("accessToM").withBinding(statNav).withTarget(mapped)
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build()).build();
		
		useUnmappedTransferObjectType(ap).withRelations(rel).build();
		
		StaticNavigation statNav2 = newStaticNavigationBuilder().withName("_E2_selector_U")
		    	.withTarget(entity2).withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build())
		    	.withGetterExpression(newReferenceExpressionTypeBuilder().withExpression("TestModel::E2").build()).build();
		
		TransferObjectRelation rel2 = newTransferObjectRelationBuilder().withName("E2").withBinding(statNav2).withTarget(defaultForEntity2)
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build()).build();
		
		useUnmappedTransferObjectType(unmapped).withRelations(rel2).build();
		
		usePackage(actorTypes).withElements(actorType).build();
		usePackage(genNav).withElements(statNav,statNav2).build();
		usePackage(defaultTo).withElements(defaultForEntity,defaultForEntity2).build();
		
		Model model = newModelBuilder().withName("TestModel")
				.withElements(string,entity,mapped,unmapped,ap,staticData,entity2)
				.withPackages(actorTypes,genNav,defaultTo)
				.build();

		psmModel.addContent(model);

		transform("testDerivedMixin");
		
		final EClass asmUnmapped = asmUtils.all(EClass.class).filter(c -> c.getName().equals(unmapped.getName()))
				.findAny().get();
		
		final Optional<EReference> asmUnmappedDerivedRelation = asmUtils.all(EReference.class)
				.filter(r -> r.getName().equals(rel2.getName())).findAny();
		assertTrue(asmUnmappedDerivedRelation.isPresent());
		assertTrue(asmUnmappedDerivedRelation.get().getEContainingClass().equals(asmUnmapped));
		
		final EAnnotation asmUnmappedDerivedExpr = asmUnmappedDerivedRelation.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmUnmappedDerivedExpr, IsNull.notNullValue());
		assertThat(asmUnmappedDerivedExpr.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmUnmappedDerivedExpr.getDetails().containsKey("getter"));
		assertTrue(asmUnmappedDerivedExpr.getDetails().get("getter").equals(statNav2.getGetterExpression().getExpression()));
		assertTrue(asmUnmappedDerivedExpr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmUnmappedDerivedExpr.getDetails().get("getter.dialect")
				.equals(statNav2.getGetterExpression().getDialect().toString()));
		
		final Optional<EAttribute> asmUnmappedDerivedAttribute = asmUtils.all(EAttribute.class)
				.filter(a -> a.getName().equals(attribute.getName())).findAny();
		assertTrue(asmUnmappedDerivedAttribute.isPresent());
		assertTrue(asmUnmappedDerivedAttribute.get().getEContainingClass().equals(asmUnmapped));
		
		final EAnnotation asmUnmappedDerivedAttr = asmUnmappedDerivedAttribute.get().getEAnnotation(EXPRESSION_SOURCE);
		assertThat(asmUnmappedDerivedAttr, IsNull.notNullValue());
		assertThat(asmUnmappedDerivedAttr.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmUnmappedDerivedAttr.getDetails().containsKey("getter"));
		assertTrue(asmUnmappedDerivedAttr.getDetails().get("getter").equals(staticData.getGetterExpression().getExpression()));
		assertTrue(asmUnmappedDerivedAttr.getDetails().containsKey("getter.dialect"));
		assertTrue(asmUnmappedDerivedAttr.getDetails().get("getter.dialect")
				.equals(statNav2.getGetterExpression().getDialect().toString()));
	}
}
