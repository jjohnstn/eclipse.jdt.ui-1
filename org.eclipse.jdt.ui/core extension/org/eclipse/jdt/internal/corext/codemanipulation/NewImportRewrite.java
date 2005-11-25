/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.corext.codemanipulation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.WildcardType;

import org.eclipse.jdt.ui.PreferenceConstants;

/**
 * The {@link NewImportRewrite} helps updating imports following a import order and on-demand imports threshold as configured by a project.
 * <p>
 * The import rewrite is created on a compilation unit and collects references to types that are added or removed. When adding imports, e.g. using
 * {@link #addImport(String)}, the import rewrite evaluates if the type can be imported and returns the a reference to the type that can be used in code.
 * This reference is either unqualified if the import could be added, or fully qualified if the import failed due to a conflict with another element of the same name.
 * </p>
 * <p>
 * On {@link #rewriteImports(CompilationUnit, IProgressMonitor)} or {@link #rewriteImports(IProgressMonitor)} the rewrite translates these descriptions into
 * text edits that can then be applied to the original source. The rewrite infrastructure tries to generate minimal text changes and only
 * works on the import statements. It is possible to combine the result of an import rewrite with the result of a {@link org.eclipse.jdt.core.dom.rewrite.ASTRewrite}
 * as long as no import statements are modified by the AST rewrite.
 * </p>
 * 
 * This class is not intended to be subclassed.
 * </p>
 * @since 3.2
 */
public final class NewImportRewrite {
	
	/**
	 * A {@link ImportRewriteContext} can optionally be used in e.g. {@link NewImportRewrite#addImport(String, ImportRewriteContext)} to
	 * give more information about the types visible in the scope. These types can be for example inherited inner types where it is
	 * unnecessary to add import statements for. 
	 * 
	 * </p>
	 * This class can be implemented by clients.
	 * </p>
	 */
	public static abstract class ImportRewriteContext {
		
		/**
		 * Result constant signaling that the given element is know in the context. 
		 */
		public final static int RES_NAME_FOUND= 1;
		
		/**
		 * Result constant signaling that the given element is not know in the context. 
		 */
		public final static int RES_NAME_UNKNOWN= 2;
		
		/**
		 * Result constant signaling that the given element is conflicting with an other element in the context. 
		 */
		public final static int RES_NAME_CONFLICT= 3;
		
		/**
		 * Kind constant specifying that the element is a type import.
		 */
		public final static int KIND_TYPE= 1;
		
		/**
		 * Kind constant specifying that the element is a static field import.
		 */
		public final static int KIND_STATIC_FIELD= 2;
		
		/**
		 * Kind constant specifying that the element is a static method import.
		 */
		public final static int KIND_STATIC_METHOD= 3;
		
		/**
		 * Searches for the given element in the context and reports if the element is known ({@link #RES_NAME_FOUND}),
		 * unknown ({@link #RES_NAME_UNKNOWN}) or if its name conflicts ({@link #RES_NAME_CONFLICT}) with an other element.
		 * @param qualifier The qualifier of the element, can be package or the qualified name of a type 
		 * @param name The simple name of the element; either a type, method or field name or * for on-demand imports.
		 * @param kind The kind of the element. Can be either {@link #KIND_TYPE}, {@link #KIND_STATIC_FIELD} or
		 * {@link #KIND_STATIC_METHOD}. Implementors should be prepared for new, currently unspecified kinds and return
		 * {@link #RES_NAME_UNKNOWN} by default.
		 * @return Returns the result of the lookup. Can be either {@link #RES_NAME_FOUND}, {@link #RES_NAME_UNKNOWN} or
		 * {@link #RES_NAME_CONFLICT}.
		 */
		public abstract int findInContext(String qualifier, String name, int kind);
	}

	/**
	 * A named preference that holds a list of semicolon separated import group names. The list specifies the preferred import order.
	 * Imports are added to the group matching their qualified name most. The empty group name groups all imports not matching
	 * any other group.
	 * Static imports are managed in separate groups. Static import group names are prefixed with a '#' character. The group called '#'
	 * matches all imports not matching any other static import group.
	 * <p>
	 * For example the import order <code>'java.util;java.net;#java.util.Math;;#'</code> specifies 5 order groups:
	 * <dl>
	 *   <li>'java.util': Grouping all import starting with 'java.util'</li>
	 *   <li>'java.net': Grouping all import starting with 'java.net'</li>
	 *   <li>'#java.util.Math': Grouping all static import starting with 'java.util.Math'</li>
	 *   <li>'': Grouping all imports not matching any other group</li>
	 *   <li>'#': Grouping all static imports not matching any other group</li>
	 * </dl>
	 * </p> 
	 * <p>
	 * Value is of type <code>String</code> representing a semicolon separated list of import group names.
	 * </p>
	 */
	public static final String IMPORTREWRITE_IMPORT_ORDER= PreferenceConstants.ORGIMPORTS_IMPORTORDER;
	
