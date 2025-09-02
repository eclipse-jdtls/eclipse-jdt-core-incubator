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
package org.eclipse.jdt.internal.core.search.matching;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.IModuleBinding;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

public class DOMMatchLocator extends MatchLocator {

	final Set<IModuleBinding> moduleBindings = new HashSet<>();

	DOMMatchLocator(SearchPattern pattern, SearchRequestor requestor, IJavaSearchScope scope,
			IProgressMonitor progressMonitor) {
		super(pattern, requestor, scope, progressMonitor);
	}

	public void registerModuleBinding(IModuleBinding binding) {
		if (binding != null) {
			this.moduleBindings.add(binding);
		}
	}

	@Override
	public MethodBinding getMethodBinding(MethodPattern methodPattern) {
		char[][] compositeTypeName = Stream.of(methodPattern.declaringType.getFullyQualifiedName().split("\\.")).map(String::toCharArray).toArray(char[][]::new);
		ReferenceBinding typeRefBinding = lookupEnvironment.askForType(compositeTypeName, lookupEnvironment.module);
		for (MethodBinding method : typeRefBinding.methods()) {
			int numParams = method.parameters.length;
			if (numParams != methodPattern.parameterCount) {
				continue;
			}
			for (int i = 0; i < numParams; i++) {
				StringBuilder patternParam = new StringBuilder();
				if (methodPattern.parameterQualifications[i] != null && methodPattern.parameterQualifications[i].length > 0) {
					patternParam.append(methodPattern.parameterQualifications[i]);
					patternParam.append('.');
				}
				patternParam.append(methodPattern.parameterSimpleNames[i]);
				char[] patternParamChar = patternParam.toString().replaceAll("\\.", "/").toCharArray();
				TypeBinding typeBinding = method.parameters[i];
				char[] potentialMatchParamType = typeBinding.constantPoolName();
				if (!CharOperation.equals(patternParamChar, potentialMatchParamType)) {
					continue;
				}
			}
			if (CharOperation.equals(method.selector, methodPattern.selector)) {
				return method;
			}
		}
		return null;
	}

}
