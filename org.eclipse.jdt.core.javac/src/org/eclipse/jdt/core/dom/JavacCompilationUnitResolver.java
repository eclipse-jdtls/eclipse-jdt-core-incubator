/*******************************************************************************
 * Copyright (c) 2023, Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.core.dom;

import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.batch.FileSystem.Classpath;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ITypeRequestor;
import org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;
import org.eclipse.jdt.internal.compiler.util.Util;
import org.eclipse.jdt.internal.core.CancelableNameEnvironment;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.dom.ICompilationUnitResolver;
import org.eclipse.jdt.internal.core.util.BindingKeyParser;
import org.eclipse.jdt.internal.javac.JavacProblemConverter;
import org.eclipse.jdt.internal.javac.JavacUtils;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavadocTokenizer;
import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;

/**
 * Allows to create and resolve DOM ASTs using Javac
 * @implNote Cannot move to another package because parent class is package visible only
 */
public class JavacCompilationUnitResolver implements ICompilationUnitResolver {
	public JavacCompilationUnitResolver() {
		// 0-arg constructor
	}
	private interface GenericRequestor {
		public void acceptBinding(String bindingKey, IBinding binding);
	}
	private List<org.eclipse.jdt.internal.compiler.env.ICompilationUnit> createSourceUnitList(String[] sourceFilePaths, String[] encodings) {
		// make list of source unit
		int length = sourceFilePaths.length;
		List<org.eclipse.jdt.internal.compiler.env.ICompilationUnit> sourceUnitList = new ArrayList<>(length);
		for (int i = 0; i < length; i++) {
			org.eclipse.jdt.internal.compiler.env.ICompilationUnit obj = createSourceUnit(sourceFilePaths[i], encodings[i]);
			if( obj != null )
				sourceUnitList.add(obj);
		}
		return sourceUnitList;
	}

	private org.eclipse.jdt.internal.compiler.env.ICompilationUnit createSourceUnit(String sourceFilePath, String encoding) {
		char[] contents = null;
		try {
			contents = Util.getFileCharContent(new File(sourceFilePath), encoding);
		} catch(IOException e) {
			return null;
		}
		if (contents == null) {
			return null;
		}
		return new org.eclipse.jdt.internal.compiler.batch.CompilationUnit(contents, sourceFilePath, encoding);
	}


	@Override
	public void resolve(String[] sourceFilePaths, String[] encodings, String[] bindingKeys, FileASTRequestor requestor,
			int apiLevel, Map<String, String> compilerOptions, List<Classpath> classpaths, int flags,
			IProgressMonitor monitor) {
		List<org.eclipse.jdt.internal.compiler.env.ICompilationUnit> sourceUnitList = createSourceUnitList(sourceFilePaths, encodings);
		JavacBindingResolver bindingResolver = null;

		// parse source units
		Map<org.eclipse.jdt.internal.compiler.env.ICompilationUnit, CompilationUnit> res =
				parse(sourceUnitList.toArray(org.eclipse.jdt.internal.compiler.env.ICompilationUnit[]::new), apiLevel, compilerOptions, flags, (IJavaProject)null, monitor);

		for (var entry : res.entrySet()) {
			CompilationUnit cu = entry.getValue();
			requestor.acceptAST(new String(entry.getKey().getFileName()), cu);
			if (bindingResolver == null && (JavacBindingResolver)cu.ast.getBindingResolver() != null) {
				bindingResolver = (JavacBindingResolver)cu.ast.getBindingResolver();
			}
		}

		resolveRequestedBindingKeys(bindingResolver, bindingKeys,
				(a,b) -> requestor.acceptBinding(a,b),
				classpaths.stream().toArray(Classpath[]::new),
				new CompilerOptions(compilerOptions),
				res.values(), null, monitor);
	}

	@Override
	public void resolve(ICompilationUnit[] compilationUnits, String[] bindingKeys, ASTRequestor requestor, int apiLevel,
			Map<String, String> compilerOptions, IJavaProject project, WorkingCopyOwner workingCopyOwner, int flags,
			IProgressMonitor monitor) {
		Map<ICompilationUnit, CompilationUnit> units = parse(compilationUnits, apiLevel, compilerOptions, flags, monitor);
		if (requestor != null) {
			final JavacBindingResolver[] bindingResolver = new JavacBindingResolver[1];
			bindingResolver[0] = null;
			units.forEach((a,b) -> {
				if (bindingResolver[0] == null && (JavacBindingResolver)b.ast.getBindingResolver() != null) {
					bindingResolver[0] = (JavacBindingResolver)b.ast.getBindingResolver();
				}
				requestor.acceptAST(a,b);
				resolveBindings(b, bindingKeys, requestor, apiLevel);
			});

			resolveRequestedBindingKeys(bindingResolver[0], bindingKeys,
					(a,b) -> requestor.acceptBinding(a,b),
					new Classpath[0], // TODO need some classpaths
					new CompilerOptions(compilerOptions),
					units.values(), project, monitor);
		} else {
			Iterator<CompilationUnit> it = units.values().iterator();
			while(it.hasNext()) {
				resolveBindings(it.next(), apiLevel);
			}
		}
	}


