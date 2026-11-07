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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Restore vs build wall time on the Northwind artifacts. A measurement, not a gate: the times are logged, nothing is
 * asserted about them.
 */
@Slf4j
public class QueryModelTimingTest {

    static final int ITERATIONS = 5;

    /**
     * TP-46: five restores (load + index restore) and five builds after one warm-up each; the wall times and the median
     * are logged. Only completion is asserted.
     */
    @Test
    void restoreVersusBuildTiming() throws Exception {
        final QueryModelPersistenceTest.Saved saved = new QueryModelPersistenceTest.Saved(NorthwindFixture.create());
        assertThat(saved.save().isWritten(), equalTo(true));

        // warm-up
        restore(saved);
        build(saved);

        final List<Long> restores = new ArrayList<>();
        final List<Long> builds = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            restores.add(time(() -> restore(saved)));
            builds.add(time(() -> build(saved)));
        }
        assertThat(restores, hasSize(ITERATIONS));
        assertThat(builds, hasSize(ITERATIONS));

        final long restoreMedian = median(restores);
        final long buildMedian = median(builds);
        assertThat(restoreMedian, greaterThan(0L));
        assertThat(buildMedian, greaterThan(0L));
        log.info("TP-46 restore (load + index restore) wall times [ms]: {}, median {} ms", millis(restores), TimeUnit.NANOSECONDS.toMillis(restoreMedian));
        log.info("TP-46 build (Asm2Query) wall times [ms]: {}, median {} ms", millis(builds), TimeUnit.NANOSECONDS.toMillis(buildMedian));
        log.info("TP-46 artifact size: {} bytes, build / restore median ratio: {}", saved.artifact.length, String.format("%.2f", (double) buildMedian / restoreMedian));
    }

    private static void restore(final QueryModelPersistenceTest.Saved saved) throws Exception {
        final QueryModelPersistence.LoadResult loaded = saved.load(saved.artifact);
        assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));
        assertThat(QueryIndex.restore(saved.fixture.asmUtils, loaded.getInfo()), notNullValue());
    }

    private static void build(final QueryModelPersistenceTest.Saved saved) {
        assertThat(Asm2Query.executeAsm2Query(saved.fixture.asmModel, saved.fixture.measureModel, saved.fixture.expressionModel), notNullValue());
    }

    private interface Action {
        void run() throws Exception;
    }

    private static long time(final Action action) throws Exception {
        final long start = System.nanoTime();
        action.run();
        return System.nanoTime() - start;
    }

    private static long median(final List<Long> values) {
        final List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static List<Long> millis(final List<Long> nanos) {
        final List<Long> millis = new ArrayList<>();
        nanos.forEach(n -> millis.add(TimeUnit.NANOSECONDS.toMillis(n)));
        return millis;
    }
}
