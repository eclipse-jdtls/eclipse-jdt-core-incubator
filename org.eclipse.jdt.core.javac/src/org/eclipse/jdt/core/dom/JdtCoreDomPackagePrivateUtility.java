package org.eclipse.jdt.core.dom;

public class JdtCoreDomPackagePrivateUtility {
	public static BindingResolver getBindingResolver(AST ast) {
		return ast.getBindingResolver();
	}

	public static BindingResolver getBindingResolver(ASTNode node) {
		return node.getAST().getBindingResolver();
	}

	public static JavacBindingResolver getJavacBindingResolverOrNull(ASTNode node) {
		return getJavacBindingResolverOrNull(node.getAST());
	}

	public static JavacBindingResolver getJavacBindingResolverOrNull(AST ast) {
		BindingResolver br = getBindingResolver(ast);
		if( br != null ) {
			if( br instanceof JavacBindingResolver br2 )
				return br2;
		}
		return null;
	}

	public static IBinding findBindingForType(ASTNode node, String signature) {
		return findBindingForType(node.getAST(), signature);
	}

	public static IBinding findBindingForType(AST ast, String signature) {
		JavacBindingResolver jcbr = getJavacBindingResolverOrNull(ast);
		IBinding ret1 = jcbr.findBinding(signature);
		if( ret1 == null ) {
			String sig2 = signature.replaceAll("\\.", "/");
			ret1 = jcbr.findBinding(sig2);
		}
		return ret1;
	}

	public static IBinding findUnresolvedBindingForType(ASTNode node, String signature) {
		return findUnresolvedBindingForType(node.getAST(), signature);
	}

	public static IBinding findUnresolvedBindingForType(AST ast, String signature) {
		JavacBindingResolver jcbr = getJavacBindingResolverOrNull(ast);
		IBinding ret1 = jcbr instanceof JavacBindingResolver br2 ? br2.findBinding(signature) : null;
		if( ret1 == null ) {
			ret1 = jcbr instanceof JavacBindingResolver br2 ? br2.findUnresolvedBinding(signature) : null;
		}
		return ret1;
	}


}
