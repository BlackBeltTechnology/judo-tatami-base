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
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import java.util.Map;

import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsHelper.*;
import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames.*;

/**
 * Class transformation rules from class.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>EClassToRdbmsTable - transforms entity EClass to RdbmsTable</li>
 *   <li>EClassToTableIdField - creates ID field for entity table</li>
 *   <li>EClassToTableTypeField - creates TYPE field for entity table</li>
 *   <li>EClassToTableVersionField - creates VERSION field for entity table</li>
 *   <li>EClassToTableCreateUsernameField - creates CREATE_USERNAME field</li>
 *   <li>EClassToTableCreateUserIdField - creates CREATE_USER_ID field</li>
 *   <li>EClassToTableCreateTimestampField - creates CREATE_TIMESTAMP field</li>
 *   <li>EClassToTableUpdateUsernameField - creates UPDATE_USERNAME field</li>
 *   <li>EClassToTableUpdateUserIdField - creates UPDATE_USER_ID field</li>
 *   <li>EClassToTableUpdateTimestampField - creates UPDATE_TIMESTAMP field</li>
 * </ul>
 * </p>
 * <p>
 * Key ETL pattern - named equivalent lookup:
 * <pre>
 * var table = s.equivalent("EClassToRdbmsTable");
 * table.fields.add(t);
 * </pre>
 * In Zeta:
 * <pre>
 * RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
 * table.getFields().add(t);
 * </pre>
 * </p>
 */
@Slf4j
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EClass.class, target = RdbmsTable.class)
public class ClassRules {

    private AsmUtils asmUtils;
    private Map<String, TypeMapping> typeMappings;

    /**
     * Default constructor required for TransformationRegistry.
     */
    public ClassRules() {
    }

