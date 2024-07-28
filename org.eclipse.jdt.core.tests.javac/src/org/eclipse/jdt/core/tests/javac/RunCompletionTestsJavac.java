/*******************************************************************************
 * Copyright (c) 2024, Red Hat, Inc. and others.
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
package org.eclipse.jdt.core.tests.javac;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.tests.junit.extension.TestCase;
import org.eclipse.jdt.core.tests.model.AbstractJavaModelCompletionTests;
import org.eclipse.jdt.core.tests.model.CompletionTests;
import org.eclipse.jdt.core.tests.model.CompletionTests17;
import org.eclipse.jdt.core.tests.model.RunCompletionModelTests;

import junit.framework.Test;
import junit.framework.TestSuite;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class RunCompletionTestsJavac extends junit.framework.TestCase {
	public static final List COMPLETION_SUITES = new ArrayList();
	static {
		COMPLETION_SUITES.add(CompletionTests.class);
	}
	
	public RunCompletionTestsJavac(String name) {
		super(name);
	}
	
	public static Class[] getAllTestClasses() {
		return new Class[] { CompletionTests17.class };
	}
	
	public static Test suite() {
		TestSuite ts = new TestSuite(RunCompletionModelTests.class.getName());
		
		// Store test classes with same "Completion"project
		AbstractJavaModelCompletionTests.COMPLETION_SUITES = new ArrayList(COMPLETION_SUITES);
		
		// Get all classes
		Class[] allClasses = (Class[]) COMPLETION_SUITES.toArray(len -> new Class[len]);
		
		// Reset forgotten subsets of tests
		TestCase.TESTS_PREFIX = null;
		TestCase.TESTS_NAMES = null;
		TestCase.TESTS_NUMBERS = null;
		TestCase.TESTS_RANGE = null;
		TestCase.RUN_ONLY_ID = null;
		
		// Add all tests suite of tests
		for (int i = 0, length = allClasses.length; i < length; i++) {
			Class testClass = allClasses[i];
			
			// call the suite() method and add the resulting suite to the suite
			try {
				Method suiteMethod = testClass.getDeclaredMethod("suite", new Class[0]); //$NON-NLS-1$
				Test suite = (Test) suiteMethod.invoke(null, new Object[0]);
				ts.addTest(suite);
				if (suite.countTestCases() == 0) {
					AbstractJavaModelCompletionTests.COMPLETION_SUITES.remove(testClass);
				}
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.getTargetException().printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		}
		return ts;
	}
}
