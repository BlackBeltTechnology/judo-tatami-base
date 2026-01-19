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
import hu.blackbelt.judo.meta.rdbmsRules.Rule;
import hu.blackbelt.judo.meta.rdbmsRules.Rules;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

import java.util.Map;

import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsHelper.*;
import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames.*;

/**
 * Reference transformation rules from reference.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>EReferenceToRdbmsTableForeignKey - creates FK for references</li>
 *   <li>EReferenceToRdbmsTableInverseForeignKey - creates inverse FK</li>
 *   <li>EReferenceToRdbmsJunctionTable (@Lazy) - creates junction table on demand</li>
 *   <li>EReferenceToRdbmsJunctionTablePrimaryKey - creates junction table PK</li>
 *   <li>EReferenceToRdbmsJunctionTableForeignKeyBidirectional - creates bidirectional junction FK</li>
 *   <li>EReferenceToRdbmsJunctionTableForeignKeyUnidirectional - creates both unidirectional junction FKs</li>
 * </ul>
 * </p>
 * <p>
 * Key ETL pattern - @lazy annotation:
 * <pre>
 * @lazy
 * rule EReferenceToRdbmsJunctionTable
 *     transform s : ASM!EReference
 *     to t : RDBMS!RdbmsJunctionTable { ... }
 * </pre>
 * The lazy rule is only executed when another rule calls:
 * <pre>
 * var table = s.equivalent("EReferenceToRdbmsJunctionTable");
 * </pre>
 * In Zeta, we use @Lazy annotation and the rule is triggered by ctx.equivalent() call.
 * </p>
 */
@Slf4j
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EReference.class, target = RdbmsForeignKey.class)
public class ReferenceRules {

    private AsmUtils asmUtils;
    private Map<String, TypeMapping> typeMappings;
    private Rules rules;

    /**
     * Default constructor required for TransformationRegistry.
     */
    public ReferenceRules() {
    }

