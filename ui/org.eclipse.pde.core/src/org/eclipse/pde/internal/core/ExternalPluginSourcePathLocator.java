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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.pde.core.IPluginSourcePathLocator;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.internal.core.copyfrom.oomph.P2Index;
import org.eclipse.pde.internal.core.copyfrom.oomph.P2Index.Repository;
import org.eclipse.pde.internal.core.copyfrom.oomph.P2IndexImpl;
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

	private P2Index p2Index;

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
		try {
			// Lazy initialization of P2Index
			if (p2Index == null) {
				File indexCacheDir = new File(Platform.getStateLocation(PDECore.getDefault().getBundle()).toFile(),
						"index"); //$NON-NLS-1$
				p2Index = new P2IndexImpl(indexCacheDir);
			}

			String pluginId = plugin.getId();
			String sourcePluginId = pluginId + ".source"; //$NON-NLS-1$

			// Query the index for the source bundle using OSGi bundle capability
			Map<Repository, Set<Version>> repositories = p2Index
					.lookupCapabilities(PublisherHelper.CAPABILITY_NS_OSGI_BUNDLE, sourcePluginId);

			if (repositories != null && !repositories.isEmpty()) {
				// Find the repository with the matching version
				String pluginVersion = plugin.getVersion();
				for (Entry<Repository, Set<Version>> entry : repositories.entrySet()) {
					for (Version version : entry.getValue()) {
						if (version.toString().equals(pluginVersion)) {
							// Found matching source bundle in the index
							// The repository location is available but actual download/resolution
							// would require P2 repository manager which is not available in this context
							// For now, log the finding and return null (to be implemented with P2 integration)
							PDECore.log("Found source bundle " + sourcePluginId + " version " + version //$NON-NLS-1$ //$NON-NLS-2$
									+ " in repository " + entry.getKey().getLocation()); //$NON-NLS-1$
							// TODO: Implement actual artifact resolution and download from the repository
							return null;
						}
					}
				}
			}
		} catch (Exception e) {
			// Log but don't fail - this is just one lookup strategy
			PDECore.log(e);
		}
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
