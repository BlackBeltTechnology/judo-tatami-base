package hu.blackbelt.judo.tatami.asm2rdbms.zeta.rules;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMapping;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformGuard;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;

import java.util.Map;

import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsHelper.*;
import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames.*;

/**
 * Attribute transformation rules from attribute.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>EAttributeToRdbmsField (@Abstract) - base rule for attribute to field</li>
 *   <li>EAttributeToTableValueField (@Extends) - concrete rule that adds field to table</li>
 *   <li>EAttributeToIndex - creates index for identifier attributes</li>
 * </ul>
 * </p>
 * <p>
 * Key ETL patterns - @abstract with @extends:
 * <pre>
 * @abstract
 * rule EAttributeToRdbmsField
 *     transform s : ASM!EAttribute
 *     to t : RDBMS!RdbmsField { ... }
 *
 * rule EAttributeToTableValueField
 *     transform s : ASM!EAttribute
 *     to t : RDBMS!RdbmsValueField
 *     extends EAttributeToRdbmsField { ... }
 * </pre>
 * In Zeta:
 * <pre>
 * @Abstract
 * public TransformFunction&lt;EAttribute, RdbmsField&gt; eAttributeToRdbmsField() { ... }
 *
 * @Extends({EATTRIBUTE_TO_RDBMS_FIELD})
 * public TransformFunction&lt;EAttribute, RdbmsValueField&gt; eAttributeToTableValueField() {
 *     return (s, ctx) -> {
 *         RdbmsValueField t = ctx.executeParentRule(EATTRIBUTE_TO_RDBMS_FIELD, s);
 *         // ...
 *     };
 * }
 * </pre>
 * </p>
 */
@Slf4j
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EAttribute.class, target = RdbmsField.class)
public class AttributeRules {

    private AsmUtils asmUtils;
    private Map<String, TypeMapping> typeMappings;

    /**
     * Default constructor required for TransformationRegistry.
     */
    public AttributeRules() {
    }

    /**
     * Constructor with dependencies.
     */
    public AttributeRules(AsmUtils asmUtils, Map<String, TypeMapping> typeMappings) {
        this.asmUtils = asmUtils;
        this.typeMappings = typeMappings;
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: EAttribute's containing class is an entity type.
     * Matches ETL: guard : s.eContainingClass.isEntityType()
     */
    public TransformGuard isAttributeInEntityType() {
        return (source, ctx) -> {
            if (!(source instanceof EAttribute attr)) return false;
            EClass containingClass = attr.getEContainingClass();
            return containingClass != null &&
                   containingClass.getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/entity") != null;
        };
    }

    /**
     * Guard: EAttribute's containing class is entity type AND attribute is not derived.
     * Matches ETL: guard : s.eContainingClass.isEntityType() and not s.derived
     */
    public TransformGuard isAttributeInEntityTypeNotDerived() {
        return (source, ctx) -> {
            if (!(source instanceof EAttribute attr)) return false;
            if (attr.isDerived()) return false;
            return isAttributeInEntityType().evaluate(source, ctx);
        };
    }

    /**
     * Guard: Attribute is an identifier in entity type and not derived.
     * Matches ETL: guard : s.eContainingClass.isEntityType() and asmUtils.isIdentifier(s) and not s.derived
     */
    public TransformGuard isIdentifierAttributeInEntityType() {
        return (source, ctx) -> {
            if (!(source instanceof EAttribute attr)) return false;
            if (attr.isDerived()) return false;
            AsmUtils utils = getAsmUtils(ctx);
            if (utils == null || !utils.isIdentifier(attr)) return false;
            return isAttributeInEntityType().evaluate(source, ctx);
        };
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private AsmUtils getAsmUtils(TransformationContext ctx) {
        if (asmUtils != null) return asmUtils;
        return ctx.getAttribute("asmUtils");
    }

    private Map<String, TypeMapping> getTypeMappings(TransformationContext ctx) {
        if (typeMappings != null) return typeMappings;
        return ctx.getAttribute("typeMappings");
    }

    // =========================================================================
    // ABSTRACT RULE
    // =========================================================================

    /**
     * @abstract
     * rule EAttributeToRdbmsField
     *     transform s : ASM!EAttribute
     *     to t : RDBMS!RdbmsField {
     *         guard : s.eContainingClass.isEntityType()
     *         t.fillType(s.eType.instanceClassName, s);
     *         t.sqlName = s.fieldSqlName();
     *         t.mandatory = false;
     *         t.name = s.getAttributeFQName();
     *     }
     * <p>
     * This abstract rule sets up common field properties.
     * Extended by EAttributeToTableValueField.
     * </p>
     */
    @TransformRule(name = EATTRIBUTE_TO_RDBMS_FIELD, description = "Abstract base rule for attribute to field transformation")
    @Abstract
    @Guard(method = "isAttributeInEntityType")
    @Transform(type = EAttribute.class)
    @To(type = RdbmsField.class)
    public TransformFunction<EAttribute, RdbmsField> eAttributeToRdbmsField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("  Class: {}", utils.getClassifierFQName(s.getEContainingClass()));

            // Create field - actual type will be RdbmsValueField from extending rule
            RdbmsField t = ctx.createTarget(RdbmsField.class, s, "RdbmsField");
            t.setUuid(ctx.buildSourceBasedId(s, "RdbmsField"));
            t.setName(utils.getAttributeFQName(s));
            t.setMandatory(false);

            // Set SQL name
            int columnNameMaxSize = ctx.getAttribute("columnNameMaxSize");
            String columnPrefix = ctx.getAttribute("columnPrefix");
            int nameSize = ctx.getAttribute("nameSize");
            t.setSqlName(fieldSqlName(s, columnNameMaxSize, columnPrefix, nameSize, utils));

            // Set type based on attribute type
            EClassifier eType = s.getEType();
            Map<String, TypeMapping> mappings = getTypeMappings(ctx);
            if (eType instanceof EEnum) {
                fillType(t, "java.lang.Integer", s, mappings, utils);
            } else if (eType != null) {
                fillType(t, eType.getInstanceClassName(), s, mappings, utils);
            } else {
                log.warn("Could not determine type for: {}#{}", s.getEContainingClass().getName(), s.getName());
                fillType(t, "java.lang.Integer", s, mappings, utils);
            }

            return t;
        };
    }

