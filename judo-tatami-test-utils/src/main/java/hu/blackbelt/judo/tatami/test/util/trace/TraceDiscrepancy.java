package hu.blackbelt.judo.tatami.test.util.trace;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2025 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

/**
 * Represents a discrepancy between ETL and Zeta transformation traces.
 * Used by {@link DualTraceComparator} to report differences in source-to-target mappings.
 */
public record TraceDiscrepancy(
        String sourceId,
        String etlTargetId,
        String zetaTargetId,
        String etlRuleName,
        String zetaRuleName,
        Type type
) {
    public enum Type {
        /** ETL produced a target for this source, but Zeta did not. */
        MISSING_IN_ZETA,
        /** Zeta produced a target for this source, but ETL did not. */
        MISSING_IN_ETL,
        /** Both produced a target, but the target types differ. */
        TARGET_MISMATCH
    }

    public String describe() {
        return switch (type) {
            case MISSING_IN_ZETA ->
                    String.format("MISSING_IN_ZETA: source=%s, etlTarget=%s (rule=%s)",
                            sourceId, etlTargetId, etlRuleName);
            case MISSING_IN_ETL ->
                    String.format("MISSING_IN_ETL: source=%s, zetaTarget=%s (rule=%s)",
                            sourceId, zetaTargetId, zetaRuleName);
            case TARGET_MISMATCH ->
                    String.format("TARGET_MISMATCH: source=%s, etlTarget=%s (rule=%s), zetaTarget=%s (rule=%s)",
                            sourceId, etlTargetId, etlRuleName, zetaTargetId, zetaRuleName);
        };
    }
}
