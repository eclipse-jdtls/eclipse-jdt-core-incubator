/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.core.search.matching;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;

public class DOMASTNodeUtils {

	public static IJavaElement getEnclosingJavaElement(ASTNode node) {
		if (node == null) {
			return null;
		}
		if (node instanceof AbstractTypeDeclaration
			|| node instanceof MethodDeclaration
			|| node instanceof VariableDeclaration
			|| node instanceof CompilationUnit
			|| node instanceof AnnotationTypeMemberDeclaration) {
			return getDeclaringJavaElement(node);
		}
		return getEnclosingJavaElement(node.getParent());
	}

	public static IJavaElement getDeclaringJavaElement(ASTNode key) {
		if (key instanceof CompilationUnit unit) {
			return unit.getJavaElement();
		}
		return Optional.ofNullable(key).map(DOMASTNodeUtils::getBinding).map(IBinding::getJavaElement).orElse(null);
	}

	private static IBinding getBinding(ASTNode astNode) {
		if (astNode instanceof Name name) {
			return name.resolveBinding();
		}
		if (astNode instanceof VariableDeclaration variable) {
			return variable.resolveBinding();
		}
		if (astNode instanceof EnumConstantDeclaration enumConstantDeclaration) {
			return enumConstantDeclaration.resolveVariable();
		}
		if (astNode instanceof FieldAccess fieldAcces) {
			return fieldAcces.resolveFieldBinding();
		}
		if (astNode instanceof MethodInvocation method) {
			return method.resolveMethodBinding();
		}
		if (astNode instanceof Type type) {
			return type.resolveBinding();
		}
		if (astNode instanceof AbstractTypeDeclaration type) {
			return type.resolveBinding();
		}
		if (astNode instanceof MethodDeclaration method) {
			return method.resolveBinding();
		}
		if (astNode instanceof SuperFieldAccess superField) {
			return superField.resolveFieldBinding();
		}
		if (astNode instanceof SuperMethodInvocation superMethod) {
			return superMethod.resolveMethodBinding();
		}
		if (astNode instanceof SuperMethodReference superRef) {
			return superRef.resolveMethodBinding();
		}
		if (astNode instanceof MethodRef methodRef) {
			return methodRef.resolveBinding();
		}
		if (astNode instanceof MethodReference methodRef) {
			return methodRef.resolveMethodBinding();
		}
		// TODO more...
		return null;
	}

	public static boolean insideDocComment(org.eclipse.jdt.core.dom.ASTNode node) {
		return node.getRoot() instanceof org.eclipse.jdt.core.dom.CompilationUnit unit &&
			((List<Comment>)unit.getCommentList()).stream().anyMatch(comment -> comment.getStartPosition() <= node.getStartPosition() && comment.getStartPosition() + comment.getLength() >= node.getStartPosition() + node.getLength());
	}
}
