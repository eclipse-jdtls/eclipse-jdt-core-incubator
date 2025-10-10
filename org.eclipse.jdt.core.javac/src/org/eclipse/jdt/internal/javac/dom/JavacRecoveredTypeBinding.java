/*******************************************************************************
 * Copyright (c) 2025, Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.javac.dom;

import java.util.Objects;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.GenericRecoveredTypeBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IntersectionType;
import org.eclipse.jdt.core.dom.JavacBindingResolver;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;

import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.PackageType;

public class JavacRecoveredTypeBinding extends JavacTypeBinding {

	private final ASTNode domNode;

	public JavacRecoveredTypeBinding(com.sun.tools.javac.code.Type type, org.eclipse.jdt.core.dom.ASTNode domName, JavacBindingResolver resolver) {
		super(type, type != null ? type.tsym : null, null, null, false, resolver);
		this.domNode = domName;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ this.domNode.toString().hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		return obj instanceof JavacRecoveredTypeBinding recovered &&
			Objects.equals(recovered.domNode.toString(), this.domNode.toString()) &&
			Objects.equals(recovered.domNode.getAST(), this.domNode.getAST()) &&
			super.equals(obj);
	}

	@Override
	public JavacTypeBinding getComponentType() {
		if (this.type instanceof ArrayType javacArrayType && javacArrayType.isErroneous()) {
			if (getDimensions() == 1 && this.domNode instanceof org.eclipse.jdt.core.dom.ArrayType domArrayType) {
				return this.resolver.bindings.getRecoveredTypeBinding(javacArrayType.elemtype, domArrayType.getElementType());
			}
			if (this.domNode instanceof org.eclipse.jdt.core.dom.Type t) {
				return this.resolver.bindings.getRecoveredTypeBinding(javacArrayType.elemtype, t);
			} else if (this.domNode instanceof org.eclipse.jdt.core.dom.Name n) {
				return this.resolver.bindings.getRecoveredTypeBinding(javacArrayType.elemtype, n);
			}
		}
		return super.getComponentType();
	}

	@Override
	public JavacTypeBinding getElementType() {
		if (this.type != null) {
			return (JavacTypeBinding)super.getElementType();
		}
		if (this.domNode instanceof org.eclipse.jdt.core.dom.ArrayType domArrayType) {
			org.eclipse.jdt.core.dom.Type cursor = domArrayType.getElementType();
			while (cursor instanceof org.eclipse.jdt.core.dom.ArrayType at) {
				cursor = at.getElementType();
			}
			return this.resolver.bindings.getRecoveredTypeBinding(this.type, cursor);
		}
		return null;
	}

	@Override
	public IPackageBinding getPackage() {
		if (isArray()) {
			return null;
		}
		if (this.type == null || this.type.isErroneous() || this.type instanceof PackageType) {
			if (domName() instanceof QualifiedName qname) {
				return this.resolver.bindings.getPackageBinding(qname.getQualifier());
			} else {
				ASTNode current = this.domNode;
				while (current != null) {
					if (current instanceof AbstractTypeDeclaration typeDecl) {
						ITypeBinding declaringTypeBinding = typeDecl.resolveBinding();
						if (declaringTypeBinding != null) {
							return declaringTypeBinding.getPackage();
						}
					}
					current = current.getParent();
				}
			}
			return this.resolver.bindings.getPackageBinding("");
		}
		return super.getPackage();
	}

	private org.eclipse.jdt.core.dom.Name domName() {
		ASTNode toConsider = this.domNode;
		if (toConsider instanceof ParameterizedType parameterizedType) {
			toConsider = parameterizedType.getType();
		}
		if (toConsider instanceof SimpleType type) {
			return type.getName();
		}
		if (toConsider instanceof org.eclipse.jdt.core.dom.Name name) {
			return name;
		}
		return null;
	}

	@Override
	public String getQualifiedName() {
		if (isArray()) {
			return getComponentType().getQualifiedName() + "[]";
		}
		if (this.type == null || this.type.isErroneous()) {
			StringBuilder res = new StringBuilder(getPackage().getName());
			if (!res.isEmpty()) {
				res.append('.');
			}
			var name = domName();
			if (name == null) {
				return "";
			}
			String simpleName = name.isSimpleName() ?
						((SimpleName)name).getIdentifier() :
						((QualifiedName)name).getName().getIdentifier();
			res.append(simpleName);
			return res.toString();
		}
		return super.getQualifiedName();
	}

	@Override
	public boolean isRecovered() {
		return true;
	}

	@Override
	public IJavaElement getJavaElement() {
		IPackageBinding pack = getPackage();
		if (pack != null && pack.getJavaElement() instanceof IPackageFragment pkgFragment) {
			return pkgFragment.getCompilationUnit(getName() + ".java").getType(getName());
		}
		return null;
	}

	@Override
	public boolean isParameterizedType() {
		return this.domNode instanceof ParameterizedType;
	}
	@Override
	public boolean isAnonymous() {
		if (this.typeSymbol != null) {
			return super.isAnonymous();
		}
		return this.domNode.getParent() instanceof AnonymousClassDeclaration;
	}
	@Override
	public boolean isEnum() {
		if (typeSymbol != null) {
			return super.isEnum();
		}
		return this.domNode.getParent() instanceof EnumDeclaration;
	}
	@Override
	public boolean isMember() {
		if (this.typeSymbol != null) {
			return super.isMember();
		}
		// guess?
		return false;
	}
	@Override
	public boolean isIntersectionType() {
		if (this.typeSymbol != null) {
			return super.isAnonymous();
		}
		return this.domNode instanceof IntersectionType;
	}
	@Override
	public boolean isInterface() {
		if (typeSymbol != null) {
			return super.isInterface();
		}
		// guess?
		return false;
	}
	@Override
	public boolean isLocal() {
		if (typeSymbol != null) {
			super.isLocal();
		}
		// guess
		return false;
	}
	@Override
	public ITypeBinding getTypeDeclaration() {
		if (isParameterizedType() && this.domNode instanceof org.eclipse.jdt.core.dom.Type domType) {
			return new GenericRecoveredTypeBinding(this.resolver, domType, this);
		}
		return super.getTypeDeclaration();
	}

	@Override
	public IVariableBinding[] getDeclaredFields() {
		return new IVariableBinding[0];
	}
	@Override
	public IMethodBinding[] getDeclaredMethods() {
		return new IMethodBinding[0];
	}
	@Override
	public ITypeBinding getDeclaringClass() {
		if (this.typeSymbol != null) {
			return super.getDeclaringClass();
		}
		return null;
	}
	@Override
	public String getName(boolean checkParameterized, boolean sourceName) {
		if (this.typeSymbol != null) {
			return super.getName(checkParameterized, sourceName);
		}
		return this.domNode.toString();
	}
	@Override
	public String getQualifiedName(boolean includeParams) {
		if (this.typeSymbol != null) {
			return super.getQualifiedName(includeParams);
		}
		ASTNode cursor = this.domNode;
		while (cursor != null && !(cursor instanceof CompilationUnit)) {
			cursor = cursor.getParent();
		}
		StringBuilder qualifiedNameBuilder = new StringBuilder();
		if (cursor instanceof CompilationUnit cu) {
			if (cu.getPackage() != null) {
				qualifiedNameBuilder.append(cu.getPackage().getName().toString());
				qualifiedNameBuilder.append(".");
			}
		}
		qualifiedNameBuilder.append(this.domNode.toString());
		return qualifiedNameBuilder.toString();
	}
	@Override
	public String getKey() {
		if (this.type != null) {
			return super.getKey();
		}
		return "L" + getQualifiedName() + ";";
	}
	@Override
	public String getKey(boolean includeTypeParameters, boolean useSlashes) {
		if (this.type != null) {
			return super.getKey(includeTypeParameters, useSlashes);
		}
		if (useSlashes) {
			return "L" + getQualifiedName().replace('.', '/') + ";";
		} else {
			return "L" + getQualifiedName() + ";";
		}
	}
	@Override
	public String getGenericTypeSignature(boolean useSlashes) {
		if (this.type == null) {
			return getKey(false, useSlashes);
		}
		return super.getGenericTypeSignature(useSlashes);
	}

	@Override
	public ITypeBinding[] getInterfaces() {
		if (this.type != null) {
			return super.getInterfaces();
		}
		return new ITypeBinding[0];
	}

	@Override
	public ITypeBinding getSuperclass() {
		if (this.type != null) {
			return super.getSuperclass();
		}
		return null;
	}
}
