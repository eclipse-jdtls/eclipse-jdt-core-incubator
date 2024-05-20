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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringTemplateExpression;
import org.eclipse.jdt.internal.codeassist.DOMCompletionEngine.Bindings;

/**
 * This class define methods which helps to find most suitable bindings.
 */
final class DOMCompletionEngineRecoveredNodeScanner {
    // this class might need to consider the offset when scanning for suitable nodes since some times we get the full
    // statement where we might find multiple suitable node, so to narrow down the perfect we must check the offset.

    public DOMCompletionEngineRecoveredNodeScanner() {
    }

    // todo: we might need to improve not to traverse already traversed node paths.
    private static class SuitableNodeVisitor extends ASTVisitor {
        private ITypeBinding foundBinding = null;
        private Bindings scope;

        public SuitableNodeVisitor(Bindings scope) {
            this.scope = scope;
        }

        public boolean foundNode() {
            return this.foundBinding != null;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            this.foundBinding = node.resolveTypeBinding();
            if (this.foundBinding != null) {
                return false;
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(FieldAccess node) {
            this.foundBinding = node.resolveTypeBinding();
            if (this.foundBinding != null) {
                return false;
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(ExpressionStatement node) {
            this.foundBinding = node.getExpression().resolveTypeBinding();
            if (this.foundBinding != null) {
                return false;
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(StringTemplateExpression node) {
            // statement such as 'System.out.println("hello" + Thread.currentThread().)' are identified as a
            // StringFragment part of StringTemplateExpression, the invocation which we are interested might be in the
            // the processor of the expression
            this.foundBinding = node.getProcessor().resolveTypeBinding();
            if (this.foundBinding != null) {
                return false;
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(SimpleType node) {
            // this is part of a statement that is recovered due to syntax errors, so first check if the type is a
            // actual recoverable type, if not treat the type name as a variable name and search for such variable in
            // the context.
            var binding = node.resolveBinding();
            if (!binding.isRecovered()) {
                this.foundBinding = binding;
                return false;
            } else {
                var possibleVarName = binding.getName();
                var result = this.scope.stream().filter(IVariableBinding.class::isInstance)
                        .filter(b -> possibleVarName.equals(b.getName())).map(IVariableBinding.class::cast)
                        .map(v -> v.getType()).findFirst();
                if (result.isPresent()) {
                    this.foundBinding = result.get();
                    return false;
                }
            }
            return super.visit(node);
        }

        @Override
        public boolean visit(SimpleName node) {
            this.foundBinding = null;
            return false;
        }

        public ITypeBinding foundTypeBinding() {
            return this.foundBinding;
        }
    }

    /**
     * Find the closest suitable node for completions from the recovered nodes at the given node.
     */
    public ITypeBinding findClosestSuitableBinding(ASTNode node, Bindings scope) {
        ASTNode parent = node;
        var visitor = new SuitableNodeVisitor(scope);
        while (parent != null) {
            parent.accept(visitor);
            if (visitor.foundNode()) {
                break;
            }
            parent = parent.getParent();
        }
        return visitor.foundTypeBinding();
    }
}