	private void resolveRequestedBindingKeys(JavacBindingResolver bindingResolver, String[] bindingKeys, GenericRequestor requestor,
			Classpath[] cp,CompilerOptions opts,
			Collection<CompilationUnit> units,
			IJavaProject project,
			IProgressMonitor monitor) {
		if (bindingResolver == null) {
			var compiler = ToolProvider.getSystemJavaCompiler();
			var context = new Context();
			JavacTask task = (JavacTask) compiler.getTask(null, null, null, List.of(), List.of(), List.of());
			bindingResolver = new JavacBindingResolver(null, task, context, new JavacConverter(null, null, context, null, true));
		}

		HashMap<String, IBinding> bindingMap = new HashMap<>();
		for (CompilationUnit cu : units) {
			cu.accept(new BindingBuilder(bindingMap));
		}

		INameEnvironment environment = null;
		if (project instanceof JavaProject javaProject) {
			try {
				environment = new CancelableNameEnvironment(javaProject, null, monitor);
			} catch (JavaModelException e) {
				// do nothing
			}
		}
		if (environment == null) {
			environment = new NameEnvironmentWithProgress(cp, null, monitor);
		}

		LookupEnvironment lu = new LookupEnvironment(new ITypeRequestor() {

			@Override
			public void accept(IBinaryType binaryType, PackageBinding packageBinding,
					AccessRestriction accessRestriction) {
				// do nothing
			}

			@Override
			public void accept(org.eclipse.jdt.internal.compiler.env.ICompilationUnit unit,
					AccessRestriction accessRestriction) {
				// do nothing
			}

			@Override
			public void accept(ISourceType[] sourceType, PackageBinding packageBinding,
					AccessRestriction accessRestriction) {
				// do nothing
			}

		}, opts, null, environment);

		// resolve the requested bindings
		for (String bindingKey : bindingKeys) {

			IBinding bindingFromMap = bindingMap.get(bindingKey);
			if (bindingFromMap != null) {
				// from parsed files
				requestor.acceptBinding(bindingKey, bindingFromMap);
			} else {

				CustomBindingKeyParser bkp = new CustomBindingKeyParser(bindingKey);
				bkp.parse(true);
				char[][] name = bkp.compoundName;

//				// from ECJ
//				char[] charArrayFQN = Signature.toCharArray(bindingKey.toCharArray());
//				char[][] twoDimensionalCharArrayFQN = Stream.of(new String(charArrayFQN).split("/")) //
//						.map(myString -> myString.toCharArray()) //
//						.toArray(char[][]::new);
//				char[][] twoDimensionalCharArrayFQN = new char[][] {};
				NameEnvironmentAnswer answer = environment.findType(name);
				if( answer != null ) {
					IBinaryType binaryType = answer.getBinaryType();
					if (binaryType != null) {
						BinaryTypeBinding binding = lu.cacheBinaryType(binaryType, null);
						requestor.acceptBinding(bindingKey, new TypeBinding(bindingResolver, binding));
					}
				}
			}

		}

	}

	private static class CustomBindingKeyParser extends BindingKeyParser {

		private char[] secondarySimpleName;
		private char[][] compoundName;

		public CustomBindingKeyParser(String key) {
			super(key);
		}

		@Override
		public void consumeSecondaryType(char[] simpleTypeName) {
			this.secondarySimpleName = simpleTypeName;
		}

		@Override
		public void consumeFullyQualifiedName(char[] fullyQualifiedName) {
			this.compoundName = CharOperation.splitOn('/', fullyQualifiedName);
		}
	}

	@Override
	public void parse(ICompilationUnit[] compilationUnits, ASTRequestor requestor, int apiLevel,
			Map<String, String> compilerOptions, int flags, IProgressMonitor monitor) {
		Map<ICompilationUnit, CompilationUnit>  units = parse(compilationUnits, apiLevel, compilerOptions, flags, monitor);
		if (requestor != null) {
			units.forEach(requestor::acceptAST);
		}
	}

