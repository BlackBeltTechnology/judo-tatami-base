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

import hu.blackbelt.judo.meta.rdbms.RdbmsConfiguration;
import hu.blackbelt.judo.meta.rdbms.RdbmsModel;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsHelper.*;
import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames.*;

/**
 * Package transformation rules from package.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>rootPackegeToModel - transforms root EPackage to RdbmsModel</li>
 *   <li>rootPackegeToConfiguration - transforms root EPackage to RdbmsConfiguration</li>
 * </ul>
 * </p>
 * <p>
 * ETL equivalents:
 * <pre>
 * rule rootPackegeToModel
 *     transform s : ASM!EPackage
 *     to t : RDBMS!RdbmsModel {
 *         guard : s.eSuperPackage.isUndefined()
 *         ...
 *     }
 *
 * rule rootPackegeToConfiguration
 *     transform s : ASM!EPackage
 *     to t : RDBMS!RdbmsConfiguration {
 *         guard : s.eSuperPackage.isUndefined()
 *         s.equivalent().configuration = t;  // Uses default equivalent (rootPackegeToModel)
 *         ...
 *     }
 * </pre>
 * </p>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EPackage.class, target = RdbmsModel.class)
public class PackageRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public PackageRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: EPackage is root package (no super package).
     * Matches ETL: guard : s.eSuperPackage.isUndefined()
     */
    public boolean isRootPackage(EObject source, TransformationContext ctx) {
        if (source instanceof EPackage) {
            return ((EPackage) source).getESuperPackage() == null;
        }
        return false;
    }

    // =========================================================================
    // TRANSFORMATION RULES
    // =========================================================================

    /**
     * rule rootPackegeToModel
     *     transform s : ASM!EPackage
     *     to t : RDBMS!RdbmsModel {
     *         guard : s.eSuperPackage.isUndefined()
     *         s.root().equivalent("rootPackegeToModel").rdbmsTables.add(t);  // When referenced from other rules
     *     }
     */
    @TransformRule(name = ROOT_PACKAGE_TO_MODEL, description = "Transform root EPackage to RdbmsModel")
    @Guard(method = "isRootPackage")
    @Transform(type = EPackage.class)
    @To(type = RdbmsModel.class)
    public TransformFunction<EPackage, RdbmsModel> rootPackegeToModel() {
        return (s, ctx) -> {
            RdbmsModel t = ctx.createTarget(RdbmsModel.class);

            // Get version from context attribute (set by orchestrator)
            String modelVersion = ctx.getAttribute("modelVersion");
            t.setVersion(modelVersion);
            t.setName(s.getName());

            return t;
        };
    }

    /**
     * rule rootPackegeToConfiguration
     *     transform s : ASM!EPackage
     *     to t : RDBMS!RdbmsConfiguration {
     *         guard : s.eSuperPackage.isUndefined()
     *         s.equivalent().configuration = t;  // Uses default equivalent (rootPackegeToModel)
     *     }
     * <p>
     * Key ETL pattern: s.equivalent() returns the RdbmsModel created by rootPackegeToModel.
     * In Zeta: ctx.equivalent(s, ROOT_PACKAGE_TO_MODEL) gets the named equivalent.
     * </p>
     */
    @TransformRule(name = ROOT_PACKAGE_TO_CONFIGURATION, description = "Transform root EPackage to RdbmsConfiguration")
    @Guard(method = "isRootPackage")
    @Transform(type = EPackage.class)
    @To(type = RdbmsConfiguration.class)
    public TransformFunction<EPackage, RdbmsConfiguration> rootPackegeToConfiguration() {
        return (s, ctx) -> {
            RdbmsConfiguration t = ctx.createTarget(RdbmsConfiguration.class);

            // Get dialect from context attribute (set by orchestrator)
            String dialect = ctx.getAttribute("dialect");
            t.setDialect(dialect);

            // Link to model using named equivalent lookup (matches ETL: s.equivalent().configuration = t)
            RdbmsModel model = ctx.equivalent(s, ROOT_PACKAGE_TO_MODEL);
            if (model != null) {
                model.setConfiguration(t);
            }

            return t;
        };
    }
}
