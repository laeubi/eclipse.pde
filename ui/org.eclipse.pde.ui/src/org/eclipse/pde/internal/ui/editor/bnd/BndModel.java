/*******************************************************************************
 * Copyright (c) 2023 Christoph Läubrich and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.bnd;

import aQute.bnd.build.model.BndEditModel;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.internal.core.NLResourceHelper;
import org.eclipse.pde.internal.core.text.AbstractEditingModel;
import org.eclipse.pde.internal.core.text.IEditingModel;

public class BndModel extends AbstractEditingModel implements IBaseModel, IEditingModel {

	private final BndEditModel bndModel = new BndEditModel();

	public BndModel(IDocument document, boolean isReconciling) {
		super(document, isReconciling);

	}

	@Override
	public void adjustOffsets(IDocument document) throws CoreException {

	}

	@Override
	protected NLResourceHelper createNLResourceHelper() {
		return null;
	}

	public BndEditModel getBndModel() {
		return bndModel;
	}


	@Override
	public void load(InputStream source, boolean outOfSync) throws CoreException {
		try {
			bndModel.loadFrom(source);
			fLoaded = true;
		} catch (IOException e) {
			fLoaded = false;
		}
	}

}
