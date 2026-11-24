package hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.incremental;

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

import hu.blackbelt.judo.meta.liquibase.ChangeSet;
import hu.blackbelt.judo.meta.liquibase.SqlFile;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsCreateFieldOperation;
import hu.blackbelt.judo.meta.rdbms.RdbmsFieldOperation;
import hu.blackbelt.judo.meta.rdbms.RdbmsModifyFieldOperation;
import hu.blackbelt.judo.meta.rdbms.RdbmsTableOperation;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.ReviewResolver;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseRuleNames.*;

/**
 * Data Update After Incremental transformation phase for RDBMS to Liquibase transformation.
 * <p>
 * This phase creates SQL file references for data updates that need to occur after schema changes:
 * <ul>
 *   <li>Mandatory field reviews</li>
 *   <li>Create field reviews</li>
 *   <li>Value field to foreign key conversions (after)</li>
 *   <li>Foreign key to value field conversions (after)</li>
 *   <li>Type changes (after)</li>
 * </ul>
 * Mirrors {@code dataUpdateAfterIncremental.etl}: the rules run one after the other (rule declaration order), every
 * rule creates its change set first, and a {@link SqlFile} is added only for an existing non-empty review script;
 * unknown scripts are recorded in {@code missingReviewScripts}.
 * See change: rdbms-incremental-without-epsilon.
 * </p>
 */
@Slf4j
public class DataUpdateAfterZetaTransformation extends AbstractIncrementalSubTransformation {

    private final ReviewResolver reviewResolver;
    private final Map<String, String> missingReviewScripts;

    @Builder
    public DataUpdateAfterZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize,
            @NonNull ReviewResolver reviewResolver,
            Map<String, String> missingReviewScripts) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
        this.reviewResolver = reviewResolver;
        this.missingReviewScripts = missingReviewScripts != null ? missingReviewScripts : new HashMap<>();
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing Data Update After Incremental transformation");

        initializeChangeLog();

        reviewRule(RdbmsModifyFieldOperation.class, op -> op.isReviewRequired() && op.isMandatoryChanged(),
                "modify-mandatory-", "modify-mandatory", "_mandatory_",
                "Sql script not found for mandatory field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_MANDATORY_REVIEW);
        reviewRule(RdbmsCreateFieldOperation.class, RdbmsCreateFieldOperation::isReviewRequired,
                "create-field-", "create-field", "_create_",
                "Sql script not found for newly created mandatory field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_CREATE_FIELD_REVIEW);
        reviewRule(RdbmsModifyFieldOperation.class, RdbmsModifyFieldOperation::isChangedValueFieldToForeignKey,
                "change-to-foreign-key-", "change-to-foreign-key", "_to_foreign_key_after_",
                "Sql script not found for value field changed to foreign key [%s] of [%s]",
                CREATE_SQL_FILE_FOR_CHANGING_TO_FK_AFTER);
        reviewRule(RdbmsModifyFieldOperation.class, RdbmsModifyFieldOperation::isChangedForeignKeyToValueField,
                "change-to-value-field-", "change-to-value-field", "_to_value_field_after_",
                "Sql script not found for foreign key changed to value field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_AFTER);
        reviewRule(RdbmsModifyFieldOperation.class, RdbmsModifyFieldOperation::isTypeChanged,
                "modify-type-in-after-", "modify-type", "_type_after_",
                "Sql script not found for changed type of field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_TYPE_CHANGE_AFTER);

        log.debug("Data Update After Incremental transformation completed");
        return buildTraceResult();
    }

    private <T extends RdbmsFieldOperation> void reviewRule(
            Class<T> operationType, Predicate<T> guard, String changeSetIdPrefix, String logicalFilePath,
            String sqlNameInfix, String messageFormat, String ruleName) {
        all(operationType).filter(guard).forEach(op -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();
            log.debug("  Transform SQL file {}: {}.{}", ruleName, tableName, columnName);

            ChangeSet changeSet = getOrCreateChangeSet(changeSetIdPrefix + tableName + "-" + context, logicalFilePath);
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + sqlNameInfix + dialect + ".sql";
            SqlFile sqlFile = addReviewSqlFile(reviewResolver, missingReviewScripts, changeSet, sqlName,
                    String.format(messageFormat, columnName, tableName));
            addTrace(op, ruleName, sqlFile);
        });
    }
}