	private Map<ICompilationUnit, CompilationUnit> parse(ICompilationUnit[] compilationUnits, int apiLevel,
			Map<String, String> compilerOptions, int flags, IProgressMonitor monitor) {
		// TODO ECJCompilationUnitResolver has support for dietParse and ignore method body
		// is this something we need?
		if (compilationUnits.length > 0
			&& Arrays.stream(compilationUnits).map(ICompilationUnit::getJavaProject).distinct().count() == 1
			&& Arrays.stream(compilationUnits).allMatch(org.eclipse.jdt.internal.compiler.env.ICompilationUnit.class::isInstance)) {
			// all in same project, build together
			return
				parse(Arrays.stream(compilationUnits).map(org.eclipse.jdt.internal.compiler.env.ICompilationUnit.class::cast).toArray(org.eclipse.jdt.internal.compiler.env.ICompilationUnit[]::new),
					apiLevel, compilerOptions, flags, compilationUnits[0].getJavaProject(), monitor)
				.entrySet().stream().collect(Collectors.toMap(entry -> (ICompilationUnit)entry.getKey(), entry -> entry.getValue()));
		}
		// build individually
		Map<ICompilationUnit, CompilationUnit> res = new HashMap<>(compilationUnits.length, 1.f);
		for (ICompilationUnit in : compilationUnits) {
			if (in instanceof org.eclipse.jdt.internal.compiler.env.ICompilationUnit compilerUnit) {
				res.put(in, parse(new org.eclipse.jdt.internal.compiler.env.ICompilationUnit[] { compilerUnit },
						apiLevel, compilerOptions, flags, in.getJavaProject(), monitor).get(compilerUnit));
			}
		}
		return res;
	}

	@Override
	public void parse(String[] sourceFilePaths, String[] encodings, FileASTRequestor requestor, int apiLevel,
			Map<String, String> compilerOptions, int flags, IProgressMonitor monitor) {

		for( int i = 0; i < sourceFilePaths.length; i++ ) {
			org.eclipse.jdt.internal.compiler.env.ICompilationUnit ast = createSourceUnit(sourceFilePaths[i], encodings[i]);
			Map<org.eclipse.jdt.internal.compiler.env.ICompilationUnit, CompilationUnit> res =
					parse(new org.eclipse.jdt.internal.compiler.env.ICompilationUnit[] {ast}, apiLevel, compilerOptions, flags, (IJavaProject)null, monitor);
			CompilationUnit result = res.get(ast);
			requestor.acceptAST(sourceFilePaths[i], result);
		}
	}


	private void respondBinding(IBinding binding, List<String> bindingKeys, ASTRequestor requestor) {
		if( binding != null ) {
			String k = binding.getKey();
			if( k != null && bindingKeys.contains(k)) {
				requestor.acceptBinding(k, binding);
			}
		}
	}

	private void resolveBindings(CompilationUnit unit, int apiLevel) {
		resolveBindings(unit, new String[0], null, apiLevel);
	}

	private void resolveBindings(CompilationUnit unit, String[] bindingKeys, ASTRequestor requestor, int apiLevel) {
		List<String> keys = Arrays.asList(bindingKeys);

		if (unit.getPackage() != null) {
			IPackageBinding pb = unit.getPackage().resolveBinding();
			respondBinding(pb, keys, requestor);
		}
		if (!unit.types().isEmpty()) {
			List types = unit.types();
			for( int i = 0; i < types.size(); i++ ) {
				ITypeBinding tb = ((AbstractTypeDeclaration) types.get(i)).resolveBinding();
				respondBinding(tb, keys, requestor);
			}
		}
		if( apiLevel >= AST.JLS9_INTERNAL) {
			if (unit.getModule() != null) {
				IModuleBinding mb = unit.getModule().resolveBinding();
				respondBinding(mb, keys, requestor);
			}
		}
	}

