/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - Initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.dom;

/**
 * Can provide additional bindings given the key while <code>ASTParser.createASTs</code> is being run.
 */
interface AdditionalBindingCreator {

	/**
	 * Returns the binding for the given key if <code>ASTParser.createASTs</code> is in progress, or throws a <code>RuntimeException</code> if <code>ASTParser.createASTs</code> is not in progress.
	 *
	 * @param key the key of the binding to resolve
	 * @throws RuntimeException if <code>ASTParser.createASTs</code> is not in progress
	 * @return the binding for the given key
	 */
	IBinding createBinding(String key);

}
