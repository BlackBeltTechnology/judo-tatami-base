package hu.blackbelt.judo.tatami.psm.validation;

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

import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.validation.PsmJavaValidationException;
import hu.blackbelt.judo.meta.psm.validation.PsmValidator;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.zeta.validation.core.Severity;
import hu.blackbelt.judo.zeta.validation.core.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Work class for PSM validation using Zeta (native Java) validation rules.
 * This provides an alternative to {@link PsmValidationWork} which uses Epsilon EVL.
 */
@Slf4j
public class PsmValidationZetaWork extends AbstractTransformationWork {

    public PsmValidationZetaWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
        psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

        List<ValidationResult> results = PsmValidator.validate(log, psmModel.get());

        // Check for errors
        List<ValidationResult> errors = results.stream()
                .filter(r -> r.getSeverity() == Severity.ERROR)
                .collect(Collectors.toList());

        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("PSM Zeta validation failed with errors:\n");
            for (ValidationResult error : errors) {
                message.append("  - [").append(error.getConstraintName()).append("] ")
                        .append(error.getMessage()).append("\n");
            }
            throw new PsmJavaValidationException(
                    message.toString(),
                    results,
                    errors.stream().map(r -> r.getConstraintName() + "|" + r.getMessage()).collect(Collectors.toSet()),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet()
            );
        }

        log.info("PSM Zeta validation completed successfully");
    }
}
