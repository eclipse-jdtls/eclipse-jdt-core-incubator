/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.ast;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.codegen.BranchLabel;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.RecordComponentBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBindingVisitor;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;

public class UnnamedPattern extends VariablePattern {

	public UnnamedPattern(LocalDeclaration local) {
		this.local = local;
		this.isTotalTypeNode = true;
	}

	@Override
	public void generateOptimizedBoolean(BlockScope currentScope, CodeStream codeStream, BranchLabel trueLabel,
			BranchLabel falseLabel) {
		this.local.generateCode(currentScope, codeStream);
		codeStream.pop();
		this.local.binding.recordInitializationStartPC(codeStream.position);
	}

	@Override
	protected void generatePatternVariable(BlockScope currentScope, CodeStream codeStream, BranchLabel trueLabel,
			BranchLabel falseLabel) {
		this.local.generateCode(currentScope, codeStream);
		codeStream.pop();
		this.local.binding.recordInitializationStartPC(codeStream.position);
	}

	@Override
	protected void wrapupGeneration(CodeStream codeStream) {
		// do nothing
	}

	@Override
	public void resolveWithExpression(BlockScope scope, Expression expression) {
		// do nothing
	}

	@Override
	public TypeBinding resolveType(BlockScope scope, boolean isPatternVariable) {
		if (this.resolvedType != null)
			return this.resolvedType;
		// copied from the isTypeNameVar case of TypePattern
		Pattern enclosingPattern = this.getEnclosingPattern();
		if (enclosingPattern instanceof RecordPattern) {
			ReferenceBinding recType = (ReferenceBinding) enclosingPattern.resolvedType;
			if (recType != null) {
				RecordComponentBinding[] components = recType.components();
				if (components.length > this.index) {
					RecordComponentBinding rcb = components[this.index];
					TypeVariableBinding[] mentionedTypeVariables = findSyntheticTypeVariables(rcb.type);
					if  (mentionedTypeVariables != null && mentionedTypeVariables.length > 0) {
						this.resolvedType = recType.upwardsProjection(scope, mentionedTypeVariables);
					} else {
						this.resolvedType = rcb.type;
					}
				}
			}
		}
		this.local.type = new FakeTypeReference(this.resolvedType);
		this.local.resolve(scope, true);
		return this.local.type.resolveType(scope, true);
	}
	private TypeVariableBinding[] findSyntheticTypeVariables(TypeBinding typeBinding) {
		final Set<TypeVariableBinding> mentioned = new HashSet<>();
		TypeBindingVisitor.visit(new TypeBindingVisitor() {
			@Override
			public boolean visit(TypeVariableBinding typeVariable) {
				if (typeVariable.isCapture())
					mentioned.add(typeVariable);
				return super.visit(typeVariable);
			}
		}, typeBinding);
		if (mentioned.isEmpty()) return null;
		return mentioned.toArray(new TypeVariableBinding[mentioned.size()]);
	}

	@Override
	protected boolean isPatternTypeCompatible(TypeBinding other, BlockScope scope) {
		return true;
	}

	@Override
	public boolean dominates(Pattern p) {
		return false;
	}

	@Override
	public StringBuffer printExpression(int indent, StringBuffer output) {
		return this.local.printAsExpression(indent, output);
	}

}

class FakeTypeReference extends TypeReference {

	public FakeTypeReference(TypeBinding resolvedType) {
		this.resolvedType = resolvedType;
	}

	@Override
	public TypeBinding resolveType(BlockScope scope, boolean augment) {
		return this.resolvedType;
	}

	@Override
	public TypeReference augmentTypeWithAdditionalDimensions(int additionalDimensions,
			Annotation[][] additionalAnnotations, boolean isVarargs) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public char[] getLastToken() {
		// shouldn't be called
		return null;
	}

	@Override
	protected TypeBinding getTypeBinding(Scope scope) {
		// shouldn't be called
		return null;
	}

	@Override
	public char[][] getTypeName() {
		// shouldn't be called
		return null;
	}

	@Override
	public void traverse(ASTVisitor visitor, BlockScope scope) {
		// do nothing
	}

	@Override
	public void traverse(ASTVisitor visitor, ClassScope scope) {
		// do nothing
	}

	@Override
	public StringBuffer printExpression(int indent, StringBuffer output) {
		return output;
	}
}