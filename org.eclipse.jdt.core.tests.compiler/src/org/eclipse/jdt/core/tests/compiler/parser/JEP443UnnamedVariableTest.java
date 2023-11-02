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
package org.eclipse.jdt.core.tests.compiler.parser;

import java.util.Locale;

import org.eclipse.jdt.core.tests.util.AbstractCompilerTest;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.junit.Test;

public class JEP443UnnamedVariableTest  extends AbstractCompilerTest {
	public static boolean optimizeStringLiterals = false;
	public static long sourceLevel = ClassFileConstants.JDK21; //$NON-NLS-1$

	public JEP443UnnamedVariableTest(String testName){
		super(testName);
	}

	private CompilationUnitDeclaration parse(String source, String testName) {
		this.complianceLevel = ClassFileConstants.JDK21;
		/* using regular parser in DIET mode */
		CompilerOptions options = new CompilerOptions(getCompilerOptions());
		options.enablePreviewFeatures = true;
		Parser parser =
			new Parser(
				new ProblemReporter(
					DefaultErrorHandlingPolicies.proceedWithAllProblems(),
					options,
					new DefaultProblemFactory(Locale.getDefault())),
				optimizeStringLiterals);
		ICompilationUnit sourceUnit = new CompilationUnit(source.toCharArray(), testName, null);
		CompilationResult compilationResult = new CompilationResult(sourceUnit, 0, 0, 0);

		return parser.parse(sourceUnit, compilationResult);
	}

	@Test
	public void testCatchStatementWithUnnamedVars() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public void doSomething() {
						try {
							throw new Exception();
						} catch( Exception _) {
							System.out.println("Error");
						}
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testCatchStatementWitSyntaxError() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public void doSomething() {
						try {
							throw new Exception();
						} catch( Exception _@) {
							System.out.println("Error");
						}
					}
				}
				""", "A.java");
		assertTrue(res.compilationResult.hasErrors());
	}


	@Test
	public void testTryWithResourcesWithUnnamedVars() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public void doSomething() {
						InputStream is = null;
						File f = null;
					    try (final InputStream _ = new FileInputStream(f)){
					    	throw new Exception();
					    } catch( Exception e) {
					    }
	    			}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testTryWithResourcesWithSyntaxError() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public void doSomething() {
						InputStream is = null;
						File f = null;
					    try (final InputStream _@ = new FileInputStream(f)){
					    	throw new Exception();
					    } catch( Exception e) {
					    }
	    			}
				}
				""", "A.java");
		assertTrue(res.compilationResult.hasErrors());
	}



	@Test
	public void testLambdaUnnamedParameter() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					interface FuncInterface {
						void abstractFun(int x, String y);
					}
					public static void main(String args[]) {
						FuncInterface fobj = (int x, String _) -> System.out.println(2 * x);
						fobj.abstractFun(5, "blah");
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testLambdaUnnamedParameterWithSyntaxError() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					interface FuncInterface {
						void abstractFun(int x, String y);
					}
					public static void main(String args[]) {
						FuncInterface fobj = (int x, String a$@!) -> System.out.println(2 * x);
						fobj.abstractFun(5, "blah");
					}
				}
				""", "A.java");
		assertTrue(res.compilationResult.hasErrors());
	}


	@Test
	public void testLambdaFunctionParamsWithParens1() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.Function;
				public class A {
					public static void doSomething() {
						Function<Integer, String> myFunc =  (Integer _) -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testLambdaFunctionParamsWithParens2() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.Function;
				public class A {
					public static void doSomething() {
						Function<Integer, String> myFunc =  (_) -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}


	@Test
	public void testLambdaFunctionParamsNoParens1() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.Function;
				public class A {
					public static void doSomething() {
						Function<Integer, String> myFunc =  Integer _ -> "Hello";
					}
				}
				""", "A.java");
		assertTrue(res.compilationResult.hasErrors());
	}

	@Test
	public void testLambdaFunctionParamsNoParens2() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.Function;
				public class A {
					public static void doSomething() {
						Function<Integer, String> myFunc =  _ -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}


	@Test
	public void testLambdaBiFunctionParamsNoParens1() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.BiFunction;
				public class A {
					public static void doSomething() {
						BiFunction<Integer, Integer, String> myFunc =  (a,b) -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}


	@Test
	public void testLambdaBiFunctionParamsNoParens2() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.BiFunction;
				public class A {
					public static void doSomething() {
						BiFunction<Integer, Integer, String> myFunc =  (_,b) -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}


	@Test
	public void testLambdaBiFunctionParamsNoParens3() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.BiFunction;
				public class A {
					public static void doSomething() {
						BiFunction<Integer, Integer, String> myFunc =  (a,_) -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}



	@Test
	public void testLambdaBiFunctionParamsNoParens4() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.BiFunction;
				public class A {
					public static void doSomething() {
						BiFunction<Integer, Integer, String> myFunc =  (_,_) -> "Hello";
					}
				}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testLambdaBiFunctionParamsNoParensSyntaxError() {
		CompilationUnitDeclaration res = parse(
				"""
				import java.util.function.BiFunction;
				public class A {
					public static void doSomething() {
						BiFunction<Integer, Integer, String> myFunc =  (_#,_) -> "Hello";
					}
				}
				""", "A.java");
		assertTrue(res.compilationResult.hasErrors());
	}

	@Test
	public void testInstanceOfPatternMatchingWithUnnamedPatterns() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						if (namedPoint instanceof MyRecord(_, MyPoint(_, _))) {
							System.out.println("matched named point");
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testInstanceOfPatternMatchingWithMixedPatterns() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						if (namedPoint instanceof MyRecord(_, MyPoint(_, int y))) {
							System.out.println(y);
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testInstanceOfPatternMatchingWithUnnamedVariables() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						if (namedPoint instanceof MyRecord(String _, MyPoint(_, int _))) {
							System.out.println(y);
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testSwitchPatternMatchingWithUnnamedPatterns() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						switch (namedPoint) {
							case NamedPoint(_, Point(_, _)) -> System.out.println("I am utilizing pattern matching");
							default -> System.out.println("oh no");
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testSwitchPatternMatchingWithMixedPatterns() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						switch (namedPoint) {
							case NamedPoint(_, Point(int x, _)) -> System.out.println(x);
							default -> System.out.println("oh no");
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testSwitchPatternMatchingWithUnnamedVariables() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						switch (namedPoint) {
							case NamedPoint(String _, Point(int _, _)) -> System.out.println(x);
							default -> System.out.println("oh no");
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

	@Test
	public void testSwitchPatternMatchingWithUnnamedVariablesVar() {
		CompilationUnitDeclaration res = parse(
				"""
				public class A {
					public static void main(String[] args) {
						var namedPoint = new NamedPoint("name", new Point(1, 2));
						switch (namedPoint) {
							case NamedPoint(String _, Point(var _, _)) -> System.out.println(x);
							default -> System.out.println("oh no");
						}
					}
				}
				record NamedPoint(String name, Point point) {}
				record Point(int x, int y) {}
				""", "A.java");
		assertFalse(res.compilationResult.hasErrors());
	}

}