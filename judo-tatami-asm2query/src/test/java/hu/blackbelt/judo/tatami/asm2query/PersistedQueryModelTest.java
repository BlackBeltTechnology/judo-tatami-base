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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class PersistedQueryModelTest {

    @Test
    void locatorIsLazy() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final PersistedQueryModel locator = new PersistedQueryModel("demo", () -> {
            calls.incrementAndGet();
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        });

        assertThat(locator.getName(), equalTo("demo"));
        assertThat("supplier not invoked on creation", calls.get(), equalTo(0));

        try (InputStream in = locator.openStream()) {
            assertThat(in.read(), equalTo(1));
        }
        assertThat(calls.get(), equalTo(1));
    }
}
