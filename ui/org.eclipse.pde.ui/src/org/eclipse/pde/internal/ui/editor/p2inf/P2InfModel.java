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

import org.eclipse.jface.text.IDocument;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.core.IModelChangeProvider;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.core.IModelChangedListener;

/**
 * Model representing a p2 advice file (p2.inf). The p2.inf file is a Java
 * properties format file used to customize p2 metadata for bundles and
 * features.
 * <p>
 * The p2.inf file can contain:
 * <ul>
 * <li>Capability advice (provides, requires, metaRequirements)</li>
 * <li>Property advice</li>
 * <li>Touchpoint instruction advice</li>
 * <li>Update descriptor advice</li>
 * <li>Additional installable unit definitions</li>
 * </ul>
 * 
 * @see <a href=
 *      "https://github.com/eclipse-equinox/p2/blob/master/docs/Customizing_Metadata.md">
 *      p2 Customizing Metadata Documentation</a>
 */
public class P2InfModel implements IBaseModel, IModelChangeProvider {

	private final IDocument document;
	private volatile boolean valid;
	private volatile boolean disposed;

	/**
	 * Creates a new P2InfModel backed by the given document.
	 *
	 * @param document the document containing the p2.inf content
	 */
	public P2InfModel(IDocument document) {
		this.document = document;
	}

	/**
	 * Loads or reloads the model from the underlying document.
	 */
	public void load() {
		// For now, just mark as valid - the document contains the raw content
		// Future enhancement: parse the properties and validate syntax
		valid = document != null;
	}

	/**
	 * Returns the underlying document.
	 *
	 * @return the document
	 */
	public IDocument getDocument() {
		return document;
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	@Override
	public boolean isDisposed() {
		return disposed;
	}

	@Override
	public void dispose() {
		disposed = true;
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter == IDocument.class) {
			return adapter.cast(document);
		}
		return null;
	}

	@Override
	public void addModelChangedListener(IModelChangedListener listener) {
		// Model change listener support - can be enhanced later
	}

	@Override
	public void fireModelChanged(IModelChangedEvent event) {
		// Model change event support - can be enhanced later
	}

	@Override
	public void fireModelObjectChanged(Object object, String property, Object oldValue, Object newValue) {
		// Model object change support - can be enhanced later
	}

	@Override
	public void removeModelChangedListener(IModelChangedListener listener) {
		// Model change listener support - can be enhanced later
	}
}