    // =========================================================================
    // CONCRETE RULES
    // =========================================================================

    /**
     * rule EAttributeToTableValueField
     *     transform s : ASM!EAttribute
     *     to t : RDBMS!RdbmsValueField
     *     extends EAttributeToRdbmsField {
     *         guard : s.eContainingClass.isEntityType() and not s.derived
     *         t.setId("(asm/" + s.getId() + ")/TableValueField");
     *         s.eContainingClass.equivalent("EClassToRdbmsTable").fields.add(t);
     *     }
     * <p>
     * Key ETL patterns:
     * - extends EAttributeToRdbmsField - uses ctx.executeParentRule()
     * - s.eContainingClass.equivalent("EClassToRdbmsTable") - named equivalent lookup
     * </p>
     */
    @TransformRule(name = EATTRIBUTE_TO_TABLE_VALUE_FIELD, description = "Transform EAttribute to RdbmsValueField in table")
    @Extends({EATTRIBUTE_TO_RDBMS_FIELD})
    @Guard(method = "isAttributeInEntityTypeNotDerived")
    @Transform(type = EAttribute.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EAttribute, RdbmsValueField> eAttributeToTableValueField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add attribute: {}", utils.getAttributeFQName(s));

            // Execute parent rule to get base field setup
            RdbmsValueField t = ctx.executeParentRule(EATTRIBUTE_TO_RDBMS_FIELD, s);

            // Override XMI resource ID to /TableValueField (ETL concrete rule calls setId with this suffix)
            // Note: uuid field stays as /RdbmsField (ETL does not override uuid in the concrete rule)
            ctx.setElementId(t, ctx.buildSourceBasedId(s, "TableValueField"));

            // Add to table using named equivalent lookup
            // Matches ETL: s.eContainingClass.equivalent("EClassToRdbmsTable").fields.add(t)
            RdbmsTable table = ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }

            return t;
        };
    }

    /**
     * rule EAttributeToIndex
     *     transform s : ASM!EAttribute
     *     to u : RDBMS!RdbmsIndex {
     *         guard : s.eContainingClass.isEntityType() and asmUtils.isIdentifier(s) and not s.derived
     *         s.eContainingClass.equivalent("EClassToRdbmsTable").indexes.add(u);
     *         u.fields.add(s.equivalent("EAttributeToTableValueField"));
     *     }
     * <p>
     * Key ETL patterns:
     * - Uses named equivalent to get the table
     * - Uses named equivalent to get the value field: s.equivalent("EAttributeToTableValueField")
     * </p>
     */
    @TransformRule(name = EATTRIBUTE_TO_INDEX, description = "Create index for identifier attribute")
    @Guard(method = "isIdentifierAttributeInEntityType")
    @Transform(type = EAttribute.class)
    @To(type = RdbmsIndex.class)
    public TransformFunction<EAttribute, RdbmsIndex> eAttributeToIndex() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add index: {}", utils.getAttributeFQName(s));

            RdbmsIndex t = ctx.createTarget(RdbmsIndex.class, s, "Index");
            t.setUuid(ctx.buildSourceBasedId(s, "Index"));
            t.setName(utils.getAttributeFQName(s));
            t.setSqlName("IDX_" + md5(t.getUuid()));

            // Add to table using named equivalent lookup
            RdbmsTable table = ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getIndexes().add(t);
            }

            // Add field to index using named equivalent lookup
            // Matches ETL: u.fields.add(s.equivalent("EAttributeToTableValueField"))
            RdbmsValueField field = ctx.equivalent(s, EATTRIBUTE_TO_TABLE_VALUE_FIELD);
            if (field != null) {
                t.getFields().add(field);
            }

            return t;
        };
    }
}
