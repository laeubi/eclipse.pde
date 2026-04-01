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

import org.eclipse.pde.internal.ui.editor.GenericSourcePage;
import org.eclipse.pde.internal.ui.editor.PDEFormEditor;

/**
 * Source page for editing p2.inf files. This provides a text editor view of the
 * p2 advice file content.
 * <p>
 * The p2.inf file uses Java properties format and can contain:
 * <ul>
 * <li>Capability advice: provides.*, requires.*, metaRequirements.*</li>
 * <li>Property advice: properties.*</li>
 * <li>Touchpoint instruction advice: instructions.*</li>
 * <li>Update descriptor advice: update.*</li>
 * <li>Additional IU definitions: units.*</li>
 * </ul>
 * 
 * @see P2InfInputContext
 */
public class P2InfSourcePage extends GenericSourcePage {

	/**
	 * Creates a new P2InfSourcePage.
	 *
	 * @param editor the parent form editor
	 * @param id     the page identifier
	 * @param title  the page title
	 */
	public P2InfSourcePage(PDEFormEditor editor, String id, String title) {
		super(editor, id, title);
	}
}
