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
package org.eclipse.jdt.internal.core.search.indexing;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

class DOMToIndexVisitor extends ASTVisitor {

	private SourceIndexer sourceIndexer;

	private char[] packageName;
	private List<AbstractTypeDeclaration> enclosingTypes = new LinkedList<>();

	public DOMToIndexVisitor(SourceIndexer sourceIndexer) {
		this.sourceIndexer = sourceIndexer;
	}

	@Override
	public boolean visit(PackageDeclaration packageDeclaration) {
		this.packageName = packageDeclaration.getName().toString().toCharArray();
		return false;
	}

	@Override
	public boolean visit(TypeDeclaration type) {
		char[][] enclosing = this.enclosingTypes.stream().map(AbstractTypeDeclaration::getName).map(SimpleName::getIdentifier).map(String::toCharArray).toArray(char[][]::new);
		if (type.isInterface()) {
			this.sourceIndexer.addInterfaceDeclaration(type.getModifiers(), this.packageName, type.getName().toString().toCharArray(), enclosing, ((List<Type>)type.superInterfaceTypes()).stream().map(superInterface -> superInterface.toString().toCharArray()).toArray(char[][]::new), null, isSecondary(type));
		} else {
			this.sourceIndexer.addClassDeclaration(type.getModifiers(), this.packageName, type.getName().toString().toCharArray(), enclosing, type.getSuperclassType() == null ? null : type.getSuperclassType().toString().toCharArray(),
				((List<Type>)type.superInterfaceTypes()).stream().map(superInterface -> superInterface.toString().toCharArray()).toArray(char[][]::new), null, isSecondary(type));
			if (type.bodyDeclarations().stream().noneMatch(member -> member instanceof MethodDeclaration method && method.isConstructor())) {
				this.sourceIndexer.addDefaultConstructorDeclaration(type.getName().toString().toCharArray(),
						this.packageName, type.getModifiers(), 0);
			}
		}
		this.enclosingTypes.add(type);
		// TODO other types
		return true;
	}
	@Override
	public void endVisit(TypeDeclaration type) {
		this.enclosingTypes.remove(type);
	}

	@Override
	public boolean visit(EnumDeclaration type) {
		char[][] enclosing = this.enclosingTypes.stream().map(AbstractTypeDeclaration::getName).map(SimpleName::getIdentifier).map(String::toCharArray).toArray(char[][]::new);
		this.sourceIndexer.addEnumDeclaration(type.getModifiers(), this.packageName, type.getName().getIdentifier().toCharArray(), enclosing, Enum.class.getName().toCharArray(), ((List<Type>)type.superInterfaceTypes()).stream().map(superInterface -> superInterface.toString().toCharArray()).toArray(char[][]::new), isSecondary(type));
		this.enclosingTypes.add(type);
		return true;
	}
	@Override
	public void endVisit(EnumDeclaration type) {
		this.enclosingTypes.remove(type);
	}
	@Override
	public boolean visit(EnumConstantDeclaration enumConstant) {
		this.sourceIndexer.addFieldDeclaration(this.enclosingTypes.get(this.enclosingTypes.size() - 1).getName().toString().toCharArray(), enumConstant.getName().getIdentifier().toCharArray());
		return true;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration type) {
		char[][] enclosing = this.enclosingTypes.stream().map(AbstractTypeDeclaration::getName).map(SimpleName::getIdentifier).map(String::toCharArray).toArray(char[][]::new);
		this.sourceIndexer.addAnnotationTypeDeclaration(type.getModifiers(), this.packageName, type.getName().getIdentifier().toCharArray(), enclosing, isSecondary(type));
		this.enclosingTypes.add(type);
		return true;
	}
	@Override
	public void endVisit(AnnotationTypeDeclaration type) {
		this.enclosingTypes.remove(type);
	}

	private boolean isSecondary(AbstractTypeDeclaration type) {
		return type.getParent() instanceof CompilationUnit unit &&
			unit.types().size() > 1 &&
			unit.types().indexOf(type) > 0;
			// TODO: check name?
	}

