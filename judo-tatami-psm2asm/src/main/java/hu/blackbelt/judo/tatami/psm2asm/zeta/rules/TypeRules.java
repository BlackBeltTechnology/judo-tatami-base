package hu.blackbelt.judo.tatami.psm2asm.zeta.rules;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
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

import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.type.*;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper;
import hu.blackbelt.judo.zeta.annotation.Greedy;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Type transformation rules from type.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateEnumeration - @Greedy transforms EnumerationType to EEnum</li>
 *   <li>CreateStringType - @Greedy transforms StringType to EDataType</li>
 *   <li>CreateIntegerType - @Greedy transforms NumericType (integer) to EDataType</li>
 *   <li>CreateDecimalType - @Greedy transforms NumericType (decimal) to EDataType</li>
 *   <li>CreateBooleanType - @Greedy transforms BooleanType to EDataType</li>
 *   <li>CreateBinaryType - @Greedy transforms BinaryType to EDataType</li>
 *   <li>CreateDateType - @Greedy transforms DateType to EDataType</li>
 *   <li>CreateTimestampType - @Greedy transforms TimestampType to EDataType</li>
 *   <li>CreateTimeType - @Greedy transforms TimeType to EDataType</li>
 *   <li>CreateCustomType - @Greedy transforms CustomType to EDataType</li>
 * </ul>
 */
