/*******************************************************************************
* Copyright (c) 2025 IBM Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial API and implementation
*******************************************************************************/
package org.eclipse.jdt.internal.javac;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCNewClass;

/**
 * Collects access restriction-related problems by scanning a parsed javac AST.
 */
public class AccessRestrictionTreeScanner extends TreeScanner<Void, Void> {

	// copied from ProblemReporter
	private final static byte TYPE_ACCESS = 0x0, FIELD_ACCESS = 0x4, CONSTRUCTOR_ACCESS = 0x8, METHOD_ACCESS = 0xC;

	/**
	 * javac considers some keywords as fields for the purpose of field access
	 * nodes. ECJ doesn't report problems on these, likely to avoid adding too much
	 * noise to the results.
	 */
	private static final Set<String> UNINTERESTING_FIELDS = new HashSet<>();
	static {
		UNINTERESTING_FIELDS.add("this");
		UNINTERESTING_FIELDS.add("class");
		UNINTERESTING_FIELDS.add("super");
	}

	private INameEnvironment nameEnvironment;
	private IProblemFactory problemFactory;
	private CompilerOptions compilerOptions;

	private List<CategorizedProblem> accessRestrictionProblems = new ArrayList<>();
	private JCCompilationUnit unit = null;

	public AccessRestrictionTreeScanner(INameEnvironment nameEnvironment, IProblemFactory problemFactory,
			CompilerOptions compilerOptions) {
		this.nameEnvironment = nameEnvironment;
		this.problemFactory = problemFactory;
		this.compilerOptions = compilerOptions;
	}

	@Override
	public Void scan(Tree tree, Void p) {
		if (tree == null) {
			return super.scan(tree, p);
		}

		if (unit != null && tree instanceof JCTree jcTree) {
			Comment c = unit.docComments.getComment(jcTree);
			if (c != null
					&& (c.getStyle() == CommentStyle.JAVADOC_BLOCK || c.getStyle() == CommentStyle.JAVADOC_LINE)) {
				AccessRestrictionDocTreeScanner docTreeScanner = new AccessRestrictionDocTreeScanner(c::getSourcePos);
				var docCommentTree = unit.docComments.getCommentTree(jcTree);
				docTreeScanner.scan(docCommentTree, p);
			}
		}
		return super.scan(tree, p);
	}

