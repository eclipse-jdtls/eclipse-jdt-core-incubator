package org.eclipse.jdt.core.dom;

interface AdditionalBindingCreator {

	/**
	 * Returns the binding for the given key if <code>ASTParser.createASTs</code> is in progress, or throws a <code>RuntimeException</code> if <code>ASTParser.createASTs</code> is not in progress.
	 *
	 * @param key the key of the binding to resolve
	 * @throws RuntimeException if <code>ASTParser.createASTs</code> is not in progress
	 * @return the binding for the given key
	 */
	IBinding createBinding(String key);

}