@Slf4j
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EObject.class, target = EClassifier.class)
public class TypeRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public TypeRules() {
    }

    // =========================================================================
    // ENUMERATION RULES
    // =========================================================================

    /**
     * @greedy
     * rule CreateEnumeration
     *     transform s : JUDOPSM!EnumerationType
     *     to t : ASM!EEnum
     */
    @TransformRule(name = CREATE_ENUMERATION, description = "Transform EnumerationType to EEnum")
    @Greedy
    @Transform(type = EnumerationType.class)
    @To(type = EEnum.class)
    public TransformFunction<EnumerationType, EEnum> createEnumeration() {
        return (s, ctx) -> {
            EEnum t = ctx.createTarget(EEnum.class);
            t.setName(s.getName());
            
            // Create enum literals
            int loopCount = 0;
            for (EnumerationMember m : s.getMembers()) {
                EEnumLiteral l = ctx.create(EEnumLiteral.class);
                l.setValue(m.getOrdinal());
                l.setLiteral(m.getName());
                l.setName(m.getName());
                Psm2AsmHelper.addEnumLiteral(t, l);
                loopCount++;
            }

            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);
            
            return t;
        };
    }

    // =========================================================================
    // STRING TYPE RULES
    // =========================================================================

    /**
     * @greedy
     * rule CreateStringType
     *     transform s : JUDOPSM!StringType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_STRING_TYPE, description = "Transform StringType to EDataType")
    @Greedy
    @Transform(type = StringType.class)
    @To(type = EDataType.class)
    public TransformFunction<StringType, EDataType> createStringType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("java.lang.String");

            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    // =========================================================================
    // NUMERIC TYPE RULES
    // =========================================================================

    /**
     * Guard for CreateIntegerType: s.isInteger()
     * Guard signature required by framework: (EObject, TransformationContext) -> boolean
     */
    public boolean isIntegerGuard(EObject source, TransformationContext ctx) {
        if (source instanceof NumericType) {
            return Psm2AsmHelper.isInteger((NumericType) source);
        }
        return false;
    }

    /**
     * Guard for CreateDecimalType: s.isDecimal()
     * Guard signature required by framework: (EObject, TransformationContext) -> boolean
     */
    public boolean isDecimalGuard(EObject source, TransformationContext ctx) {
        if (source instanceof NumericType) {
            return Psm2AsmHelper.isDecimal((NumericType) source);
        }
        return false;
    }

    /**
     * @greedy
     * rule CreateIntegerType
     *     transform s : JUDOPSM!NumericType
     *     to t : ASM!EDataType {
     *         guard: s.isInteger()
     *     }
     */
    @TransformRule(name = CREATE_INTEGER_TYPE, description = "Transform NumericType (integer) to EDataType")
    @Greedy
    @Guard(method = "isIntegerGuard")
    @Transform(type = NumericType.class)
    @To(type = EDataType.class)
    public TransformFunction<NumericType, EDataType> createIntegerType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName(getIntegerClassName(s));
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * @greedy
     * rule CreateDecimalType
     *     transform s : JUDOPSM!NumericType
     *     to t : ASM!EDataType {
     *         guard: s.isDecimal()
     *     }
     */
    @TransformRule(name = CREATE_DECIMAL_TYPE, description = "Transform NumericType (decimal) to EDataType")
    @Greedy
    @Guard(method = "isDecimalGuard")
    @Transform(type = NumericType.class)
    @To(type = EDataType.class)
    public TransformFunction<NumericType, EDataType> createDecimalType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName(getDecimalClassName(s));
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * rule CreateMeasuredAnnotationOfIntegerType
     *     transform s : JUDOPSM!MeasuredType
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = CREATE_MEASURED_ANNOTATION_OF_INTEGER_TYPE, description = "Add measured annotation to numeric types")
    @Transform(type = MeasuredType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<MeasuredType, EAnnotation> createMeasuredAnnotationOfIntegerType() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("measured"));

            // Add unit detail
            if (s.getStoreUnit() != null) {
                t.getDetails().put("unit", s.getStoreUnit().getName());

                // Add measure detail (namespace of the unit)
                if (s.getStoreUnit().eContainer() instanceof NamespaceElement) {
                    String measure = namespaceElementToString((NamespaceElement) s.getStoreUnit().eContainer());
                    t.getDetails().put("measure", measure);
                }
            }

            // Add to equivalent type using named rule (thread-safe)
            // First try to find the target via the integer or decimal rule
            EDataType dataType = null;
            if (Psm2AsmHelper.isInteger(s)) {
                dataType = ctx.equivalent(s, EDataType.class, CREATE_INTEGER_TYPE);
            } else if (Psm2AsmHelper.isDecimal(s)) {
                dataType = ctx.equivalent(s, EDataType.class, CREATE_DECIMAL_TYPE);
            }
            if (dataType != null) {
                Psm2AsmHelper.addAnnotation(dataType, t);
            }

            return t;
        };
    }

    // =========================================================================
    // BOOLEAN TYPE RULES
    // =========================================================================

    /**
     * @greedy
     * rule CreateBooleanType
     *     transform s : JUDOPSM!BooleanType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_BOOLEAN_TYPE, description = "Transform BooleanType to EDataType")
    @Greedy
    @Transform(type = BooleanType.class)
    @To(type = EDataType.class)
    public TransformFunction<BooleanType, EDataType> createBooleanType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("java.lang.Boolean");
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    // =========================================================================
    // BINARY TYPE RULES
    // =========================================================================

    /**
     * @greedy
     * rule CreateBinaryType
     *     transform s : JUDOPSM!BinaryType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_BINARY_TYPE, description = "Transform BinaryType to EDataType")
    @Greedy
    @Transform(type = BinaryType.class)
    @To(type = EDataType.class)
    public TransformFunction<BinaryType, EDataType> createBinaryType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("byte[]");

            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * rule CreateBinaryConstraintsAnnotation
     *     transform s : JUDOPSM!BinaryType
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = CREATE_BINARY_CONSTRAINTS_ANNOTATION, description = "Add constraints annotation to BinaryType")
    @Transform(type = BinaryType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BinaryType, EAnnotation> createBinaryConstraintsAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));

            if (s.getMimeTypes() != null && !s.getMimeTypes().isEmpty()) {
                t.getDetails().put("mimeTypes", String.join(",", s.getMimeTypes()));
            }

            if (s.getMaxFileSize() > 0) {
                t.getDetails().put("maxFileSize", String.valueOf(s.getMaxFileSize()));
            }

            // Add to equivalent BinaryType using named rule (thread-safe)
            EDataType binaryType = ctx.equivalent(s, EDataType.class, CREATE_BINARY_TYPE);
            if (binaryType != null) {
                Psm2AsmHelper.addAnnotation(binaryType, t);
            }

            return t;
        };
    }

    // =========================================================================
    // DATE/TIME TYPE RULES
    // =========================================================================

    /**
     * @greedy
     * rule CreateDateType
     *     transform s : JUDOPSM!DateType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_DATE_TYPE, description = "Transform DateType to EDataType")
    @Greedy
    @Transform(type = DateType.class)
    @To(type = EDataType.class)
    public TransformFunction<DateType, EDataType> createDateType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("java.time.LocalDate");
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * @greedy
     * rule CreateTimestampType
     *     transform s : JUDOPSM!TimestampType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_TIMESTAMP_TYPE, description = "Transform TimestampType to EDataType")
    @Greedy
    @Transform(type = TimestampType.class)
    @To(type = EDataType.class)
    public TransformFunction<TimestampType, EDataType> createTimestampType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("java.time.LocalDateTime");
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * @greedy
     * rule CreateTimeType
     *     transform s : JUDOPSM!TimeType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_TIME_TYPE, description = "Transform TimeType to EDataType")
    @Greedy
    @Transform(type = TimeType.class)
    @To(type = EDataType.class)
    public TransformFunction<TimeType, EDataType> createTimeType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("java.time.LocalTime");
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }

    // =========================================================================
    // CUSTOM TYPE RULES
    // =========================================================================

    /**
     * Guard for CreateCustomType: excludes types handled by other rules
     */
    public boolean isCustomTypeGuard(EObject source, TransformationContext ctx) {
        if (source instanceof CustomType) {
            CustomType customType = (CustomType) source;
            return !(customType instanceof NumericType) 
                && !(customType instanceof BooleanType) 
                && !(customType instanceof EnumerationType) 
                && !(customType instanceof StringType) 
                && !(customType instanceof DateType) 
                && !(customType instanceof TimestampType) 
                && !(customType instanceof TimeType)
                && !(customType instanceof PasswordType)
                && !(customType instanceof XMLType);
        }
        return false;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * @greedy
     * rule CreateCustomType
     *     transform s : JUDOPSM!CustomType
     *     to t : ASM!EDataType
     */
    @TransformRule(name = CREATE_CUSTOM_TYPE, description = "Transform CustomType to EDataType")
    @Greedy
    @Guard(method = "isCustomTypeGuard")
    @Transform(type = CustomType.class)
    @To(type = EDataType.class)
    public TransformFunction<CustomType, EDataType> createCustomType() {
        return (s, ctx) -> {
            EDataType t = ctx.createTarget(EDataType.class);
            t.setName(s.getName());
            t.setInstanceClassName("java.lang.Object");
            
            // Add to container package (thread-safe)
            EPackage containerPkg = Psm2AsmHelper.getContainerPackage(s, ctx);
            Psm2AsmHelper.addClassifier(containerPkg, t);

            return t;
        };
    }
}
