package org.eclipse.pde.internal.ui.editor.bnd;

import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.ui.editor.KeyValueSourcePage;
import org.eclipse.pde.internal.ui.editor.PDEFormEditor;

public class BndSourcePage extends KeyValueSourcePage {

	public BndSourcePage(PDEFormEditor editor, String id, String title) {
		super(editor, id, title);
	}

	@Override
	public ILabelProvider createOutlineLabelProvider() {
		return new LabelProvider();
	}

	@Override
	public ITreeContentProvider createOutlineContentProvider() {
		return new BndOutlineContentProvider();
	}

	@Override
	public void updateSelection(Object object) {
	}

	private static class BndOutlineContentProvider implements ITreeContentProvider {

		@Override
		public Object[] getElements(Object inputElement) {
			return new Object[0];
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return false;
		}

	}
}