    /**
     * Constructor with dependencies.
     */
    public ReferenceRules(AsmUtils asmUtils, Map<String, TypeMapping> typeMappings, Rules rules) {
        this.asmUtils = asmUtils;
        this.typeMappings = typeMappings;
        this.rules = rules;
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: Reference is between entity types and not derived.
     */
    public boolean isValidReference(EObject source, TransformationContext ctx) {
        if (source instanceof EReference) {
            EReference ref = (EReference) source;
            if (ref.isDerived()) return false;
            return isEntityType(ref.getEContainingClass()) && isEntityType(ref.getEReferenceType());
        }
        return false;
    }

    /**
     * Guard: Reference needs a foreign key.
     */
    public boolean needsForeignKey(EObject source, TransformationContext ctx) {
        if (!isValidReference(source, ctx)) return false;
        EReference ref = (EReference) source;
        Rule rule = getRules(ctx).getRuleFromReference(ref);
        return rule.isForeignKey();
    }

    /**
     * Guard: Reference needs an inverse foreign key.
     */
    public boolean needsInverseForeignKey(EObject source, TransformationContext ctx) {
        if (!isValidReference(source, ctx)) return false;
        EReference ref = (EReference) source;
        Rule rule = getRules(ctx).getRuleFromReference(ref);
        return rule.isInverseForeignKey();
    }

    /**
     * Guard: Reference is valid for junction table (between entity types).
     */
    public boolean isValidReferenceForJunction(EObject source, TransformationContext ctx) {
        return isValidReference(source, ctx);
    }

    /**
     * Guard: Reference needs junction table and is the first reference.
     */
    public boolean needsJunctionTableFirst(EObject source, TransformationContext ctx) {
        if (!isValidReference(source, ctx)) return false;
        EReference ref = (EReference) source;
        Rule rule = getRules(ctx).getRuleFromReference(ref);
        return rule.isJoinTable() && rule.isFirst();
    }

    /**
     * Guard: Reference needs junction table with bidirectional FK (has opposite).
     */
    public boolean needsJunctionTableBidirectional(EObject source, TransformationContext ctx) {
        if (!isValidReference(source, ctx)) return false;
        EReference ref = (EReference) source;
        Rule rule = getRules(ctx).getRuleFromReference(ref);
        return rule.isJoinTable() && ref.getEOpposite() != null;
    }

    /**
     * Guard: Reference needs junction table with unidirectional FK (no opposite).
     */
    public boolean needsJunctionTableUnidirectional(EObject source, TransformationContext ctx) {
        if (!isValidReference(source, ctx)) return false;
        EReference ref = (EReference) source;
        Rule rule = getRules(ctx).getRuleFromReference(ref);
        return rule.isJoinTable() && ref.getEOpposite() == null;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private boolean isEntityType(EClass eClass) {
        return eClass != null &&
               eClass.getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/entity") != null;
    }

    private AsmUtils getAsmUtils(TransformationContext ctx) {
        if (asmUtils != null) return asmUtils;
        return ctx.getAttribute("asmUtils");
    }

    private Map<String, TypeMapping> getTypeMappings(TransformationContext ctx) {
        if (typeMappings != null) return typeMappings;
        return ctx.getAttribute("typeMappings");
    }

    private Rules getRules(TransformationContext ctx) {
        if (rules != null) return rules;
        return ctx.getAttribute("rules");
    }

    // =========================================================================
    // FOREIGN KEY RULES
    // =========================================================================

    /**
     * rule EReferenceToRdbmsTableForeignKey
     *     transform s : ASM!EReference
     *     to fk : RDBMS!RdbmsForeignKey {
     *         guard: s.eReferenceType.isEntityType() and s.eContainingClass.isEntityType()
     *                and s.ruleMapping().foreignKey and not s.derived
     *         s.eContainingClass.equivalent("EClassToRdbmsTable").fields.add(fk);
     *         fk.referenceKey = s.eReferenceType.equivalent("EClassToRdbmsTable").primaryKey;
     *     }
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY, description = "Create foreign key for reference")
    @Guard(method = "needsForeignKey")
    @Transform(type = EReference.class)
    @To(type = RdbmsForeignKey.class)
    public TransformFunction<EReference, RdbmsForeignKey> eReferenceToRdbmsTableForeignKey() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add foreign key: {}", utils.getReferenceFQName(s));

            RdbmsForeignKey fk = ctx.createTarget(RdbmsForeignKey.class);
            fk.setName(s.getName());
            fk.setUuid("(asm/" + ctx.getElementId(s) + ")/TableForeignKey");
            fk.setMandatory(false);

            int columnNameMaxSize = ctx.getAttribute("columnNameMaxSize");
            String columnPrefix = ctx.getAttribute("columnPrefix");
            String foreignKeyPrefix = ctx.getAttribute("foreignKeyPrefix");
            String junctionTablePrefix = ctx.getAttribute("junctionTablePrefix");
            boolean createSimpleName = ctx.getAttribute("createSimpleName");
            int shortNameSize = ctx.getAttribute("shortNameSize");
            int nameSize = ctx.getAttribute("nameSize");
            fk.setSqlName(referenceIdentifierSqlName(s, columnNameMaxSize, columnPrefix, nameSize, utils));
            fk.setForeignKeySqlName(referenceFkSqlName(s, foreignKeyPrefix, junctionTablePrefix, columnNameMaxSize, createSimpleName, shortNameSize, nameSize, utils));

            // Set reference to target table's primary key using named equivalent
            // Matches ETL: fk.referenceKey = s.eReferenceType.equivalent("EClassToRdbmsTable").primaryKey
            RdbmsTable targetTable = ctx.equivalent(s.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
            if (targetTable != null && targetTable.getPrimaryKey() != null) {
                fk.setReferenceKey(targetTable.getPrimaryKey());
                copyTypeFromField(fk, targetTable.getPrimaryKey());
            }

            if (s.isContainer()) {
                fk.setReadOnly(true);
                fk.setDeleteOnCascade(true);
            } else {
                fk.setReadOnly(false);
                fk.setDeleteOnCascade(false);
            }

            // Add to source table using named equivalent
            // Matches ETL: s.eContainingClass.equivalent("EClassToRdbmsTable").fields.add(fk)
            RdbmsTable sourceTable = ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
            if (sourceTable != null) {
                sourceTable.getFields().add(fk);
            }

            return fk;
        };
    }

    /**
     * rule EReferenceToRdbmsTableInverseForeignKey
     *     transform s : ASM!EReference
     *     to fk : RDBMS!RdbmsForeignKey {
     *         guard: ... and s.ruleMapping().inverseForeignKey and not s.derived
     *         s.eReferenceType.equivalent("EClassToRdbmsTable").fields.add(fk);
     *         fk.referenceKey = s.eContainingClass.equivalent("EClassToRdbmsTable").primaryKey;
     *     }
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_TABLE_INVERSE_FOREIGN_KEY, description = "Create inverse foreign key for reference")
    @Guard(method = "needsInverseForeignKey")
    @Transform(type = EReference.class)
    @To(type = RdbmsForeignKey.class)
    public TransformFunction<EReference, RdbmsForeignKey> eReferenceToRdbmsTableInverseForeignKey() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add inverse foreign key: {}", utils.getReferenceFQName(s));

            RdbmsForeignKey fk = ctx.createTarget(RdbmsForeignKey.class);
            fk.setName(firstToLowerCase(s.getEContainingClass().getName()) + firstToUpperCase(s.getName()));
            fk.setUuid("(asm/" + ctx.getElementId(s) + ")/TableInverseForeignKey");
            fk.setMandatory(false);
            fk.setReadOnly(false);
            fk.setDeleteOnCascade(false);

            int columnNameMaxSize = ctx.getAttribute("columnNameMaxSize");
            String columnPrefix = ctx.getAttribute("columnPrefix");
            String inverseForeignKeyPrefix = ctx.getAttribute("inverseForeignKeyPrefix");
            boolean createSimpleName = ctx.getAttribute("createSimpleName");
            int shortNameSize = ctx.getAttribute("shortNameSize");
            int nameSize = ctx.getAttribute("nameSize");
            fk.setSqlName(referenceInverseIdentifierSqlName(s, columnNameMaxSize, columnPrefix, createSimpleName, shortNameSize, nameSize, utils));
            fk.setForeignKeySqlName(referenceInvFkSqlName(s, inverseForeignKeyPrefix, columnNameMaxSize, createSimpleName, shortNameSize, nameSize, utils));

            // Set reference to source table's primary key
            RdbmsTable sourceTable = ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
            if (sourceTable != null && sourceTable.getPrimaryKey() != null) {
                fk.setReferenceKey(sourceTable.getPrimaryKey());
                copyTypeFromField(fk, sourceTable.getPrimaryKey());
            }

            // Add to target table
            RdbmsTable targetTable = ctx.equivalent(s.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
            if (targetTable != null) {
                targetTable.getFields().add(fk);
            }

            return fk;
        };
    }

    // =========================================================================
    // LAZY JUNCTION TABLE RULE
    // =========================================================================

    /**
     * @lazy
     * rule EReferenceToRdbmsJunctionTable
     *     transform s : ASM!EReference
     *     to t : RDBMS!RdbmsJunctionTable {
     *         guard: s.eReferenceType.isEntityType() and s.eContainingClass.isEntityType()
     *         s.eReferenceType.root().equivalent("rootPackegeToModel").rdbmsTables.add(t);
     *     }
     * <p>
     * Key ETL pattern: @lazy means this rule is only executed when another rule
     * calls s.equivalent("EReferenceToRdbmsJunctionTable").
     * In Zeta, we use @Lazy annotation.
     * </p>
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE, description = "Create junction table for many-to-many reference (lazy)")
    @Lazy
    @Guard(method = "isValidReferenceForJunction")
    @Transform(type = EReference.class)
    @To(type = RdbmsJunctionTable.class)
    public TransformFunction<EReference, RdbmsJunctionTable> eReferenceToRdbmsJunctionTable() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Create junction table: {}", utils.getReferenceFQName(s));

            RdbmsJunctionTable t = ctx.createTarget(RdbmsJunctionTable.class);
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/JunctionTable");

            int tableNameMaxSize = ctx.getAttribute("tableNameMaxSize");
            String junctionTablePrefix = ctx.getAttribute("junctionTablePrefix");
            boolean createSimpleName = ctx.getAttribute("createSimpleName");
            int shortNameSize = ctx.getAttribute("shortNameSize");
            int nameSize = ctx.getAttribute("nameSize");
            t.setSqlName(referenceManyToManyTableSqlName(s, tableNameMaxSize, junctionTablePrefix, createSimpleName, shortNameSize, nameSize, utils));

            if (s.getEOpposite() != null) {
                t.setName(utils.getReferenceFQName(s) + " to " + utils.getReferenceFQName(s.getEOpposite()));
            } else {
                t.setName(utils.getReferenceFQName(s) + " to " + utils.getClassifierFQName(s.getEReferenceType()));
            }

            // Add to model using named equivalent
            EPackage rootPackage = getRootPackage(s.getEReferenceType());
            RdbmsModel model = ctx.equivalent(rootPackage, ROOT_PACKAGE_TO_MODEL);
            if (model != null) {
                model.getRdbmsTables().add(t);
            }

            log.debug("Junction table created: {}", t.getName());
            return t;
        };
    }

    /**
     * rule EReferenceToRdbmsJunctionTablePrimaryKey
     *     transform s : ASM!EReference
     *     to p : RDBMS!RdbmsIdentifierField {
     *         guard : ... and s.ruleMapping().joinTable and s.ruleMapping().first and not s.derived
     *         var table = s.equivalent("EReferenceToRdbmsJunctionTable");  // Triggers lazy creation
     *         table.fields.add(p);
     *         table.primaryKey = p;
     *     }
     * <p>
     * This rule triggers the lazy junction table creation via equivalent() call.
     * </p>
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE_PRIMARY_KEY, description = "Create primary key for junction table")
    @Guard(method = "needsJunctionTableFirst")
    @Transform(type = EReference.class)
    @To(type = RdbmsIdentifierField.class)
    public TransformFunction<EReference, RdbmsIdentifierField> eReferenceToRdbmsJunctionTablePrimaryKey() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add junction table PK: {}", utils.getReferenceFQName(s));

            // This triggers lazy creation of the junction table
            RdbmsJunctionTable table = ctx.equivalent(s, EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
            if (table == null) {
                log.warn("Junction table not found for: {}", utils.getReferenceFQName(s));
                return null;
            }

            RdbmsIdentifierField p = ctx.createTarget(RdbmsIdentifierField.class);
            p.setName(table.getName() + "#id");
            p.setUuid("(asm/" + ctx.getElementId(s) + ")/JunctionTablePrimaryKey");
            p.setSqlName("ID");

            Map<String, TypeMapping> mappings = getTypeMappings(ctx);
            fillType(p, "java.util.UUID", null, mappings, utils);

            table.getFields().add(p);
            table.setPrimaryKey(p);

            return p;
        };
    }

    /**
     * rule EReferenceToRdbmsJunctionTableForeignKeyBidirectional
     *     transform s : ASM!EReference
     *     to fk : RDBMS!RdbmsForeignKey {
     *         guard : ... and s.ruleMapping().joinTable and s.eOpposite.isDefined() and not s.derived
     *         var mainReference;
     *         if (s.name.compareTo(s.eOpposite.name) <= 0) {
     *             mainReference = s;
     *             mainReference.equivalent("EReferenceToRdbmsJunctionTable").field1 = fk;
     *         } else {
     *             mainReference = s.eOpposite;
     *             mainReference.equivalent("EReferenceToRdbmsJunctionTable").field2 = fk;
     *         }
     *     }
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_BIDIRECTIONAL, description = "Create bidirectional FK for junction table")
    @Guard(method = "needsJunctionTableBidirectional")
    @Transform(type = EReference.class)
    @To(type = RdbmsForeignKey.class)
    public TransformFunction<EReference, RdbmsForeignKey> eReferenceToRdbmsJunctionTableForeignKeyBidirectional() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add junction FK bidirectional: {}", utils.getReferenceFQName(s));

            // Determine if this is field1 or field2 based on alphabetical order
            boolean isFirst = s.getName().compareTo(s.getEOpposite().getName()) <= 0;
            EReference mainReference = isFirst ? s : s.getEOpposite();

            // Get junction table using the main reference (alphabetically first)
            // Both sides of a bidirectional relation must use the SAME junction table
            RdbmsJunctionTable table = ctx.equivalent(mainReference, EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
            if (table == null) {
                log.warn("Junction table not found for bidirectional: {}", utils.getReferenceFQName(s));
                return null;
            }

            RdbmsForeignKey fk = ctx.createTarget(RdbmsForeignKey.class);
            fk.setName(s.getName());
            fk.setUuid("(asm/" + ctx.getElementId(s) + ")/JunctionTableForeignKeyBidirectional");
            fk.setMandatory(isMandatory(s));
            fk.setReadOnly(true);
            fk.setDeleteOnCascade(true);

            int columnNameMaxSize = ctx.getAttribute("columnNameMaxSize");
            String columnPrefix = ctx.getAttribute("columnPrefix");
            String foreignKeyPrefix = ctx.getAttribute("foreignKeyPrefix");
            String junctionTablePrefix = ctx.getAttribute("junctionTablePrefix");
            boolean createSimpleName = ctx.getAttribute("createSimpleName");
            int shortNameSize = ctx.getAttribute("shortNameSize");
            int nameSize = ctx.getAttribute("nameSize");
            fk.setSqlName(referenceIdentifierSqlName(s, columnNameMaxSize, columnPrefix, nameSize, utils));
            fk.setForeignKeySqlName(referenceFkSqlName(s, foreignKeyPrefix, junctionTablePrefix, columnNameMaxSize, createSimpleName, shortNameSize, nameSize, utils));

            // Set reference to target table's primary key
            RdbmsTable targetTable = ctx.equivalent(s.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
            if (targetTable != null && targetTable.getPrimaryKey() != null) {
                fk.setReferenceKey(targetTable.getPrimaryKey());
                copyTypeFromField(fk, targetTable.getPrimaryKey());
            }

            table.getFields().add(fk);
            if (isFirst) {
                table.setField1(fk);
            } else {
                table.setField2(fk);
            }

            return fk;
        };
    }

    /**
     * rule EReferenceToRdbmsJunctionTableForeignKeyUnidirectional
     *     transform s : ASM!EReference
     *     to fk1 : RDBMS!RdbmsForeignKey, fk2 : RDBMS!RdbmsForeignKey {
     *         guard : ... and s.ruleMapping().joinTable and s.eOpposite.isUndefined() and not s.derived
     *         junctionTable.field1 = fk1;
     *         junctionTable.field2 = fk2;
     *     }
     * <p>
     * Note: ETL produces two outputs. In Zeta, we create both FKs in one rule
     * and trace them separately.
     * </p>
     */
    @TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_UNIDIRECTIONAL_1, description = "Create unidirectional FK1 for junction table")
    @Guard(method = "needsJunctionTableUnidirectional")
    @Transform(type = EReference.class)
    @To(type = RdbmsForeignKey.class)
    public TransformFunction<EReference, RdbmsForeignKey> eReferenceToRdbmsJunctionTableForeignKeyUnidirectional() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add junction FK unidirectional: {}", utils.getReferenceFQName(s));

            RdbmsJunctionTable table = ctx.equivalent(s, EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
            if (table == null) {
                log.warn("Junction table not found for unidirectional: {}", utils.getReferenceFQName(s));
                return null;
            }

            int columnNameMaxSize = ctx.getAttribute("columnNameMaxSize");
            String columnPrefix = ctx.getAttribute("columnPrefix");
            String foreignKeyPrefix = ctx.getAttribute("foreignKeyPrefix");
            String junctionTablePrefix = ctx.getAttribute("junctionTablePrefix");
            boolean createSimpleName = ctx.getAttribute("createSimpleName");
            int shortNameSize = ctx.getAttribute("shortNameSize");
            int nameSize = ctx.getAttribute("nameSize");

            // FK1 - to target type
            RdbmsForeignKey fk1 = ctx.createTarget(RdbmsForeignKey.class);
            fk1.setName(s.getName());
            fk1.setUuid("(asm/" + ctx.getElementId(s) + ")/JunctionTableForeignKeyUnidirectional1");
            fk1.setMandatory(isMandatory(s));
            fk1.setSqlName(abbreviate(referenceIdentifierSqlName(s, columnNameMaxSize, columnPrefix, nameSize, utils), columnNameMaxSize - 1).toUpperCase() + "1");
            fk1.setForeignKeySqlName(referenceFkSqlName(s, foreignKeyPrefix, junctionTablePrefix, columnNameMaxSize, createSimpleName, shortNameSize, nameSize, utils) + "1");
            fk1.setReadOnly(true);
            fk1.setDeleteOnCascade(true);

            RdbmsTable targetTable = ctx.equivalent(s.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
            if (targetTable != null && targetTable.getPrimaryKey() != null) {
                fk1.setReferenceKey(targetTable.getPrimaryKey());
                copyTypeFromField(fk1, targetTable.getPrimaryKey());
            }

            table.getFields().add(fk1);
            table.setField1(fk1);

            // FK2 - to containing class (created inline)
            // Note: ETL produces two outputs, Zeta produces one; fk2 is created directly
            RdbmsForeignKey fk2 = RdbmsFactory.eINSTANCE.createRdbmsForeignKey();
            fk2.setName(s.getEContainingClass().getName() + "#" + s.getName());
            fk2.setUuid("(asm/" + ctx.getElementId(s) + ")/JunctionTableForeignKeyUnidirectional2");
            fk2.setMandatory(false);
            fk2.setReadOnly(true);
            fk2.setDeleteOnCascade(true);

            RdbmsTable sourceTable = ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
            if (sourceTable != null && sourceTable.getPrimaryKey() != null) {
                fk2.setReferenceKey(sourceTable.getPrimaryKey());
                copyTypeFromField(fk2, sourceTable.getPrimaryKey());
                int tableNameMaxSize = ctx.getAttribute("tableNameMaxSize");
                String tablePrefix = ctx.getAttribute("tablePrefix");
                fk2.setSqlName(abbreviate(sqlLongName(s, nameSize, utils) + "_" + tableSqlName(s.getEContainingClass(), tableNameMaxSize, tablePrefix, createSimpleName, shortNameSize, nameSize, utils) + "_" + sourceTable.getPrimaryKey().getSqlName(), columnNameMaxSize - 1).toUpperCase() + "2");
            }
            fk2.setForeignKeySqlName(referenceUniFkSqlName(s, foreignKeyPrefix, columnNameMaxSize, createSimpleName, shortNameSize, nameSize, utils) + "2");

            table.getFields().add(fk2);
            table.setField2(fk2);

            return fk1;
        };
    }
}