	@Override
	public boolean visit(RecordDeclaration recordDecl) {
		// copied processing of TypeDeclaration
		this.sourceIndexer.addClassDeclaration(recordDecl.getModifiers(), this.packageName, recordDecl.getName().toString().toCharArray(), null, null,
				((List<Type>)recordDecl.superInterfaceTypes()).stream().map(type -> type.toString().toCharArray()).toArray(char[][]::new), null, false);
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration method) {
		char[] methodName = method.getName().toString().toCharArray();
		char[][] parameterTypes = ((List<VariableDeclaration>)method.parameters()).stream()
			.filter(SingleVariableDeclaration.class::isInstance)
			.map(SingleVariableDeclaration.class::cast)
			.map(SingleVariableDeclaration::getType)
			.map(Type::toString)
			.map(String::toCharArray)
			.toArray(char[][]::new);
		char[] returnType = null;
		if (method.getReturnType2() instanceof SimpleType simple) {
			returnType = simple.getName().toString().toCharArray();
		} else if (method.getReturnType2() instanceof PrimitiveType primitive) {
			returnType = primitive.getPrimitiveTypeCode().toString().toCharArray();
		} else if (method.getReturnType2() == null) {
			// do nothing
		} else {
			returnType = method.getReturnType2().toString().toCharArray();
		}
		char[][] exceptionTypes = ((List<Type>)method.thrownExceptionTypes()).stream()
			.map(Type::toString)
			.map(String::toCharArray)
			.toArray(char[][]::new);
		char[][] parameterNames = ((List<VariableDeclaration>)method.parameters()).stream()
				.map(VariableDeclaration::getName)
				.map(SimpleName::toString)
				.map(String::toCharArray)
				.toArray(char[][]::new);
		if (!method.isConstructor()) {
			this.sourceIndexer.addMethodDeclaration(methodName, parameterTypes, returnType, exceptionTypes);
			this.sourceIndexer.addMethodDeclaration(this.enclosingTypes.get(this.enclosingTypes.size() - 1).getName().toString().toCharArray(),
				null /* TODO: fully qualified name of enclosing type? */,
				methodName,
				parameterTypes.length,
				null,
				parameterTypes,
				parameterNames,
				returnType,
				method.getModifiers(),
				this.packageName,
				0 /* TODO What to put here? */,
				exceptionTypes,
				0 /* TODO ExtraFlags.IsLocalType ? */);
		} else {
			this.sourceIndexer.addConstructorDeclaration(method.getName().toString().toCharArray(),
					method.parameters().size(),
					null, parameterTypes, parameterNames, method.getModifiers(), this.packageName, 0, exceptionTypes, 0);
		}
		return true;
	}

	@Override
	public boolean visit(FieldDeclaration field) {
		char[] typeName = field.getType().toString().toCharArray();
		for (VariableDeclarationFragment fragment: (List<VariableDeclarationFragment>)field.fragments()) {
			this.sourceIndexer.addFieldDeclaration(typeName, fragment.getName().toString().toCharArray());
		}
		return true;
	}

	@Override
	public boolean visit(MethodInvocation methodInvocation) {
		this.sourceIndexer.addMethodReference(methodInvocation.getName().toString().toCharArray(), methodInvocation.arguments().size());
		return true;
	}
	@Override
	public boolean visit(ExpressionMethodReference methodInvocation) {
		this.sourceIndexer.addMethodReference(methodInvocation.getName().toString().toCharArray(), 0);
		return true;
	}
	@Override
	public boolean visit(TypeMethodReference methodInvocation) {
		this.sourceIndexer.addMethodReference(methodInvocation.getName().toString().toCharArray(), 0);
		return true;
	}
	@Override
	public boolean visit(SuperMethodInvocation methodInvocation) {
		this.sourceIndexer.addMethodReference(methodInvocation.getName().toString().toCharArray(), 0);
		return true;
	}
	@Override
	public boolean visit(SuperMethodReference methodInvocation) {
		this.sourceIndexer.addMethodReference(methodInvocation.getName().toString().toCharArray(), 0);
		return true;
	}
	@Override
	public boolean visit(ClassInstanceCreation methodInvocation) {
		this.sourceIndexer.addConstructorReference(methodInvocation.getType().toString().toCharArray(), methodInvocation.arguments().size());
		return true;
	}
	@Override
	public boolean visit(CreationReference methodInvocation) {
		this.sourceIndexer.addConstructorReference(methodInvocation.getType().toString().toCharArray(), 0);
		return true;
	}

	@Override
	public boolean visit(SimpleType type) {
		this.sourceIndexer.addTypeReference(type.getName().toString().toCharArray());
		return true;
	}
	@Override
	public boolean visit(SimpleName name) {
		this.sourceIndexer.addNameReference(name.getIdentifier().toString().toCharArray());
		return true;
	}
	// TODO (cf SourceIndexer and SourceIndexerRequestor)
	// * Module: addModuleDeclaration/addModuleReference/addModuleExportedPackages
	// * Lambda: addIndexEntry/addClassDeclaration
	// * FieldReference
	// * Deprecated
}
