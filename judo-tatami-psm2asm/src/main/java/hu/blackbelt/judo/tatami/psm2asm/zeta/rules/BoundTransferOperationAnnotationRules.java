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

import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Annotation transformation rules for BoundTransferOperation.
 * These rules add annotations to BoundTransferOperation that are equivalent to
 * what BoundOperation receives, since both are bound operations in ETL.
 *
 * Note: In ETL, CreateBoundOperationAnnotation transforms OperationDeclaration and
 * checks if it's a BoundOperation or BoundTransferOperation. In Zeta, the
 * CreateBoundAnnotationForTransferOperation rule already handles both because
 * BoundTransferOperation extends TransferOperation.
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = BoundTransferOperation.class, target = EOperation.class)
public class BoundTransferOperationAnnotationRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public BoundTransferOperationAnnotationRules() {
    }
}
