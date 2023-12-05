package org.eclipse.jdt.core.tests.compiler.regression;

import java.util.Map;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import junit.framework.Test;

public class UseOfUnderscoreTest extends AbstractBatchCompilerTest {

	public static Test suite() {
		return buildMinimalComplianceTestSuite(UseOfUnderscoreTest.class, F_1_8);
	}

	public UseOfUnderscoreTest(String name) {
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

	public void testReportsUnderscoreParameterAsErrorUnicodeEscape() {
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
					public static void foo(int \\u005F) {
						System.out.println("Hello, World!");
					}
				}
				""" },
				"----------\n" +
				"1. " + errorLevel + " in A.java (at line 5)\n" +
				"	public static void foo(int \\u005F) {\n" +
				"	                           ^^^^^^\n" +
				message + "\n" +
				"----------\n");
	}

}
