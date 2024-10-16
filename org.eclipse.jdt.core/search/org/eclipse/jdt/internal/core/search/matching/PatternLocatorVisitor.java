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

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IntersectionType;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Visits an AST to feel the possible match with nodes
 */
class PatternLocatorVisitor extends ASTVisitor {

	private final PatternLocator patternLocator;
	private final MatchingNodeSet nodeSet;

	public PatternLocatorVisitor(PatternLocator patternLocator, MatchingNodeSet nodeSet) {
		super(true);
		this.patternLocator = patternLocator;
		this.nodeSet = nodeSet;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(MethodInvocation node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveMethodBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(ExpressionMethodReference node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveMethodBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(SuperMethodReference node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveMethodBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(SuperMethodInvocation node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveMethodBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}

	private boolean visitAbstractTypeDeclaration(AbstractTypeDeclaration node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(EnumDeclaration node) {
		return visitAbstractTypeDeclaration(node);
	}
	@Override
	public boolean visit(TypeDeclaration node) {
		return visitAbstractTypeDeclaration(node);
	}
	@Override
	public boolean visit(RecordDeclaration node) {
		return visitAbstractTypeDeclaration(node);
	}
	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}

	private boolean visitType(Type node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(SimpleType type) {
		visitType(type);
		return false; // No need to visit single name child
	}
	@Override
	public boolean visit(QualifiedType type) {
		return visitType(type);
	}
	@Override
	public boolean visit(NameQualifiedType type) {
		return visitType(type);
	}
	@Override
	public boolean visit(ParameterizedType node) {
		return visitType(node);
	}
	@Override
	public boolean visit(IntersectionType node) {
		return visitType(node);
	}
	@Override
	public boolean visit(UnionType node) {
		return visitType(node);
	}
	@Override
	public boolean visit(ClassInstanceCreation node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveConstructorBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(CreationReference node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveMethodBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(SuperConstructorInvocation node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveConstructorBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(SimpleName node) {
		if (node.getLocationInParent() == QualifiedName.NAME_PROPERTY ||
			node.getLocationInParent() == VariableDeclarationFragment.NAME_PROPERTY ||
			node.getLocationInParent() == SingleVariableDeclaration.NAME_PROPERTY ||
			node.getLocationInParent() == TypeDeclaration.NAME_PROPERTY ||
			node.getLocationInParent() == EnumDeclaration.NAME_PROPERTY ||
			node.getLocationInParent() == MethodDeclaration.NAME_PROPERTY) {
			return false; // skip as parent was most likely already matched
		}
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(VariableDeclarationFragment node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(SingleVariableDeclaration node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(EnumConstantDeclaration node) {
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = Math.max(this.patternLocator.resolveLevel(node.resolveVariable()), this.patternLocator.resolveLevel(node.resolveConstructorBinding()));
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
	@Override
	public boolean visit(QualifiedName node) {
		if (node.getLocationInParent() == SimpleType.NAME_PROPERTY) {
			return false; // type was already checked
		}
		int level = this.patternLocator.match(node, this.nodeSet);
		if ((level & PatternLocator.MATCH_LEVEL_MASK) == PatternLocator.POSSIBLE_MATCH && (this.nodeSet.mustResolve || this.patternLocator.mustResolve)) {
			level = this.patternLocator.resolveLevel(node.resolveBinding());
		}
		this.nodeSet.addMatch(node, level);
		return true;
	}
}
