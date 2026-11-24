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
 * Data Update Before Incremental transformation phase for RDBMS to Liquibase transformation.
 * <p>
 * This phase creates SQL file references for data updates that need to occur before schema changes:
 * <ul>
 *   <li>Value field to foreign key conversions</li>
 *   <li>Foreign key to value field conversions</li>
 *   <li>Field size changes</li>
 *   <li>Type changes</li>
 * </ul>
 * Mirrors {@code dataUpdateBeforeIncremental.etl}: the rules run one after the other over all modify-field operations
 * (rule declaration order), every rule creates its change set first, and a {@link SqlFile} is added only for an
 * existing non-empty review script; unknown scripts are recorded in {@code missingReviewScripts}.
 * See change: rdbms-incremental-without-epsilon.
 * </p>
 */
@Slf4j
public class DataUpdateBeforeZetaTransformation extends AbstractIncrementalSubTransformation {

    private final ReviewResolver reviewResolver;
    private final Map<String, String> missingReviewScripts;

    @Builder
    public DataUpdateBeforeZetaTransformation(
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
        log.debug("Executing Data Update Before Incremental transformation");

        initializeChangeLog();

        reviewRule(RdbmsModifyFieldOperation::isChangedValueFieldToForeignKey,
                "change-to-foreign-key-", "change-to-foreign-key", "_to_foreign_key_before_",
                "Sql script not found for value field changed to foreign key [%s] of [%s]",
                CREATE_SQL_FILE_FOR_CHANGING_TO_FK_BEFORE);
        reviewRule(RdbmsModifyFieldOperation::isChangedForeignKeyToValueField,
                "change-to-value-field-", "change-to-value-field", "_to_value_field_before_",
                "Sql script not found for foreign key changed to value field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_BEFORE);
        reviewRule(op -> op.isReviewRequired() && op.isSizeChanged(),
                "modify-size-in-", "modify-size", "_size_",
                "Sql script not found for changed size of field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_SIZE_CHANGE);
        reviewRule(RdbmsModifyFieldOperation::isTypeChanged,
                "modify-type-in-before-", "modify-type", "_type_before_",
                "Sql script not found for changed type of field [%s] of [%s]",
                CREATE_SQL_FILE_FOR_TYPE_CHANGE_BEFORE);

        log.debug("Data Update Before Incremental transformation completed");
        return buildTraceResult();
    }

    private void reviewRule(Predicate<RdbmsModifyFieldOperation> guard, String changeSetIdPrefix,
                            String logicalFilePath, String sqlNameInfix, String messageFormat, String ruleName) {
        all(RdbmsModifyFieldOperation.class).filter(guard).forEach(op -> {
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
