package hu.blackbelt.judo.tatami.asm2rdbms;

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

import static hu.blackbelt.judo.tatami.asm2rdbms.AbbreviateUtils.abbreviate;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class AbbreviateUtilsTest {

    @Test
    public void testJng1303() {
        assertThat(abbreviate("entty_wth_ndrctnl_ssctns_crdnlty", 6, "_"), equalTo("nwnssc"));
    }


    @Test
    public void testWithStrings() {
        assertThat(abbreviate("ManOfBreedingAndDeilcacyCouldNotButFeelSome", 32, "_"), equalTo("ma_of_bdg_ad_dlccy_cld_n_b_fl_sm"));
        assertThat(abbreviate("TRANSPORTATIONORDERREPORT_TRANSPORTATIONORDERREPORTCONTRACTORS_ID", 59, "_"), equalTo("transporttionordrrport_transportationordrrportcontrctors_id"));
        assertThat(abbreviate("Man Of Breeding And Deilcacy Could Not But Feel Some", 32, "_"), equalTo("ma_of_bdg_ad_dlccy_cld_n_b_fl_sm"));
        assertThat(abbreviate("man of breeding and deilcacy could not but feel some", 32, "_"), equalTo("ma_of_bdg_ad_dlccy_cld_n_b_fl_sm"));
        assertThat(abbreviate("man_of_breeding_and_deilcacy_could_not_but_feel_some", 32, "_"), equalTo("ma_of_bdg_ad_dlccy_cld_n_b_fl_sm"));
        assertThat(abbreviate("MAN_OF_BREEDING_AND_DEILCACY_COULD_NOT_BUT_FEEL_SOME", 32, "_"), equalTo("ma_of_bdg_ad_dlccy_cld_n_b_fl_sm"));
        assertThat(abbreviate("man_of_breeding_and_deilcacy_could_not_but_feel_some", 20, "_"), equalTo("ma_o_b_a_dc_c_n_b_fs"));
        assertThat(abbreviate("man_of_breeding_and_deilcacy_could_not_but_feel_some", 16, "_"), equalTo("ma_o_b_a_dccnbfs"));
    }
}
