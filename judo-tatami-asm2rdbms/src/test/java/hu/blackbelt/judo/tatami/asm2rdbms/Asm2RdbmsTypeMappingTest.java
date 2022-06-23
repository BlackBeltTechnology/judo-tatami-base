package hu.blackbelt.judo.tatami.asm2rdbms;

import com.google.common.collect.ImmutableList;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static hu.blackbelt.judo.meta.asm.runtime.AsmUtils.addExtensionAnnotation;
import static org.eclipse.emf.ecore.util.builder.EcoreBuilders.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Slf4j
public class Asm2RdbmsTypeMappingTest extends Asm2RdbmsMappingTestBase {
    private static final String INTEGER = "INTEGER";
    private static final String BIGINT = "BIGINT";
    private static final String DECIMAL = "DECIMAL";
    private static final String FLOAT = "FLOAT";
    private static final String DOUBLE = "DOUBLE";
    private static final String VARCHAR = "VARCHAR";
    private static final String BOOLEAN = "BOOLEAN";
    private static final String DATE = "DATE";
    private static final String TIMESTAMP = "TIMESTAMP";
    private static final String TIME = "TIME";
    private static final String TIME_WITH_TIMEZONE = "TIME WITH TIME ZONE";
    private static final String TIMESTAMP_WITH_TIMEZONE = "TIMESTAMP WITH TIME ZONE";

    /**
     * Asserts the fundamental properties of a RdbmsField
     *
     * @param rdbmsField        RdbmsField to check
     * @param expectedType      name of the expected type
     * @param expectedSize      -1 if undefined
     * @param expectedPrecision -1 if undefined
     * @param expectedScale     -1 if undefined
     */
    private void typeAsserter(final RdbmsField rdbmsField, final String expectedType,
                              final int expectedSize, final int expectedPrecision, final int expectedScale) {
        assertNotNull(rdbmsField);
        assertEquals(expectedType, rdbmsField.getRdbmsTypeName());
        assertEquals(expectedSize, rdbmsField.getSize());
        assertEquals(expectedPrecision, rdbmsField.getPrecision());
        assertEquals(expectedScale, rdbmsField.getScale());
    }

    /**
     * Creates EDataType with given instance type name
     *
     * @param instanceTypeName name of instance type
     * @return new EDataType with name created from instanceTypeName without dots
     */
    private EDataType customEDataTypeBuilder(final String instanceTypeName) {
        return newEDataTypeBuilder()
                .withName(instanceTypeName.replace(".", ""))
                .withInstanceTypeName(instanceTypeName)
                .build();
    }

