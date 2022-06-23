package hu.blackbelt.judo.tatami.asm2rdbms;

import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.tatami.asm2rdbms.AbbreviateUtils.abbreviate;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class AbbreviateUtilsTest {

    @Test
    public void testJng1303() {
        assertThat(abbreviate("entty_wth_ndrctnl_ssctns_crdnlty", 6), equalTo("nwnssc"));
    }


    @Test
    public void testWithStrings() {
        assertThat(abbreviate("man_of_breeding_and_deilcacy_could_not_but_feel_some", 32), equalTo("ma_of_bdg_ad_dlccy_cld_n_b_fl_sm"));
        assertThat(abbreviate("man_of_breeding_and_deilcacy_could_not_but_feel_some", 20), equalTo("ma_o_b_a_dc_c_n_b_fs"));
        assertThat(abbreviate("man_of_breeding_and_deilcacy_could_not_but_feel_some", 16), equalTo("ma_o_b_a_dccnbfs"));
    }
}
