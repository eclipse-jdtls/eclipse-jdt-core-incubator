/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Utility methods for modifying the protected fields of LambdaExpression and LambdaMethod IJavaElements.
 */
public class LambdaUtils {

	/**
	 * Attaches the given LambdaMethod to the given LambdaExpression.
	 *
	 * @param method the lambda method
	 * @param expression the lambda expression
	 */
	public static void attachMethodToExpression(LambdaMethod method, LambdaExpression expression) {
		expression.lambdaMethod = method;
	}

	/**
	 * Sets the arguments of the element info of the given LambdaMethod.
	 *
	 * @param method the lambda method to set the arguments of
	 * @param arguments the arguments of the lambda method
	 */
	public static void attachMethodArgumentsToLambda(LambdaMethod method, ILocalVariable[] arguments) {
		try {
			((SourceMethodElementInfo)method.getElementInfo()).arguments = arguments;
		} catch (JavaModelException e) {
			ILog.get().error("While attaching arguments to lambda method", e);
		}
	}

}
