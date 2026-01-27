/*******************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.p2inf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.internal.ui.editor.PDEFormEditor;
import org.eclipse.pde.internal.ui.editor.context.InputContext;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorInput;

/**
 * Input context for p2.inf files. Manages the editing lifecycle of p2 advice
 * files within the PDE editors.
 * <p>
 * p2.inf files are located at:
 * <ul>
 * <li>META-INF/p2.inf for bundle/plugin projects</li>
 * <li>p2.inf (root) for feature projects</li>
 * </ul>
 */
public class P2InfInputContext extends InputContext {

	/**
	 * Partition type for p2.inf content.
	 */
	public static final String P2_INF_PARTITION = "___p2_inf_partition"; //$NON-NLS-1$

	/**
	 * Context identifier for p2.inf files.
	 */
	public static final String CONTEXT_ID = "p2inf-context"; //$NON-NLS-1$

	/**
	 * Creates a new P2InfInputContext.
	 *
	 * @param editor  the form editor containing this context
	 * @param input   the editor input for the p2.inf file
	 * @param primary whether this is the primary input context
	 */
	public P2InfInputContext(PDEFormEditor editor, IEditorInput input, boolean primary) {
		super(editor, input, primary);
		create();
	}

	@Override
	protected Charset getDefaultCharset() {
		// p2.inf files use UTF-8 encoding
		return StandardCharsets.UTF_8;
	}

	@Override
	protected IBaseModel createModel(IEditorInput input) throws CoreException {
		IDocument document = getDocumentProvider().getDocument(input);
		P2InfModel model = new P2InfModel(document);
		model.load();
		return model;
	}

	@Override
	public P2InfModel getModel() {
		return (P2InfModel) super.getModel();
	}

	@Override
	public String getId() {
		return CONTEXT_ID;
	}

	@Override
	protected void addTextEditOperation(ArrayList<TextEdit> ops, IModelChangedEvent event) {
		// Text edit operations are handled by the document directly
		// No special processing needed for basic source editing
	}

	@Override
	public void doRevert() {
		// Revert to the saved version
		P2InfModel model = getModel();
		if (model != null) {
			model.load();
		}
	}

	@Override
	protected String getPartitionName() {
		return P2_INF_PARTITION;
	}
}
