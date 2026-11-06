package hu.blackbelt.judo.tatami.asm2query;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.derived.DataProperty;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.type.StringType;
import hu.blackbelt.judo.tatami.asm2expression.Asm2ExpressionWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;

import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.newAttributeBuilder;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.newEntityTypeBuilder;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newDataExpressionTypeBuilder;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newDataPropertyBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.newMappedTransferObjectTypeBuilder;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.newTransferAttributeBuilder;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newStringTypeBuilder;
import static hu.blackbelt.judo.tatami.psm2asm.etl.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.etl.Psm2Asm.executePsm2AsmTransformation;

/**
 * A minimal model with no measure model at all: one entity with a stored and a derived string attribute, and a mapped
 * transfer object type over it. Northwind cannot be used for "no measure model": its expressions use units.
 */
public final class MinimalFixture {

    public final AsmModel asmModel;
    public final ExpressionModel expressionModel;
    public final TransformationContext context;
    public final AsmUtils asmUtils;

    private MinimalFixture() throws Exception {
        final StringType stringType = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        final Attribute name = newAttributeBuilder().withName("name").withDataType(stringType).build();
        final DataProperty label = newDataPropertyBuilder().withName("label").withDataType(stringType)
                .withGetterExpression(newDataExpressionTypeBuilder().withExpression("self.name + '!'")).build();
        final EntityType person = newEntityTypeBuilder().withName("Person").withAttributes(name).build();
        person.getDataProperties().add(label);
        final Model model = newModelBuilder().withName("minimal")
                .withElements(stringType, person, newMappedTransferObjectTypeBuilder().withName("PersonDTO")
                        .withEntityType(person)
                        .withAttributes(newTransferAttributeBuilder().withName("name").withDataType(stringType).withBinding(name).build())
                        .withAttributes(newTransferAttributeBuilder().withName("label").withDataType(stringType).withBinding(label).build())
                        .build())
                .build();
        final PsmModel psmModel = PsmModel.buildPsmModel().build();
        psmModel.addContent(model);

        asmModel = AsmModel.buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter().psmModel(psmModel).asmModel(asmModel).parallel(false).useCache(true));

        context = new TransformationContext("minimal");
        context.put(asmModel);
        new Asm2ExpressionWork(context).execute();
        expressionModel = context.getByClass(ExpressionModel.class).orElseThrow(IllegalStateException::new);
        asmUtils = new AsmUtils(asmModel.getResourceSet());
    }

    public static MinimalFixture create() {
        try {
            return new MinimalFixture();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