	@Override
	public CompilationUnit toCompilationUnit(org.eclipse.jdt.internal.compiler.env.ICompilationUnit sourceUnit,
			boolean initialNeedsToResolveBinding, IJavaProject project, List<Classpath> classpaths,
			int focalPoint, int apiLevel, Map<String, String> compilerOptions,
			WorkingCopyOwner workingCopyOwner, WorkingCopyOwner typeRootWorkingCopyOwner, int flags, IProgressMonitor monitor) {
		// TODO currently only parse
		CompilationUnit res = parse(new org.eclipse.jdt.internal.compiler.env.ICompilationUnit[] { sourceUnit},
				apiLevel, compilerOptions, flags, project, monitor).get(sourceUnit);
		if (initialNeedsToResolveBinding) {
			((JavacBindingResolver)res.ast.getBindingResolver()).isRecoveringBindings = (flags & ICompilationUnit.ENABLE_BINDINGS_RECOVERY) != 0;
			resolveBindings(res, apiLevel);
		}
		// For comparison
//		CompilationUnit res2  = CompilationUnitResolver.FACADE.toCompilationUnit(sourceUnit, initialNeedsToResolveBinding, project, classpaths, nodeSearcher, apiLevel, compilerOptions, typeRootWorkingCopyOwner, typeRootWorkingCopyOwner, flags, monitor);
//		//res.typeAndFlags=res2.typeAndFlags;
//		String res1a = res.toString();
//		String res2a = res2.toString();
//
//		AnnotationTypeDeclaration l1 = (AnnotationTypeDeclaration)res.types().get(0);
//		AnnotationTypeDeclaration l2 = (AnnotationTypeDeclaration)res2.types().get(0);
//		Object o1 = l1.bodyDeclarations().get(0);
//		Object o2 = l2.bodyDeclarations().get(0);
		return res;
	}

	private Map<org.eclipse.jdt.internal.compiler.env.ICompilationUnit, CompilationUnit> parse(org.eclipse.jdt.internal.compiler.env.ICompilationUnit[] sourceUnits, int apiLevel, Map<String, String> compilerOptions,
			int flags, IJavaProject javaProject, IProgressMonitor monitor) {
		if (sourceUnits.length == 0) {
			return Collections.emptyMap();
		}
		var compiler = ToolProvider.getSystemJavaCompiler();
		Context context = new Context();
		Map<org.eclipse.jdt.internal.compiler.env.ICompilationUnit, CompilationUnit> result = new HashMap<>(sourceUnits.length, 1.f);
		Map<JavaFileObject, CompilationUnit> filesToUnits = new HashMap<>();
		var problemConverter = new JavacProblemConverter(compilerOptions, context);
		DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
			findTargetDOM(filesToUnits, diagnostic).ifPresent(dom -> {
				var newProblem = problemConverter.createJavacProblem(diagnostic);
				if (newProblem != null) {
					IProblem[] previous = dom.getProblems();
					IProblem[] newProblems = Arrays.copyOf(previous, previous.length + 1);
					newProblems[newProblems.length - 1] = newProblem;
					dom.setProblems(newProblems);
				}
			});
		};
		// must be 1st thing added to context
		context.put(DiagnosticListener.class, diagnosticListener);
		JavacUtils.configureJavacContext(context, compilerOptions, javaProject);
		var fileManager = (JavacFileManager)context.get(JavaFileManager.class);
		List<JavaFileObject> fileObjects = new ArrayList<>(); // we need an ordered list of them
		for (var sourceUnit : sourceUnits) {
			File unitFile = new File(new String(sourceUnit.getFileName()));
			Path sourceUnitPath;
			if (!unitFile.getName().endsWith(".java") || sourceUnit.getFileName() == null || sourceUnit.getFileName().length == 0) {
				sourceUnitPath = Path.of(new File("whatever.java").toURI());
			} else {
				sourceUnitPath = Path.of(unitFile.toURI());
			}
			var fileObject = fileManager.getJavaFileObject(sourceUnitPath);
			fileManager.cache(fileObject, CharBuffer.wrap(sourceUnit.getContents()));
			AST ast = createAST(compilerOptions, apiLevel, context, flags);
			CompilationUnit res = ast.newCompilationUnit();
			result.put(sourceUnit, res);
			filesToUnits.put(fileObject, res);
			fileObjects.add(fileObject);
		}


		JCCompilationUnit javacCompilationUnit = null;
		Iterable<String> options = Arrays.asList("-proc:none"); // disable annotation processing in the parser.
		JavacTask task = ((JavacTool)compiler).getTask(null, fileManager, null /* already added to context */, options, List.of() /* already set */, fileObjects, context);
		{
			// don't know yet a better way to ensure those necessary flags get configured
			var javac = com.sun.tools.javac.main.JavaCompiler.instance(context);
			javac.keepComments = true;
			javac.genEndPos = true;
			javac.lineDebugInfo = true;
		}