	@Override
	public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
		unit = (JCCompilationUnit) node;
		return super.visitCompilationUnit(node, p);
	}

	@Override
	public Void visitMemberSelect(MemberSelectTree node, Void p) {
		JCFieldAccess fieldAccess = (JCFieldAccess) node;
		if (fieldAccess.selected.type == null || fieldAccess.selected.type.isErroneous()) {
			// symbol is not built; not much we can do
			return super.visitMemberSelect(node, p);
		}
		Symbol sym = fieldAccess.selected.type.tsym;
		String fqn = getQualifiedName(sym);

		int startPos = fieldAccess.selected.getEndPosition(this.unit.endPositions) + 1;
		int endPos = startPos + fieldAccess.name.length() - 1;

		String fieldName = fieldAccess.name.toString();

		boolean isField = false;
		if (!(sym instanceof Symbol.TypeSymbol typeSym) || typeSym.members() == null) {
			return super.visitMemberSelect(node, p);
		}
		for (Symbol elt : typeSym.getEnclosedElements()) {
			if (fieldName.equals(elt.getSimpleName().toString())) {
				if (elt instanceof Symbol.VarSymbol) {
					isField = true;
				}
			}
		}

		if (!UNINTERESTING_FIELDS.contains(fieldName)) {
			if (fieldAccess.type instanceof Type.MethodType methodType) {
				collectProblemForFQN(fqn, startPos, endPos, METHOD_ACCESS,
						toDisplayString(fieldAccess.name.toString(), methodType));
			} else if (isField) {
				collectProblemForFQN(fqn, startPos, endPos, FIELD_ACCESS, fieldAccess.name.toString());
			} else {
				collectProblemForFQN(fqn + "$" + fieldName, startPos, endPos, TYPE_ACCESS, null);
			}
		}
		return super.visitMemberSelect(node, p);
	}

	@Override
	public Void visitIdentifier(IdentifierTree node, Void p) {
		if (this.unit == null) {
			return super.visitIdentifier(node, p);
		}
		JCIdent ident = (JCIdent) node;
		String fqn = null;
		byte accessKind = TYPE_ACCESS;
		String memberName = null;
		if (ident.sym instanceof Symbol.ClassSymbol classSymbol) {
			fqn = getQualifiedName(classSymbol);
		} else if (ident.sym instanceof Symbol.MethodSymbol methodSymbol) {
			memberName = toDisplayString(methodSymbol);
			Symbol cursor = methodSymbol;
			while (cursor != null && !(cursor instanceof Symbol.ClassSymbol)) {
				cursor = cursor.owner;
			}
			if (cursor != null) {
				fqn = getQualifiedName(cursor);
			}
			accessKind = METHOD_ACCESS;
		} else if (ident.sym instanceof Symbol.VarSymbol varSymbol) {
			memberName = varSymbol.toString();
			Symbol cursor = varSymbol;
			while (cursor != null && !(cursor instanceof Symbol.ClassSymbol)) {
				cursor = cursor.owner;
			}
			if (cursor != null) {
				fqn = getQualifiedName(cursor);
			}
			accessKind = FIELD_ACCESS;
		}
		collectProblemForFQN(fqn, ident, accessKind, memberName);
		return super.visitIdentifier(node, p);
	}

	@Override
	public Void visitNewClass(NewClassTree node, Void p) {
		JCNewClass newClassNode = (JCNewClass) node;
		if (newClassNode.constructor == null
				|| !(newClassNode.constructorType instanceof Type.MethodType constructorMethodType)
				|| constructorMethodType.isErroneous()) {
			// symbol is not built; not much we can do
			return super.visitNewClass(newClassNode, p);
		}
		String fqn = getQualifiedName(newClassNode.constructor.owner);
		String simpleName = getSimpleNameFromFQN(fqn);
		collectProblemForFQN(fqn, newClassNode.getIdentifier(), CONSTRUCTOR_ACCESS,
				toDisplayString(simpleName, constructorMethodType));
		return super.visitNewClass(node, p);
	}

	private class AccessRestrictionDocTreeScanner extends com.sun.source.util.DocTreeScanner<Void, Void> {

		private Function<Integer, Integer> translateOffset;

		public AccessRestrictionDocTreeScanner(Function<Integer, Integer> translateOffset) {
			this.translateOffset = translateOffset;
		}

		@Override
		public Void visitLink(LinkTree node, Void p) {
			if (node.getReference() != null) {
				visitRef(node.getReference());
			}
			return super.visitLink(node, p);
		}

		@Override
		public Void visitSee(SeeTree node, Void p) {
			if (node.getReference() != null) {
				for (DocTree ref : node.getReference()) {
					visitRef(ref);
				}
			}
			return super.visitSee(node, p);
		}

		private void visitRef(DocTree ref) {
			if (ref instanceof com.sun.tools.javac.tree.DCTree.DCReference dcRef && dcRef.qualifierExpression != null
					&& dcRef.qualifierExpression.type != null) {
				Symbol.TypeSymbol sym = dcRef.qualifierExpression.type.tsym;
				String fqn = getQualifiedName(dcRef.qualifierExpression.type.tsym);

				int startPos = translateOffset.apply(dcRef.getStartPosition());
				int endPos = startPos + dcRef.qualifierExpression.toString().length() - 1;

				AccessRestrictionTreeScanner.this.collectProblemForFQN(fqn, startPos, endPos, TYPE_ACCESS, null);
				if (dcRef.memberName != null) {
					String memberName = dcRef.memberName.toString();
					int numParams = dcRef.paramTypes == null ? 0 : dcRef.paramTypes.size();
					Symbol.MethodSymbol methodSymbol = null;
					Symbol.VarSymbol varSymbol = null;
					for (Symbol elt : sym.getEnclosedElements()) {
						if (memberName.equals(elt.getSimpleName().toString())) {
							if (elt instanceof Symbol.MethodSymbol eltMethodSymbol
									&& eltMethodSymbol.params().length() == numParams) {
								methodSymbol = eltMethodSymbol;
								break;
							} else if (numParams == 0 && elt instanceof Symbol.VarSymbol eltVarSymbol) {
								varSymbol = eltVarSymbol;
							}
						}
					}

					int memberStartPos = endPos + 2;
					int memberEndPos = memberStartPos + dcRef.memberName.length() - 1;
					if (methodSymbol != null) {
						AccessRestrictionTreeScanner.this.collectProblemForFQN(fqn, memberStartPos, memberEndPos,
								METHOD_ACCESS, toDisplayString(methodSymbol));
					} else if (varSymbol != null) {
						AccessRestrictionTreeScanner.this.collectProblemForFQN(fqn, memberStartPos, memberEndPos,
								FIELD_ACCESS, dcRef.memberName.toString());
					}
				}
			}
		}
	}

	private void collectProblemForFQN(String fqn, JCTree node, byte accessType, String memberName) {
		int startPos = node.getStartPosition();
		int endPos = node.getEndPosition(this.unit.endPositions) - 1;
		collectProblemForFQN(fqn, startPos, endPos, accessType, memberName);
	}

	private void collectProblemForFQN(String fqn, int startPos, int endPos, byte accessType, String memberName) {
		if (fqn == null || fqn.equals(getCurrentUnitFQN())) {
			return;
		}
		if (startPos == -1) {
			// this might be a synthetic node
			return;
		}
		char[][] fqnChar = Stream.of(fqn.split("\\.")).map(String::toCharArray).toArray(char[][]::new);
		NameEnvironmentAnswer ans = nameEnvironment.findType(fqnChar);
		if (ans != null && ans.getAccessRestriction() != null) {
			AccessRestriction accessRestriction = ans.getAccessRestriction();
			this.accessRestrictionProblems
					.add(toCategorizedProblem(startPos, endPos, fqn, accessRestriction, accessType, memberName));
		}
	}

	private CategorizedProblem toCategorizedProblem(int startPos, int endPos, String fqn,
			AccessRestriction accessRestriction, byte accessType, /* Nullable */ String memberName) {
		int problemId = accessRestriction.getProblemId();
		int realProblemId = problemId & 0xFFFF;

		String simpleName = getSimpleNameFromFQN(fqn);
		int severity = toSeverity(problemId);

		int line = unit.getLineMap().getLineNumber(startPos);
		int column = unit.getLineMap().getColumnNumber(startPos);

		String[] messageArguments;
		if (memberName != null) {
			messageArguments = new String[] { accessRestriction.classpathEntryName, memberName, simpleName };
		} else {
			messageArguments = new String[] { accessRestriction.classpathEntryName, simpleName };
		}

		// elaboration ID is always based on ForbiddenReference's ID, even if it's a
		// discouraged reference
		int elaborationId = (IProblem.ForbiddenReference << 8) | (accessType | accessRestriction.classpathEntryType);

		return this.problemFactory.createProblem(unit.getSourceFile().getName().toCharArray(), //
				realProblemId, //
				new String[] { simpleName }, elaborationId, messageArguments, severity, //
				startPos, endPos, line, column);
	}

	/**
	 * Returns the collected access restriction problems.
	 *
	 * @return the collected access restriction problems
	 */
	public List<CategorizedProblem> getAccessRestrictionProblems() {
		return this.accessRestrictionProblems;
	}

	private String getCurrentUnitFQN() {
		String simpleFileName = unit.getSourceFile().getName();
		simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf('/') + 1, simpleFileName.lastIndexOf('.'));
		String currentUnitName = simpleFileName;
		if (unit.packge != null && !unit.packge.getQualifiedName().toString().isEmpty()) {
			currentUnitName = unit.packge.getQualifiedName().toString() + "." + currentUnitName;
		}
		return currentUnitName;
	}

	private int toSeverity(int jdtProblemId) {
		int irritant = ProblemReporter.getIrritant(jdtProblemId);
		if (irritant != 0) {
			int res = this.compilerOptions.getSeverity(irritant);
			res &= ~ProblemSeverities.Optional; // reject optional flag at this stage
			return res;
		}

		return ProblemSeverities.Warning;
	}

	private String toDisplayString(Symbol.MethodSymbol sym) {
		StringBuilder builder = new StringBuilder();
		builder.append(sym.name);
		builder.append('(');
		for (int i = 0; i < sym.params().length(); i++) {
			Symbol.VarSymbol param = sym.params().get(i);
			builder.append(param.type.tsym.getSimpleName());
			if (i + 1 < sym.params().length()) {
				builder.append(", ");
			}
		}
		builder.append(')');
		return builder.toString();
	}

	private static String toDisplayString(String name, Type.MethodType type) {
		StringBuilder builder = new StringBuilder();
		builder.append(name);
		builder.append('(');
		for (int i = 0; i < type.argtypes.length(); i++) {
			Type paramType = type.argtypes.get(i);
			builder.append(paramType.tsym.getSimpleName());
			if (i + 1 < type.argtypes.length()) {
				builder.append(", ");
			}
		}
		builder.append(')');
		return builder.toString();
	}

	/**
	 * Returns the fully qualified name of the given type symbol
	 *
	 * Handles differences between JDT and javac representation of inner classes.
	 *
	 * @param sym the symbol to get the fully qualified name of
	 * @return the fully qualified name as a string
	 */
	private static String getQualifiedName(Symbol sym) {
		if (sym.owner instanceof Symbol.ClassSymbol) {
			StringBuilder builder = new StringBuilder();
			builder.append(getQualifiedName(sym.owner));
			builder.append("$");
			builder.append(sym.getSimpleName().toString());
			return builder.toString();
		}
		return sym.getQualifiedName().toString();
	}

	/**
	 * Returns the simple name given the fully qualified name.
	 *
	 * Handles differences between JDT and javac representation of inner classes.
	 *
	 * @param fqn the fully qualified name
	 * @return the simple name given the fully qualified name
	 */
	private static String getSimpleNameFromFQN(String fqn) {
		// HACK: this works because if `.` is not found it returns -1,
		// which will in turn will result in a substring starting from 0,
		// which will be the same string
		String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
		simpleName = simpleName.replace('$', '.');
		return simpleName;
	}

}
