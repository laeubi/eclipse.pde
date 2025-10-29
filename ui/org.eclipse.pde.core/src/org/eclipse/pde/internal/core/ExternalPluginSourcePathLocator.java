/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
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
package org.eclipse.pde.internal.core;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.pde.core.IPluginSourcePathLocator;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.internal.ui.IPreferenceConstants;
import org.eclipse.pde.internal.ui.PDEPlugin;

/**
 * A plugin source path locator that queries external repositories and the
 * Eclipse index for source bundles based on the configured preferences.
 * <p>
 * This locator checks the Source Lookups preference page settings and performs
 * lookups according to the configured order.
 * </p>
 * 
 * @since 3.17
 */
public class ExternalPluginSourcePathLocator implements IPluginSourcePathLocator {

	// Source lookup strategy identifiers
	private static final String STRATEGY_REPOSITORIES = "REPOSITORIES"; //$NON-NLS-1$
	private static final String STRATEGY_TARGETS = "TARGETS"; //$NON-NLS-1$
	private static final String STRATEGY_INDEX = "INDEX"; //$NON-NLS-1$
	private static final String STRATEGY_SITES = "SITES"; //$NON-NLS-1$

	@Override
	public IPath locateSource(IPluginBase plugin) {
		IPreferenceStore store = PDEPlugin.getDefault().getPreferenceStore();

		// Check if source lookup is enabled
		if (!store.getBoolean(IPreferenceConstants.SOURCE_LOOKUP_ENABLED)) {
			return null;
		}

		// Get the lookup order
		List<String> lookupOrder = getLookupOrder(store);

		// Execute lookups in the configured order
		for (String strategy : lookupOrder) {
			IPath result = null;
			switch (strategy) {
			case STRATEGY_REPOSITORIES:
				result = searchInRepositories(plugin, getConfiguredRepositories(store));
				break;
			case STRATEGY_TARGETS:
				result = searchInTargets(plugin, getSelectedTargets(store));
				break;
			case STRATEGY_INDEX:
				result = queryEclipseIndex(plugin);
				break;
			case STRATEGY_SITES:
				result = queryAvailableSoftwareSites(plugin);
				break;
			default:
				// Unknown strategy, skip
				break;
			}
			
			if (result != null) {
				return result;
			}
		}

		return null;
	}

	/**
	 * Gets the configured lookup order from preferences.
	 * 
	 * @param store the preference store
	 * @return the list of strategy identifiers in order
	 */
	private List<String> getLookupOrder(IPreferenceStore store) {
		String order = store.getString(IPreferenceConstants.SOURCE_LOOKUP_ORDER);
		if (order == null || order.trim().isEmpty()) {
			// Use default order
			return Arrays.asList(STRATEGY_REPOSITORIES, STRATEGY_TARGETS, STRATEGY_INDEX, STRATEGY_SITES);
		}
		return Arrays.asList(order.split(",")); //$NON-NLS-1$
	}

	/**
	 * Searches for the source bundle in the configured repositories.
	 * 
	 * @param plugin       the plugin to locate sources for
	 * @param repositories the list of repository URLs
	 * @return the path to the source bundle or null if not found
	 */
	private IPath searchInRepositories(IPluginBase plugin, List<String> repositories) {
		// TODO: Implement searching in configured repositories
		// This will be implemented in a future step
		return null;
	}

	/**
	 * Searches for the source bundle in the specified target platforms.
	 * 
	 * @param plugin          the plugin to locate sources for
	 * @param selectedTargets the list of selected target platform names
	 * @return the path to the source bundle or null if not found
	 */
	private IPath searchInTargets(IPluginBase plugin, List<String> selectedTargets) {
		// TODO: Implement searching in selected target platforms
		// This will be implemented in a future step
		return null;
	}

	/**
	 * Queries the Eclipse index for the source bundle.
	 * 
	 * @param plugin the plugin to locate sources for
	 * @return the path to the source bundle or null if not found
	 */
	private IPath queryEclipseIndex(IPluginBase plugin) {
		// TODO: Implement querying the Eclipse index
		// This will be implemented in a future step
		return null;
	}

	/**
	 * Queries available software sites for the source bundle.
	 * 
	 * @param plugin the plugin to locate sources for
	 * @return the path to the source bundle or null if not found
	 */
	private IPath queryAvailableSoftwareSites(IPluginBase plugin) {
		// TODO: Implement querying available software sites
		// This will be implemented in a future step
		return null;
	}

	/**
	 * Gets the list of selected target platform names from preferences.
	 * 
	 * @param store the preference store
	 * @return the list of selected target names
	 */
	private List<String> getSelectedTargets(IPreferenceStore store) {
		String selectedTargets = store.getString(IPreferenceConstants.SOURCE_LOOKUP_SELECTED_TARGETS);
		if (selectedTargets == null || selectedTargets.trim().isEmpty()) {
			return List.of();
		}
		return Arrays.asList(selectedTargets.split(",")); //$NON-NLS-1$
	}

	/**
	 * Gets the list of configured repository URLs from preferences.
	 * 
	 * @param store the preference store
	 * @return the list of repository URLs
	 */
	private List<String> getConfiguredRepositories(IPreferenceStore store) {
		String repositories = store.getString(IPreferenceConstants.SOURCE_LOOKUP_REPOSITORIES);
		if (repositories == null || repositories.trim().isEmpty()) {
			return List.of();
		}
		return Arrays.asList(repositories.split(",")); //$NON-NLS-1$
	}
}
