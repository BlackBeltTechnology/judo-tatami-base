package hu.blackbelt.judo.tatami.psm2asm;

import com.google.common.collect.ImmutableList;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.data.*;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.measure.*;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
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
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newReferenceExpressionTypeBuilder;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newStaticNavigationBuilder;
import static hu.blackbelt.judo.meta.psm.measure.util.builder.MeasureBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.calculatePsm2AsmTransformationScriptURI;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmServiceTest {

	public static final String MODEL_NAME = "Test";
	public static final String TARGET_TEST_CLASSES = "target/test-classes";

	public static final String MAPPED_SOURCE = AsmUtils.getAnnotationUri("mappedEntityType");
	public static final String CONSTRAINTS_SOURCE = AsmUtils.getAnnotationUri("constraints");
	public static final String BINDING_SOURCE = AsmUtils.getAnnotationUri("binding");
	public static final String EMBEDDED_SOURCE = AsmUtils.getAnnotationUri("embedded");
	public static final String PERMISSIONS_SOURCE = AsmUtils.getAnnotationUri("permissions");
	public static final String SCRIPT_SOURCE = AsmUtils.getAnnotationUri("script");
	public static final String OUTPUTPARAMETERNAME_SOURCE = AsmUtils.getAnnotationUri("outputParameterName");
	public static final String STATEFUL_SOURCE = AsmUtils.getAnnotationUri("stateful");
	public static final String INSTANCEREPRESENTATION_SOURCE = AsmUtils.getAnnotationUri("instanceRepresentation");
	public static final String BOUND_SOURCE = AsmUtils.getAnnotationUri("bound");
	public static final String ABSTRACT_SOURCE = AsmUtils.getAnnotationUri("abstract");
	public static final String INITIALIZER_SOURCE = AsmUtils.getAnnotationUri("initializer");
	public static final String BEHAVIOUR_SOURCE = AsmUtils.getAnnotationUri("behaviour");
	public static final String TRANSFER_OBJECT_TYPE = AsmUtils.getAnnotationUri("transferObjectType");
	public static final String TRANSIENT_SOURCE = AsmUtils.getAnnotationUri("transient");

	PsmModel psmModel;
	PsmUtils psmUtils;
	AsmModel asmModel;
	AsmUtils asmUtils;

	@BeforeEach
	public void setUp() {
		// Loading PSM to isolated ResourceSet, because in Tatami
		// there is no new namespace registration made.
		psmModel = buildPsmModel().name(MODEL_NAME).build();
		psmUtils = new PsmUtils();
		// Create empty ASM model
		asmModel = buildAsmModel().name(MODEL_NAME).build();
		asmUtils = new AsmUtils(asmModel.getResourceSet());
	}

	private void transform(final String testName) throws Exception {
		psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
				.file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model")).build());

		assertTrue(psmModel.isValid());
		try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
			validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());

			executePsm2AsmTransformation(psm2AsmParameter()
					.psmModel(psmModel)
					.asmModel(asmModel)
					.log(bufferedLog)
					.scriptUri(calculatePsm2AsmTransformationScriptURI()));
		}

		assertTrue(asmModel.isValid());
		asmModel.saveAsmModel(asmSaveArgumentsBuilder()
				.file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model")).build());
	}

	@Test
	void testTransferObject() throws Exception {

		StringType strType = newStringTypeBuilder().withName("string").withMaxLength(256).build();
		NumericType intType = newNumericTypeBuilder().withName("int").withPrecision(6).withScale(0).build();
		BooleanType boolType = newBooleanTypeBuilder().withName("bool").build();
		CustomType custom = newCustomTypeBuilder().withName("object").build();
		Unit unit = newUnitBuilder().withName("u").build();
		Measure m = newMeasureBuilder().withName("measure").withUnits(unit).build();
		MeasuredType measuredType = newMeasuredTypeBuilder().withName("measuredType").withStoreUnit(unit)
				.withPrecision(5).withScale(3).build();

		EntityType entity1 = newEntityTypeBuilder().withName("entity1").build();
		EntityType entity2 = newEntityTypeBuilder().withName("entity2").build();
		EntityType entity3 = newEntityTypeBuilder().withName("entity3").withSuperEntityTypes(ImmutableList.of(entity1))
				.build();
		EntityType entity4 = newEntityTypeBuilder().withName("entity4").withSuperEntityTypes(ImmutableList.of(entity3))
				.build();
		EntityType entity5 = newEntityTypeBuilder().withName("entity5").build();

		Attribute boolAttr = newAttributeBuilder().withName("a3").withDataType(boolType).build();
		entity1.getAttributes().addAll(ImmutableList.of(boolAttr));

		AssociationEnd association = newAssociationEndBuilder().withName("association")
				.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1)).withTarget(entity2).build();
		entity1.getRelations().addAll(ImmutableList.of(association));

		UnmappedTransferObjectType unmapped = newUnmappedTransferObjectTypeBuilder().withName("unmapped").build();
		MappedTransferObjectType mapped1 = newMappedTransferObjectTypeBuilder().withName("mapped1")
				.withEntityType(entity1).build();
		MappedTransferObjectType mapped2 = newMappedTransferObjectTypeBuilder().withName("mapped2")
				.withEntityType(entity2).build();
		MappedTransferObjectType mapped3 = newMappedTransferObjectTypeBuilder().withName("mapped3")
				.withEntityType(entity3)
				.build();
		MappedTransferObjectType mapped4 = newMappedTransferObjectTypeBuilder().withName("mapped4")
				.withEntityType(entity4)
				.build();
		MappedTransferObjectType mapped5 = newMappedTransferObjectTypeBuilder().withName("mapped5")
				.withEntityType(entity5).build();

		TransferAttribute stringTransferAttr = newTransferAttributeBuilder().withName("ta1").withDataType(strType)
				.withRequired(true).build();
		TransferAttribute customTransferAttr = newTransferAttributeBuilder().withName("ta2").withDataType(custom)
				.build();
		TransferAttribute boolTransferAttr = newTransferAttributeBuilder().withName("ta3").withDataType(boolType)
				.withBinding(boolAttr).build();
		TransferAttribute intTransferAttr = newTransferAttributeBuilder().withName("ta4").withDataType(intType)
				.withRequired(true).build();
		TransferAttribute measuredTransferAttr = newTransferAttributeBuilder().withName("ta5")
				.withDataType(measuredType).build();

		mapped1.getAttributes().addAll(ImmutableList.of(stringTransferAttr, customTransferAttr, boolTransferAttr,
				intTransferAttr, measuredTransferAttr));
		
		TransferAttribute stringTransferAttr2 = newTransferAttributeBuilder().withName("ta1").withDataType(strType)
				.withRequired(true).build();
		TransferAttribute customTransferAttr2 = newTransferAttributeBuilder().withName("ta2").withDataType(custom)
				.build();
		TransferAttribute boolTransferAttr2 = newTransferAttributeBuilder().withName("ta3").withDataType(boolType)
				.withBinding(boolAttr).build();
		TransferAttribute intTransferAttr2 = newTransferAttributeBuilder().withName("ta4").withDataType(intType)
				.withRequired(true).build();
		TransferAttribute measuredTransferAttr2 = newTransferAttributeBuilder().withName("ta5")
				.withDataType(measuredType).build();

		mapped3.getAttributes().addAll(ImmutableList.of(stringTransferAttr2, customTransferAttr2, boolTransferAttr2,
				intTransferAttr2, measuredTransferAttr2));
		
		TransferAttribute stringTransferAttr3 = newTransferAttributeBuilder().withName("ta1").withDataType(strType)
				.withRequired(true).build();
		TransferAttribute customTransferAttr3 = newTransferAttributeBuilder().withName("ta2").withDataType(custom)
				.build();
		TransferAttribute boolTransferAttr3 = newTransferAttributeBuilder().withName("ta3").withDataType(boolType)
				.withBinding(boolAttr).build();
		TransferAttribute intTransferAttr3 = newTransferAttributeBuilder().withName("ta4").withDataType(intType)
				.withRequired(true).build();
		TransferAttribute measuredTransferAttr3 = newTransferAttributeBuilder().withName("ta5")
				.withDataType(measuredType).build();

		mapped4.getAttributes().addAll(ImmutableList.of(stringTransferAttr3, customTransferAttr3, boolTransferAttr3,
				intTransferAttr3, measuredTransferAttr3));

		TransferObjectRelation tr1 = newTransferObjectRelationBuilder().withName("tr1").withBinding(association)
				.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1)).withTarget(mapped2).build();
		TransferObjectRelation tr2 = newTransferObjectRelationBuilder().withName("tr2")
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1)).withTarget(mapped2).build();
		TransferObjectRelation tr3 = newTransferObjectRelationBuilder().withName("tr3").withEmbedded(true)
				.withEmbeddedCreate(true).withEmbeddedUpdate(true)
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(1)).withTarget(mapped5).build();

		mapped1.getRelations().addAll(ImmutableList.of(tr1, tr2, tr3));

		Model model = newModelBuilder().withName("M")
				.withElements(ImmutableList.of(entity1, entity2, entity3, entity4, entity5, strType, intType, boolType,
						custom, measuredType, m, unmapped, mapped1, mapped2, mapped3, mapped4, mapped5))
				.build();

		psmModel.addContent(model);

		transform("testTransferObject");

		final EPackage asmModel = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(model.getName()))
				.findAny().get();

		final Optional<EClass> asmEntity1 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entity1.getName()))
				.findAny();
		
		assertTrue(asmEntity1.isPresent());
		
		final Optional<EClass> asmEntity1Reference = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entity1.getName() + "__Reference"))
				.findAny();
		
		assertTrue(asmEntity1Reference.isPresent());
		
		final EAnnotation asmTransferObjectTypeAnnotation2 = asmEntity1Reference.get().getEAnnotation(TRANSFER_OBJECT_TYPE);
		assertThat(asmTransferObjectTypeAnnotation2, IsNull.notNullValue());
		assertTrue(asmTransferObjectTypeAnnotation2.getDetails().containsKey("value"));
		assertTrue(asmTransferObjectTypeAnnotation2.getDetails().get("value").equals("true"));
		assertTrue(asmTransferObjectTypeAnnotation2.getEModelElement().equals(asmEntity1Reference.get()));

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

		final Optional<EClass> asmUnmapped = asmUtils.all(EClass.class)
				.filter(c -> c.getName().equals(unmapped.getName())).findAny();
		assertTrue(asmUnmapped.isPresent());
		assertTrue(asmUnmapped.get().getEPackage().equals(asmModel));
		assertFalse(asmUnmapped.get().isAbstract());
		assertFalse(asmUnmapped.get().isInterface());
		assertTrue(asmUnmapped.get().getESuperTypes().isEmpty());
		assertTrue(asmUnmapped.get().getEOperations().isEmpty());
		assertTrue(asmUnmapped.get().getEStructuralFeatures().isEmpty());
		assertTrue(asmUnmapped.get().getEReferences().isEmpty());
		assertTrue(asmUnmapped.get().getEAttributes().isEmpty());

		final EAnnotation asmTransferObjectTypeAnnotation3 = asmUnmapped.get().getEAnnotation(TRANSFER_OBJECT_TYPE);
		assertThat(asmTransferObjectTypeAnnotation3, IsNull.notNullValue());
		assertTrue(asmTransferObjectTypeAnnotation3.getDetails().containsKey("value"));
		assertTrue(asmTransferObjectTypeAnnotation3.getDetails().get("value").equals("true"));
		assertTrue(asmTransferObjectTypeAnnotation3.getEModelElement().equals(asmUnmapped.get()));
		
		final Optional<EClass> asmMapped1 = asmUtils.all(EClass.class)
				.filter(c -> c.getName().equals(mapped1.getName())).findAny();
		assertTrue(asmMapped1.isPresent());
		assertTrue(asmMapped1.get().getEPackage().equals(asmModel));
		assertFalse(asmMapped1.get().isAbstract());
		assertFalse(asmMapped1.get().isInterface());
		
		final EAnnotation asmTransferObjectTypeAnnotation4 = asmMapped1.get().getEAnnotation(TRANSFER_OBJECT_TYPE);
		assertThat(asmTransferObjectTypeAnnotation4, IsNull.notNullValue());
		assertTrue(asmTransferObjectTypeAnnotation4.getDetails().containsKey("value"));
		assertTrue(asmTransferObjectTypeAnnotation4.getDetails().get("value").equals("true"));
		assertTrue(asmTransferObjectTypeAnnotation4.getEModelElement().equals(asmMapped1.get()));

		final EAnnotation asmMapped1Annotation = asmMapped1.get().getEAnnotation(MAPPED_SOURCE);
		assertThat(asmMapped1Annotation, IsNull.notNullValue());
		assertTrue(asmMapped1Annotation.getDetails().containsKey("value"));
		assertTrue(asmMapped1Annotation.getDetails().get("value").equals(asmUtils.getClassifierFQName(asmEntity1.get())));
		assertTrue(asmMapped1Annotation.getEModelElement().equals(asmMapped1.get()));

		final Optional<EClass> refEClass1 = asmUtils.all(EClass.class)
				.filter(c -> c.getName().equals(entity1.getName() + "__" + "Reference")).findAny();

		assertTrue(refEClass1.isPresent());
		final EAnnotation refEClass1Annotation = refEClass1.get().getEAnnotation(MAPPED_SOURCE);
		assertThat(refEClass1Annotation, IsNull.notNullValue());
		assertTrue(asmMapped1Annotation.getDetails().containsKey("value"));
		assertTrue(asmMapped1Annotation.getDetails().get("value").equals(asmUtils.getClassifierFQName(asmEntity1.get())));
		assertTrue(asmMapped1Annotation.getEModelElement().equals(asmMapped1.get()));

		final EClass asmMapped2 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(mapped2.getName()))
				.findAny().get();
		final EClass asmMapped5 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(mapped5.getName()))
				.findAny().get();

		final EClass refEClass3 = asmUtils.all(EClass.class)
				.filter(c -> c.getName().equals(entity3.getName() + "__" + "Reference")).findAny().get();
		final EClass refEClass4 = asmUtils.all(EClass.class)
				.filter(c -> c.getName().equals(entity4.getName() + "__" + "Reference")).findAny().get();
		assertTrue(refEClass4.getESuperTypes().contains(refEClass3));
		assertTrue(refEClass4.getEAllSuperTypes().contains(refEClass3));
		assertTrue(refEClass4.getEAllSuperTypes().contains(refEClass1.get()));

		final String CUSTOM_TYPE_VALUE = psmUtils.namespaceElementToString(custom).replace("::", ".");
		final String MEASURE_VALUE = psmUtils.namespaceElementToString(measuredType.getStoreUnit().getMeasure())
				.replace("::", ".");
		final String MEASURE_UNIT_VALUE = measuredType.getStoreUnit().getName();

		final Optional<EAttribute> asmStringTransferAttr = asmUtils.all(EAttribute.class)
				.filter(c -> c.getName().equals(stringTransferAttr.getName())).findAny();
		final Optional<EAttribute> asmCustomTransferAttr = asmUtils.all(EAttribute.class)
				.filter(c -> c.getName().equals(customTransferAttr.getName())).findAny();
		final Optional<EAttribute> asmBoolTransferAttr = asmUtils.all(EAttribute.class)
				.filter(c -> c.getName().equals(boolTransferAttr.getName())).findAny();
		final Optional<EAttribute> asmIntTransferAttr = asmUtils.all(EAttribute.class)
				.filter(c -> c.getName().equals(intTransferAttr.getName())).findAny();
		final Optional<EAttribute> asmMeasuredTransferAttr = asmUtils.all(EAttribute.class)
				.filter(c -> c.getName().equals(measuredTransferAttr.getName())).findAny();

		assertTrue(asmStringTransferAttr.isPresent());
		assertTrue(asmCustomTransferAttr.isPresent());
		assertTrue(asmBoolTransferAttr.isPresent());
		assertTrue(asmIntTransferAttr.isPresent());
		assertTrue(asmMeasuredTransferAttr.isPresent());

		assertThat(asmStringTransferAttr.get().getLowerBound(), IsEqual.equalTo(1));
		assertThat(asmCustomTransferAttr.get().getLowerBound(), IsEqual.equalTo(0));
		assertThat(asmBoolTransferAttr.get().getLowerBound(), IsEqual.equalTo(0));
		assertThat(asmIntTransferAttr.get().getLowerBound(), IsEqual.equalTo(1));
		assertThat(asmMeasuredTransferAttr.get().getLowerBound(), IsEqual.equalTo(0));

		assertTrue(asmStringTransferAttr.get().getEType().equals(asmStr));
		assertTrue(asmCustomTransferAttr.get().getEType().equals(asmCustom));
		assertTrue(asmBoolTransferAttr.get().getEType().equals(asmBoolType));
		assertTrue(asmIntTransferAttr.get().getEType().equals(asmInt));
		assertTrue(asmMeasuredTransferAttr.get().getEType().equals(asmMeasured));

		assertTrue(asmStringTransferAttr.get().getEContainingClass().equals(asmMapped1.get()));
		assertTrue(asmCustomTransferAttr.get().getEContainingClass().equals(asmMapped1.get()));
		assertTrue(asmBoolTransferAttr.get().getEContainingClass().equals(asmMapped1.get()));
		assertTrue(asmIntTransferAttr.get().getEContainingClass().equals(asmMapped1.get()));
		assertTrue(asmMeasuredTransferAttr.get().getEContainingClass().equals(asmMapped1.get()));

		assertFalse(asmStringTransferAttr.get().isDerived());
		assertFalse(asmCustomTransferAttr.get().isDerived());
		assertFalse(asmBoolTransferAttr.get().isDerived());
		assertFalse(asmIntTransferAttr.get().isDerived());
		assertFalse(asmMeasuredTransferAttr.get().isDerived());

		assertTrue(asmStringTransferAttr.get().isChangeable());
		assertTrue(asmCustomTransferAttr.get().isChangeable());
		assertTrue(asmBoolTransferAttr.get().isChangeable());
		assertTrue(asmIntTransferAttr.get().isChangeable());
		assertTrue(asmMeasuredTransferAttr.get().isChangeable());

		final EAnnotation asmStringTransferAttrAnnotation = asmStringTransferAttr.get()
				.getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmStringTransferAttrAnnotation, IsNull.notNullValue());
		assertThat(asmStringTransferAttrAnnotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmStringTransferAttrAnnotation.getDetails().containsKey("maxLength"));
		assertTrue(asmStringTransferAttrAnnotation.getDetails().get("maxLength")
				.equals(String.valueOf(strType.getMaxLength())));
		
		final EAnnotation asmStringTransferAttrTransient = asmStringTransferAttr.get()
				.getEAnnotation(TRANSIENT_SOURCE);
		assertThat(asmStringTransferAttrTransient, IsNull.notNullValue());

		final EAnnotation asmCustomTransferAttrAnnotation = asmCustomTransferAttr.get()
				.getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmCustomTransferAttrAnnotation, IsNull.notNullValue());
		assertThat(asmCustomTransferAttrAnnotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmCustomTransferAttrAnnotation.getDetails().containsKey("customType"));
		assertTrue(asmCustomTransferAttrAnnotation.getDetails().get("customType").equals(CUSTOM_TYPE_VALUE));

		final EAnnotation asmBoolTransferAttrAttrAnnotation = asmBoolTransferAttr.get().getEAnnotation(BINDING_SOURCE);
		assertThat(asmBoolTransferAttrAttrAnnotation, IsNull.notNullValue());
		assertThat(asmBoolTransferAttrAttrAnnotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoolTransferAttrAttrAnnotation.getDetails().containsKey("value"));
		assertTrue(asmBoolTransferAttrAttrAnnotation.getDetails().get("value").equals(boolAttr.getName()));
		final EAnnotation asmBoolTransferAttrTransient = asmBoolTransferAttr.get()
				.getEAnnotation(TRANSIENT_SOURCE);
		assertThat(asmBoolTransferAttrTransient, IsNull.nullValue());

		final EAnnotation asmIntTransferAttrAnnotation = asmIntTransferAttr.get().getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmIntTransferAttrAnnotation, IsNull.notNullValue());
		assertThat(asmIntTransferAttrAnnotation.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmIntTransferAttrAnnotation.getDetails().containsKey("precision"));
		assertTrue(asmIntTransferAttrAnnotation.getDetails().containsKey("scale"));
		assertTrue(asmIntTransferAttrAnnotation.getDetails().get("precision")
				.equals(String.valueOf(intType.getPrecision())));
		assertTrue(asmIntTransferAttrAnnotation.getDetails().get("scale").equals(String.valueOf(intType.getScale())));

		final EAnnotation asmMeasuredTransferAttrAnnotation = asmMeasuredTransferAttr.get()
				.getEAnnotation(CONSTRAINTS_SOURCE);
		assertThat(asmMeasuredTransferAttrAnnotation, IsNull.notNullValue());
		assertThat(asmMeasuredTransferAttrAnnotation.getDetails().size(), IsEqual.equalTo(4));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().containsKey("precision"));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().containsKey("scale"));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().get("precision")
				.equals(String.valueOf(measuredType.getPrecision())));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().get("scale")
				.equals(String.valueOf(measuredType.getScale())));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().containsKey("measure"));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().containsKey("unit"));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().get("measure").equals(MEASURE_VALUE));
		assertTrue(asmMeasuredTransferAttrAnnotation.getDetails().get("unit").equals(MEASURE_UNIT_VALUE));

		final Optional<EReference> asmTR1 = asmUtils.all(EReference.class)
				.filter(r -> r.getName().equals(tr1.getName())).findAny();
		final Optional<EReference> asmTR2 = asmUtils.all(EReference.class)
				.filter(r -> r.getName().equals(tr2.getName())).findAny();
		final Optional<EReference> asmTR3 = asmUtils.all(EReference.class)
				.filter(r -> r.getName().equals(tr3.getName())).findAny();

		assertTrue(asmTR1.isPresent());
		assertTrue(asmTR2.isPresent());
		assertTrue(asmTR3.isPresent());

		assertTrue(asmTR1.get().getEContainingClass().equals(asmMapped1.get()));
		assertTrue(asmTR2.get().getEContainingClass().equals(asmMapped1.get()));
		assertTrue(asmTR3.get().getEContainingClass().equals(asmMapped1.get()));

		assertThat(asmTR1.get().getLowerBound(), IsEqual.equalTo(tr1.getCardinality().getLower()));
		assertThat(asmTR2.get().getLowerBound(), IsEqual.equalTo(tr2.getCardinality().getLower()));
		assertThat(asmTR3.get().getLowerBound(), IsEqual.equalTo(tr3.getCardinality().getLower()));

		assertThat(asmTR1.get().getUpperBound(), IsEqual.equalTo(tr1.getCardinality().getUpper()));
		assertThat(asmTR2.get().getUpperBound(), IsEqual.equalTo(tr2.getCardinality().getUpper()));
		assertThat(asmTR3.get().getUpperBound(), IsEqual.equalTo(tr3.getCardinality().getUpper()));

		assertTrue(asmTR1.get().getEType().equals(asmMapped2));
		assertTrue(asmTR2.get().getEType().equals(asmMapped2));
		assertTrue(asmTR3.get().getEType().equals(asmMapped5));

		assertFalse(asmTR1.get().isContainment());
		assertFalse(asmTR2.get().isContainment());
		assertTrue(asmTR3.get().isContainment());

		assertFalse(asmTR1.get().isDerived());
		assertFalse(asmTR2.get().isDerived());
		assertFalse(asmTR3.get().isDerived());

		assertTrue(asmTR1.get().isChangeable());
		assertTrue(asmTR2.get().isChangeable());
		assertTrue(asmTR3.get().isChangeable());

		final EAnnotation asmTR1Annotation = asmTR1.get().getEAnnotation(BINDING_SOURCE);
		assertThat(asmTR1Annotation, IsNull.notNullValue());
		assertThat(asmTR1Annotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmTR1Annotation.getDetails().containsKey("value"));
		assertTrue(asmTR1Annotation.getDetails().get("value").equals(association.getName()));
		final EAnnotation asmTR1Transient = asmTR1.get()
				.getEAnnotation(TRANSIENT_SOURCE);
		assertThat(asmTR1Transient, IsNull.nullValue());
		
		final EAnnotation asmTR2Transient = asmTR2.get()
				.getEAnnotation(TRANSIENT_SOURCE);
		assertThat(asmTR2Transient, IsNull.notNullValue());

		final EAnnotation asmTR3Annotation = asmTR3.get().getEAnnotation(EMBEDDED_SOURCE);
		assertThat(asmTR3Annotation, IsNull.notNullValue());
		assertTrue(asmTR3Annotation.getDetails().containsKey("create"));
		assertTrue(asmTR3Annotation.getDetails().containsKey("update"));
		assertTrue(asmTR3Annotation.getDetails().containsKey("delete"));
		assertTrue(asmTR3Annotation.getDetails().get("create").equals(String.valueOf(tr3.isEmbeddedCreate())));
		assertTrue(asmTR3Annotation.getDetails().get("update").equals(String.valueOf(tr3.isEmbeddedUpdate())));
		assertTrue(asmTR3Annotation.getDetails().get("delete").equals(String.valueOf(tr3.isEmbeddedDelete())));
	}

	@Test
	void testOperation() throws Exception {

		EntityType p = newEntityTypeBuilder().withName("p").withAbstract_(true).build();
		EntityType e1 = newEntityTypeBuilder().withName("e1").build();
		EntityType ch = newEntityTypeBuilder().withName("ch").withSuperEntityTypes(e1).build();
		EntityType e2 = newEntityTypeBuilder().withName("e2").build();
		EntityType e3 = newEntityTypeBuilder().withName("e3").build();

		MappedTransferObjectType pt = newMappedTransferObjectTypeBuilder().withName("pt").withEntityType(p).build();
		MappedTransferObjectType t1 = newMappedTransferObjectTypeBuilder().withName("t1").withEntityType(e1).build();
		MappedTransferObjectType ct = newMappedTransferObjectTypeBuilder().withName("ct")
				.withEntityType(ch)
				.build();
		MappedTransferObjectType type = newMappedTransferObjectTypeBuilder().withName("type").withEntityType(e2)
				.build();
		MappedTransferObjectType t3 = newMappedTransferObjectTypeBuilder().withName("t3").withEntityType(e3).build();

		StaticNavigation sn = newStaticNavigationBuilder().withName("sel").withTarget(e1)
				.withGetterExpression(newReferenceExpressionTypeBuilder().withExpression("model::e1"))
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build()).build();
		TransferObjectRelation owner = newTransferObjectRelationBuilder().withName("eg")
				.withTarget(t1)
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build()).build();
		ActorType actorType = newActorTypeBuilder().withName("actor").build();
		TransferObjectType ap = newUnmappedTransferObjectTypeBuilder().withName("ap")
				.withActorType(actorType)
				.withRelations(owner).build();
		actorType.setTransferObjectType(ap);

		TransferObjectRelation ownerRel = newTransferObjectRelationBuilder().withName("owner").withTarget(type)
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build()).build();
		t1.getRelations().add(ownerRel);

		TransferObjectRelation relation = newTransferObjectRelationBuilder().withName("relation").withTarget(t3)
				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build()).build();
		type.getRelations().add(relation);

		BoundOperation boundOp1 = newBoundOperationBuilder().withName("createBinding").withInstanceRepresentation(t1)
				.withOutput(newParameterBuilder().withName("output").withType(type)
						.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build()).build())
				.withInput(newParameterBuilder().withName("input").withType(type)
						.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build()).build())
				.build();

		BoundOperation boundOp2 = newBoundOperationBuilder().withName("withImpl").withInstanceRepresentation(t1)
				.withOutput(newParameterBuilder().withName("output").withType(type)
						.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build()).build())
				.withInput(newParameterBuilder().withName("input").withType(type)
						.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build()).build())
				.withImplementation(newOperationBodyBuilder().withBody("return input")).build();

		e1.getOperations().addAll(ImmutableList.of(boundOp1, boundOp2));

		BoundOperation boundAbstract = newBoundOperationBuilder().withName("abstract").withAbstract_(true)
				.withInstanceRepresentation(pt).build();

		p.getOperations().addAll(ImmutableList.of(boundAbstract));

		BoundTransferOperation boundTransferOp1 = newBoundTransferOperationBuilder().withName("createRel")
				.withBehaviour(newTransferOperationBehaviourBuilder()
						.withBehaviourType(TransferOperationBehaviourType.CREATE_INSTANCE).withOwner(ownerRel).build())
				.withOutput(newParameterBuilder().withName("output").withType(type)
						.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build())
						.build())
				.withInput(newParameterBuilder().withName("input").withType(type)
						.withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build()).build())
				.withBinding(boundOp1)
				.withUpdateOnResult(true)
				.withDeleteOnResult(true)
				.build();

		UnboundOperation unbound1 = newUnboundOperationBuilder().withName("list")
				.withBehaviour(newTransferOperationBehaviourBuilder()
						.withBehaviourType(TransferOperationBehaviourType.LIST).withOwner(owner).build())
				.withOutput(newParameterBuilder().withName("output").withType(t1)
						.withCardinality(newCardinalityBuilder().withLower(owner.getCardinality().getLower())
								.withUpper(ownerRel.getCardinality().getUpper()).build())
						.build())
				.build();

		UnboundOperation unbound2Init = newUnboundOperationBuilder().withName("init").withInitializer(true)
				.withImplementation(newOperationBodyBuilder().withBody("new p")).build();

		t1.getOperations().addAll(ImmutableList.of(boundTransferOp1, unbound2Init));
		ap.getOperations().add(unbound1);

		Model model = newModelBuilder().withName("M")
				.withElements(ImmutableList.of(e1, e2, e3, t1, type, t3, ct, pt, ch, p, sn, ap, actorType)).build();

		psmModel.addContent(model);

		transform("testOperation");

		final EClass asmE1 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(e1.getName())).findAny().get();
		final EClass asmT1 = asmUtils.all(EClass.class).filter(c -> c.getName().equals(t1.getName())).findAny().get();
		final EClass asmType = asmUtils.all(EClass.class).filter(c -> c.getName().equals(type.getName())).findAny()
				.get();

		final Optional<EOperation> asmBoundOp1 = asmUtils.all(EOperation.class)
				.filter(c -> c.getName().equals(boundOp1.getName())).findAny();
		assertTrue(asmBoundOp1.isPresent());
		assertThat(asmBoundOp1.get().getEContainingClass(), IsEqual.equalTo(asmE1));
		assertThat(asmBoundOp1.get().getLowerBound(),
				IsEqual.equalTo(boundOp1.getOutput().getCardinality().getLower()));
		assertThat(asmBoundOp1.get().getUpperBound(),
				IsEqual.equalTo(boundOp1.getOutput().getCardinality().getUpper()));
		assertThat(asmBoundOp1.get().getEType(), IsEqual.equalTo(asmType));

		final EAnnotation asmBoundOp1Annotation = asmBoundOp1.get().getEAnnotation(OUTPUTPARAMETERNAME_SOURCE);
		assertThat(asmBoundOp1Annotation, IsNull.notNullValue());
		assertThat(asmBoundOp1Annotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundOp1Annotation.getDetails().containsKey("value"));
		assertTrue(asmBoundOp1Annotation.getDetails().get("value").equals(boundOp1.getOutput().getName()));

		final EAnnotation asmBoundOp1Annotation2 = asmBoundOp1.get().getEAnnotation(INSTANCEREPRESENTATION_SOURCE);
		assertThat(asmBoundOp1Annotation2, IsNull.notNullValue());
		assertThat(asmBoundOp1Annotation2.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundOp1Annotation2.getDetails().containsKey("value"));
		assertTrue(asmBoundOp1Annotation2.getDetails().get("value").equals(asmUtils.getClassifierFQName(asmT1)));

		final EAnnotation asmBoundOp1Annotation3 = asmBoundOp1.get().getEAnnotation(BOUND_SOURCE);
		assertThat(asmBoundOp1Annotation3, IsNull.notNullValue());
		assertThat(asmBoundOp1Annotation3.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundOp1Annotation3.getDetails().containsKey("value"));
		assertTrue(asmBoundOp1Annotation3.getDetails().get("value").equals(String.valueOf(true)));

		final Optional<EParameter> asmInput = asmUtils.all(EParameter.class).filter(
				c -> c.getName().equals(boundOp1.getInput().getName()) && c.getEOperation().equals(asmBoundOp1.get()))
				.findAny();
		assertTrue(asmInput.isPresent());
		assertThat(asmInput.get().getLowerBound(), IsEqual.equalTo(boundOp1.getInput().getCardinality().getLower()));
		assertThat(asmInput.get().getUpperBound(), IsEqual.equalTo(boundOp1.getInput().getCardinality().getUpper()));
		assertThat(asmInput.get().getEType(), IsEqual.equalTo(asmType));

		final EAnnotation asmOutputAnnotation = asmBoundOp1.get().getEAnnotation(OUTPUTPARAMETERNAME_SOURCE);
		assertThat(asmOutputAnnotation, IsNull.notNullValue());
		assertThat(asmOutputAnnotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmOutputAnnotation.getDetails().containsKey("value"));
		assertTrue(asmOutputAnnotation.getDetails().get("value").equals(boundOp1.getOutput().getName()));

		final Optional<EOperation> asmBoundAbstract = asmUtils.all(EOperation.class)
				.filter(c -> c.getName().equals(boundAbstract.getName())).findAny();
		assertTrue(asmBoundAbstract.isPresent());

		final EAnnotation asmBoundAbstractAnnotation = asmBoundAbstract.get().getEAnnotation(ABSTRACT_SOURCE);
		assertThat(asmBoundAbstractAnnotation, IsNull.notNullValue());
		assertThat(asmBoundAbstractAnnotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundAbstractAnnotation.getDetails().containsKey("value"));
		assertTrue(asmBoundAbstractAnnotation.getDetails().get("value").equals(String.valueOf(true)));

		final Optional<EOperation> asmBoundOp2 = asmUtils.all(EOperation.class)
				.filter(c -> c.getName().equals(boundOp2.getName())).findAny();
		assertTrue(asmBoundOp2.isPresent());

		final EAnnotation asmBoundOp2Annotation = asmBoundOp2.get().getEAnnotation(SCRIPT_SOURCE);
		assertThat(asmBoundOp2Annotation, IsNull.notNullValue());
		assertThat(asmBoundOp2Annotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundOp2Annotation.getDetails().containsKey("body"));
		assertTrue(asmBoundOp2Annotation.getDetails().get("body").equals(boundOp2.getImplementation().getBody()));

		final Optional<EOperation> asmBoundTr1 = asmUtils.all(EOperation.class)
				.filter(c -> c.getName().equals(boundTransferOp1.getName())).findAny();
		assertTrue(asmBoundTr1.isPresent());
		assertThat(asmBoundTr1.get().getEContainingClass(), IsEqual.equalTo(asmT1));
		assertThat(asmBoundTr1.get().getLowerBound(),
				IsEqual.equalTo(boundTransferOp1.getOutput().getCardinality().getLower()));
		assertThat(asmBoundTr1.get().getUpperBound(),
				IsEqual.equalTo(boundTransferOp1.getOutput().getCardinality().getUpper()));
		assertThat(asmBoundTr1.get().getEType(), IsEqual.equalTo(asmType));

		final EAnnotation asmBoundTr1Annotation = asmBoundTr1.get().getEAnnotation(BINDING_SOURCE);
		assertThat(asmBoundTr1Annotation, IsNull.notNullValue());
		assertThat(asmBoundTr1Annotation.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundTr1Annotation.getDetails().containsKey("value"));
		assertTrue(asmBoundTr1Annotation.getDetails().get("value").equals(boundOp1.getName()));

		final EAnnotation asmBoundTr1Annotation2 = asmBoundTr1.get().getEAnnotation(STATEFUL_SOURCE);
		assertThat(asmBoundTr1Annotation2, IsNull.notNullValue());
		assertThat(asmBoundTr1Annotation2.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundTr1Annotation2.getDetails().containsKey("value"));
		assertTrue(asmBoundTr1Annotation2.getDetails().get("value").equals(String.valueOf(true)));

		final EAnnotation asmBoundTr1Annotation3 = asmBoundTr1.get().getEAnnotation(BOUND_SOURCE);
		assertThat(asmBoundTr1Annotation3, IsNull.notNullValue());
		assertThat(asmBoundTr1Annotation3.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmBoundTr1Annotation3.getDetails().containsKey("value"));
		assertTrue(asmBoundTr1Annotation3.getDetails().get("value").equals(String.valueOf(true)));

		final EReference asmTR1 = asmUtils.all(EReference.class).filter(r -> r.getName().equals(ownerRel.getName()))
				.findAny().get();
		final EAnnotation asmBoundTr1Annotation4 = asmBoundTr1.get().getEAnnotation(BEHAVIOUR_SOURCE);
		assertThat(asmBoundTr1Annotation4, IsNull.notNullValue());
		assertThat(asmBoundTr1Annotation4.getDetails().size(), IsEqual.equalTo(2));
		assertTrue(asmBoundTr1Annotation4.getDetails().containsKey("type"));
		assertTrue(asmBoundTr1Annotation4.getDetails().get("type").equals("createInstance"));
		assertTrue(asmBoundTr1Annotation4.getDetails().containsKey("owner"));
		assertTrue(asmBoundTr1Annotation4.getDetails().get("owner").equals(asmUtils.getReferenceFQName(asmTR1)));

		final EAnnotation asmBoundTr1Annotation6 = asmBoundTr1.get().getEAnnotation(PERMISSIONS_SOURCE);
		assertThat(asmBoundTr1Annotation6, IsNull.notNullValue());
		assertTrue(asmBoundTr1Annotation6.getDetails().containsKey("update"));
		assertTrue(asmBoundTr1Annotation6.getDetails().containsKey("delete"));
		assertTrue(asmBoundTr1Annotation6.getDetails().get("update").equals(String.valueOf(boundTransferOp1.isUpdateOnResult())));
		assertTrue(asmBoundTr1Annotation6.getDetails().get("delete").equals(String.valueOf(boundTransferOp1.isDeleteOnResult())));

		final Optional<EOperation> asmUnboundInit = asmUtils.all(EOperation.class)
				.filter(c -> c.getName().equals(unbound2Init.getName())).findAny();
		assertTrue(asmUnboundInit.isPresent());
		assertThat(asmUnboundInit.get().getEContainingClass(), IsEqual.equalTo(asmT1));

		final EAnnotation asmUnboundInitAnnotation1 = asmUnboundInit.get().getEAnnotation(STATEFUL_SOURCE);
		assertThat(asmUnboundInitAnnotation1, IsNull.notNullValue());
		assertThat(asmUnboundInitAnnotation1.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmUnboundInitAnnotation1.getDetails().containsKey("value"));
		assertTrue(asmUnboundInitAnnotation1.getDetails().get("value")
				.equals(String.valueOf(unbound2Init.getImplementation().isStateful())));

		final EAnnotation asmUnboundInitAnnotation2 = asmUnboundInit.get().getEAnnotation(SCRIPT_SOURCE);
		assertThat(asmUnboundInitAnnotation2, IsNull.notNullValue());
		assertThat(asmUnboundInitAnnotation2.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmUnboundInitAnnotation2.getDetails().containsKey("body"));
		assertTrue(
				asmUnboundInitAnnotation2.getDetails().get("body").equals(unbound2Init.getImplementation().getBody()));

		final EAnnotation asmUnboundInitAnnotation3 = asmUnboundInit.get().getEAnnotation(INITIALIZER_SOURCE);
		assertThat(asmUnboundInitAnnotation3, IsNull.notNullValue());
		assertThat(asmUnboundInitAnnotation3.getDetails().size(), IsEqual.equalTo(1));
		assertTrue(asmUnboundInitAnnotation3.getDetails().containsKey("value"));
		assertTrue(asmUnboundInitAnnotation3.getDetails().get("value").equals(String.valueOf(true)));

	}
	
	/*
    @Test
    public void testTransferObjectTypeAnnotationOnDefaultTransferObjects() throws Exception {
    	
    	hu.blackbelt.judo.meta.esm.structure.EntityType entityType = hu.blackbelt.judo.meta.esm.structure.util.builder.StructureBuilders.newEntityTypeBuilder().withName("entityType").build();
        entityType.setMapping(newMappingBuilder()
                .withTarget(entityType)
                .build());

        final hu.blackbelt.judo.meta.esm.namespace.Model model = hu.blackbelt.judo.meta.esm.namespace.util.builder.NamespaceBuilders.newModelBuilder()
                .withName("TestModel")
                .withElements(ImmutableList.of(entityType))
                .build();
        
        EsmModel esmModel = buildEsmModel()
                .uri(URI.createURI("urn:test.judo-meta-esm"))
                .name("test")
                .build();
        
        esmModel.addContent(model);
    	
        assertTrue(esmModel.isValid());

		try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
			validateEsm(bufferedLog, esmModel, calculateEsmValidationScriptURI());

			executeEsm2PsmTransformation(Esm2Psm.Esm2PsmParameter.esm2PsmParameter()
					.esmModel(esmModel)
					.psmModel(psmModel)
					.log(bufferedLog)
					.scriptUri(calculateEsm2PsmTransformationScriptURI()));

			assertTrue(psmModel.isValid());
			validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
		}

        transform("testDefaultTransferObjectAnnotations");
        
        
    	final String ENTITY_SOURCE = AsmUtils.getAnnotationUri("entity");
    	final String TRANSFER_OBJECT_TYPE = AsmUtils.getAnnotationUri("transferObjectType");
    	
    	final Optional<EClass> orderDefault = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entityType.getName()) && c.getEAnnotation(TRANSFER_OBJECT_TYPE) != null).findAny();
    	assertTrue(orderDefault.isPresent());
    	final Optional<EClass> orderEntity = asmUtils.all(EClass.class).filter(c -> c.getName().equals(entityType.getName()) && c.getEAnnotation(ENTITY_SOURCE) != null).findAny();
    	assertTrue(orderEntity.isPresent());
    	assertFalse(orderDefault.get().getEPackage().equals(orderEntity.get().getEPackage()));
    }
    */
}
