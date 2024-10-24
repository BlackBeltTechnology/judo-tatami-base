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

import org.apache.commons.lang3.StringUtils;

/**
 * Abbreviate a string to the requested length, trying to keep it human readable as possible and minimizing the chance
 * the possible collosion with other similar strings. It removing vowels and consents by its english frequency.
 * http://pi.math.cornell.edu/~mec/2003-2004/cryptography/subs/frequencies.html
 */
public class AbbreviateUtils {

    private static char[] vowelsByFrequency = new char[] {'e', 'a', 'o', 'i', 'u'};
    private static char[] consonantByFrequency = new char[] {'t', 'n', 's', 'r', 'h', 'd', 'l', 'c', 'm', 'f', 'y', 'w', 'g', 'p', 'b', 'v', 'k', 'x', 'q', 'j', 'z'};

    public static String abbreviate(String text, Integer maxLength, String separator) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("Too short maxLength");
        }
        String str = StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(text.replaceAll("_", " ")), " ");
        while (str.contains("  ")) {
            str = str.replaceAll("  ", " ");
        }
        String[] arr = str.toLowerCase().split(" ");
        for (int i = 1; i < arr.length; i++) {
            arr[i] = separator + arr[i];
        }

        int shrinkCharsLeft = StringUtils.join(arr).length() - maxLength;
        int vowelIndex = -1;
        int consentIndex = -1;
        boolean changeCharacter = false;
        char actRemovableChar = vowelsByFrequency[0];
        int checkedLastPosIndex = 1;
        int partIndex = arr.length - 1;
        boolean containActRemovableChar = false;

        while (shrinkCharsLeft > 0) {

            /*
            StringBuilder ss = new StringBuilder();
            for (int i=0; i<arr.length; i++) {
                ss.append(arr[i]);
            }
            System.out.println(ss.toString() + " - Shrink chars left: " + shrinkCharsLeft +
                    " actRemovableChar: " + actRemovableChar +
                    " partIndex: " + partIndex +
                    " changeCharacter: " + changeCharacter +
                    " containRemovableCharacter: " + containActRemovableChar
            ); */

            if (changeCharacter) {
                changeCharacter = false;
                partIndex = arr.length - 1;
                if (vowelIndex < vowelsByFrequency.length - 1) {
                    vowelIndex++;
                    actRemovableChar = vowelsByFrequency[vowelIndex];
                } else if (consentIndex < consonantByFrequency.length - 1) {
                    consentIndex++;
                    actRemovableChar = consonantByFrequency[consentIndex];
                } else {
                    if (!containActRemovableChar && separator.charAt(0) == actRemovableChar) {
                        vowelIndex = -1;
                        consentIndex = -1;
                        actRemovableChar = vowelsByFrequency[0];
                    } else {
                        actRemovableChar = separator.charAt(0);
                        checkedLastPosIndex = -1;
                    }
                }
            }

            if (partIndex < 0) {
                partIndex = arr.length - 1;
                if (!containActRemovableChar) {
                    changeCharacter = true;
                }
            }

            containActRemovableChar = false;
            if (shrinkCharsLeft > 0 && arr[partIndex].length() > checkedLastPosIndex + 1 && arr[partIndex].lastIndexOf(actRemovableChar) > checkedLastPosIndex) {
                shrinkCharsLeft--;
                String cns = arr[partIndex].substring(checkedLastPosIndex + 1);
                int pos =  cns.lastIndexOf(actRemovableChar);
                cns = cns.substring(0, pos) + cns.substring(pos + 1);
                arr[partIndex] = arr[partIndex].substring(0, checkedLastPosIndex + 1) + cns;
                if (arr[partIndex].lastIndexOf(actRemovableChar) > checkedLastPosIndex) {
                    containActRemovableChar = true;
                }
            }
            partIndex--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<arr.length; i++) {
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
