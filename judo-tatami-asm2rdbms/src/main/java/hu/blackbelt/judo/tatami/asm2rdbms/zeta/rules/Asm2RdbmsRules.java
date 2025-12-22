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

import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames.*;

/**
 * ASM to RDBMS transformation rules.
 * <p>
 * This class documents the transformation rules using @TransformRule annotations.
 * The actual transformation is orchestrated by {@link hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation}.
 * </p>
 * <p>
 * Rule categories:
 * <ul>
 *   <li>Package Rules (package.etl) - ROOT_PACKAGE_TO_MODEL, ROOT_PACKAGE_TO_CONFIGURATION</li>
 *   <li>Class Rules (class.etl) - ECLASS_TO_RDBMS_TABLE and system field rules</li>
 *   <li>Attribute Rules (attribute.etl) - EATTRIBUTE_TO_TABLE_VALUE_FIELD, EATTRIBUTE_TO_INDEX</li>
 *   <li>Reference Rules (reference.etl) - Foreign key and junction table rules</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EPackage.class, target = RdbmsModel.class)
public class Asm2RdbmsRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public Asm2RdbmsRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: EPackage is root package (no super package)
     */
    public boolean isRootPackage(EObject source, TransformationContext ctx) {
        if (source instanceof EPackage) {
            return ((EPackage) source).getESuperPackage() == null;
        }
        return false;
    }

    /**
     * Guard: EClass is an entity type (has entityType annotation)
     */
    public boolean isEntityType(EObject source, TransformationContext ctx) {
        if (source instanceof EClass) {
            EClass eClass = (EClass) source;
            return eClass.getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/entity") != null;
        }
        return false;
    }

    /**
     * Guard: EAttribute is non-derived
     */
    public boolean isNonDerivedAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof EAttribute) {
            return !((EAttribute) source).isDerived();
        }
        return false;
    }

    // =========================================================================
    // PACKAGE TRANSFORMATION RULES (package.etl)
    // =========================================================================

    /**
     * rule rootPackegeToModel
     *     transform s : ASM!EPackage
     *     to t : RDBMS!RdbmsModel {
     *         guard: s.eSuperPackage.isUndefined()
     *     }
     */
    @TransformRule(name = ROOT_PACKAGE_TO_MODEL, description = "Transform root EPackage to RdbmsModel")
    @Guard(method = "isRootPackage")
    @Transform(type = EPackage.class)
    @To(type = RdbmsModel.class)
    public TransformFunction<EPackage, RdbmsModel> rootPackageToModel() {
        return (s, ctx) -> {
            RdbmsModel t = ctx.createTarget(RdbmsModel.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule rootPackegeToConfiguration
     *     transform s : ASM!EPackage
     *     to t : RDBMS!RdbmsConfiguration {
     *         guard: s.eSuperPackage.isUndefined()
     *     }
     */
    @TransformRule(name = ROOT_PACKAGE_TO_CONFIGURATION, description = "Transform root EPackage to RdbmsConfiguration")
    @Guard(method = "isRootPackage")
    @Transform(type = EPackage.class)
    @To(type = RdbmsConfiguration.class)
    public TransformFunction<EPackage, RdbmsConfiguration> rootPackageToConfiguration() {
        return (s, ctx) -> {
            RdbmsConfiguration t = ctx.createTarget(RdbmsConfiguration.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    // =========================================================================
    // CLASS TRANSFORMATION RULES (class.etl)
    // =========================================================================

    /**
     * rule EClassToRdbmsTable
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsTable {
     *         guard: isEntityType(s)
     *     }
     */
    @TransformRule(name = ECLASS_TO_RDBMS_TABLE, description = "Transform entity EClass to RdbmsTable")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsTable.class)
    public TransformFunction<EClass, RdbmsTable> eClassToRdbmsTable() {
        return (s, ctx) -> {
            RdbmsTable t = ctx.createTarget(RdbmsTable.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule EClassToTableIdField
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsIdentifierField {
     *         guard: isEntityType(s)
     *     }
     */
    @TransformRule(name = ECLASS_TO_TABLE_ID_FIELD, description = "Create ID field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsIdentifierField.class)
    public TransformFunction<EClass, RdbmsIdentifierField> eClassToTableIdField() {
        return (s, ctx) -> {
            RdbmsIdentifierField t = ctx.createTarget(RdbmsIdentifierField.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule EClassToTableTypeField
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsValueField {
     *         guard: isEntityType(s)
     *     }
     */
    @TransformRule(name = ECLASS_TO_TABLE_TYPE_FIELD, description = "Create TYPE field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableTypeField() {
        return (s, ctx) -> {
            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule EClassToTableVersionField
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsValueField {
     *         guard: isEntityType(s)
     *     }
     */
    @TransformRule(name = ECLASS_TO_TABLE_VERSION_FIELD, description = "Create VERSION field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableVersionField() {
        return (s, ctx) -> {
            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    // =========================================================================
    // ATTRIBUTE TRANSFORMATION RULES (attribute.etl)
    // =========================================================================

    /**
     * rule EAttributeToTableValueField
     *     transform s : ASM!EAttribute
     *     to t : RDBMS!RdbmsValueField {
     *         guard: isEntityType(s.eContainingClass) and not s.derived
     *     }
     */
    @TransformRule(name = EATTRIBUTE_TO_TABLE_VALUE_FIELD, description = "Transform EAttribute to RdbmsValueField")
    @Guard(method = "isNonDerivedAttribute")
    @Transform(type = EAttribute.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EAttribute, RdbmsValueField> eAttributeToTableValueField() {
        return (s, ctx) -> {
            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule EAttributeToIndex
     *     transform s : ASM!EAttribute
     *     to t : RDBMS!RdbmsIndex {
     *         guard: isIdentifier(s)
     *     }
     */
    @TransformRule(name = EATTRIBUTE_TO_INDEX, description = "Create index for identifier attribute")
    @Transform(type = EAttribute.class)
    @To(type = RdbmsIndex.class)
    public TransformFunction<EAttribute, RdbmsIndex> eAttributeToIndex() {
        return (s, ctx) -> {
            RdbmsIndex t = ctx.createTarget(RdbmsIndex.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    // =========================================================================
    // REFERENCE TRANSFORMATION RULES (reference.etl)
    // =========================================================================

    /**
     * rule EReferenceToRdbmsTableForeignKey
     *     transform s : ASM!EReference
     *     to t : RDBMS!RdbmsForeignKey {
     *         guard: needsForeignKey(s)
     *     }
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY, description = "Create foreign key for reference")
    @Transform(type = EReference.class)
    @To(type = RdbmsForeignKey.class)
    public TransformFunction<EReference, RdbmsForeignKey> eReferenceToRdbmsTableForeignKey() {
        return (s, ctx) -> {
            RdbmsForeignKey t = ctx.createTarget(RdbmsForeignKey.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule EReferenceToRdbmsTableInverseForeignKey
     *     transform s : ASM!EReference
     *     to t : RDBMS!RdbmsForeignKey {
     *         guard: needsInverseForeignKey(s)
     *     }
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_TABLE_INVERSE_FOREIGN_KEY, description = "Create inverse foreign key for reference")
    @Transform(type = EReference.class)
    @To(type = RdbmsForeignKey.class)
    public TransformFunction<EReference, RdbmsForeignKey> eReferenceToRdbmsTableInverseForeignKey() {
        return (s, ctx) -> {
            RdbmsForeignKey t = ctx.createTarget(RdbmsForeignKey.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }

    /**
     * rule EReferenceToRdbmsJunctionTable
     *     transform s : ASM!EReference
     *     to t : RDBMS!RdbmsJunctionTable {
     *         guard: needsJunctionTable(s)
     *     }
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE, description = "Create junction table for many-to-many reference")
    @Transform(type = EReference.class)
    @To(type = RdbmsJunctionTable.class)
    public TransformFunction<EReference, RdbmsJunctionTable> eReferenceToRdbmsJunctionTable() {
        return (s, ctx) -> {
            RdbmsJunctionTable t = ctx.createTarget(RdbmsJunctionTable.class);
            // Implementation handled by Asm2RdbmsZetaTransformation
            return t;
        };
    }
}
