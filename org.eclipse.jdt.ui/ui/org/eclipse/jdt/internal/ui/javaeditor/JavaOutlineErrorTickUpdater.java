package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelListener;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.jdt.core.IWorkingCopy;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

/**
 * The <code>JavaOutlineErrorTickUpdater</code> will register as a AnnotationModelListener on the annotation model
 * and update all images in the outliner tree when the annotation model changed.
 */
public class JavaOutlineErrorTickUpdater implements IAnnotationModelListener {

	private TreeViewer fViewer;
	private JavaElementLabelProvider fLabelProvider;
	private IAnnotationModel fAnnotationModel;

	/**
	 * @param viewer The viewer of the outliner
	 * @param labelProvider The label provider used by the outliner
	 */
	public JavaOutlineErrorTickUpdater(TreeViewer viewer, JavaElementLabelProvider labelProvider) {
		fViewer= viewer;
		fLabelProvider= labelProvider;
	}

	/**
	 * Defines the annotation model to listen to. To be called when the
	 * annotation model changes.
	 * @param model The new annotation model or <code>null</code>
	 * to uninstall.
	 */
	public void setAnnotationModel(IAnnotationModel model) {
		if (fAnnotationModel != null) {
			fAnnotationModel.removeAnnotationModelListener(this);
		}
				
		if (model != null) {
			fAnnotationModel= model;
			fAnnotationModel.addAnnotationModelListener(this);
			fLabelProvider.setErrorTickManager(new AnnotationErrorTickProvider(fAnnotationModel));
			modelChanged(model);
		} else {
			fAnnotationModel= null;
			fLabelProvider.setErrorTickManager(null);
		}	
	}	
	
		
	/**
	 * @see IAnnotationModelListener#modelChanged(IAnnotationModel)
	 */
	public void modelChanged(IAnnotationModel model) {
		Control control= fViewer.getControl();
		if (control != null && !control.isDisposed()) {
			control.getDisplay().asyncExec(new Runnable() {
				public void run() {
					// until we have deltas, update all error ticks
					doUpdateErrorTicks();
				}
			});
		}		
	}
	
	private void doUpdateErrorTicks() {
		// do not look at class files
		if (!(fViewer.getInput() instanceof IWorkingCopy)) {
			return;
		}
		
		Tree tree= fViewer.getTree();
		if (!tree.isDisposed()) { // defensive code
			TreeItem[] items= fViewer.getTree().getItems();
			for (int i= 0; i < items.length; i++) {
				updateItem(items[i]);
			}
		}
	}
	
	private void updateItem(TreeItem item) {
		Object data= item.getData();
		Image old= item.getImage();
		Image image= fLabelProvider.getImage(data);
		if (image != null && image != old) {
			item.setImage(image);
		}
		TreeItem[] children= item.getItems();
		for (int i= 0; i < children.length; i++) {
			updateItem(children[i]);
		}
	}	
	

}

