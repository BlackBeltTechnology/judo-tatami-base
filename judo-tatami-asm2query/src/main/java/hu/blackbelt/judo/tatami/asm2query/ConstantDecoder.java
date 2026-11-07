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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * Decoder of {@code EJavaObject} values ({@code Constant.value}) of a persisted query model.
 *
 * <p>EMF writes an {@code EJavaObject} as the hex encoding of the Java serialization of the value. Decoding such a
 * string from a file is an unsafe deserialization unless the classes are restricted. The decoder keeps EMF's
 * encoding and decodes through a per-stream {@link ObjectInputFilter} allow-list (no JVM-wide filter is set):</p>
 * <ul>
 *     <li>{@code java.lang.{Boolean, Integer, Long, Short, Byte, Double, Float, String, Number}};</li>
 *     <li>{@code java.math.{BigInteger, BigDecimal}};</li>
 *     <li>{@code java.time.*}, including {@code java.time.Ser};</li>
 *     <li>primitive arrays (at any dimension), which {@code BigInteger.magnitude} needs;</li>
 *     <li>limits of depth, array length, object references and stream bytes.</li>
 * </ul>
 * Anything else is rejected with an {@link InvalidClassException}, wrapped into an {@link UncheckedIOException}
 * because the decoding happens inside the EMF XML loader. The first failure is also remembered
 * ({@link #getFailure()}), so a load can fail even if the loader swallows the exception into the resource errors.
 */
final class ConstantDecoder {

    static final long MAX_DEPTH = 16;
    static final long MAX_ARRAY_LENGTH = 65_536;
    static final long MAX_REFERENCES = 4_096;
    static final long MAX_STREAM_BYTES = 262_144;

    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "java.lang.Boolean",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.String",
            "java.lang.Number",
            "java.math.BigInteger",
            "java.math.BigDecimal");

    private static final String ALLOWED_PACKAGE_PREFIX = "java.time.";

    /**
     * The filter applied to every decoded stream.
     */
    static final ObjectInputFilter FILTER = ConstantDecoder::check;

    private volatile Exception failure;

    private static ObjectInputFilter.Status check(final ObjectInputFilter.FilterInfo info) {
        if (info.depth() > MAX_DEPTH
                || info.arrayLength() > MAX_ARRAY_LENGTH
                || info.references() > MAX_REFERENCES
                || info.streamBytes() > MAX_STREAM_BYTES) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> serialClass = info.serialClass();
        if (serialClass == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        if (serialClass.isArray()) {
            Class<?> component = serialClass;
            while (component.isArray()) {
                component = component.getComponentType();
            }
            return component.isPrimitive() ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
        }
        final String className = serialClass.getName();
        if (ALLOWED_CLASSES.contains(className) || className.startsWith(ALLOWED_PACKAGE_PREFIX)) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    }

    /**
     * Decode a hex encoded serialized value through the allow-list.
     *
     * @param hex EMF encoding of the value
     * @return decoded value
     * @throws UncheckedIOException when the value is malformed or contains a class that is not allowed
     */
    Object decode(final String hex) {
        try {
            return decodeChecked(hex);
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            if (failure == null) {
                failure = e;
            }
            throw e instanceof IOException ? new UncheckedIOException("Constant value rejected: " + e.getMessage(), (IOException) e)
                    : new IllegalArgumentException("Constant value rejected: " + e, e);
        }
    }

    /**
     * @return the first failure of this decoder, {@code null} if every value was decoded
     */
    Exception getFailure() {
        return failure;
    }

    private static Object decodeChecked(final String hex) throws IOException, ClassNotFoundException {
        final byte[] bytes = fromHex(hex);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(FILTER);
            final Object value = in.readObject();
            if (in.available() > 0) {
                throw new InvalidClassException("Trailing bytes after the constant value");
            }
            return value;
        }
    }

    private static byte[] fromHex(final String hex) throws InvalidClassException {
        if (hex.length() % 2 != 0) {
            throw new InvalidClassException("Odd length hex string");
        }
        final byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            final int high = Character.digit(hex.charAt(2 * i), 16);
            final int low = Character.digit(hex.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) {
                throw new InvalidClassException("Not a hex string");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