    @Test
    @DisplayName("Test Numeric Types")
    public void testNumericTypes() {
        final EcorePackage ecore = EcorePackage.eINSTANCE;

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        // create custom numeric types
        final EDataType javalangByte = customEDataTypeBuilder("java.lang.Byte");
        final EDataType javalangShort = customEDataTypeBuilder("java.lang.Short");
        final EDataType javalangInteger = customEDataTypeBuilder("java.lang.Integer");
        final EDataType javalangLong = customEDataTypeBuilder("java.lang.Long");
        final EDataType javamathBigInteger = customEDataTypeBuilder("java.math.BigInteger");
        final EDataType javalangFloat = customEDataTypeBuilder("java.lang.Float");
        final EDataType javalangDouble = customEDataTypeBuilder("java.lang.Double");
        final EDataType javamathBigDecimal = customEDataTypeBuilder("java.math.BigDecimal");

        ePackage.getEClassifiers().addAll(
                ImmutableList.of(javalangByte, javalangShort, javalangInteger, javalangLong, javamathBigInteger,
                                 javalangFloat, javalangDouble, javamathBigDecimal));

        final EAnnotation bigDecimalAttrAnnotation = newEAnnotationBuilder()
                .withSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints")
                .build();
        bigDecimalAttrAnnotation.getDetails().put("precision", "64");
        bigDecimalAttrAnnotation.getDetails().put("scale", "20");
        
        final EAnnotation bigIntegerAttrAnnotation = newEAnnotationBuilder()
                .withSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints")
                .build();
        bigIntegerAttrAnnotation.getDetails().put("precision", "18");
        
        final EAnnotation javaMathBigIntegerAttrAnnotation = newEAnnotationBuilder()
                .withSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints")
                .build();
        javaMathBigIntegerAttrAnnotation.getDetails().put("precision", "18");
        
        final EAnnotation javaMathBigDecimalAttrAnnotation = newEAnnotationBuilder()
                .withSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints")
                .build();
        javaMathBigDecimalAttrAnnotation.getDetails().put("precision", "64");
        javaMathBigDecimalAttrAnnotation.getDetails().put("scale", "20");
        
        // create class with numeric type attributes
        final EClass eClass = newEClassBuilder()
                .withName("TestNumericTypesClass")
                .withEStructuralFeatures(
                        ImmutableList.of(
                                newEAttributeBuilder()
                                        .withName("bigDecimalAttr")
                                        .withEType(ecore.getEBigDecimal())
                                        .withEAnnotations(bigDecimalAttrAnnotation)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("bigInteger")
                                        .withEType(ecore.getEBigInteger())
                                        .withEAnnotations(bigIntegerAttrAnnotation)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("doubleAttr")
                                        .withEType(ecore.getEDouble())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("floatAttr")
                                        .withEType(ecore.getEFloat())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("intAttr")
                                        .withEType(ecore.getEInt())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("longAttr")
                                        .withEType(ecore.getELong())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("shortAttr")
                                        .withEType(ecore.getEShort())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("byteAttr")
                                        .withEType(ecore.getEByte())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangByteAttr")
                                        .withEType(javalangByte)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangShortAttr")
                                        .withEType(javalangShort)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangIntegerAttr")
                                        .withEType(javalangInteger)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangLongAttr")
                                        .withEType(javalangLong)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javamathBigIntegerAttr")
                                        .withEType(javamathBigInteger)
                                        .withEAnnotations(javaMathBigIntegerAttrAnnotation)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangFloatAttr")
                                        .withEType(javalangFloat)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangDoubleAttr")
                                        .withEType(javalangDouble)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javamathBigDecimalAttr")
                                        .withEType(javamathBigDecimal)
                                        .withEAnnotations(javaMathBigDecimalAttrAnnotation)
                                        .build()
                        )
                )
                .build();
        ePackage.getEClassifiers().add(eClass);
        addExtensionAnnotation(eClass, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        // transform previously created asm model to rdbms model
        executeTransformation("testNumericTypes");

        // check eclass -> tables
        final String RDBMS_TABLE_NAME = "TestEpackage.TestNumericTypesClass";

        // create and fill expected sets
        Set<String> table = new HashSet<>();
        Set<String> fields = new HashSet<>();

        table.add(RDBMS_TABLE_NAME);
        fields.add(RDBMS_TABLE_NAME + "#_id");
        fields.add(RDBMS_TABLE_NAME + "#_type");
        fields.add(RDBMS_TABLE_NAME + "#_version");
        fields.add(RDBMS_TABLE_NAME + "#_create_username");
        fields.add(RDBMS_TABLE_NAME + "#_create_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_create_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#_update_username");
        fields.add(RDBMS_TABLE_NAME + "#_update_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_update_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#bigDecimalAttr");
        fields.add(RDBMS_TABLE_NAME + "#bigInteger");
        fields.add(RDBMS_TABLE_NAME + "#doubleAttr");
        fields.add(RDBMS_TABLE_NAME + "#floatAttr");
        fields.add(RDBMS_TABLE_NAME + "#intAttr");
        fields.add(RDBMS_TABLE_NAME + "#longAttr");
        fields.add(RDBMS_TABLE_NAME + "#shortAttr");
        fields.add(RDBMS_TABLE_NAME + "#byteAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangByteAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangShortAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangIntegerAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangLongAttr");
        fields.add(RDBMS_TABLE_NAME + "#javamathBigIntegerAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangFloatAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangDoubleAttr");
        fields.add(RDBMS_TABLE_NAME + "#javamathBigDecimalAttr");

        // compare expected and actual sets
        assertTables(table);
        assertFields(fields, RDBMS_TABLE_NAME);

        // check field types based on typemapping table
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#bigDecimalAttr").get(),
                DECIMAL,
                -1,
                64,
                20);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#bigInteger").get(),
                DECIMAL,
                -1,
                18,
                0);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#doubleAttr").get(),
                DOUBLE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#longAttr").get(),
                BIGINT,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#floatAttr").get(),
                FLOAT,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#intAttr").get(),
                INTEGER,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#shortAttr").get(),
                INTEGER,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#byteAttr").get(),
                INTEGER,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangByteAttr").get(),
                INTEGER,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangShortAttr").get(),
                INTEGER,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangIntegerAttr").get(),
                INTEGER,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangLongAttr").get(),
                BIGINT,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javamathBigIntegerAttr").get(),
                DECIMAL,
                -1,
                18,
                0);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangFloatAttr").get(),
                FLOAT,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangDoubleAttr").get(),
                DOUBLE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javamathBigDecimalAttr").get(),
                DECIMAL,
                -1,
                64,
                20);

    }

    @Test
    @DisplayName("Test String-like Types")
    public void testStringlikeTypes() {
        final EcorePackage ecore = EcorePackage.eINSTANCE;

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        // create custom string-like type
        final EDataType javalangString = customEDataTypeBuilder("java.lang.String");
        ePackage.getEClassifiers().add(javalangString);
        
        EAnnotation annotationStringAttr = newEAnnotationBuilder()
            .withSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints")
            .build();
        annotationStringAttr.getDetails().put("maxLength", "255");
        
        EAnnotation annotationJavalangStringAttr = newEAnnotationBuilder()
                .withSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints")
                .build();
        annotationJavalangStringAttr.getDetails().put("maxLength", "255");
        
        // create class with string-like type attributes
        final EClass eClass = newEClassBuilder()
                .withName("TestStringlikeTypesClass")
                .withEStructuralFeatures(
                        ImmutableList.of(
                                newEAttributeBuilder()
                                        .withName("stringAttr")
                                        .withEType(ecore.getEString())
                                        .withEAnnotations(annotationStringAttr)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangStringAttr")
                                        .withEType(javalangString)
                                        .withEAnnotations(annotationJavalangStringAttr)
                                        .build()
                        )
                )
                .build();
        ePackage.getEClassifiers().add(eClass);
        addExtensionAnnotation(eClass, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        // transform previously created asm model to rdbms model
        executeTransformation("testStringlikeTypes");

        // check eclass -> tables
        final String RDBMS_TABLE_NAME = "TestEpackage.TestStringlikeTypesClass";

        // create and fill expected sets
        Set<String> table = new HashSet<>();
        Set<String> fields = new HashSet<>();

        table.add(RDBMS_TABLE_NAME);
        fields.add(RDBMS_TABLE_NAME + "#_id");
        fields.add(RDBMS_TABLE_NAME + "#_type");
        fields.add(RDBMS_TABLE_NAME + "#_version");
        fields.add(RDBMS_TABLE_NAME + "#_create_username");
        fields.add(RDBMS_TABLE_NAME + "#_create_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_create_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#_update_username");
        fields.add(RDBMS_TABLE_NAME + "#_update_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_update_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#stringAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangStringAttr");

        // compare actual end expected fields
        assertTables(table);
        assertFields(fields, RDBMS_TABLE_NAME);

        // check field types based on typemapping table
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#stringAttr").get(),
                VARCHAR,
                255,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangStringAttr").get(),
                VARCHAR,
                255,
                -1,
                -1);
    }

    @Test
    @DisplayName("Test Date Types")
    public void testDateTypes() {
        final EcorePackage ecore = EcorePackage.eINSTANCE;

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        // create custom date types
        final EDataType javautilDate = customEDataTypeBuilder("java.util.Date");
        final EDataType javasqlDate = customEDataTypeBuilder("java.sql.Date");
        final EDataType javatimeLocalDate = customEDataTypeBuilder("java.time.LocalDate");
        final EDataType orgjodatimeLocalDate = customEDataTypeBuilder("org.joda.time.LocalDate");
        final EDataType javasqlTimestamp = customEDataTypeBuilder("java.sql.Timestamp");
        final EDataType javasqlTime = customEDataTypeBuilder("java.sql.Time");
        final EDataType javatimeLocalDateTime = customEDataTypeBuilder("java.time.LocalDateTime");
        final EDataType javatimeOffsetDateTime = customEDataTypeBuilder("java.time.OffsetDateTime");
        final EDataType javatimeZonedDateTime = customEDataTypeBuilder("java.time.ZonedDateTime");
        final EDataType javatimeLocalTime = customEDataTypeBuilder("java.time.LocalTime");
        final EDataType orgjodatimeDateTime = customEDataTypeBuilder("org.joda.time.DateTime");
        final EDataType orgjodatimeLocalDateTime = customEDataTypeBuilder("org.joda.time.LocalDateTime");
        final EDataType orgjodatimeMutableDateTime = customEDataTypeBuilder("org.joda.time.MutableDateTime");
        final EDataType orgjodatimeLocalTime = customEDataTypeBuilder("org.joda.time.LocalTime");

        ePackage.getEClassifiers().addAll(
                ImmutableList.of(javautilDate, javasqlDate, javatimeLocalDate, orgjodatimeLocalDate, javasqlTimestamp, javasqlTime,
                                 javatimeLocalDateTime, javatimeOffsetDateTime, javatimeZonedDateTime, javatimeLocalTime, orgjodatimeDateTime,
                                 orgjodatimeLocalDateTime, orgjodatimeMutableDateTime, orgjodatimeLocalTime));

        // create class with date type attributes
        final EClass eClass = newEClassBuilder()
                .withName("TestDateTypesClass")
                .withEStructuralFeatures(
                        ImmutableList.of(
                                newEAttributeBuilder()
                                        .withName("dateAttr")
                                        .withEType(ecore.getEDate())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javautilDateAttr")
                                        .withEType(javautilDate)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javasqlDateAttr")
                                        .withEType(javasqlDate)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javatimeLocalDateAttr")
                                        .withEType(javatimeLocalDate)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("orgjodatimeLocalDateAttr")
                                        .withEType(orgjodatimeLocalDate)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javasqlTimestampAttr")
                                        .withEType(javasqlTimestamp)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javasqlTimeAttr")
                                        .withEType(javasqlTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javatimeLocalDateTimeAttr")
                                        .withEType(javatimeLocalDateTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javatimeOffsetDateTimeAttr")
                                        .withEType(javatimeOffsetDateTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javatimeZonedDateTimeAttr")
                                        .withEType(javatimeZonedDateTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javatimeLocalTimeAttr")
                                        .withEType(javatimeLocalTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("orgjodatimeDateTimeAttr")
                                        .withEType(orgjodatimeDateTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("orgjodatimeLocalDateTimeAttr")
                                        .withEType(orgjodatimeLocalDateTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("orgjodatimeMutableDateTimeAttr")
                                        .withEType(orgjodatimeMutableDateTime)
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("orgjodatimeLocalTimeAttr")
                                        .withEType(orgjodatimeLocalTime)
                                        .build()
                        )
                )
                .build();
        ePackage.getEClassifiers().add(eClass);
        addExtensionAnnotation(eClass, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        // transform previously created asm model to rdbms model
        executeTransformation("testDateTypes");

        // check eclass -> tables
        final String RDBMS_TABLE_NAME = "TestEpackage.TestDateTypesClass";

        // create and fill expected sets
        Set<String> table = new HashSet<>();
        Set<String> fields = new HashSet<>();

        table.add(RDBMS_TABLE_NAME);
        fields.add(RDBMS_TABLE_NAME + "#_id");
        fields.add(RDBMS_TABLE_NAME + "#_type");
        fields.add(RDBMS_TABLE_NAME + "#_version");
        fields.add(RDBMS_TABLE_NAME + "#_create_username");
        fields.add(RDBMS_TABLE_NAME + "#_create_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_create_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#_update_username");
        fields.add(RDBMS_TABLE_NAME + "#_update_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_update_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#dateAttr");
        fields.add(RDBMS_TABLE_NAME + "#javautilDateAttr");
        fields.add(RDBMS_TABLE_NAME + "#javasqlDateAttr");
        fields.add(RDBMS_TABLE_NAME + "#javatimeLocalDateAttr");
        fields.add(RDBMS_TABLE_NAME + "#orgjodatimeLocalDateAttr");
        fields.add(RDBMS_TABLE_NAME + "#javasqlTimestampAttr");
        fields.add(RDBMS_TABLE_NAME + "#javasqlTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#javatimeLocalDateTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#javatimeOffsetDateTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#javatimeZonedDateTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#javatimeLocalTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#orgjodatimeDateTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#orgjodatimeLocalDateTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#orgjodatimeMutableDateTimeAttr");
        fields.add(RDBMS_TABLE_NAME + "#orgjodatimeLocalTimeAttr");

        // compare actual end expected fields
        assertTables(table);
        assertFields(fields, RDBMS_TABLE_NAME);

        // check field types based on typemapping table
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#dateAttr").get(),
                DATE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javautilDateAttr").get(),
                DATE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javasqlDateAttr").get(),
                DATE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javatimeLocalDateAttr").get(),
                DATE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#orgjodatimeLocalDateAttr").get(),
                DATE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javasqlTimestampAttr").get(),
                TIMESTAMP_WITH_TIMEZONE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javasqlTimeAttr").get(),
                TIMESTAMP  ,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javatimeLocalDateTimeAttr").get(),
                TIMESTAMP,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javatimeOffsetDateTimeAttr").get(),
                TIMESTAMP_WITH_TIMEZONE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javatimeZonedDateTimeAttr").get(),
                TIMESTAMP_WITH_TIMEZONE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javatimeLocalTimeAttr").get(),
                TIMESTAMP,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#orgjodatimeDateTimeAttr").get(),
                TIMESTAMP_WITH_TIMEZONE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#orgjodatimeLocalDateTimeAttr").get(),
                TIMESTAMP,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#orgjodatimeMutableDateTimeAttr").get(),
                TIMESTAMP_WITH_TIMEZONE,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#orgjodatimeLocalTimeAttr").get(),
                TIMESTAMP,
                -1,
                -1,
                -1);
    }

    @Test
    @DisplayName("Test Boolean Types")
    public void testBooleanTypes() {
        final EcorePackage ecore = EcorePackage.eINSTANCE;

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        // create custom type
        final EDataType javalangBoolean = customEDataTypeBuilder("java.lang.Boolean");
        ePackage.getEClassifiers().add(javalangBoolean);

        // create class with boolean type attributes
        final EClass eClass = newEClassBuilder()
                .withName("TestBooleanTypesClass")
                .withEStructuralFeatures(
                        ImmutableList.of(
                                newEAttributeBuilder()
                                        .withName("booleanAttr")
                                        .withEType(ecore.getEBoolean())
                                        .build(),
                                newEAttributeBuilder()
                                        .withName("javalangBooleanAttr")
                                        .withEType(javalangBoolean)
                                        .build()
                        )
                )
                .build();
        ePackage.getEClassifiers().add(eClass);
        addExtensionAnnotation(eClass, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        // transform previously created asm model to rdbms model
        executeTransformation("testBooleanTypes");

        // check eclass -> tables
        final String RDBMS_TABLE_NAME = "TestEpackage.TestBooleanTypesClass";

        // create and fill expected sets
        Set<String> table = new HashSet<>();
        Set<String> fields = new HashSet<>();

        table.add(RDBMS_TABLE_NAME);
        fields.add(RDBMS_TABLE_NAME + "#_id");
        fields.add(RDBMS_TABLE_NAME + "#_type");
        fields.add(RDBMS_TABLE_NAME + "#_version");
        fields.add(RDBMS_TABLE_NAME + "#_create_username");
        fields.add(RDBMS_TABLE_NAME + "#_create_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_create_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#_update_username");
        fields.add(RDBMS_TABLE_NAME + "#_update_user_id");
        fields.add(RDBMS_TABLE_NAME + "#_update_timestamp");
        fields.add(RDBMS_TABLE_NAME + "#booleanAttr");
        fields.add(RDBMS_TABLE_NAME + "#javalangBooleanAttr");

        // compare actual end expected fields
        assertTables(table);
        assertFields(fields, RDBMS_TABLE_NAME);

        // check field types based on typemapping table
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#booleanAttr").get(),
                BOOLEAN,
                -1,
                -1,
                -1);
        typeAsserter(rdbmsUtils.getRdbmsField(RDBMS_TABLE_NAME, RDBMS_TABLE_NAME + "#javalangBooleanAttr").get(),
                BOOLEAN,
                -1,
                -1,
                -1);
    }

}