	/**
	 * A named preference that specifies the number of imports added before a star-import declaration is used.
	 * <p>
	 * Value is of type <code>String</code> representing a positive integer.
	 * </p>
	 */
	public static final String IMPORTREWRITE_ONDEMAND_THRESHOLD= PreferenceConstants.ORGIMPORTS_ONDEMANDTHRESHOLD;
	
	private static final char STATIC_PREFIX= 's';
	private static final char NORMAL_PREFIX= 'n';
	
	private final ImportRewriteContext fDefaultContext;

	private final ICompilationUnit fCompilationUnit;
	private final boolean fRestoreExistingImports;
	private final List fExistingImports;
	
	private List fAddedImports;
	private List fRemovedImports;

	private String[] fCreatedImports;
	private String[] fCreatedStaticImports;
	
	private boolean fFilterImplicitImports;
	
	/**
	 * Creates a {@link NewImportRewrite} from a {@link ICompilationUnit}. If <code>restoreExistingImports</code>
	 * is <code>true</code>, all existing imports are kept, and new imports will be inserted at best matching locations. If
	 * <code>restoreExistingImports</code> is <code>false</code>, the existing imports will be removed and only the
	 * newly added imports will be created.
	 * <p>
	 * Note that {@link #create(ICompilationUnit, boolean)} is more efficient than this method if an AST for
	 * the compilation unit is already available.
	 * </p>
	 * @param cu the compilation unit to create the imports for
	 * @param restoreExistingImports specifies if the existing imports should be kept or removed.
	 * @return the created import rewriter.
	 * @throws JavaModelException thrown when the compilation unit could not be accessed.
	 */
	public static NewImportRewrite create(ICompilationUnit cu, boolean restoreExistingImports) throws JavaModelException {
		if (cu == null) {
			throw new IllegalArgumentException("Compilation unit must not be null"); //$NON-NLS-1$
		}
		List existingImport= null;
		if (restoreExistingImports) {
			existingImport= new ArrayList();
			IImportDeclaration[] imports= cu.getImports();
			for (int i= 0; i < imports.length; i++) {
				IImportDeclaration curr= imports[i];
				char prefix= Flags.isStatic(curr.getFlags()) ? STATIC_PREFIX : NORMAL_PREFIX;			
				existingImport.add(prefix + curr.getElementName());
			}
		}
		return new NewImportRewrite(cu, existingImport);
	}
	
	/**
	 * Creates a {@link NewImportRewrite} from a an AST ({@link CompilationUnit}). The AST has to be created from a
	 * {@link ICompilationUnit}, that means {@link ASTParser#setSource(ICompilationUnit)} has been used when creating the
	 * AST. If <code>restoreExistingImports</code> is <code>true</code>, all existing imports are kept, and new imports
	 * will be inserted at best matching locations. If <code>restoreExistingImports</code> is <code>false</code>, the
	 * existing imports will be removed and only the newly added imports will be created.
	 * <p>
	 * Note that this method is more efficient than using {@link #create(ICompilationUnit, boolean)} if an AST is already available.
	 * </p>
	 * @param astRoot the AST root node to create the imports for
	 * @param restoreExistingImports specifies if the existing imports should be kept or removed.
	 * @return the created import rewriter.
	 * @throws IllegalArgumentException thrown when the passed AST is null or was not created from a compilation unit.
	 */
	public static NewImportRewrite create(CompilationUnit astRoot, boolean restoreExistingImports) {
		if (astRoot == null) {
			throw new IllegalArgumentException("AST must not be null"); //$NON-NLS-1$
		}
		if (!(astRoot.getJavaElement() instanceof ICompilationUnit)) {
			throw new IllegalArgumentException("AST must have been constructed from a Java element"); //$NON-NLS-1$
		}
		List existingImport= null;
		if (restoreExistingImports) {
			existingImport= new ArrayList();
			List imports= astRoot.imports();
			for (int i= 0; i < imports.size(); i++) {
				ImportDeclaration curr= (ImportDeclaration) imports.get(i);
				StringBuffer buf= new StringBuffer();
				buf.append(curr.isStatic() ? STATIC_PREFIX : NORMAL_PREFIX).append(curr.getName().getFullyQualifiedName());
				if (curr.isOnDemand()) {
					if (buf.length() > 1)
						buf.append('.');
					buf.append('*');
				}
				existingImport.add(buf.toString());
			}
		}
		return new NewImportRewrite((ICompilationUnit) astRoot.getJavaElement(), existingImport);
	}
		
