/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Gayan Perera - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.codeassist;

import java.util.List;

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;

final class DOMCompletionEngineVisibleBindingsCollector {

    /**
     * Collect visible bindings from the given method declaration. Currently it collects
     * 
     * <pre>
     * - parameter variable bindings
     * </pre>
     * 
     * @return The parameter variable bindings or empty list
     */
    public List<IVariableBinding> collectVisibleBindingsFrom(MethodDeclaration declaration) {
        return ((List<VariableDeclaration>) declaration.parameters()).stream().map(VariableDeclaration::resolveBinding)
                .toList();
    }

    /**
     * Collect visible bindings from the given lambda expression. Currently it collects
     * 
     * <pre>
     * - lambda parameter variable bindings
     * </pre>
     * 
     * @return The parameter variable bindings or empty list
     */
    public List<IVariableBinding> collectVisibleBindingsFrom(LambdaExpression expression) {
        return ((List<VariableDeclaration>) expression.parameters()).stream().map(VariableDeclaration::resolveBinding)
                .toList();
    }
}
