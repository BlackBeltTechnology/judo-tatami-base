package hu.blackbelt.judo.tatami.asm2sdk;

import com.google.common.base.CaseFormat;

public class TransformationUtils {

    public static String camelCaseToSnakeCase(String text) {
        return text != null ? CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, text) : null;
    }
}
