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
package org.eclipse.jdt.core.tests.compiler.regression;

import java.util.Map;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import junit.framework.Test;

public class UnnamedPatternsAndVariablesTest extends AbstractBatchCompilerTest {

	public static Test suite() {
		return buildMinimalComplianceTestSuite(UnnamedPatternsAndVariablesTest.class, F_1_8);
	}

	public UnnamedPatternsAndVariablesTest(String name) {
		super(name);
	}

	@Override
	protected Map<String, String> getCompilerOptions() {
		CompilerOptions compilerOptions = new CompilerOptions(super.getCompilerOptions());
		if (compilerOptions.sourceLevel == ClassFileConstants.JDK21) {
			compilerOptions.enablePreviewFeatures = true;
		}
		return compilerOptions.getMap();
	}

	public void testReportsUnderscoreInstanceMemberAsError() {
		CompilerOptions options = new CompilerOptions(getCompilerOptions());

		String message;
		String errorLevel;
		if (options.sourceLevel < ClassFileConstants.JDK9) {
			message = "'_' should not be used as an identifier, since it is a reserved keyword from source level 1.8 on";
			errorLevel = "WARNING";
		} else if (options.sourceLevel < ClassFileConstants.JDK21) {
			message = "'_' is a keyword from source level 9 onwards, cannot be used as identifier";
			errorLevel = "ERROR";
		} else {
			message = "As of release 21, '_' is only allowed to declare unnamed patterns, local variables, exception parameters or lambda parameters";
			errorLevel = "ERROR";
		}

		runNegativeTest(new String[] { "A.java", """
				public class A {
					int _ = 1;
					public static void main(String[] args) {
						System.out.println("Hello, World!");
					}
				}
				""" },

				"----------\n" +
				"1. " + errorLevel + " in A.java (at line 2)\n" +
				"	int _ = 1;\n" +
				"	    ^\n" +
				message + "\n" +
				"----------\n");
	}

	public void testReportsUnicodeEscapeUnderscoreInstanceMemberAsError() {
		CompilerOptions options = new CompilerOptions(getCompilerOptions());

		String message;
		String errorLevel;
		if (options.sourceLevel < ClassFileConstants.JDK9) {
			message = "'_' should not be used as an identifier, since it is a reserved keyword from source level 1.8 on";
			errorLevel = "WARNING";
		} else if (options.sourceLevel < ClassFileConstants.JDK21) {
			message = "'_' is a keyword from source level 9 onwards, cannot be used as identifier";
			errorLevel = "ERROR";
		} else {
			message = "As of release 21, '_' is only allowed to declare unnamed patterns, local variables, exception parameters or lambda parameters";
			errorLevel = "ERROR";
		}

		runNegativeTest(new String[] { "A.java", """
				public class A {
					int \\u005F = 1;
					public static void main(String[] args) {
						System.out.println("Hello, World!");
					}
				}
				""" },
				"----------\n" +
				"1. " + errorLevel + " in A.java (at line 2)\n" +
				"	int \\u005F = 1;\n" +
				"	    ^^^^^^\n" +
				message + "\n" +
				"----------\n");
	}

	public void testReportsUnderscoreParameterAsError() {
		CompilerOptions options = new CompilerOptions(getCompilerOptions());

		String message;
		String errorLevel;
		if (options.sourceLevel < ClassFileConstants.JDK9) {
			message = "'_' should not be used as an identifier, since it is a reserved keyword from source level 1.8 on";
			errorLevel = "WARNING";
		} else if (options.sourceLevel < ClassFileConstants.JDK21) {
			message = "'_' is a keyword from source level 9 onwards, cannot be used as identifier";
			errorLevel = "ERROR";
		} else {
			message = "As of release 21, '_' is only allowed to declare unnamed patterns, local variables, exception parameters or lambda parameters";
			errorLevel = "ERROR";
		}

		runNegativeTest(new String[] { "A.java", """
				public class A {
					public static void main(String[] args) {
						foo(1);
					}
					public static void foo(int _) {
						System.out.println("Hello, World!");
					}
				}
				""" },
				"----------\n" +
				"1. " + errorLevel + " in A.java (at line 5)\n" +
				"	public static void foo(int _) {\n" +
				"	                           ^\n" +
				message + "\n" +
				"----------\n");
	}

	public void testInstanceofUnnamedPatternMatching() {
		CompilerOptions options = new CompilerOptions(getCompilerOptions());
		if (options.sourceLevel < ClassFileConstants.JDK21) {
			return;
		}

		runConformTest(new String[] { "A.java", """
				public class A {
					public static void main(String[] args) {
						Object r = null;
						if (r instanceof ColoredPoint(Point(int x, _), _)) {
							System.out.println("Hello, World!" + x);
						}
					}
				}
				record Point(int x, int y) { }
				enum Color { RED, GREEN, BLUE }
				record ColoredPoint(Point p, Color c) { }
				"""}, "", null, new String[] { "--enable-preview" });
	}

	public void testReuseLocalUnnamedVariable() {
		CompilerOptions options = new CompilerOptions(getCompilerOptions());
		if (options.sourceLevel < ClassFileConstants.JDK21) {
			return;
		}

		runConformTest(new String[] { "A.java", """
				public class A {
					public static void main(String[] args) {
						int _ = 1;
						int _ = 2;
						int _ = 3;
					}
				}
				record Point(int x, int y) { }
				enum Color { RED, GREEN, BLUE }
				record ColoredPoint(Point p, Color c) { }
				"""}, "", null, new String[] { "--enable-preview" });
	}

	public void testReuseLocalUnnamedVariableUnicodeEscape() {
		CompilerOptions options = new CompilerOptions(getCompilerOptions());
		if (options.sourceLevel < ClassFileConstants.JDK21) {
			return;
		}

		runConformTest(new String[] { "A.java", """
				public class A {
					public static void main(String[] args) {
						int _ = 1;
						int \\u005F = 2;
						int \\u005F = 3;
					}
				}
				record Point(int x, int y) { }
				enum Color { RED, GREEN, BLUE }
				record ColoredPoint(Point p, Color c) { }
				"""}, "", null, new String[] { "--enable-preview" });
	}
}