		try {
			var elements = task.parse().iterator();

			Throwable cachedThrown = null;

			for (int i = 0 ; i < sourceUnits.length; i++) {
				if (elements.hasNext() && elements.next() instanceof JCCompilationUnit u) {
					javacCompilationUnit = u;
				} else {
					return Map.of();
				}
				try {
					String rawText = null;
					try {
						rawText = fileObjects.get(i).getCharContent(true).toString();
					} catch( IOException ioe) {
						// ignore
					}
					CompilationUnit res = result.get(sourceUnits[i]);
					AST ast = res.ast;
					int savedDefaultNodeFlag = ast.getDefaultNodeFlag();
					ast.setDefaultNodeFlag(ASTNode.ORIGINAL);
					JavacConverter converter = new JavacConverter(ast, javacCompilationUnit, context, rawText, JavaCore.ENABLED.equals(compilerOptions.get(JavaCore.COMPILER_DOC_COMMENT_SUPPORT)));
					converter.populateCompilationUnit(res, javacCompilationUnit);
					// javadoc problems explicitly set as they're not sent to DiagnosticListener (maybe find a flag to do it?)
					var javadocProblems = converter.javadocDiagnostics.stream()
							.map(problemConverter::createJavacProblem)
							.filter(Objects::nonNull)
							.toArray(IProblem[]::new);
					if (javadocProblems.length > 0) {
						int initialSize = res.getProblems().length;
						var newProblems = Arrays.copyOf(res.getProblems(), initialSize + javadocProblems.length);
						System.arraycopy(javadocProblems, 0, newProblems, initialSize, javadocProblems.length);
						res.setProblems(newProblems);
					}
					List<org.eclipse.jdt.core.dom.Comment> javadocComments = new ArrayList<>();
					res.accept(new ASTVisitor(true) {
						@Override
						public void postVisit(ASTNode node) { // fix some positions
							if( node.getParent() != null ) {
								if( node.getStartPosition() < node.getParent().getStartPosition()) {
									int parentEnd = node.getParent().getStartPosition() + node.getParent().getLength();
									if( node.getStartPosition() >= 0 ) {
										node.getParent().setSourceRange(node.getStartPosition(), parentEnd - node.getStartPosition());
									}
								}
							}
						}
						@Override
						public boolean visit(Javadoc javadoc) {
							javadocComments.add(javadoc);
							return true;
						}
					});
					addCommentsToUnit(javadocComments, res);
					addCommentsToUnit(converter.notAttachedComments, res);
					attachMissingComments(res, context, rawText, converter, compilerOptions);
					ast.setBindingResolver(new JavacBindingResolver(javaProject, task, context, converter));
					//
					ast.setOriginalModificationCount(ast.modificationCount()); // "un-dirty" AST so Rewrite can process it
					ast.setDefaultNodeFlag(savedDefaultNodeFlag);
				} catch (Throwable thrown) {
					if (cachedThrown == null) {
						cachedThrown = thrown;
					}
					ILog.get().error("Internal failure while parsing or converting AST for unit " + new String(sourceUnits[i].getFileName()));
					ILog.get().error(thrown.getMessage(), thrown);
				}
			}
			if (cachedThrown != null) {
				throw new RuntimeException(cachedThrown);
			}
		} catch (IOException ex) {
			ILog.get().error(ex.getMessage(), ex);
		}

		return result;
	}

	private Optional<CompilationUnit> findTargetDOM(Map<JavaFileObject, CompilationUnit> filesToUnits, Object obj) {
		if (obj == null) {
			return Optional.empty();
		}
		if (obj instanceof JavaFileObject o) {
			return Optional.ofNullable(filesToUnits.get(o));
		}
		if (obj instanceof DiagnosticSource source) {
			return findTargetDOM(filesToUnits, source.getFile());
		}
		if (obj instanceof Diagnostic diag) {
			return findTargetDOM(filesToUnits, diag.getSource());
		}
		return Optional.empty();
	}

	private AST createAST(Map<String, String> options, int level, Context context, int flags) {
		AST ast = AST.newAST(level, JavaCore.ENABLED.equals(options.get(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES)));
		ast.setFlag(flags);
		String sourceModeSetting = options.get(JavaCore.COMPILER_SOURCE);
		long sourceLevel = CompilerOptions.versionToJdkLevel(sourceModeSetting);
		if (sourceLevel == 0) {
			// unknown sourceModeSetting
			sourceLevel = ClassFileConstants.getLatestJDKLevel();
		}
		ast.scanner.sourceLevel = sourceLevel;
		String compliance = options.get(JavaCore.COMPILER_COMPLIANCE);
		long complianceLevel = CompilerOptions.versionToJdkLevel(compliance);
		if (complianceLevel == 0) {
			// unknown sourceModeSetting
			complianceLevel = sourceLevel;
		}
		ast.scanner.complianceLevel = complianceLevel;
		ast.scanner.previewEnabled = JavaCore.ENABLED.equals(options.get(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES));
		return ast;
	}

