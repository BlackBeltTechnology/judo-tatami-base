package hu.blackbelt.judo.tatami.asm2rdbms.etl;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Rdbms :: ETL
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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsEtlEngine;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsTransformationTrace;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsWork;
import org.slf4j.Logger;

/**
 * {@link Asm2RdbmsEtlEngine} backed by the Epsilon ETL scripts and mapping workbooks of this artifact.
 * Discovered via META-INF/services (plain Java) or registered by {@code osgi.Asm2RdbmsEtlEngineComponent} (Karaf).
 */
public class Asm2RdbmsEtlEngineImpl implements Asm2RdbmsEtlEngine {

    @Override
    public Asm2RdbmsTransformationTrace execute(AsmModel asmModel, RdbmsModel rdbmsModel, String dialect, Logger log,
                                                Asm2RdbmsWork.Asm2RdbmsWorkParameter p) throws Exception {
        return Asm2Rdbms.executeAsm2RdbmsTransformation(Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter()
                .asmModel(asmModel)
                .rdbmsModel(rdbmsModel)
                .log(log)
                .dialect(dialect)
                .parallel(p.getParallel())
                .useCache(p.getUseCache())
                .createTrace(p.getCreateTrace())
                .createSimpleName(p.getCreateSimpleName())
                .nameSize(p.getNameSize())
                .shortNameSize(p.getShortNameSize())
                .tableNameMaxSize(p.getTableNameMaxSize())
                .columnNameMaxSize(p.getColumnMaxNameSize())
                .tablePrefix(p.getTablePrefix())
                .columnPrefix(p.getColumnPrefix())
                .foreignKeyPrefix(p.getForeignKeyPrefix())
                .inverseForeignKeyPrefix(p.getInverseForeignKeyPrefix())
                .junctionTablePrefix(p.getJunctionTablePrefix()));
    }
}
