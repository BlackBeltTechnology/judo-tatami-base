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

import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Locator of a persisted query model artifact: the logical model name and exactly one supplier of the artifact stream.
 *
 * <p>The supplier is never invoked until the load-or-build decision needs the stream: creating the locator does not
 * read anything. Containers (Guice, Spring, OSGi) bind this one type.</p>
 */
@Getter
public final class PersistedQueryModel {

    /**
     * Opens the artifact stream. The caller closes it.
     */
    @FunctionalInterface
    public interface StreamSupplier {
        InputStream open() throws IOException;
    }

    /**
     * Logical model name, the artifact is addressed as {@code <name>-query.model}.
     */
    @NonNull
    private final String name;

    @Getter(lombok.AccessLevel.NONE)
    private final StreamSupplier streamSupplier;

    public PersistedQueryModel(@NonNull final String name, @NonNull final StreamSupplier streamSupplier) {
        this.name = name;
        this.streamSupplier = streamSupplier;
    }

    /**
     * Open the artifact stream. This is the only place the supplier is invoked.
     *
     * @return artifact stream, to be closed by the caller
     * @throws IOException if the stream cannot be opened
     */
    public InputStream openStream() throws IOException {
        return streamSupplier.open();
    }
}
