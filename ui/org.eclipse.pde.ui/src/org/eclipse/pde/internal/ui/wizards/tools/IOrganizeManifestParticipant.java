/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation and others.
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
package org.eclipse.pde.internal.ui.wizards.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Interface for participants in the Organize Manifests operation.
 * Participants can contribute additional cleanup operations.
 * 
 * @since 3.16.300
 */
public interface IOrganizeManifestParticipant {

	/**
	 * Performs cleanup operations on the given project.
	 * 
	 * @param project the project to clean up
	 * @param monitor progress monitor
	 */
	void cleanup(IProject project, IProgressMonitor monitor);

	/**
	 * Returns whether this participant is enabled for the current workspace.
	 * This allows participants to check if required dependencies are available.
	 * 
	 * @return true if this participant can run, false otherwise
	 */
	boolean isEnabled();

}
