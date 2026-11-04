/*-
 * #%L
 * JUDO Tatami parent
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

/**
 * Structural model comparison utilities for EMF models.
 *
 * <h2>Overview</h2>
 * <p>
 * This package provides utilities for deep structural comparison of EMF models using
 * checksum-based equality. The main components are:
 * </p>
 * <ul>
 *   <li>{@link hu.blackbelt.judo.tatami.test.util.comparison.ModelChecksumCalculator} -
 *       Builds structural representation with checksums</li>
 *   <li>{@link hu.blackbelt.judo.tatami.test.util.comparison.StructuralModelComparator} -
 *       Compares two model structures</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * ModelChecksumCalculator calc = new ModelChecksumCalculator();
 * ModelNode expected = calc.calculate(etlResource);
 * ModelNode actual = calc.calculate(zetaResource);
 *
 * StructuralModelComparator comparator = new StructuralModelComparator();
 * ComparisonResult result = comparator.compare(expected, actual);
 *
 * if (!result.isMatch()) {
 *     System.out.println(comparator.formatForLLM(result));
 * }
 * }</pre>
 *
 * <h2>JSON Format</h2>
 * <p>
 * The {@link hu.blackbelt.judo.tatami.test.util.comparison.ModelChecksumCalculator#toJson(ModelNode)}
 * method exports model structures to JSON with the following schema:
 * </p>
 * <pre>{@code
 * {
 *   "type": "EClass",              // EClass type name
 *   "identifier": "MyClass",       // Element identifier (usually name)
 *   "path": "EPackage:pkg/EClass:MyClass",  // Containment path
 *   "checksum": "abc123...",       // SHA-256 structural checksum
 *   "attributes": {                // Optional: attribute values
 *     "abstract": "false",
 *     "name": "MyClass"
 *   },
 *   "references": {                // Optional: non-containment references
 *     "eSuperTypes": [
 *       {"path": "EPackage:pkg/EClass:Base", "checksum": "def456..."}
 *     ]
 *   },
 *   "containments": {              // Optional: contained children
 *     "eStructuralFeatures": [
 *       { ... child node ... }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <h2>Comparison Result JSON Format</h2>
 * <p>
 * The {@link hu.blackbelt.judo.tatami.test.util.comparison.StructuralModelComparator#saveJson}
 * method saves comparison results with the following schema:
 * </p>
 * <pre>{@code
 * {
 *   "schemaVersion": "1.0",
 *   "timestamp": "2024-01-18T23:45:00Z",
 *   "isMatch": false,
 *   "expectedChecksum": "abc123...",
 *   "actualChecksum": "def456...",
 *   "differenceCount": 3,
 *   "differences": [
 *     {
 *       "type": "MISSING",
 *       "path": "EPackage:pkg/EClass:Customer",
 *       "description": "Element missing: EClass:Customer",
 *       "expectedType": "EClass",
 *       "expectedId": "Customer"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>LLM-Friendly XML Format</h2>
 * <p>
 * The {@link hu.blackbelt.judo.tatami.test.util.comparison.StructuralModelComparator#formatForLLM}
 * method produces XML output optimized for LLM agent consumption:
 * </p>
 * <pre>{@code
 * <model-comparison>
 *   <summary>
 *     <status>MISMATCH</status>
 *     <total-differences>3</total-differences>
 *     <breakdown>
 *       <missing>1</missing>
 *       <attribute_mismatch>2</attribute_mismatch>
 *     </breakdown>
 *   </summary>
 *   <differences>
 *     <difference type="MISSING">
 *       <path>EPackage:pkg/EClass:Customer</path>
 *       <description>Element missing: EClass:Customer</description>
 *       <context>
 *         <missing-element type="EClass" id="Customer"/>
 *       </context>
 *       <suggestion>Add the missing element to the actual model</suggestion>
 *     </difference>
 *   </differences>
 * </model-comparison>
 * }</pre>
 *
 * @see hu.blackbelt.judo.tatami.test.util.comparison.ModelChecksumCalculator
 * @see hu.blackbelt.judo.tatami.test.util.comparison.StructuralModelComparator
 * @see hu.blackbelt.judo.tatami.test.util.comparison.ModelNode
 * @see hu.blackbelt.judo.tatami.test.util.comparison.ComparisonResult
 */
package hu.blackbelt.judo.tatami.test.util.comparison;
