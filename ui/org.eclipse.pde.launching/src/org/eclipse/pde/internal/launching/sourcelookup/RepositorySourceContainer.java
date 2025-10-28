/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.pde.internal.launching.sourcelookup;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.containers.AbstractSourceContainer;
import org.eclipse.debug.core.sourcelookup.containers.ExternalArchiveSourceContainer;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.core.target.P2TargetUtils;
import org.eclipse.pde.internal.launching.IPDEConstants;
import org.eclipse.pde.internal.launching.PDELaunchingPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Source container that searches for source bundles in enabled P2 repositories.
 * This is used as a fallback when source cannot be found in workspace or target platform.
 * 
 * @since 3.8
 */
public class RepositorySourceContainer extends AbstractSourceContainer {

	/**
	 * Unique identifier for this source container type
	 */
	public static final String TYPE_ID = IPDEConstants.PLUGIN_ID + ".containerType.repository"; //$NON-NLS-1$

	private final String fBundleId;
	
	/**
	 * Constructs a repository source container for the given bundle.
	 * 
	 * @param bundleId symbolic name of the bundle to search for source
	 */
	public RepositorySourceContainer(String bundleId) {
		fBundleId = bundleId;
	}

	@Override
	public String getName() {
		return NLS.bind("Repository Source: {0}", fBundleId); //$NON-NLS-1$
	}

	@Override
	public ISourceContainerType getType() {
		return getSourceContainerType(TYPE_ID);
	}

	@Override
	public Object[] findSourceElements(String name) throws CoreException {
		// Only search if the preference is enabled
		if (!PDELaunchingPlugin.getDefault().getPreferenceManager()
				.getBoolean(org.eclipse.pde.internal.launching.ILaunchingPreferenceConstants.PROP_SEARCH_REPOSITORIES_FOR_SOURCE)) {
			return EMPTY;
		}

		// Search for source bundle in repositories
		File sourceBundle = findSourceBundle(fBundleId);
		if (sourceBundle != null && sourceBundle.exists()) {
			// Create an external archive source container for the source bundle
			ExternalArchiveSourceContainer container = new ExternalArchiveSourceContainer(
					sourceBundle.getAbsolutePath(), true);
			return container.findSourceElements(name);
		}
		
		return EMPTY;
	}

	/**
	 * Searches enabled P2 repositories for a source bundle matching the given bundle ID.
	 * 
	 * @param bundleId the symbolic name of the bundle
	 * @return the source bundle file, or null if not found
	 */
	private File findSourceBundle(String bundleId) {
		try {
			IProvisioningAgent agent = getProvisioningAgent();
			if (agent == null) {
				return null;
			}

			IMetadataRepositoryManager metadataManager = agent.getService(IMetadataRepositoryManager.class);
			IArtifactRepositoryManager artifactManager = agent.getService(IArtifactRepositoryManager.class);

			if (metadataManager == null || artifactManager == null) {
				return null;
			}

			// Get all known repositories
			URI[] metadataRepos = metadataManager.getKnownRepositories(IMetadataRepositoryManager.REPOSITORIES_ALL);

			for (URI repoUri : metadataRepos) {
				try {
					// Load the metadata repository
					IMetadataRepository metadataRepo = metadataManager.loadRepository(repoUri, null);
					
					// Query for source bundles matching the bundle ID
					// Source bundles typically have .source appended to the ID
					String sourceId = bundleId + ".source"; //$NON-NLS-1$
					IQuery<IInstallableUnit> query = QueryUtil.createLatestQuery(
							QueryUtil.createIUQuery(sourceId));
					IQueryResult<IInstallableUnit> result = metadataRepo.query(query, null);

					if (!result.isEmpty()) {
						IInstallableUnit sourceIU = result.iterator().next();
						
						// Get the artifact from the artifact repository
						for (IArtifactKey key : sourceIU.getArtifacts()) {
							File sourceFile = getArtifactFile(artifactManager, key, repoUri);
							if (sourceFile != null && sourceFile.exists()) {
								return sourceFile;
							}
						}
					}
				} catch (Exception e) {
					// Continue searching in other repositories
					PDELaunchingPlugin.log(new Status(IStatus.WARNING, IPDEConstants.PLUGIN_ID,
							"Error searching repository: " + repoUri, e)); //$NON-NLS-1$
				}
			}
		} catch (Exception e) {
			PDELaunchingPlugin.log(new Status(IStatus.ERROR, IPDEConstants.PLUGIN_ID,
					"Error searching for source bundle: " + bundleId, e)); //$NON-NLS-1$
		}

		return null;
	}

	/**
	 * Gets the provisioning agent for P2 operations.
	 */
	private IProvisioningAgent getProvisioningAgent() {
		BundleContext context = PDELaunchingPlugin.getDefault().getBundle().getBundleContext();
		ServiceReference<IProvisioningAgentProvider> reference = context
				.getServiceReference(IProvisioningAgentProvider.class);
		if (reference != null) {
			IProvisioningAgentProvider agentProvider = context.getService(reference);
			if (agentProvider != null) {
				try {
					return agentProvider.createAgent(P2TargetUtils.AGENT_LOCATION);
				} catch (Exception e) {
					PDELaunchingPlugin.log(e);
				} finally {
					context.ungetService(reference);
				}
			}
		}
		return null;
	}

	/**
	 * Retrieves the local file for an artifact from the repository.
	 */
	private File getArtifactFile(IArtifactRepositoryManager manager, IArtifactKey key, URI repoUri) {
		try {
			IArtifactRepository artifactRepo = manager.loadRepository(repoUri, null);
			File location = artifactRepo.getArtifact(key);
			if (location != null && location.exists()) {
				return location;
			}
		} catch (Exception e) {
			// Ignore and return null
		}
		return null;
	}
}
