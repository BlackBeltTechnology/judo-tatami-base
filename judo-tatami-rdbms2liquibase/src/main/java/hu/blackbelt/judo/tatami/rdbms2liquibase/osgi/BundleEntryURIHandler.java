package hu.blackbelt.judo.tatami.rdbms2liquibase.osgi;

/*-
 * #%L
 * Judo :: Tatami :: Rdbms2Liquibase
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

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;
import org.osgi.framework.Bundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Read-only EMF {@link org.eclipse.emf.ecore.resource.URIHandler} serving a URI scheme from bundle entries.
 * Epsilon-free port of {@code hu.blackbelt.epsilon.runtime.osgi.BundleURIHandler} (epsilon-runtime-osgi 2.8.0),
 * same behaviour; see change: split-etl-artifacts.
 */
class BundleEntryURIHandler extends URIHandlerImpl {

    private final String urlSchema;
    private final String rootPath;
    private final Bundle bundle;

    BundleEntryURIHandler(String urlSchema, String rootPath, Bundle bundle) {
        this.urlSchema = urlSchema == null ? "" : urlSchema;
        this.rootPath = rootPath == null ? "" : rootPath;
        this.bundle = bundle;
    }

    @Override
    public boolean canHandle(URI uri) {
        if (urlSchema.isEmpty()) {
            return uri.scheme() == null || uri.scheme().isEmpty();
        }
        return urlSchema.equals(uri.scheme());
    }

    @Override
    public OutputStream createOutputStream(URI uri, Map<?, ?> options) throws IOException {
        throw new IOException("Bundle URIHandler is readonly");
    }

    @Override
    public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
        URL entry = bundle.getEntry(getFullPath(uri));
        if (entry == null) {
            throw new IOException("Bundle entry not found: " + getFullPath(uri));
        }
        InputStream inputStream = entry.openStream();
        Map<Object, Object> response = getResponse(options);
        if (response != null) {
            response.put(URIConverter.RESPONSE_TIME_STAMP_PROPERTY, bundle.getLastModified());
        }
        return inputStream;
    }

    @Override
    public void delete(URI uri, Map<?, ?> options) throws IOException {
        throw new IOException("Bundle URIHandler is readonly");
    }

    @Override
    public boolean exists(URI uri, Map<?, ?> options) {
        try {
            String fullPath = getFullPath(uri);
            int slash = fullPath.lastIndexOf('/');
            String directory = slash > 0 ? fullPath.substring(0, slash) : "";
            String name = slash >= 0 ? fullPath.substring(slash + 1) : fullPath;
            Enumeration<URL> entries = bundle.findEntries(directory, name, false);
            return entries != null && entries.hasMoreElements();
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public Map<String, ?> getAttributes(URI uri, Map<?, ?> options) {
        Map<String, Object> result = new HashMap<>();
        if (exists(uri, options)) {
            Set<String> requested = getRequestedAttributes(options);
            if (requested == null || requested.contains(URIConverter.ATTRIBUTE_TIME_STAMP)) {
                result.put(URIConverter.ATTRIBUTE_TIME_STAMP, bundle.getLastModified());
            }
            if (requested == null || requested.contains(URIConverter.ATTRIBUTE_LENGTH)) {
                result.put(URIConverter.ATTRIBUTE_LENGTH, -1);
            }
            if (requested == null || requested.contains(URIConverter.ATTRIBUTE_READ_ONLY)) {
                result.put(URIConverter.ATTRIBUTE_READ_ONLY, true);
            }
            if (requested == null || requested.contains(URIConverter.ATTRIBUTE_HIDDEN)) {
                result.put(URIConverter.ATTRIBUTE_HIDDEN, false);
            }
            if (requested == null || requested.contains(URIConverter.ATTRIBUTE_DIRECTORY)) {
                result.put(URIConverter.ATTRIBUTE_DIRECTORY, false);
            }
        }
        return result;
    }

    @Override
    public void setAttributes(URI uri, Map<String, ?> attributes, Map<?, ?> options) throws IOException {
        throw new IOException("Bundle URIHandler is readonly");
    }

    private String getFullPath(URI uri) {
        String path = "";
        if (uri.isFile()) {
            path = uri.path();
        } else if (uri.hasOpaquePart()) {
            path = uri.opaquePart();
        }
        if (!rootPath.isEmpty()) {
            path = rootPath + File.separator + path;
        }
        return path;
    }
}
