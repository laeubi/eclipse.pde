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
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.IRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.pde.core.IPluginSourcePathLocator;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetLocation;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.core.copyfrom.oomph.P2Index;
import org.eclipse.pde.internal.core.copyfrom.oomph.P2Index.Repository;
import org.eclipse.pde.internal.core.target.IUBundleContainer;
import org.eclipse.pde.internal.core.target.P2TargetUtils;
import org.eclipse.pde.internal.core.target.TargetPlatformService;
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
		if (repositories == null || repositories.isEmpty()) {
			return null;
		}

		String pluginId = plugin.getId();
		String sourcePluginId = pluginId + ".source"; //$NON-NLS-1$
		String pluginVersion = plugin.getVersion();

		// Loop through all configured repository URLs
		for (String repositoryURL : repositories) {
			try {
				// Try to download the source bundle from this repository
				URI repoURI = URI.create(repositoryURL.trim());
				IPath result = downloadArtifact(repoURI, sourcePluginId, pluginVersion);
				if (result != null) {
					// Found and downloaded successfully
					return result;
				}
			} catch (Exception e) {
				// Log and continue to next repository
				PDECore.log("Failed to search for source in repository " + repositoryURL + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

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
		if (selectedTargets == null || selectedTargets.isEmpty()) {
			return null;
		}

		String pluginId = plugin.getId();
		String sourcePluginId = pluginId + ".source"; //$NON-NLS-1$
		String pluginVersion = plugin.getVersion();

		try {
			// Get the target platform service
			ITargetPlatformService service = TargetPlatformService.getDefault();
			if (service == null) {
				return null;
			}

			// Get all available target definitions
			ITargetHandle[] targetHandles = service.getTargets(null);
			if (targetHandles == null) {
				return null;
			}

			// Loop through all target handles
			for (ITargetHandle targetHandle : targetHandles) {
				try {
					ITargetDefinition targetDef = targetHandle.getTargetDefinition();
					if (targetDef == null || targetDef.getName() == null) {
						continue;
					}

					// Check if this target is in the selected list
					if (!selectedTargets.contains(targetDef.getName())) {
						continue;
					}

					// Get all target locations from this target
					ITargetLocation[] locations = targetDef.getTargetLocations();
					if (locations == null) {
						continue;
					}

					// Loop through locations and look for IUBundleContainer (IU locations)
					for (ITargetLocation location : locations) {
						if (location instanceof IUBundleContainer) {
							IUBundleContainer iuLocation = (IUBundleContainer) location;

							// Get the repository URIs from this IU location
							List<URI> repositories = iuLocation.getRepositories();
							if (repositories == null || repositories.isEmpty()) {
								continue;
							}

							// Search each repository for the source bundle
							for (URI repoURI : repositories) {
								IPath result = downloadArtifact(repoURI, sourcePluginId, pluginVersion);
								if (result != null) {
									// Found and downloaded successfully
									return result;
								}
							}
						}
					}
				} catch (CoreException e) {
					// Log and continue to next target
					PDECore.log("Failed to process target definition: " + e.getMessage()); //$NON-NLS-1$
				}
			}
		} catch (Exception e) {
			// Log but don't fail - this is just one lookup strategy
			PDECore.log(e);
		}

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
				p2Index = new P2Index(indexCacheDir);
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
							URI repositoryURI = URI.create(entry.getKey().getLocation().toString());
							return downloadArtifact(repositoryURI, sourcePluginId, pluginVersion);
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
	 * Downloads an artifact from a P2 repository to the bundle pool.
	 * 
	 * @param repositoryURL the repository URL
	 * @param artifactId    the artifact ID (bundle symbolic name)
	 * @param version       the artifact version
	 * @return the file location where it was downloaded or null if download failed
	 */
	private IPath downloadArtifact(URI repositoryURL, String artifactId, String version) {
		try {
			IProgressMonitor monitor = new NullProgressMonitor();

			// Get P2 managers
			IMetadataRepositoryManager metadataManager = P2TargetUtils.getMetadataRepositoryManager();
			IArtifactRepositoryManager artifactManager = P2TargetUtils.getArtifactRepositoryManager();

			// Load the repository
			IMetadataRepository metadataRepo = metadataManager.loadRepository(repositoryURL,
					IRepository.REPOSITORY_HINT_MODIFIABLE, monitor);

			// Find the installable unit for the source bundle
			IQueryResult<IInstallableUnit> queryResult = metadataRepo.query(
					QueryUtil.createIUQuery(artifactId, Version.create(version)), monitor);

			if (queryResult.isEmpty()) {
				PDECore.log("Source bundle " + artifactId + " version " + version + " not found in repository " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ repositoryURL);
				return null;
			}

			IInstallableUnit iu = queryResult.iterator().next();

			// Get the artifact key
			IArtifactKey artifactKey = iu.getArtifacts().iterator().next();

			// Get the bundle pool (destination)
			IArtifactRepository bundlePool = P2TargetUtils.getBundlePool();

			// Check if already in bundle pool
			if (bundlePool.contains(artifactKey)) {
				// Already downloaded, find the file
				File bundlePoolLocation = bundlePool.getLocation();
				File artifactFile = new File(bundlePoolLocation,
						"plugins/" + artifactId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				if (artifactFile.exists()) {
					return new Path(artifactFile.getAbsolutePath());
				}
			}

			// Download the artifact to the bundle pool
			IArtifactRepository sourceRepo = artifactManager.loadRepository(repositoryURL,
					IRepository.REPOSITORY_HINT_MODIFIABLE, monitor);

			IArtifactRequest request = artifactManager.createMirrorRequest(artifactKey, bundlePool, null, null);
			IStatus status = artifactManager.perform(new IArtifactRequest[] { request }, monitor);

			if (status.isOK()) {
				// Find the downloaded file in the bundle pool
				File bundlePoolLocation = bundlePool.getLocation();
				File artifactFile = new File(bundlePoolLocation,
						"plugins/" + artifactId + "_" + version + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				if (artifactFile.exists()) {
					PDECore.log("Downloaded source bundle " + artifactId + " version " + version + " from " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ repositoryURL);
					return new Path(artifactFile.getAbsolutePath());
				}
			} else {
				PDECore.log(status);
			}

		} catch (Exception e) {
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
		String pluginId = plugin.getId();
		String sourcePluginId = pluginId + ".source"; //$NON-NLS-1$
		String pluginVersion = plugin.getVersion();

		try {
			// Get the metadata repository manager
			IMetadataRepositoryManager metadataManager = P2TargetUtils.getMetadataRepositoryManager();
			if (metadataManager == null) {
				return null;
			}

			// Get all known repositories from P2
			URI[] knownRepositories = metadataManager.getKnownRepositories(IRepositoryManager.REPOSITORIES_ALL);
			if (knownRepositories == null || knownRepositories.length == 0) {
				return null;
			}

			// Loop through all known repositories
			for (URI repositoryURI : knownRepositories) {
				try {
					// Try to download the source bundle from this repository
					IPath result = downloadArtifact(repositoryURI, sourcePluginId, pluginVersion);
					if (result != null) {
						// Found and downloaded successfully
						return result;
					}
				} catch (Exception e) {
					// Log and continue to next repository
					PDECore.log("Failed to search for source in known repository " + repositoryURI + ": " //$NON-NLS-1$ //$NON-NLS-2$
							+ e.getMessage());
				}
			}
		} catch (Exception e) {
			// Log but don't fail - this is just one lookup strategy
			PDECore.log(e);
		}

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
