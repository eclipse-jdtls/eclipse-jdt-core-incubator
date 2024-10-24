/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.javac.dom;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.JavacBindingResolver;

import com.sun.tools.javac.code.Attribute.Compound;

public abstract class JavacAnnotationBinding implements IAnnotationBinding {

	private final JavacBindingResolver resolver;
	private final Compound annotation;

	private final IBinding recipient;

	public JavacAnnotationBinding(Compound ann, JavacBindingResolver resolver, IBinding recipient) {
		this.resolver = resolver;
		this.annotation = ann;
		this.recipient = recipient;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof JavacAnnotationBinding other
				&& Objects.equals(this.resolver, other.resolver)
				&& Objects.equals(this.annotation, other.annotation)
				&& Objects.equals(this.recipient, other.recipient);
	}
	@Override
	public int hashCode() {
		return Objects.hash(this.resolver, this.annotation, this.recipient);
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return new IAnnotationBinding[0];
	}

	@Override
	public int getKind() {
		return ANNOTATION;
	}

	@Override
	public int getModifiers() {
		return getAnnotationType().getModifiers();
	}

	@Override
	public boolean isDeprecated() {
		return getAnnotationType().isDeprecated();
	}

	@Override
	public boolean isRecovered() {
		return getAnnotationType().isRecovered();
	}

	@Override
	public boolean isSynthetic() {
		return getAnnotationType().isSynthetic();
	}

	@Override
	public IJavaElement getJavaElement() {
		return getAnnotationType().getJavaElement();
	}

	@Override
	public String getKey() {
		StringBuilder builder = new StringBuilder();
		if (this.recipient != null) {
			builder.append(this.recipient.getKey());
		}
		builder.append('@');
		ITypeBinding annotationType = this.getAnnotationType();
		if (annotationType != null) {
			builder.append(this.getAnnotationType().getKey());
		} else {
			ILog.get().error("missing annotation type");
		}
		return builder.toString();
	}

	@Override
	public boolean isEqualTo(IBinding binding) {
		return binding instanceof IAnnotationBinding other && Objects.equals(getKey(), other.getKey());
	}

	@Override
	public IMemberValuePairBinding[] getAllMemberValuePairs() {
		// TODO see testBug405908 - expected to return all POSSIBLE pairs declared on the annotation definition, not user?? 
		return this.annotation.getElementValues().entrySet().stream()
			.map(entry -> this.resolver.bindings.getMemberValuePairBinding(entry.getKey(), entry.getValue()))
			.filter(Objects::nonNull)
			.toArray(IMemberValuePairBinding[]::new);
	}

	@Override
	public ITypeBinding getAnnotationType() {
		return this.resolver.bindings.getTypeBinding(this.annotation.type);
	}

	@Override
	public IMemberValuePairBinding[] getDeclaredMemberValuePairs() {
		return getAllMemberValuePairs();
	}

	@Override
	public String getName() {
		return getAnnotationType().getName();
	}

	@Override
	public String toString() {
		return '@' + getName() + '(' +
				Arrays.stream(getAllMemberValuePairs()).map(IMemberValuePairBinding::toString).collect(Collectors.joining(","))
			+ ')';
	}
}
