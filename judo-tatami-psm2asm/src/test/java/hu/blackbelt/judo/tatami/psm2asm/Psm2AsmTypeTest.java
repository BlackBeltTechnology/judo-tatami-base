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
import hu.blackbelt.judo.meta.psm.measure.*;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.type.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.measure.util.builder.MeasureBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmTypeTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";
    
    public static final String TEST_MODEL_NAME = "Model";
    public static final String STRING = "java.lang.String";
    public static final String INTEGER = "java.lang.Integer";
    public static final String LONG = "java.lang.Long";
    public static final String BIG_DECIMAL = "java.math.BigDecimal";
    
    public static final String FLOAT = "java.lang.Float";
    public static final String DOUBLE = "java.lang.Double";
    public static final String BOOLEAN = "java.lang.Boolean";
    public static final String LOCALE_DATE = "java.time.LocalDate";
    public static final String DATE_TIME = "java.time.OffsetDateTime";
    public static final String TIME = "java.time.LocalTime";
    public static final String OBJECT = "java.lang.Object";
    
    PsmModel psmModel;
    AsmModel asmModel;
    AsmUtils asmUtils;

    @BeforeEach
    public void setUp() {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .name(MODEL_NAME)
                .build();
        // Create empty ASM model
        asmModel = buildAsmModel()
                .name(MODEL_NAME)
                .build();
        asmUtils = new AsmUtils(asmModel.getResourceSet());
    }

    private void transform(final String testName) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());
        
        assertTrue(psmModel.isValid());
        try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        assertTrue(asmModel.isValid());
        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
                .build());
    }
    
    @Test
    void testType() throws Exception {
    	
    	EnumerationMember a = newEnumerationMemberBuilder().withName("a").withOrdinal(1).build();
    	EnumerationMember b = newEnumerationMemberBuilder().withName("b").withOrdinal(2).build();
    	EnumerationMember c = newEnumerationMemberBuilder().withName("c").withOrdinal(3).build();
        
    	EnumerationType enumType = newEnumerationTypeBuilder().withName("enum")
    			.withMembers(ImmutableList.of(a,b,c)).build();
    	StringType strType = newStringTypeBuilder().withName("string").withMaxLength(256).withRegExp(".*").build();
    	
    	NumericType intType = newNumericTypeBuilder().withName("int").withPrecision(6).withScale(0).build();
    	NumericType longType = newNumericTypeBuilder().withName("long").withPrecision(16).withScale(0).build();
    	NumericType bigDecimalIntType = newNumericTypeBuilder().withName("bigdecimalint").withPrecision(21).withScale(0).build();
    	
    	NumericType floatType = newNumericTypeBuilder().withName("float").withPrecision(6).withScale(3).build();
    	NumericType doubleType = newNumericTypeBuilder().withName("bouble").withPrecision(10).withScale(3).build();
    	NumericType bigDecimalType = newNumericTypeBuilder().withName("bigdecimal").withPrecision(17).withScale(8).build();
    	
    	BooleanType boolType = newBooleanTypeBuilder().withName("bool").build();
    	
    	//not supported yet
    	PasswordType pwType = newPasswordTypeBuilder().withName("pw").build();
    	//not supported yet
    	XMLType xmlType = newXMLTypeBuilder().withName("xml").build();

    	DateType dateType = newDateTypeBuilder().withName("date").build();
    	TimestampType timeStampType = newTimestampTypeBuilder().withName("timestamp").build();
        TimeType timeType = newTimeTypeBuilder().withName("time").build();

    	CustomType custom = newCustomTypeBuilder().withName("object").build();
    	
		Unit unit = newUnitBuilder().withName("u").build();
		Measure m = newMeasureBuilder().withName("measure").withUnits(unit).build();
		MeasuredType measuredType = newMeasuredTypeBuilder().withName("measuredType").withStoreUnit(unit)
				.withPrecision(5).withScale(3).build();
    	
		Model model = newModelBuilder().withName(TEST_MODEL_NAME)
				.withElements(ImmutableList.of(
						enumType,strType,intType,longType,bigDecimalIntType,
						floatType,doubleType,bigDecimalType,boolType,dateType,timeStampType,timeType,custom,
						m,measuredType)).build();

        psmModel.addContent(model);

        transform("testType");
        
        final EPackage asmTestModel = asmUtils.all(EPackage.class).filter(p -> p.getName().equals(TEST_MODEL_NAME)).findAny().get();
        final Optional<EEnum> asmEnum = asmUtils.all(EEnum.class).filter(e -> e.getName().equals(enumType.getName())).findAny();
        assertTrue(asmEnum.isPresent());
        assertTrue(asmEnum.get().getEPackage().equals(asmTestModel));
        
        assertThat(asmEnum.get().getEEnumLiteral(1), IsNull.notNullValue());
        assertThat(asmEnum.get().getEEnumLiteral(2), IsNull.notNullValue());
        assertThat(asmEnum.get().getEEnumLiteral(3), IsNull.notNullValue());
        assertTrue(asmEnum.get().getEEnumLiteral(1).getLiteral().equals(a.getName()));
        assertTrue(asmEnum.get().getEEnumLiteral(2).getLiteral().equals(b.getName()));
        assertTrue(asmEnum.get().getEEnumLiteral(3).getLiteral().equals(c.getName()));
        
        final Optional<EDataType> asmStr = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(strType.getName())).findAny();
        assertTrue(asmStr.isPresent());
        assertTrue(asmStr.get().getEPackage().equals(asmTestModel));
        assertTrue(asmStr.get().getInstanceClassName().equals(STRING));
        
        final Optional<EDataType> asmInt = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(intType.getName())).findAny();
        assertTrue(asmInt.isPresent());
        assertTrue(asmInt.get().getEPackage().equals(asmTestModel));
        assertTrue(asmInt.get().getInstanceClassName().equals(INTEGER));
        
        final Optional<EDataType> asmLongType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(longType.getName())).findAny();
        assertTrue(asmLongType.isPresent());
        assertTrue(asmLongType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmLongType.get().getInstanceClassName().equals(LONG));
        
        final Optional<EDataType> asmBigDecimalIntType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(bigDecimalIntType.getName())).findAny();
        assertTrue(asmBigDecimalIntType.isPresent());
        assertTrue(asmBigDecimalIntType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmBigDecimalIntType.get().getInstanceClassName().equals(BIG_DECIMAL));
        
        final Optional<EDataType> asmFloatType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(floatType.getName())).findAny();
        assertTrue(asmFloatType.isPresent());
        assertTrue(asmFloatType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmFloatType.get().getInstanceClassName().equals(FLOAT));
        
        final Optional<EDataType> asmDoubleType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(doubleType.getName())).findAny();
        assertTrue(asmDoubleType.isPresent());
        assertTrue(asmDoubleType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmDoubleType.get().getInstanceClassName().equals(DOUBLE));
        
        final Optional<EDataType> asmBigDecimalType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(bigDecimalType.getName())).findAny();
        assertTrue(asmBigDecimalType.isPresent());
        assertTrue(asmBigDecimalType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmBigDecimalType.get().getInstanceClassName().equals(BIG_DECIMAL));
        
        final Optional<EDataType> asmBoolType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(boolType.getName())).findAny();
        assertTrue(asmBoolType.isPresent());
        assertTrue(asmBoolType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmBoolType.get().getInstanceClassName().equals(BOOLEAN));
        
        final Optional<EDataType> asmDateType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(dateType.getName())).findAny();
        assertTrue(asmDateType.isPresent());
        assertTrue(asmDateType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmDateType.get().getInstanceClassName().equals(LOCALE_DATE));
        
        final Optional<EDataType> asmTimeStampType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(timeStampType.getName())).findAny();
        assertTrue(asmTimeStampType.isPresent());
        assertTrue(asmTimeStampType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmTimeStampType.get().getInstanceClassName().equals(DATE_TIME));

        final Optional<EDataType> asmTimeType = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(timeType.getName())).findAny();
        assertTrue(asmTimeType.isPresent());
        assertTrue(asmTimeType.get().getEPackage().equals(asmTestModel));
        assertTrue(asmTimeType.get().getInstanceClassName().equals(TIME));

        final Optional<EDataType> asmCustom = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(custom.getName())).findAny();
        assertTrue(asmCustom.isPresent());
        assertTrue(asmCustom.get().getEPackage().equals(asmTestModel));
        assertTrue(asmCustom.get().getInstanceClassName().equals(OBJECT));
        
        final Optional<EDataType> asmMeasured = asmUtils.all(EDataType.class).filter(e -> e.getName().equals(measuredType.getName())).findAny();
        assertTrue(asmMeasured.isPresent());
        assertTrue(asmMeasured.get().getEPackage().equals(asmTestModel));
        assertTrue(asmMeasured.get().getInstanceClassName().equals(FLOAT));
    }
}
