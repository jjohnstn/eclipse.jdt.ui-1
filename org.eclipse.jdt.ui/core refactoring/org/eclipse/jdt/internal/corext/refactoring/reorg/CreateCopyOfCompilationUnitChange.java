/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import org.eclipse.text.edits.ReplaceEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

public class CreateCopyOfCompilationUnitChange extends CreateTextFileChange {

	private ICompilationUnit fOldCu;
	private INewNameQuery fNameQuery;
	
	public CreateCopyOfCompilationUnitChange(IPath path, String source, ICompilationUnit oldCu, INewNameQuery nameQuery) {
		super(path, source, "java"); //$NON-NLS-1$
		fOldCu= oldCu;
		fNameQuery= nameQuery;
	}

	/*
	 * @see CreateFileChange#getOldFile()
	 */
	protected IFile getOldFile(IProgressMonitor pm) {
		pm.beginTask("", 10); //$NON-NLS-1$
		String oldSource= super.getSource();
		IPath oldPath= super.getPath();
		String newTypeName= fNameQuery.getNewName();
		try {
			String newSource= getCopiedFileSource(new SubProgressMonitor(pm, 9), fOldCu, newTypeName);
			setSource(newSource);
			setPath(constructNewPath(newTypeName));
			return super.getOldFile(new SubProgressMonitor(pm, 1));
		} catch (CoreException e) {
			setSource(oldSource);
			setPath(oldPath);
			return super.getOldFile(pm);
		}
	}

	private IPath constructNewPath(String newTypeName) throws JavaModelException{
		return ResourceUtil.getResource(fOldCu).getParent().getFullPath().append(newTypeName + ".java"); //$NON-NLS-1$
	}

	private static String getCopiedFileSource(IProgressMonitor pm, ICompilationUnit cu, String newTypeName) throws CoreException {
		ICompilationUnit wc= WorkingCopyUtil.getNewWorkingCopy(cu);
		try {
			TextChangeManager manager= createChangeManager(pm, wc, newTypeName);
			String result= manager.get(wc).getPreviewContent();
			return result;
		} finally {
			wc.destroy();
		}
	}
	
	private static TextChangeManager createChangeManager(IProgressMonitor pm, ICompilationUnit wc, String newName) throws CoreException {
		TextChangeManager manager= new TextChangeManager();
		SearchResultGroup refs= getReferences(wc, pm);
		if (refs == null)
			return manager;
		if (refs.getCompilationUnit() == null)	
			return manager;
				
		String name= RefactoringCoreMessages.getString("CopyRefactoring.update_ref"); //$NON-NLS-1$
		SearchMatch[] results= refs.getSearchResults();
		for (int j= 0; j < results.length; j++){
			SearchMatch searchResult= results[j];
			if (searchResult.getAccuracy() == SearchMatch.A_INACCURATE)
				continue;
			// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=49994 , 49774
			if (SearchUtils.getEnd(searchResult) <= 1)
				continue;
			String oldName= wc.findPrimaryType().getElementName();
			int length= oldName.length();
			int offset= SearchUtils.getEnd(searchResult) - length;
			TextChangeCompatibility.addTextEdit(manager.get(wc), name, new ReplaceEdit(offset, length, newName));
		}
		return manager;
	}
	
	private static SearchResultGroup getReferences(ICompilationUnit wc, IProgressMonitor pm) throws JavaModelException{
		pm.subTask(RefactoringCoreMessages.getString("CopyRefactoring.searching")); //$NON-NLS-1$
		IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[]{wc});
		if (wc.findPrimaryType() == null)
			return null;
		SearchPattern pattern= createSearchPattern(wc.findPrimaryType());
		SearchResultGroup[] groups= RefactoringSearchEngine.search(pattern, scope, pm, new ICompilationUnit[]{wc});
		Assert.isTrue(groups.length <= 1); //just 1 file or none
		if (groups.length == 0)
			return null;
		else	
			return groups[0];
	}
	
	private static SearchPattern createSearchPattern(IType type) throws JavaModelException{
		SearchPattern pattern= SearchPattern.createPattern(type, IJavaSearchConstants.ALL_OCCURRENCES);
		IMethod[] constructors= JavaElementUtil.getAllConstructors(type);
		if (constructors.length == 0)
			return pattern;
		SearchPattern constructorDeclarationPattern= RefactoringSearchEngine.createOrPattern(constructors, IJavaSearchConstants.DECLARATIONS);
		return SearchPattern.createOrPattern(pattern, constructorDeclarationPattern);
	}

}