/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.util;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;

public class SearchUtils {

	/**
	 * @return the enclosing {@link IJavaElement}, or null iff none
	 */
	public static IJavaElement getEnclosingJavaElement(SearchMatch match) {
		Object element = match.getElement();
		if (element instanceof IJavaElement)
			return (IJavaElement) element;
		else
			return null;
	}
	
	/** @deprecated TODO change callers to use getOffset() + getLength() */
	public static int getEnd(SearchMatch match) {
		int offset = match.getOffset();
		int length = match.getLength();
		if (offset == -1 || length == -1)
			return -1;
		else
			return offset + length;
	}

	/**
	 * @return the enclosing {@link ICompilationUnit} of the given match, or null iff none
	 */
	public static ICompilationUnit getCompilationUnit(SearchMatch match) {
		IJavaElement enclosingElement = getEnclosingJavaElement(match);
		if (enclosingElement != null){
			if (enclosingElement instanceof ICompilationUnit)
				return (ICompilationUnit) enclosingElement;
			ICompilationUnit cu= (ICompilationUnit) enclosingElement.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (cu != null)
				return cu;
		}
		
		IJavaElement jElement= JavaCore.create(match.getResource());
		if (jElement != null && jElement.exists() && jElement.getElementType() == IJavaElement.COMPILATION_UNIT)
			return (ICompilationUnit) jElement;
		return null;
	}
	
	public static SearchParticipant[] getDefaultSearchParticipants() {
		return new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };
	}
}