	private NewImportRewrite(ICompilationUnit cu, List existingImports) {
		fCompilationUnit= cu;
		if (existingImports != null) {
			fExistingImports= existingImports;
			fRestoreExistingImports= !existingImports.isEmpty();
		} else {
			fExistingImports= new ArrayList();
			fRestoreExistingImports= false;
		}
		fFilterImplicitImports= true;

		fDefaultContext= new ImportRewriteContext() {
			public int findInContext(String qualifier, String name, int kind) {
				return findInImports(qualifier, name, kind);
			}
		};
		fAddedImports= null; // Initialized on use
		fRemovedImports= null; // Initialized on use
		fCreatedImports= null;
		fCreatedStaticImports= null;
	}
	
	/**
	 * The compilation unit for which this import rewrite was created for.
	 * @return the compilation unit for which this import rewrite was created for.
	 */
	public ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;
	}

	/**
	 * Returns the default rewrite context that only knows about the imported types. Clients
	 * can write their own context and use the default context for the default behavior.
	 * @return the default import rewrite context.
	 */
	public ImportRewriteContext getDefaultImportRewriteContext() {
		return fDefaultContext;
	}
	
	/**
	 * Specifies that implicit imports (types in default package, package <code>java.lang</code> or
	 * in the same package as the rewrite compilation unit should not be created except if necessary
	 * to resolve an on-demand import conflict. The filter is enabled by default.
	 * @param filterImplicitImports if set, implicit imports will be filtered.
	 */
	public void setFilterImplicitImports(boolean filterImplicitImports) {
		fFilterImplicitImports= filterImplicitImports;
	}
	
	private static int compareImport(char prefix, String qualifier, String name, String curr) {
		if (curr.charAt(0) != prefix || !curr.endsWith(name)) {
			return ImportRewriteContext.RES_NAME_UNKNOWN;
		}
		
		curr= curr.substring(1); // remove the prefix
		
		if (curr.length() == name.length()) {
			if (qualifier.length() == 0) {
				return ImportRewriteContext.RES_NAME_FOUND;
			}
			return ImportRewriteContext.RES_NAME_CONFLICT; 
		}
		// at this place: curr.length > name.length
		
		int dotPos= curr.length() - name.length() - 1;
		if (curr.charAt(dotPos) != '.') {
			return ImportRewriteContext.RES_NAME_UNKNOWN;
		}
		if (qualifier.length() != dotPos || !curr.startsWith(qualifier)) {
			return ImportRewriteContext.RES_NAME_CONFLICT; 
		}
		return ImportRewriteContext.RES_NAME_FOUND; 
	}
	
	private int findInImports(String qualifier, String name, int kind) {
		boolean allowAmbiguity=  (kind == ImportRewriteContext.KIND_STATIC_METHOD) || (name.length() == 1 && name.charAt(0) == '*');
		List imports= fExistingImports;
		char prefix= (kind == ImportRewriteContext.KIND_TYPE) ? NORMAL_PREFIX : STATIC_PREFIX;
		
		for (int i= imports.size() - 1; i >= 0 ; i--) {
			String curr= (String) imports.get(i);
			int res= compareImport(prefix, qualifier, name, curr);
			if (res != ImportRewriteContext.RES_NAME_UNKNOWN) {
				if (!allowAmbiguity || res == ImportRewriteContext.RES_NAME_FOUND) {
					return res;
				}
			}
		}
		return ImportRewriteContext.RES_NAME_UNKNOWN;
	}

	/**
	 * Adds a new import to the rewriter's record and returns a {@link Type} node that can be used
	 * in the code as a reference to the type. The type binding can be an array binding, type variable or wildcard.
	 * If the binding is a generic type, the type parameters are ignored. For parameterized types, also the type
	 * arguments are processed and imports added if necessary. Anonymous types inside type arguments are normalized to their base type, wildcard
	 * of wildcards are ignored.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param typeSig the signature of the type to be added.
	 * @param ast the AST to create the returned type for.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public Type addImportFromSignature(String typeSig, AST ast) {
		return addImportFromSignature(typeSig, ast, fDefaultContext);
	}
	
	/**
	 * Adds a new import to the rewriter's record and returns a {@link Type} node that can be used
	 * in the code as a reference to the type. The type binding can be an array binding, type variable or wildcard.
	 * If the binding is a generic type, the type parameters are ignored. For parameterized types, also the type
	 * arguments are processed and imports added if necessary. Anonymous types inside type arguments are normalized to their base type, wildcard
	 * of wildcards are ignored.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param typeSig the signature of the type to be added.
	 * @param ast the AST to create the returned type for.
	 * @param context an optional context that knows about types visible in the current scope or <code>null</code>
	 * to use the default context only using the available imports.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public Type addImportFromSignature(String typeSig, AST ast, ImportRewriteContext context) {	
		if (typeSig == null || typeSig.length() == 0) {
			throw new IllegalArgumentException("Invalid type signature: empty or null"); //$NON-NLS-1$
		}
		int sigKind= Signature.getTypeSignatureKind(typeSig);
		switch (sigKind) {
			case Signature.BASE_TYPE_SIGNATURE:
				return ast.newPrimitiveType(PrimitiveType.toCode(Signature.toString(typeSig)));
			case Signature.ARRAY_TYPE_SIGNATURE:
				Type elementType= addImportFromSignature(Signature.getElementType(typeSig), ast);
				return ast.newArrayType(elementType, Signature.getArrayCount(typeSig));
			case Signature.CLASS_TYPE_SIGNATURE:
				String erasureSig= Signature.getTypeErasure(typeSig);

				String erasureName= Signature.toString(erasureSig);
				if (erasureSig.charAt(0) == Signature.C_RESOLVED) {
					erasureName= internalAddImport(erasureName, context);
				}
				Type baseType= ast.newSimpleType(ast.newName(erasureName));
				String[] typeArguments= Signature.getTypeArguments(typeSig);
				if (typeArguments.length > 0) {
					ParameterizedType type= ast.newParameterizedType(baseType);
					List argNodes= type.typeArguments();
					for (int i= 0; i < typeArguments.length; i++) {
						argNodes.add(addImportFromSignature(typeArguments[i], ast));
					}
					return type;
				}
				return baseType;
			case Signature.TYPE_VARIABLE_SIGNATURE:
				return ast.newSimpleType(ast.newSimpleName(Signature.toString(typeSig)));
			case Signature.WILDCARD_TYPE_SIGNATURE:
				WildcardType wildcardType= ast.newWildcardType();
				char ch= typeSig.charAt(0);
				if (ch != Signature.C_STAR) {
					Type bound= addImportFromSignature(typeSig.substring(1), ast);
					wildcardType.setBound(bound, ch == Signature.C_EXTENDS);
				}
				return wildcardType;
			case Signature.CAPTURE_TYPE_SIGNATURE:
				return addImportFromSignature(typeSig.substring(1), ast);
			default:
				throw new IllegalArgumentException("Unknown type signature kind: " + typeSig); //$NON-NLS-1$
		}
	}
	
	/**
	 * Adds a new import to the rewriter's record and returns a type reference that can be used
	 * in the code. The type binding can be an array binding, type variable or wildcard.
	 * If the binding is a generic type, the type parameters are ignored. For parameterized types, also the type
	 * arguments are processed and imports added if necessary. Anonymous types inside type arguments are normalized to their base type, wildcard
	 * of wildcards are ignored. 
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param binding the signature of the type to be added.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public String addImport(ITypeBinding binding) {
		return addImport(binding, fDefaultContext);
	}
		
	/**
	 * Adds a new import to the rewriter's record and returns a type reference that can be used
	 * in the code. The type binding can be an array binding, type variable or wildcard.
	 * If the binding is a generic type, the type parameters are ignored. For parameterized types, also the type
	 * arguments are processed and imports added if necessary. Anonymous types inside type arguments are normalized to their base type, wildcard
	 * of wildcards are ignored.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param binding the signature of the type to be added.
	 * @param context an optional context that knows about types visible in the current scope or <code>null</code>
	 * to use the default context only using the available imports.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public String addImport(ITypeBinding binding, ImportRewriteContext context) {
		if (binding.isPrimitive() || binding.isTypeVariable()) {
			return binding.getName();
		}
		
		ITypeBinding normalizedBinding= normalizeTypeBinding(binding);
		if (normalizedBinding == null) {
			return "invalid"; //$NON-NLS-1$
		}
		if (normalizedBinding.isWildcardType()) {
			StringBuffer res= new StringBuffer("?"); //$NON-NLS-1$
			ITypeBinding bound= normalizedBinding.getBound();
			if (bound != null && !bound.isWildcardType() && !bound.isCapture()) { // bug 95942
				if (normalizedBinding.isUpperbound()) {
					res.append(" extends "); //$NON-NLS-1$
				} else {
					res.append(" super "); //$NON-NLS-1$
				}
				res.append(addImport(bound));
			}
			return res.toString();
		}
		
		if (normalizedBinding.isArray()) {
			StringBuffer res= new StringBuffer(addImport(normalizedBinding.getElementType()));
			for (int i= normalizedBinding.getDimensions(); i > 0; i--) {
				res.append("[]"); //$NON-NLS-1$
			}
			return res.toString();
		}
	
		String qualifiedName= getRawQualifiedName(normalizedBinding);
		if (qualifiedName.length() > 0) {
			String str= internalAddImport(qualifiedName, context);
			
			ITypeBinding[] typeArguments= normalizedBinding.getTypeArguments();
			if (typeArguments.length > 0) {
				StringBuffer res= new StringBuffer(str);
				res.append('<');
				for (int i= 0; i < typeArguments.length; i++) {
					if (i > 0) {
						res.append(','); 
					}
					res.append(addImport(typeArguments[i]));
				}
				res.append('>');
				return res.toString();
			}
			return str;
		}
		return getRawName(normalizedBinding);
	}
	
	private static ITypeBinding normalizeTypeBinding(ITypeBinding binding) {
		if (binding != null && !binding.isNullType() && !"void".equals(binding.getName())) { //$NON-NLS-1$
			if (binding.isAnonymous()) {
				ITypeBinding[] baseBindings= binding.getInterfaces();
				if (baseBindings.length > 0) {
					return baseBindings[0];
				}
				return binding.getSuperclass();
			}
			if (binding.isCapture()) {
				return binding.getWildcard();
			}
			return binding;
		}
		return null;
	}
	
	/**
	 * Adds a new import to the rewriter's record and returns a {@link Type} that can be used
	 * in the code. The type binding can be an array binding, type variable or wildcard.
	 * If the binding is a generic type, the type parameters are ignored. For parameterized types, also the type
	 * arguments are processed and imports added if necessary. Anonymous types inside type arguments are normalized to their base type, wildcard
	 * of wildcards are ignored.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param binding the signature of the type to be added.
	 * @param ast the AST to create the returned type for.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public Type addImport(ITypeBinding binding, AST ast) {
		return addImport(binding, ast, fDefaultContext);
	}
	
	/**
	 * Adds a new import to the rewriter's record and returns a {@link Type} that can be used
	 * in the code. The type binding can be an array binding, type variable or wildcard.
	 * If the binding is a generic type, the type parameters are ignored. For parameterized types, also the type
	 * arguments are processed and imports added if necessary. Anonymous types inside type arguments are normalized to their base type, wildcard
	 * of wildcards are ignored. 
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param binding the signature of the type to be added.
	 * @param ast the AST to create the returned type for.
	 * @param context an optional context that knows about types visible in the current scope or <code>null</code>
	 * to use the default context only using the available imports.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public Type addImport(ITypeBinding binding, AST ast, ImportRewriteContext context) {
		if (binding.isPrimitive()) {
			return ast.newPrimitiveType(PrimitiveType.toCode(binding.getName()));
		}
		
		ITypeBinding normalizedBinding= normalizeTypeBinding(binding);
		if (normalizedBinding == null) {
			return ast.newSimpleType(ast.newSimpleName("invalid")); //$NON-NLS-1$
		}
		
		if (normalizedBinding.isTypeVariable()) {
			// no import
			return ast.newSimpleType(ast.newSimpleName(binding.getName()));
		}
		if (normalizedBinding.isWildcardType()) {
			WildcardType wcType= ast.newWildcardType();
			ITypeBinding bound= normalizedBinding.getBound();
			if (bound != null && !bound.isWildcardType() && !bound.isCapture()) { // bug 96942
				Type boundType= addImport(bound, ast);
				wcType.setBound(boundType, normalizedBinding.isUpperbound());
			}
			return wcType;
		}
		
		if (normalizedBinding.isArray()) {
			Type elementType= addImport(normalizedBinding.getElementType(), ast);
			return ast.newArrayType(elementType, normalizedBinding.getDimensions());
		}
		
		String qualifiedName= getRawQualifiedName(normalizedBinding);
		if (qualifiedName.length() > 0) {
			String res= internalAddImport(qualifiedName, context);
			
			ITypeBinding[] typeArguments= normalizedBinding.getTypeArguments();
			if (typeArguments.length > 0) {
				Type erasureType= ast.newSimpleType(ast.newName(res));
				ParameterizedType paramType= ast.newParameterizedType(erasureType);
				List arguments= paramType.typeArguments();
				for (int i= 0; i < typeArguments.length; i++) {
					arguments.add(addImport(typeArguments[i], ast));
				}
				return paramType;
			}
			return ast.newSimpleType(ast.newName(res));
		}
		return ast.newSimpleType(ast.newName(getRawName(normalizedBinding)));
	}

	
	/**
	 * Adds a new import to the rewriter's record and returns a type reference that can be used
	 * in the code. The type binding can only be an array or non-generic type.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param qualifiedTypeName the qualified type name of the type to be added
	 * @param context an optional context that knows about types visible in the current scope or <code>null</code>
	 * to use the default context only using the available imports.
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public String addImport(String qualifiedTypeName, ImportRewriteContext context) {
		int angleBracketOffset= qualifiedTypeName.indexOf('<');
		if (angleBracketOffset != -1) {
			return internalAddImport(qualifiedTypeName.substring(0, angleBracketOffset), context) + qualifiedTypeName.substring(angleBracketOffset);
		}
		int bracketOffset= qualifiedTypeName.indexOf('[');
		if (bracketOffset != -1) {
			return internalAddImport(qualifiedTypeName.substring(0, bracketOffset), context) + qualifiedTypeName.substring(bracketOffset);
		}
		return internalAddImport(qualifiedTypeName, context);
	}
	
	/**
	 * Adds a new import to the rewriter's record and returns a type reference that can be used
	 * in the code. The type binding can only be an array or non-generic type.
	 * 	<p>
 	 * No imports are added for types that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param qualifiedTypeName the qualified type name of the type to be added
	 * @return returns a type to which the type binding can be assigned to. The returned type contains is unqualified
	 * when an import could be added or was already known. It is fully qualified, if an import conflict prevented the import.
	 */
	public String addImport(String qualifiedTypeName) {
		return addImport(qualifiedTypeName, fDefaultContext);
	}
	
	/**
	 * Adds a new static import to the rewriter's record and returns a reference that can be used in the code. The reference will
	 * be fully qualified if an import conflict prevented the import or unqualified if the import succeeded or was already
	 * existing.
	 * 	<p>
 	 * No imports are added for members that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param binding The binding of the static field or method to be added.
	 * @return returns either the simple member name if the import was successful or else the qualified name if
	 * an import conflict prevented the import.
	 * @throws IllegalArgumentException an {@link IllegalArgumentException} is thrown if the binding is not a static field
	 * or method.
	 */
	public String addStaticImport(IBinding binding) {
		if (Modifier.isStatic(binding.getModifiers())) {
			if (binding instanceof IVariableBinding) {
				IVariableBinding variableBinding= (IVariableBinding) binding;
				if (variableBinding.isField()) {
					ITypeBinding declaringType= variableBinding.getDeclaringClass();
					return addStaticImport(getRawQualifiedName(declaringType), binding.getName(), true);
				}
			} else if (binding instanceof IMethodBinding) {
				ITypeBinding declaringType= ((IMethodBinding) binding).getDeclaringClass();
				return addStaticImport(getRawQualifiedName(declaringType), binding.getName(), false);
			}
		}
		throw new IllegalArgumentException("Binding must be a static field or method."); //$NON-NLS-1$
	}
	
	/**
	 * Adds a new static import to the rewriter's record and returns a reference that can be used in the code. The reference will
	 * be fully qualified if an import conflict prevented the import or unqualified if the import succeeded or was already
	 * existing.
	 * 	<p>
 	 * No imports are added for members that are already known. If a import for a type is recorded to be removed, this record is discarded instead.
	 * </p>
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been added.
	 * </p>
	 * @param declaringTypeName The qualified name of the static's member declaring type
	 * @param simpleName the simple name of the member; either a field or a method name.
	 * @param isField <code>true</code> specifies that the member is a field, <code>false</code> if it is a
	 * method.
	 * @return returns either the simple member name if the import was successful or else the qualified name if
	 * an import conflict prevented the import.
	 */
	public String addStaticImport(String declaringTypeName, String simpleName, boolean isField) {
		if (declaringTypeName.indexOf('.') == -1) {
			return declaringTypeName + '.' + simpleName;
		}
		int kind= isField ? ImportRewriteContext.KIND_STATIC_FIELD : ImportRewriteContext.KIND_STATIC_METHOD;
		int res= fDefaultContext.findInContext(declaringTypeName, simpleName, kind);
		if (res == ImportRewriteContext.RES_NAME_CONFLICT) {
			return declaringTypeName + '.' + simpleName;
		}
		if (res == ImportRewriteContext.RES_NAME_UNKNOWN) {
			addEntry(STATIC_PREFIX + declaringTypeName + '.' + simpleName);
		}
		return simpleName;
	}
	
	private String internalAddImport(String fullTypeName, ImportRewriteContext context) {
		int idx= fullTypeName.lastIndexOf('.');	
		String typeContainerName, typeName;
		if (idx != -1) {
			typeContainerName= fullTypeName.substring(0, idx);
			typeName= fullTypeName.substring(idx + 1);
		} else {
			typeContainerName= ""; //$NON-NLS-1$
			typeName= fullTypeName;
		}
		
		if (typeContainerName.length() == 0 && PrimitiveType.toCode(typeName) != null) {
			return fullTypeName;
		}
		
		int res= context.findInContext(typeContainerName, typeName, ImportRewriteContext.KIND_TYPE);
		if (res == ImportRewriteContext.RES_NAME_CONFLICT) {
			return fullTypeName;
		}
		if (res == ImportRewriteContext.RES_NAME_UNKNOWN) {
			addEntry(NORMAL_PREFIX + fullTypeName);
		}
		return typeName;
	}
	
	private void addEntry(String entry) {
		fExistingImports.add(entry);
		
		if (fRemovedImports != null) {
			if (fRemovedImports.remove(entry)) {
				return;
			}
		}
		
		if (fAddedImports == null) {
			fAddedImports= new ArrayList();
		}
		fAddedImports.add(entry);
	}
	
	private boolean removeEntry(String entry) {
		if (fExistingImports.remove(entry)) {
			if (fAddedImports != null) {
				if (fAddedImports.remove(entry)) {
					return true;
				}
			}
			if (fRemovedImports == null) {
				fRemovedImports= new ArrayList();
			}
			fRemovedImports.add(entry);
			return true;
		}
		return false;
	}
	
	/**
	 * Records to remove a import. No remove is recorded if no such import exists or if such an import is recorded
	 * to be added. In that case the record of the addition is discarded.
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that an import has been removed.
	 * </p>
	 * @param qualifiedName The import name to remove.
	 * @return <code>true</code> is returned of an import of the given name could be found.
	 */
	public boolean removeImport(String qualifiedName) {
		return removeEntry(NORMAL_PREFIX + qualifiedName);
	}
		
	/**
	 * Records to remove a static import. No remove is recorded if no such import exists or if such an import is recorded
	 * to be added. In that case the record of the addition is discarded.
	 * <p>
	 * The content of the compilation unit itself is actually not modified
	 * in any way by this method; rather, the rewriter just records that a new import has been removed.
	 * </p>
	 * @param qualifiedName The import name to remove.
	 * @return <code>true</code> is returned of an import of the given name could be found.
	 */
	public boolean removeStaticImport(String qualifiedName) {
		return removeEntry(STATIC_PREFIX + qualifiedName);
	}	
	
	private static String getRawName(ITypeBinding normalizedBinding) {
		return normalizedBinding.getTypeDeclaration().getName();
	}

	private static String getRawQualifiedName(ITypeBinding normalizedBinding) {
		return normalizedBinding.getTypeDeclaration().getQualifiedName();
	}
	

	/**
	 * Converts all modifications recorded by this rewriter into an object representing the corresponding text
	 * edits to the source code of the rewrite's compilation unit. The compilation unit itself is not modified.
	 * <p>
	 * Calling this methods does not discard the modifications on record. Subsequence modifications are added
	 * to the ones already on record. If this method is called again later, the resulting text edit object will accurately
	 * reflect the net cumulative affect of all those changes.
	 * </p>
	 * <p>
	 * Note that it is more efficient to use {@link #rewriteImports(CompilationUnit, IProgressMonitor)} if a
	 * AST of the current compilation unit is already available.
	 * </p>
	 * @param monitor the progress monitor or <code>null</code>
	 * @return text edit object describing the changes to the document corresponding to the changes
	 * recorded by this rewriter
	 * @throws CoreException the exception is thrown if the rewrite fails.
	 */
	public final TextEdit rewriteImports(IProgressMonitor monitor) throws CoreException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}
		
		//TODO translate when in jdt.core
		monitor.beginTask("Updating imports", 2);  //$NON-NLS-1$
		try {
			if (!hasRecordedChanges()) {
				fCreatedImports= new String[0];
				fCreatedStaticImports= new String[0];
				return new MultiTextEdit();
			}
			ASTParser parser= ASTParser.newParser(AST.JLS3);
			parser.setSource(fCompilationUnit);
			parser.setFocalPosition(0); // reduced AST
			parser.setResolveBindings(false);
			CompilationUnit astRoot= (CompilationUnit) parser.createAST(new SubProgressMonitor(monitor, 1));
			return rewriteImports(astRoot, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * Converts all modifications recorded by this rewriter into an object representing the corresponding text
	 * edits to the source code of the rewrite's compilation unit. The compilation unit itself is not modified.
	 * <p>
	 * Calling this methods does not discard the modifications on record. Subsequence modifications are added
	 * to the ones already on record. If this method is called again later, the resulting text edit object will accurately
	 * reflect the net cumulative affect of all those changes.
	 * </p>
	 * <p>
	 * Note that this method is more efficient than using {@link #rewriteImports(IProgressMonitor)}. The AST has to
	 * be created from the rewrite's compilation unit ({@link ASTParser#setSource(ICompilationUnit)} has been used to create the AST) and must
	 * contain at least the package statement, imports and top level type declarations.
	 * </p>
	 * @param astRoot The AST created from the rewriter's compilation unit (see {@link #getCompilationUnit()}) containing
	 * at least the package statement, imports and top level type declarations. 
	 * @param monitor the progress monitor or <code>null</code>
	 * @return text edit object describing the changes to the document corresponding to the changes
	 * recorded by this rewriter
	 * @throws CoreException the exception is thrown if the rewrite failed.
	 */
	public final TextEdit rewriteImports(CompilationUnit astRoot, IProgressMonitor monitor) throws CoreException {
		if (!fCompilationUnit.equals(astRoot.getJavaElement())) {
			throw new IllegalArgumentException("AST is not from the compilation unit that was used when creating the import rewrite."); //$NON-NLS-1$
		}
		
		if (!hasRecordedChanges()) {
			fCreatedImports= new String[0];
			fCreatedStaticImports= new String[0];
			return new MultiTextEdit();
		}
		
		IJavaProject project= fCompilationUnit.getJavaProject();
		String[] order= getImportOrderPreference(project);
		int threshold= getImportNumberThreshold(project);
		
		ImportRewriteComputer computer= new ImportRewriteComputer(fCompilationUnit, astRoot, order, threshold, fRestoreExistingImports);
		computer.setFilterImplicitImports(fFilterImplicitImports);
		
		if (fAddedImports != null) {
			for (int i= 0; i < fAddedImports.size(); i++) {
				String curr= (String) fAddedImports.get(i);
				computer.addImport(curr.substring(1), STATIC_PREFIX == curr.charAt(0));
			}
		}
		
		if (fRemovedImports != null) {
			for (int i= 0; i < fRemovedImports.size(); i++) {
				String curr= (String) fRemovedImports.get(i);
				computer.removeImport(curr.substring(1), STATIC_PREFIX == curr.charAt(0));
			}
		}
			
		TextEdit result= computer.getResultingEdits(monitor);
		fCreatedImports= computer.getCreatedImports();
		fCreatedStaticImports= computer.getCreatedStaticImports();
		return result;
	}
	
	/**
	 * Returns all new non-static imports created by the last invocation of {@link #rewriteImports(CompilationUnit, IProgressMonitor)} or {@link #rewriteImports(IProgressMonitor)}
	 * or <code>null</code> if these methods have not been called yet.
	 * <p>
	 * 	Note that this list doesn't need to be the same as the added imports (see {@link #getAddedImports()}) as
	 * implicit imports are not created and some imports are represented by on-demand imports instead.
	 * </p>
	 * @return the created imports
	 */
	public String[] getCreatedImports() {
		return fCreatedImports;
	}
	
	/**
	 * Returns all new static imports created by the last invocation of {@link #rewriteImports(CompilationUnit, IProgressMonitor)} or {@link #rewriteImports(IProgressMonitor)}
	 * or <code>null</code> if these methods have not been called yet.
	 * <p>
	 * Note that this list doesn't need to be the same as the added static imports ({@link #getAddedStaticImports()}) as
	 * implicit imports are not created and some imports are represented by on-demand imports instead.
	 * </p
	 * @return the created imports
	 */
	public String[] getCreatedStaticImports() {
		return fCreatedStaticImports;
	}
	
	/**
	 * Returns all non-static imports that are recorded to be added.
	 * 
	 * @return the imports recorded to be added.
	 */
	public String[] getAddedImports() {
		return filterFromList(fAddedImports, NORMAL_PREFIX);
	}
	
	/**
	 * Returns all static imports that are recorded to be added.
	 * 
	 * @return the static imports recorded to be added.
	 */
	public String[] getAddedStaticImports() {
		return filterFromList(fAddedImports, STATIC_PREFIX);
	}
	
	/**
	 * Returns all non-static imports that are recorded to be removed.
	 * 
	 * @return the imports recorded to be removed.
	 */
	public String[] getRemovedImports() {
		return filterFromList(fRemovedImports, NORMAL_PREFIX);
	}
	
	/**
	 * Returns all static imports that are recorded to be removed.
	 * 
	 * @return the static imports recorded to be removed.
	 */
	public String[] getRemovedStaticImports() {
		return filterFromList(fRemovedImports, STATIC_PREFIX);
	}
	
	/**
	 * Returns <code>true</code> if imports have been recorded to be added or removed.
	 * @return boolean returns if any changes to imports have been recorded.
	 */
	public boolean hasRecordedChanges() {
		return !fRestoreExistingImports ||
			(fAddedImports != null && !fAddedImports.isEmpty()) ||
			(fRemovedImports != null && !fRemovedImports.isEmpty());
	}
	
	
	private static String[] filterFromList(List imports, char prefix) {
		if (imports == null) {
			return new String[0];
		}
		ArrayList res= new ArrayList();
		for (int i= 0; i < imports.size(); i++) {
			String curr= (String) imports.get(i);
			if (prefix == curr.charAt(0)) {
				res.add(curr.substring(1));
			}
		}
		return (String[]) res.toArray(new String[res.size()]);
	}
	
	private static int getImportNumberThreshold(IJavaProject project) {
		// TODO: Use jdt.core preferences
		//Object threshold= project.getOption(IMPORTS_ONDEMAND_THRESHOLD, true);
		Object threshold= PreferenceConstants.getPreference(IMPORTREWRITE_ONDEMAND_THRESHOLD, project);
		if (threshold instanceof String) {
			try {
				return Integer.parseInt((String) threshold);
			} catch (NumberFormatException e) {		
			}
		}
		return 999;
	}
	
	private static String[] getImportOrderPreference(IJavaProject project) {
		// TODO: Use jdt.core preferences
		//Object threshold= project.getOption(IMPORTS_ORDER, true);
		Object order= PreferenceConstants.getPreference(IMPORTREWRITE_IMPORT_ORDER, project);
		if (order instanceof String) {
			return ((String) order).split(String.valueOf(';'));
		}
		return new String[0];
	}
		
}
