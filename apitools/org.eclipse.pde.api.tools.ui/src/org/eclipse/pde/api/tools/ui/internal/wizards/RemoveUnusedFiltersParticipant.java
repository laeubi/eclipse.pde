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
package org.eclipse.pde.api.tools.ui.internal.wizards;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.pde.api.tools.internal.ApiBaselineManager;
import org.eclipse.pde.api.tools.internal.model.ProjectComponent;
import org.eclipse.pde.api.tools.internal.problems.ApiProblemFactory;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.IApiFilterStore;
import org.eclipse.pde.api.tools.internal.provisional.IApiMarkerConstants;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblemFilter;
import org.eclipse.pde.internal.ui.wizards.tools.IOrganizeManifestParticipant;

/**
 * Participant that removes unused API filters during Organize Manifests operation.
 * This reuses the logic from RemoveFilterProblemResolution.
 */
public class RemoveUnusedFiltersParticipant implements IOrganizeManifestParticipant {

	@Override
	public void cleanup(IProject project, IProgressMonitor monitor) {
		try {
			// Find all unused filter markers
			IMarker[] markers = project.findMarkers(IApiMarkerConstants.UNUSED_FILTER_PROBLEM_MARKER, false, IResource.DEPTH_INFINITE);
			if (markers.length == 0) {
				return;
			}
			
			SubMonitor subMonitor = SubMonitor.convert(monitor, markers.length);
			
			// Group filters by API component
			Map<IApiComponent, Set<IApiProblemFilter>> map = new HashMap<>();
			
			for (IMarker marker : markers) {
				if (subMonitor.isCanceled()) {
					break;
				}
				
				// Resolve the filter for this marker
				IApiProblemFilter filter = resolveFilter(marker);
				if (filter == null) {
					subMonitor.worked(1);
					continue;
				}
				
				// Get the API component for this project
				IApiComponent component = ApiBaselineManager.getManager().getWorkspaceBaseline().getApiComponent(project);
				
				if (component instanceof ProjectComponent) {
					Set<IApiProblemFilter> filters = map.get(component);
					if (filters == null) {
						filters = new HashSet<>();
						map.put(component, filters);
					}
					filters.add(filter);
				}
				subMonitor.worked(1);
			}
			
			// Batch remove the filters
			for (Map.Entry<IApiComponent, Set<IApiProblemFilter>> entry : map.entrySet()) {
				try {
					IApiComponent component = entry.getKey();
					Set<IApiProblemFilter> filters = entry.getValue();
					IApiFilterStore store = component.getFilterStore();
					store.removeFilters(filters.toArray(new IApiProblemFilter[filters.size()]));
				} catch (CoreException ce) {
					ApiPlugin.log(ce);
				}
			}
		} catch (CoreException e) {
			ApiPlugin.log(e);
		}
	}

	@Override
	public boolean isEnabled() {
		// Check if API baseline is available
		try {
			return ApiBaselineManager.getManager().getWorkspaceBaseline() != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Resolves the IApiProblemFilter for the given marker.
	 * This is adapted from ApiMarkerResolutionGenerator.resolveFilter().
	 */
	private static IApiProblemFilter resolveFilter(IMarker marker) {
		try {
			String filterhandle = marker.getAttribute(IApiMarkerConstants.MARKER_ATTR_FILTER_HANDLE_ID, null);
			if (filterhandle == null) {
				return null;
			}
			String[] values = filterhandle.split(org.eclipse.pde.api.tools.internal.problems.ApiProblemFilter.HANDLE_DELIMITER);
			if (values.length < 2) {
				return null;
			}
			IProject project = marker.getResource().getProject();
			IApiComponent component = ApiBaselineManager.getManager().getWorkspaceBaseline().getApiComponent(project);
			if (component != null) {
				IApiFilterStore store = component.getFilterStore();
				IPath path = IPath.fromOSString(values[1]);
				IResource resource = project.findMember(path);
				if (resource == null) {
					resource = project.getFile(path);
				}
				int hashcode = ApiProblemFactory.getProblemHashcode(filterhandle);
				IApiProblemFilter[] filters = store.getFilters(resource);
				for (IApiProblemFilter filter : filters) {
					if (filter.getUnderlyingProblem().hashCode() == hashcode) {
						return filter;
					}
				}
			}
		} catch (CoreException ce) {
			// Ignore
		}
		return null;
	}
}
