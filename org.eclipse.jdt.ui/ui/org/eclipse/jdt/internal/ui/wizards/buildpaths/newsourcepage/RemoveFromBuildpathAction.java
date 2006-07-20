/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ISetSelectionTarget;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.buildpath.BuildpathDelta;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.ClasspathModifierQueries.IRemoveLinkedFolderQuery;

//SelectedElements iff enabled: IPackageFramgentRoot || IJavaProject || ClassPathContainer
public class RemoveFromBuildpathAction extends BuildpathModifierAction {

	private final IRunnableContext fContext;

	public RemoveFromBuildpathAction(IWorkbenchSite site) {
		this(site, null, PlatformUI.getWorkbench().getProgressService());
		
		setImageDescriptor(JavaPluginImages.DESC_ELCL_REMOVE_FROM_BP);
	}
	
	public RemoveFromBuildpathAction(IRunnableContext context, ISetSelectionTarget selectionTarget) {
		this(null, selectionTarget, context);
		
		setImageDescriptor(JavaPluginImages.DESC_ELCL_REMOVE_AS_SOURCE_FOLDER);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_REMOVE_AS_SOURCE_FOLDER);
    }

	public RemoveFromBuildpathAction(IWorkbenchSite site, ISetSelectionTarget selectionTarget, IRunnableContext context) {
		super(site, selectionTarget, BuildpathModifierAction.REMOVE_FROM_BP);
		
		fContext= context;

		setText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_RemoveFromCP_label);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_RemoveFromCP_tooltip);
    }
	
	/**
	 * {@inheritDoc}
	 */
	public String getDetailedDescription() {
		if (!isEnabled())
			return null;
		
		if (getSelectedElements().size() != 1)
			return NewWizardMessages.PackageExplorerActionGroup_FormText_Default_FromBuildpath; 
		
		Object elem= getSelectedElements().get(0);
        
        if (elem instanceof IJavaProject) {
            String name= ClasspathModifier.escapeSpecialChars(((IJavaElement)elem).getElementName());
        	return Messages.format(NewWizardMessages.PackageExplorerActionGroup_FormText_ProjectFromBuildpath, name);
        } else if (elem instanceof IPackageFragmentRoot) {
            String name= ClasspathModifier.escapeSpecialChars(((IJavaElement)elem).getElementName());
        	return Messages.format(NewWizardMessages.PackageExplorerActionGroup_FormText_fromBuildpath, name);
        } else if (elem instanceof ClassPathContainer) {
        	return NewWizardMessages.PackageExplorerActionGroup_FormText_Default_FromBuildpath;
        }
        
        return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		try {

			final IJavaProject project;
			Object object= getSelectedElements().get(0);
			if (object instanceof IJavaProject) {
				project= (IJavaProject)object;
			} else if (object instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot root= (IPackageFragmentRoot)object;
				project= root.getJavaProject();
			} else {
				ClassPathContainer container= (ClassPathContainer)object;
				project= container.getJavaProject();
			}

			final List elementsToRemove= new ArrayList();
			final List foldersToDelete= new ArrayList();
			queryToRemoveLinkedFolders(elementsToRemove, foldersToDelete);
		
			final IRunnableWithProgress runnable= new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_RemoveFromBuildpath, elementsToRemove.size() + foldersToDelete.size());
						List result= removeFromClasspath(elementsToRemove, project, new SubProgressMonitor(monitor, elementsToRemove.size()));
						result.removeAll(foldersToDelete);
						deleteFolders(foldersToDelete, new SubProgressMonitor(monitor, foldersToDelete.size()));
						if (result.size() == 0)
							result.add(project);
						selectAndReveal(new StructuredSelection(result));
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
				}
			};
			fContext.run(false, false, runnable);
			
		} catch (CoreException e) {
			showExceptionDialog(e, NewWizardMessages.RemoveFromBuildpathAction_ErrorTitle);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof CoreException) {
				showExceptionDialog((CoreException)e.getCause(), NewWizardMessages.RemoveFromBuildpathAction_ErrorTitle);
			} else {
				JavaPlugin.log(e);
			}
		} catch (InterruptedException e) {
		}
	}

	private void deleteFolders(List folders, IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_RemoveFromBuildpath, folders.size()); 

			for (Iterator iter= folders.iterator(); iter.hasNext();) {
				IFolder folder= (IFolder)iter.next();
				folder.delete(true, true, new SubProgressMonitor(monitor, 1));
			}
		} finally {
			monitor.done();
		}
	}

	private List removeFromClasspath(List elements, IJavaProject project, IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_RemoveFromBuildpath, elements.size() + 1); 

			List existingEntries= ClasspathModifier.getExistingEntries(project);
			List result= new ArrayList();
			
			for (int i= 0; i < elements.size(); i++) {
				Object element= elements.get(i);

				if (element instanceof IJavaProject) {
					Object res= ClasspathModifier.removeFromClasspath((IJavaProject)element, existingEntries, new SubProgressMonitor(monitor, 1));
					result.add(res);

				} else if (element instanceof IPackageFragmentRoot) {
					Object res= ClasspathModifier.removeFromClasspath((IPackageFragmentRoot)element, existingEntries, project, new SubProgressMonitor(monitor, 1));
					if (res != null)
						result.add(res);
				} else {
					existingEntries.remove(CPListElement.createFromExisting(((ClassPathContainer)element).getClasspathEntry(), project));
				}
			}

			ClasspathModifier.commitClassPath(existingEntries, project, new SubProgressMonitor(monitor, 1));
			
        	BuildpathDelta delta= new BuildpathDelta(getToolTipText());
        	delta.setNewEntries((CPListElement[])existingEntries.toArray(new CPListElement[existingEntries.size()]));
        	informListeners(delta);

			return result;
		} finally {
			monitor.done();
		}
	}
	
	private void queryToRemoveLinkedFolders(final List elementsToRemove, final List foldersToDelete) throws JavaModelException {
		final Shell shell= getShell();
		for (Iterator iter= getSelectedElements().iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IPackageFragmentRoot) {
				IFolder folder= getLinkedSourceFolder((IPackageFragmentRoot)element);
				if (folder != null) {
					RemoveLinkedFolderDialog dialog= new RemoveLinkedFolderDialog(shell, folder);

					final int result= dialog.open() == Window.OK?dialog.getRemoveStatus():IRemoveLinkedFolderQuery.REMOVE_CANCEL;
					
					if (result != IRemoveLinkedFolderQuery.REMOVE_CANCEL) {
						if (result == IRemoveLinkedFolderQuery.REMOVE_BUILD_PATH) {
							elementsToRemove.add(element);
						} else if (result == IRemoveLinkedFolderQuery.REMOVE_BUILD_PATH_AND_FOLDER) {
							elementsToRemove.add(element);
							foldersToDelete.add(folder);
						}
					}
				} else {
					elementsToRemove.add(element);
				}
			} else {
				elementsToRemove.add(element);
			}
		}
	}

	private IFolder getLinkedSourceFolder(IPackageFragmentRoot root) throws JavaModelException {
		if (root.getKind() != IPackageFragmentRoot.K_SOURCE)
			return null;

		final IResource resource= root.getCorrespondingResource();
		if (!(resource instanceof IFolder))
			return null;

		final IFolder folder= (IFolder) resource;
		if (!folder.isLinked())
			return null;

		return folder;
	}

	protected boolean canHandle(IStructuredSelection elements) {
		if (elements.size() == 0)
			return false;

		try {
			for (Iterator iter= elements.iterator(); iter.hasNext();) {
				Object element= iter.next();

				if (element instanceof IJavaProject) {
					IJavaProject project= (IJavaProject)element;
					if (!ClasspathModifier.isSourceFolder(project))
						return false;

				} else if (element instanceof IPackageFragmentRoot) {
					IClasspathEntry entry= ((IPackageFragmentRoot) element).getRawClasspathEntry();
					if (entry != null && entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
						return false;
					}
				} else if (element instanceof ClassPathContainer) {
				} else {
					return false;
				}
			}
			return true;
		} catch (JavaModelException e) {
		}
		return false;
	}
}