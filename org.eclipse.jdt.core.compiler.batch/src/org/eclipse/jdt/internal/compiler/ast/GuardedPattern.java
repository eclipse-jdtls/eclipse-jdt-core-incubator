/*******************************************************************************
 * Copyright (c) 2022, 2023 IBM Corporation and others.
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
package org.eclipse.jdt.internal.compiler.ast;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.codegen.BranchLabel;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.flow.FlowContext;
import org.eclipse.jdt.internal.compiler.flow.FlowInfo;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;

public class GuardedPattern extends Pattern {

	public Pattern[] patterns;
	public Expression condition;
	int thenInitStateIndex1 = -1;
	int thenInitStateIndex2 = -1;
	public int restrictedIdentifierStart = -1; // used only for 'when' restricted keyword.

	public GuardedPattern(Pattern patterns[], Expression conditionalAndExpression) {
		this.patterns = patterns;
		this.condition = conditionalAndExpression;
		if (patterns.length > 0) {
			this.sourceStart = this.patterns[0].sourceStart;
		} else {
			this.sourceStart = conditionalAndExpression.sourceStart;
		}
		this.sourceEnd = conditionalAndExpression.sourceEnd;
	}

	@Override
	public LocalDeclaration getPatternVariable() {
		if (this.patterns.length == 1) {
			return this.patterns[0].getPatternVariable();
		}
		return null;
	}

	@Override
	public LocalVariableBinding[] bindingsWhenTrue() {
		LocalVariableBinding[] patternsBindings = new LocalVariableBinding[] {};
		for (Pattern pattern : this.patterns) {
			patternsBindings = LocalVariableBinding.merge(patternsBindings, pattern.bindingsWhenTrue());
		}
		return LocalVariableBinding.merge(patternsBindings, this.condition.bindingsWhenTrue());
	}

	@Override
	public FlowInfo analyseCode(BlockScope currentScope, FlowContext flowContext, FlowInfo flowInfo) {
		if (this.patterns.length >= 1) {
			flowInfo = this.patterns[0].analyseCode(currentScope, flowContext, flowInfo);
			this.thenInitStateIndex1 = currentScope.methodScope().recordInitializationStates(flowInfo);
		}
		for (int i = 1; i < this.patterns.length; i++) {
			flowInfo = this.patterns[i].analyseCode(currentScope, flowContext, flowInfo);
		}
		FlowInfo mergedFlow = this.condition.analyseCode(currentScope, flowContext, flowInfo);
		mergedFlow = mergedFlow.safeInitsWhenTrue();
		this.thenInitStateIndex2 = currentScope.methodScope().recordInitializationStates(mergedFlow);
		return mergedFlow;
	}

	@Override
	public void generateOptimizedBoolean(BlockScope currentScope, CodeStream codeStream, BranchLabel trueLabel, BranchLabel falseLabel) {
		this.thenTarget = new BranchLabel(codeStream);
		this.elseTarget = new BranchLabel(codeStream);
		for (Pattern pattern : this.patterns ) {
			pattern.generateOptimizedBoolean(currentScope, codeStream, this.thenTarget, this.elseTarget);
		}
		Constant cst =  this.condition.optimizedBooleanConstant();

		setGuardedElseTarget(currentScope, this.elseTarget);
		this.condition.generateOptimizedBoolean(
				currentScope,
				codeStream,
				this.thenTarget,
				null,
				cst == Constant.NotAConstant);
		if (this.thenInitStateIndex2 != -1) {
			codeStream.removeNotDefinitelyAssignedVariables(currentScope, this.thenInitStateIndex2);
			codeStream.addDefinitelyAssignedVariables(currentScope, this.thenInitStateIndex2);
		}
	}

	private void setGuardedElseTarget(BlockScope currentScope, BranchLabel guardedElseTarget) {
		class PatternsCollector extends ASTVisitor {
			BranchLabel guardedElseTarget1;

			public PatternsCollector(BranchLabel guardedElseTarget1) {
				this.guardedElseTarget1 = guardedElseTarget1;
			}
			@Override
			public boolean visit(RecordPattern recordPattern, BlockScope scope1) {
				recordPattern.guardedElseTarget = this.guardedElseTarget1;
				return true;
			}
		}
		PatternsCollector patCollector =  new PatternsCollector(guardedElseTarget);
		this.condition.traverse(patCollector, currentScope);
	}
	@Override
	public boolean isAlwaysTrue() {
		Constant cst = this.condition.optimizedBooleanConstant();
		return cst != Constant.NotAConstant && cst.booleanValue() == true;
	}
	@Override
	public boolean coversType(TypeBinding type) {
		if (!isAlwaysTrue()) {
			return false;
		}
		for (Pattern pattern : this.patterns) {
			if (pattern.coversType(type)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean dominates(Pattern p) {
		if (isAlwaysTrue()) {
			for (Pattern pattern : this. patterns) {
				if (pattern.dominates(p)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public TypeBinding resolveType(BlockScope scope) {
		if (this.resolvedType != null || this.patterns[0] == null)
			return this.resolvedType;
		this.resolvedType = this.patterns[0].resolveType(scope);
		// The following call (as opposed to resolveType() ensures that
		// the implicitConversion code is set properly and thus the correct
		// unboxing calls are generated.
		LocalVariableBinding[] patternsBindings = new LocalVariableBinding[] {};
		for (Pattern pattern: this.patterns) {
			patternsBindings = LocalVariableBinding.merge(patternsBindings, pattern.bindingsWhenTrue());
		}
		this.condition.resolveTypeExpectingWithBindings(patternsBindings, scope, TypeBinding.BOOLEAN);
		Constant cst = this.condition.optimizedBooleanConstant();
		if (cst.typeID() == TypeIds.T_boolean && cst.booleanValue() == false) {
			scope.problemReporter().falseLiteralInGuard(this.condition);
		}
		this.condition.traverse(new ASTVisitor() {
			@Override
			public boolean visit(
					SingleNameReference ref,
					BlockScope skope) {
				LocalVariableBinding local = ref.localVariableBinding();
				if (local != null) {
					ref.bits |= ASTNode.IsUsedInPatternGuard;
				}
				return false;
			}
			@Override
			public boolean visit(
					QualifiedNameReference ref,
					BlockScope skope) {
				if ((ref.bits & ASTNode.RestrictiveFlagMASK) == Binding.LOCAL) {
					ref.bits |= ASTNode.IsUsedInPatternGuard;
				}
				return false;
			}
		}, scope);
		return this.resolvedType = this.patterns[0].resolvedType;
	}

	@Override
	public StringBuilder printExpression(int indent, StringBuilder output) {
		for (int i = 0; i < this.patterns.length; i++) {
			this.patterns[i].print(indent, output);
			if (i < this.patterns.length - 1) {
				output.append(", "); //$NON-NLS-1$
			}
		}
		output.append(" when "); //$NON-NLS-1$
		return this.condition.print(indent, output);
	}

	@Override
	public void traverse(ASTVisitor visitor, BlockScope scope) {
		if (visitor.visit(this, scope)) {
			for (Pattern pattern : this.patterns) {
				pattern.traverse(visitor, scope);
			}
			if (this.condition != null)
				this.condition.traverse(visitor, scope);
		}
		visitor.endVisit(this, scope);
	}
	@Override
	public void suspendVariables(CodeStream codeStream, BlockScope scope) {
		codeStream.removeNotDefinitelyAssignedVariables(scope, this.thenInitStateIndex1);
		if (this.patterns[0] != null) {
			this.patterns[0].suspendVariables(codeStream, scope);
		}
	}
	@Override
	public void resumeVariables(CodeStream codeStream, BlockScope scope) {
		codeStream.addDefinitelyAssignedVariables(scope, this.thenInitStateIndex2);
		if (this.patterns[0] != null) {
			this.patterns[0].resumeVariables(codeStream, scope);
		}
	}
	@Override
	protected boolean isPatternTypeCompatible(TypeBinding other, BlockScope scope) {
		return this.patterns[0].isPatternTypeCompatible(other, scope);
	}
	@Override
	public void wrapupGeneration(CodeStream codeStream) {
		this.patterns[0].wrapupGeneration(codeStream);
	}
	@Override
	public void fullWrapupGeneration(CodeStream codeStream) {
		for (Pattern pattern : this.patterns) {
			pattern.fullWrapupGeneration(codeStream);
		}
	}
	@Override
	protected void generatePatternVariable(BlockScope currentScope, CodeStream codeStream, BranchLabel trueLabel,
			BranchLabel falseLabel) {
		this.patterns[0].generatePatternVariable(currentScope, codeStream, trueLabel, falseLabel);
	}
}
