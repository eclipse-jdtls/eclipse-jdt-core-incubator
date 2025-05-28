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
package org.eclipse.jdt.core.tests.javac;

import org.eclipse.jdt.core.tests.model.RunJavaSearchTests;

public class RunSearchJavacTests extends RunJavaSearchTests {

	public RunSearchJavacTests(String name) {
		super(name);
		// we currently ignore the match type when using Javac, but eventually, we'll need to honor them too
		System.setProperty("AbstractJavaSearchTests.IGNORE_MATCH_TYPE", Boolean.toString(true));
	}

}

