/*******************************************************************************
 * Copyright (c) 2024, Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.core.tests.javac;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.tests.model.AbstractJavaModelTests;
import org.junit.Test;

public class CompilerTests extends AbstractJavaModelTests {

	public CompilerTests() {
		super(CompilerTests.class.getName());
	}

	@Test
	public void testProceedOnErrors() throws CoreException {
		IJavaProject javaProject = createJavaProject("A", new String[]{"src"}, "bin");
		createFile("A/src/A.java", """
			public class A {
				void syntaxError() {
					System.err.println("Correct statement pre-error");
					syntaxError
				}
				void resolutionError() {
					System.err.println("Correct statement pre-error");
					this.o = 1;
					System.err.println("Correct statement post-error");
				}
			""");
		javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IFile classFile = javaProject.getProject().getFolder("bin").getFile("A.class");
		assertTrue(classFile.exists());
	}

	@Test
	public void testSwitchExpression() throws Exception {
		IJavaProject javaProject = createJava21Project("A");
		createFile("A/src/SwitchExpr.java", """
			import java.time.Month;
			public class SwitchExpr {
				public static void main(String[] args) {
					Month opt = Month.JANUARY;
					int n = switch (opt) {
						case JANUARY -> 1;
						case FEBRUARY -> 2;
						default -> 3;
					};
					System.err.println(n);
				}
			}
			""");
		javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IFile classFile = javaProject.getProject().getParent().getFolder(javaProject.getOutputLocation()).getFile("SwitchExpr.class");
		assertTrue(classFile.exists());
		//
		File tmpOutput = File.createTempFile("output", "txt");
		new ProcessBuilder().directory(classFile.getParent().getLocation().toFile())
			.command(ProcessHandle.current().info().command().orElse(""), "SwitchExpr")
			.redirectError(tmpOutput)
			.start()
			.waitFor();
		var lines = Files.readAllLines(tmpOutput.toPath());
		tmpOutput.delete();
		assertEquals("1", lines.get(0));
	}

	@Test
	public void testUnused() throws Exception {
		IJavaProject javaProject = createJava21Project("A");
		IFile file = createFile("A/src/SwitchExpr.java", """
			import java.time.Month;
			public class SwitchExpr {
				public static void main(String[] args) {
					System.err.println("1");
				}
			}
			""");
		javaProject.setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);
		javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(1, markers.length);
	}

	@Test
	public void testUsedInJavadoc() throws Exception {
		IJavaProject javaProject = createJava21Project("A");
		createFile("A/src/MonthUtil.java", """
				import java.time.Month;
				public class MonthUtil {
					public static void monthMethod(Month month) {
					}
				}
				""");
		IFile file = createFile("A/src/SwitchExpr.java", """
			import java.time.Month;
			public class SwitchExpr {
				/**
				 * @see MonthUtil#monthMethod(Month)
				 */
				public static void main(String[] args) {
					System.err.println("1");
				}
			}
			""");

		javaProject.setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);
		javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);
	}

	@Test
	public void testUsedInJavadocArray() throws Exception {
		IJavaProject javaProject = createJava21Project("A");
		createFile("A/src/MonthUtil.java", """
				import java.time.Month;
				public class MonthUtil {
					public static void monthMethod(Month[] month) {
					}
				}
				""");
		IFile file = createFile("A/src/SwitchExpr.java", """
			import java.time.Month;
			public class SwitchExpr {
				/**
				 * @see MonthUtil#monthMethod(Month[])
				 */
				public static void main(String[] args) {
					System.err.println("1");
				}
			}
			""");

		javaProject.setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);
		javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);
	}

	@Test
	public void testAccessRulesProblems() throws Exception {
		IJavaProject javaProjectA = createJava21Project("A");
		IJavaProject javaProjectB = createJava21Project("B");
		IClasspathEntry[] classpathEntries = javaProjectB.getRawClasspath();

		IAccessRule accessRule = JavaCore.newAccessRule(new Path("**/org/example/util/*"), IAccessRule.K_NON_ACCESSIBLE);

		IClasspathEntry projectAEntry = JavaCore.newProjectEntry(javaProjectA.getPath(), //
				new IAccessRule[] { accessRule },
				false,
				new IClasspathAttribute[0],
				false);

		IClasspathEntry[] newClasspathEntries = new IClasspathEntry[classpathEntries.length + 1];
		System.arraycopy(classpathEntries, 0, newClasspathEntries, 0, classpathEntries.length);
		newClasspathEntries[classpathEntries.length] = projectAEntry;

		javaProjectB.setRawClasspath(newClasspathEntries, new NullProgressMonitor());

		createFolder("A/src/org");
		createFolder("A/src/org/example");
		createFolder("A/src/org/example/util");

		IFile utilClass = createFile("A/src/org/example/util/MonthUtil.java", """
				package org.example.util;

				public class MonthUtil {
					public static String monthMethod(int monthNumber) {
						switch (monthNumber) {
						case 1:
							return "January";
						case 2:
							return "February";
						case 3:
							return "March";
						case 4:
							return "April";
						case 5:
							return "May";
						case 6:
							return "June";
						case 7:
							return "July";
						case 8:
							return "August";
						case 9:
							return "September";
						case 10:
							return "October";
						case 11:
							return "November";
						case 12:
							return "December";
						default:
							throw new IllegalArgumentException("expected a number from 1-12 inclusive");
						}
					}
				}
				""");

		javaProjectA.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectA.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = utilClass.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);

		IFile file = createFile("B/src/BreakAccessRules.java", """
			import org.example.util.MonthUtil;
			public class BreakAccessRules {
				public static void main(String[] args) {
					System.out.println(MonthUtil.monthMethod(4));
				}
			}
			""");

		javaProjectB.setOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, JavaCore.ERROR);
		javaProjectB.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectB.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		// One on MonthUtil and one on monthMethod
		assertEquals(2, markers.length);
		String actual = getMarkersString(markers, false);
		assertEquals("""
				Access restriction: The type 'MonthUtil' is not API (restriction on required project 'A')
				Access restriction: The method 'MonthUtil.monthMethod(int)' is not API (restriction on required project 'A')""", actual);
	}

	@Test
	public void testAccessRulesProblemsJavadoc() throws Exception {
		IJavaProject javaProjectA = createJava21Project("A");
		IJavaProject javaProjectB = createJava21Project("B");
		IClasspathEntry[] classpathEntries = javaProjectB.getRawClasspath();

		IAccessRule accessRule = JavaCore.newAccessRule(new Path("**/org/example/util/*"), IAccessRule.K_NON_ACCESSIBLE);

		IClasspathEntry projectAEntry = JavaCore.newProjectEntry(javaProjectA.getPath(), //
				new IAccessRule[] { accessRule },
				false,
				new IClasspathAttribute[0],
				false);

		IClasspathEntry[] newClasspathEntries = new IClasspathEntry[classpathEntries.length + 1];
		System.arraycopy(classpathEntries, 0, newClasspathEntries, 0, classpathEntries.length);
		newClasspathEntries[classpathEntries.length] = projectAEntry;

		javaProjectB.setRawClasspath(newClasspathEntries, new NullProgressMonitor());

		createFolder("A/src/org");
		createFolder("A/src/org/example");
		createFolder("A/src/org/example/util");

		IFile utilClass = createFile("A/src/org/example/util/MonthUtil.java", """
				package org.example.util;

				public class MonthUtil {
					public static String monthMethod(int monthNumber) {
						switch (monthNumber) {
						case 1:
							return "January";
						case 2:
							return "February";
						case 3:
							return "March";
						case 4:
							return "April";
						case 5:
							return "May";
						case 6:
							return "June";
						case 7:
							return "July";
						case 8:
							return "August";
						case 9:
							return "September";
						case 10:
							return "October";
						case 11:
							return "November";
						case 12:
							return "December";
						default:
							throw new IllegalArgumentException("expected a number from 1-12 inclusive");
						}
					}
				}
				""");

		javaProjectA.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectA.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = utilClass.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);

		IFile file = createFile("B/src/BreakAccessRules.java", """
			import org.example.util.MonthUtil;
			public class BreakAccessRules {
				/**
				 * @see MonthUtil#monthMethod(int)
				 * @see MonthUtil#monthMethod
				 * {@link MonthUtil#monthMethod(int)}
				 */
				public static void main(String[] args) {
					System.out.println(MonthUtil.monthMethod(4));
				}
			}
			""");

		javaProjectB.setOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, JavaCore.ERROR);
		javaProjectB.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectB.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		// In the code, one on MonthUtil and one on monthMethod
		// In the comment, two on the first line, one on the second, two on the third
		assertEquals(2 + 5, markers.length);
		String actual = getMarkersString(markers, false);
		assertEquals("""
				Access restriction: The type 'MonthUtil' is not API (restriction on required project 'A')
				Access restriction: The method 'MonthUtil.monthMethod(int)' is not API (restriction on required project 'A')
				Access restriction: The type 'MonthUtil' is not API (restriction on required project 'A')
				Access restriction: The type 'MonthUtil' is not API (restriction on required project 'A')
				Access restriction: The method 'MonthUtil.monthMethod(int)' is not API (restriction on required project 'A')
				Access restriction: The type 'MonthUtil' is not API (restriction on required project 'A')
				Access restriction: The method 'MonthUtil.monthMethod(int)' is not API (restriction on required project 'A')""", actual);
	}

	@Test
	public void testAccessRulesProblemsConstructor() throws Exception {
		IJavaProject javaProjectA = createJava21Project("A");
		IJavaProject javaProjectB = createJava21Project("B");
		IClasspathEntry[] classpathEntries = javaProjectB.getRawClasspath();

		IAccessRule accessRule = JavaCore.newAccessRule(new Path("**/org/example/util/*"), IAccessRule.K_NON_ACCESSIBLE);

		IClasspathEntry projectAEntry = JavaCore.newProjectEntry(javaProjectA.getPath(), //
				new IAccessRule[] { accessRule },
				false,
				new IClasspathAttribute[0],
				false);

		IClasspathEntry[] newClasspathEntries = new IClasspathEntry[classpathEntries.length + 1];
		System.arraycopy(classpathEntries, 0, newClasspathEntries, 0, classpathEntries.length);
		newClasspathEntries[classpathEntries.length] = projectAEntry;

		javaProjectB.setRawClasspath(newClasspathEntries, new NullProgressMonitor());

		createFolder("A/src/org");
		createFolder("A/src/org/example");
		createFolder("A/src/org/example/util");

		IFile utilClass = createFile("A/src/org/example/util/Mouth.java", """
				package org.example.util;

				public class Mouth {
					private boolean isOpen;
					public Mouth(boolean isOpen) {
						this.isOpen = isOpen;
					}

					public void open() {
						this.isOpen = true;
					}

					public void close() {
						this.isOpen = false;
					}

					public boolean getIsOpen() {
						return this.isOpen;
					}
				}
				""");

		javaProjectA.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectA.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = utilClass.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);

		IFile file = createFile("B/src/BreakAccessRules.java", """
			import org.example.util.Mouth;
			public class BreakAccessRules {
				public static void main(String[] args) {
					Mouth mouth = new Mouth(true);
					mouth.close();
				}
			}
			""");

		javaProjectB.setOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, JavaCore.ERROR);
		javaProjectB.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectB.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		// Two on the Mouth constructor (one of which is marked constructor)
		// One on the LHS of the assignment for Mouth type
		// One on open()

		assertEquals(4, markers.length);

		String actual = getMarkersString(markers, false);
		assertEquals("""
				Access restriction: The type 'Mouth' is not API (restriction on required project 'A')
				Access restriction: The constructor 'Mouth(boolean)' is not API (restriction on required project 'A')
				Access restriction: The type 'Mouth' is not API (restriction on required project 'A')
				Access restriction: The method 'Mouth.close()' is not API (restriction on required project 'A')""", actual);
	}

	@Test
	public void testAccessRulesProblemsInnerConstructor() throws Exception {
		IJavaProject javaProjectA = createJava21Project("A");
		IJavaProject javaProjectB = createJava21Project("B");
		IClasspathEntry[] classpathEntries = javaProjectB.getRawClasspath();

		IAccessRule accessRule = JavaCore.newAccessRule(new Path("**/org/example/util/*"), IAccessRule.K_NON_ACCESSIBLE);

		IClasspathEntry projectAEntry = JavaCore.newProjectEntry(javaProjectA.getPath(), //
				new IAccessRule[] { accessRule },
				false,
				new IClasspathAttribute[0],
				false);

		IClasspathEntry[] newClasspathEntries = new IClasspathEntry[classpathEntries.length + 1];
		System.arraycopy(classpathEntries, 0, newClasspathEntries, 0, classpathEntries.length);
		newClasspathEntries[classpathEntries.length] = projectAEntry;

		javaProjectB.setRawClasspath(newClasspathEntries, new NullProgressMonitor());

		createFolder("A/src/org");
		createFolder("A/src/org/example");
		createFolder("A/src/org/example/util");

		IFile utilClass = createFile("A/src/org/example/util/Mouth.java", """
				package org.example.util;

				public class Mouth {
					public static class Tongue {
						public Tongue() {
						}
						public void flapAboutEveryWhichWay() {
							System.out.println("bllblblbllblbllblbllbll!");
						}
					}
				}
				""");

		javaProjectA.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectA.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		IMarker[] markers = utilClass.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);

		IFile file = createFile("B/src/BreakAccessRules.java", """
			import org.example.util.Mouth;
			public class BreakAccessRules {
				public static void main(String[] args) {
					Mouth.Tongue tongue = new Mouth.Tongue();
					tongue.flapAboutEveryWhichWay();
				}
			}
			""");

		javaProjectB.setOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, JavaCore.ERROR);
		javaProjectB.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		javaProjectB.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
		markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
		// Two on the Mouth constructor (one of which is marked constructor)
		// One on the LHS of the assignment for Mouth type
		// One on open()
		assertEquals(6, markers.length);
		String actual = getMarkersString(markers, false);
		assertEquals("""
				Access restriction: The type 'Mouth' is not API (restriction on required project 'A')
				Access restriction: The type 'Mouth.Tongue' is not API (restriction on required project 'A')
				Access restriction: The constructor 'Mouth.Tongue()' is not API (restriction on required project 'A')
				Access restriction: The type 'Mouth' is not API (restriction on required project 'A')
				Access restriction: The type 'Mouth.Tongue' is not API (restriction on required project 'A')
				Access restriction: The method 'Mouth.Tongue.flapAboutEveryWhichWay()' is not API (restriction on required project 'A')""", actual);
	}

	// HELPERS

	/**
	 * Returns a string representation of all the given markers.
	 *
	 * The entries are new-line separated.
	 *
	 * @param markers the markers to construct a string representation of
	 * @param includeRange if true, include the range of each marker, if false, don't
	 * @return a string representation of all the given markers, containing the range and message of each
	 */
	private static String getMarkersString(IMarker[] markers, boolean includeRange) {
		StringBuilder builder = new StringBuilder();
		Stream.of(markers).sorted(new Comparator<IMarker>() {

			@Override
			public int compare(IMarker o1, IMarker o2) {
				try {
					int firstStart = (int)o1.getAttribute(IMarker.CHAR_START);
					int secondStart = (int)o2.getAttribute(IMarker.CHAR_START);

					if (firstStart != secondStart) {
						return firstStart - secondStart;
					}

					String firstMessage = (String)o1.getAttribute(IMarker.MESSAGE);
					String secondMessage = (String)o2.getAttribute(IMarker.MESSAGE);

					return firstMessage.compareTo(secondMessage);
				} catch (CoreException e) {
					// can't do much better
					return 0;
				}
			}

		}).forEach(marker -> builder.append(getSimpleMarkerString(marker, includeRange) + "\n"));
		return builder.toString().stripTrailing();
	}

	private static String getSimpleMarkerString(IMarker marker, boolean includeRange) {
		try {
			StringBuilder builder = new StringBuilder();
			if (includeRange) {
				builder.append('[');
				builder.append(marker.getAttribute(IMarker.CHAR_START));
				builder.append(", ");
				builder.append(marker.getAttribute(IMarker.CHAR_END));
				builder.append(']');
				builder.append(' ');
			}
			builder.append(marker.getAttribute(IMarker.MESSAGE));

			return builder.toString();
		} catch (CoreException e) {
			return "";
		}
	}
}