//
	/**
	 * Currently re-scans the doc to build the list of comments and then
	 * attach them to the already built AST.
	 * @param res
	 * @param context
	 * @param fileObject
	 * @param converter
	 * @param compilerOptions
	 */
	private void attachMissingComments(CompilationUnit unit, Context context, String rawText, JavacConverter converter, Map<String, String> compilerOptions) {
		ScannerFactory scannerFactory = ScannerFactory.instance(context);
		List<Comment> missingComments = new ArrayList<>();
		JavadocTokenizer commentTokenizer = new JavadocTokenizer(scannerFactory, rawText.toCharArray(), rawText.length()) {
			@Override
			protected com.sun.tools.javac.parser.Tokens.Comment processComment(int pos, int endPos, CommentStyle style) {
				// workaround Java bug 9077218
				if (style == CommentStyle.JAVADOC && endPos - pos <= 4) {
					style = CommentStyle.BLOCK;
				}
				var res = super.processComment(pos, endPos, style);
				if (noCommentAt(unit, pos)) { // not already processed
					var comment = converter.convert(res, pos, endPos);
					missingComments.add(comment);
				}
				return res;
			}
		};
		Scanner javacScanner = new Scanner(scannerFactory, commentTokenizer) {
			// subclass just to access constructor
			// TODO DefaultCommentMapper.this.scanner.linePtr == -1?
		};
		do { // consume all tokens to populate comments
			javacScanner.nextToken();
		} while (javacScanner.token() != null && javacScanner.token().kind != TokenKind.EOF);
		org.eclipse.jdt.internal.compiler.parser.Scanner ecjScanner = new ASTConverter(compilerOptions, false, null).scanner;
		ecjScanner.recordLineSeparator = true;
		ecjScanner.skipComments = false;
		try {
			ecjScanner.setSource(rawText.toCharArray());
			do {
				ecjScanner.getNextToken();
			} while (!ecjScanner.atEnd());
		} catch (InvalidInputException ex) {
			JavaCore.getPlugin().getLog().log(org.eclipse.core.runtime.Status.error(ex.getMessage(), ex));
		}

		// need to scan with ecjScanner first to populate some line indexes used by the CommentMapper
		// on longer-term, implementing an alternative comment mapper based on javac scanner might be best
		addCommentsToUnit(missingComments, unit);
		unit.initCommentMapper(ecjScanner);
	}

	static void addCommentsToUnit(Collection<Comment> comments, CompilationUnit res) {
		List<Comment> before = res.getCommentList() == null ? new ArrayList<>() : new ArrayList<>(res.getCommentList());
		comments.stream().filter(comment -> comment.getStartPosition() >= 0 && JavacCompilationUnitResolver.noCommentAt(res, comment.getStartPosition()))
			.forEach(before::add);
		before.sort(Comparator.comparingInt(Comment::getStartPosition));
		res.setCommentTable(before.toArray(Comment[]::new));
	}

	private static boolean noCommentAt(CompilationUnit unit, int pos) {
		if (unit.getCommentList() == null) {
			return true;
		}
		return ((List<Comment>)unit.getCommentList()).stream()
				.allMatch(other -> pos < other.getStartPosition() || pos >= other.getStartPosition() + other.getLength());
	}

	private static class BindingBuilder extends ASTVisitor {
		public HashMap<String, IBinding> bindingMap = new HashMap<>();

		public BindingBuilder(HashMap<String, IBinding> bindingMap) {
			this.bindingMap = bindingMap;
		}

		@Override
		public boolean visit(TypeDeclaration node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}

		@Override
		public boolean visit(EnumDeclaration node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}

		@Override
		public boolean visit(RecordDeclaration node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}

		@Override
		public boolean visit(SingleVariableDeclaration node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}

		@Override
		public boolean visit(AnnotationTypeDeclaration node) {
			IBinding binding = node.resolveBinding();
			bindingMap.putIfAbsent(binding.getKey(), binding);
			return true;
		}
	}

}