    /**
     * Constructor with dependencies.
     */
    public ClassRules(AsmUtils asmUtils, Map<String, TypeMapping> typeMappings) {
        this.asmUtils = asmUtils;
        this.typeMappings = typeMappings;
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: EClass is an entity type.
     * Matches ETL: guard : s.isEntityType()
     */
    public boolean isEntityType(EObject source, TransformationContext ctx) {
        if (source instanceof EClass) {
            EClass eClass = (EClass) source;
            return eClass.getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/entity") != null;
        }
        return false;
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

    private void fillTypeFromContext(RdbmsField field, String javaType, TransformationContext ctx) {
        fillType(field, javaType, null, getTypeMappings(ctx), getAsmUtils(ctx));
    }

    // =========================================================================
    // PRIMARY TABLE RULE
    // =========================================================================

    /**
     * rule EClassToRdbmsTable
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsTable {
     *         guard : s.isEntityType()
     *         s.root().equivalent("rootPackegeToModel").rdbmsTables.add(t);
     *     }
     * <p>
     * ETL DIFFERENCE: ETL uses @primary annotation for default equivalent.
     * Zeta uses explicit named equivalent via ctx.equivalent(source, RULE_NAME).
     * </p>
     */
    @TransformRule(name = ECLASS_TO_RDBMS_TABLE, description = "Transform entity EClass to RdbmsTable")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsTable.class)
    public TransformFunction<EClass, RdbmsTable> eClassToRdbmsTable() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("  Add table: {}", utils.getClassifierFQName(s));

            RdbmsTable t = ctx.createTarget(RdbmsTable.class);

            // Set basic properties
            int tableNameMaxSize = ctx.getAttribute("tableNameMaxSize");
            String tablePrefix = ctx.getAttribute("tablePrefix");
            boolean createSimpleName = ctx.getAttribute("createSimpleName");
            int shortNameSize = ctx.getAttribute("shortNameSize");
            int nameSize = ctx.getAttribute("nameSize");
            t.setSqlName(tableSqlName(s, tableNameMaxSize, tablePrefix, createSimpleName, shortNameSize, nameSize, utils));
            t.setName(utils.getClassifierFQName(s));
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/Table");

            // Add to model using named equivalent (ETL: s.root().equivalent("rootPackegeToModel"))
            EPackage rootPackage = getRootPackage(s);
            RdbmsModel model = ctx.equivalent(rootPackage, ROOT_PACKAGE_TO_MODEL);
            if (model != null) {
                model.getRdbmsTables().add(t);
            }

            // Set up inheritance using named equivalent lookup (ETL: sup.equivalent("EClassToRdbmsTable"))
            for (EClass superType : s.getESuperTypes()) {
                if (superType.getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/entity") != null) {
                    RdbmsTable parentTable = ctx.equivalent(superType, ECLASS_TO_RDBMS_TABLE);
                    if (parentTable != null) {
                        t.getParents().add(parentTable);
                    }
                }
            }

            // Set XMI ID
            

            return t;
        };
    }

    // =========================================================================
    // SYSTEM FIELD RULES
    // =========================================================================

    /**
     * rule EClassToTableIdField
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsIdentifierField {
     *         guard : s.isEntityType()
     *         var table = s.equivalent("EClassToRdbmsTable");
     *         table.fields.add(t);
     *         table.primaryKey = t;
     *     }
     */
    @TransformRule(name = ECLASS_TO_TABLE_ID_FIELD, description = "Create ID field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsIdentifierField.class)
    public TransformFunction<EClass, RdbmsIdentifierField> eClassToTableIdField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add primary key: {}#_id", utils.getClassifierFQName(s));

            RdbmsIdentifierField t = ctx.createTarget(RdbmsIdentifierField.class);
            t.setName(utils.getClassifierFQName(s) + "#_id");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableIdField");
            t.setSqlName("ID");
            fillTypeFromContext(t, "java.util.UUID", ctx);

            // Add to table using named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
                table.setPrimaryKey(t);
            }

            
            return t;
        };
    }

    /**
     * rule EClassToTableTypeField
     *     transform s : ASM!EClass
     *     to t : RDBMS!RdbmsValueField {
     *         guard : s.isEntityType()
     *         var table = s.equivalent("EClassToRdbmsTable");
     *         table.fields.add(t);
     *     }
     */
    @TransformRule(name = ECLASS_TO_TABLE_TYPE_FIELD, description = "Create TYPE field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableTypeField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add type: {}#_type", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_type");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableTypeField");
            t.setSqlName("TYPE");
            t.setMandatory(true);
            fillTypeFromContext(t, "java.lang.String", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }

    /**
     * rule EClassToTableVersionField
     */
    @TransformRule(name = ECLASS_TO_TABLE_VERSION_FIELD, description = "Create VERSION field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableVersionField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add version: {}#_version", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_version");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableVersionField");
            t.setSqlName("VERSION");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.lang.Integer", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }

    /**
     * rule EClassToTableCreateUsernameField
     */
    @TransformRule(name = ECLASS_TO_TABLE_CREATE_USERNAME_FIELD, description = "Create CREATE_USERNAME field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableCreateUsernameField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add create username: {}#_create_username", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_create_username");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableCreateUsernameField");
            t.setSqlName("CREATE_USERNAME");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.lang.String", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }

            
            return t;
        };
    }

    /**
     * rule EClassToTableCreateUserIdField
     */
    @TransformRule(name = ECLASS_TO_TABLE_CREATE_USER_ID_FIELD, description = "Create CREATE_USER_ID field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableCreateUserIdField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add create user ID: {}#_create_user_id", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_create_user_id");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableCreateUserIdField");
            t.setSqlName("CREATE_USER_ID");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.util.UUID", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }

    /**
     * rule EClassToTableCreateTimestampField
     */
    @TransformRule(name = ECLASS_TO_TABLE_CREATE_TIMESTAMP_FIELD, description = "Create CREATE_TIMESTAMP field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableCreateTimestampField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add create timestamp: {}#_create_timestamp", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_create_timestamp");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableCreateTimestampField");
            t.setSqlName("CREATE_TIMESTAMP");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.time.LocalDateTime", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }

    /**
     * rule EClassToTableUpdateUsernameField
     */
    @TransformRule(name = ECLASS_TO_TABLE_UPDATE_USERNAME_FIELD, description = "Create UPDATE_USERNAME field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableUpdateUsernameField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add update username: {}#_update_username", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_update_username");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableUpdateUsernameField");
            t.setSqlName("UPDATE_USERNAME");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.lang.String", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }

    /**
     * rule EClassToTableUpdateUserIdField
     */
    @TransformRule(name = ECLASS_TO_TABLE_UPDATE_USER_ID_FIELD, description = "Create UPDATE_USER_ID field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableUpdateUserIdField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add update user ID: {}#_update_user_id", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_update_user_id");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableUpdateUserIdField");
            t.setSqlName("UPDATE_USER_ID");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.util.UUID", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }

    /**
     * rule EClassToTableUpdateTimestampField
     */
    @TransformRule(name = ECLASS_TO_TABLE_UPDATE_TIMESTAMP_FIELD, description = "Create UPDATE_TIMESTAMP field for entity table")
    @Guard(method = "isEntityType")
    @Transform(type = EClass.class)
    @To(type = RdbmsValueField.class)
    public TransformFunction<EClass, RdbmsValueField> eClassToTableUpdateTimestampField() {
        return (s, ctx) -> {
            AsmUtils utils = getAsmUtils(ctx);
            log.debug("    Add update timestamp: {}#_update_timestamp", utils.getClassifierFQName(s));

            RdbmsValueField t = ctx.createTarget(RdbmsValueField.class);
            t.setName(utils.getClassifierFQName(s) + "#_update_timestamp");
            t.setUuid("(asm/" + ctx.getElementId(s) + ")/TableUpdateTimestampField");
            t.setSqlName("UPDATE_TIMESTAMP");
            t.setMandatory(false);
            fillTypeFromContext(t, "java.time.LocalDateTime", ctx);

            // Use named equivalent (ETL: s.equivalent("EClassToRdbmsTable"))
            RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
            if (table != null) {
                table.getFields().add(t);
            }


            return t;
        };
    }
}
