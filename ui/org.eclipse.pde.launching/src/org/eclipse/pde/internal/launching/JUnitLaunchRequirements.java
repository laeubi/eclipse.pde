/*******************************************************************************
 * Copyright (c) 2006, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.launching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.DependencyManager;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.launching.launcher.BundleLauncherHelper;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

public class JUnitLaunchRequirements {

	private static final String PDE_JUNIT_RUNTIME = "org.eclipse.pde.junit.runtime"; //$NON-NLS-1$
	private static final String JUNIT4_JDT_RUNTIME_PLUGIN = "org.eclipse.jdt.junit4.runtime"; //$NON-NLS-1$
	private static final String JUNIT5_JDT_RUNTIME_PLUGIN = "org.eclipse.jdt.junit5.runtime"; //$NON-NLS-1$

	public static void addRequiredJunitRuntimePlugins(ILaunchConfiguration configuration, Map<String, List<IPluginModelBase>> collectedModels, Map<IPluginModelBase, String> startLevelMap) throws CoreException {
		Set<BundleDescription> addedRuntimeBundles = addAbsentRequirements(getRequiredJunitRuntimeEclipsePlugins(configuration), collectedModels, startLevelMap);
		System.out.println("Adding runtime bundles:");
		for (BundleDescription bundleDescription : addedRuntimeBundles) {
			System.out.println("- " + bundleDescription);
		}
		Resolver resolver = PDECore.getDefault().getResolverService();
		try {
			System.out.println("Resolving...");
			Map<Resource, List<Wire>> map = resolver.resolve(new ResolveContext() {


				@Override
				public boolean isEffective(Requirement requirement) {
					System.out.println("isEffective? " + requirement);
					//First check if the requirement is effective at all...
					String effective = requirement.getDirectives().get(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE);
					if (effective != null && !Namespace.EFFECTIVE_RESOLVE.equals(effective)) {
						return false;
					}
					//Now check if it is optional, we can skip these for our use case here...
					String resolution = requirement.getDirectives().get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE);
					if (Namespace.RESOLUTION_OPTIONAL.equalsIgnoreCase(resolution)) {
						return false;
					}
					return true;
				}

				@Override
				public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
					//We simply insert it here 
					capabilities.add(0, hostedCapability);
					return 0;
				}

				@Override
				public Map<Resource, Wiring> getWirings() {
					//We don't want to assume any existing wirings
					return Map.of();
				}

				@Override
				public List<Capability> findProviders(Requirement requirement) {
					System.out.println("findProviders: " + requirement);
					List<Capability> providersInTarget = PDECore.findProvidersInTarget(requirement);
					if (providersInTarget.isEmpty()) {
						List<Capability> platform = PDECore.findProvidersInRunningPlatform(requirement);
						System.out.println("From platform " + platform);
						return platform;
					}
					System.out.println("From target: " + providersInTarget);
					return providersInTarget;
				}

				@Override
				public Collection<Resource> getMandatoryResources() {
					return addedRuntimeBundles.stream().map(Resource.class::cast).toList();
				}
			});
			System.out.println("--- Resolved by OSGI resolver ---");
			for (Entry<Resource, List<Wire>> entry : map.entrySet()) {
				System.out.println(" - " + entry.getKey());
			}
		} catch (ResolutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("-----");

		Set<BundleDescription> runtimeRequirements = DependencyManager.findRequirementsClosure(addedRuntimeBundles);
		System.out.println("Dependency manager result:");
		for (BundleDescription bundleDescription : runtimeRequirements) {
			System.out.println("- " + bundleDescription);
		}
		addAbsentRequirements(runtimeRequirements, collectedModels, startLevelMap);
	}

	@SuppressWarnings("restriction")
	public static Collection<String> getRequiredJunitRuntimeEclipsePlugins(ILaunchConfiguration configuration) {
		org.eclipse.jdt.internal.junit.launcher.ITestKind testKind = org.eclipse.jdt.internal.junit.launcher.JUnitLaunchConfigurationConstants.getTestRunnerKind(configuration);
		if (testKind.isNull()) {
			return List.of();
		}
		switch (testKind.getId()) {
			case org.eclipse.jdt.internal.junit.launcher.TestKindRegistry.JUNIT3_TEST_KIND_ID -> {
				return List.of(PDE_JUNIT_RUNTIME);
			} // Nothing to add for JUnit-3
			case org.eclipse.jdt.internal.junit.launcher.TestKindRegistry.JUNIT4_TEST_KIND_ID -> {
				return List.of(PDE_JUNIT_RUNTIME,JUNIT4_JDT_RUNTIME_PLUGIN);
			}
			case org.eclipse.jdt.internal.junit.launcher.TestKindRegistry.JUNIT5_TEST_KIND_ID -> {
				return List.of(PDE_JUNIT_RUNTIME, JUNIT5_JDT_RUNTIME_PLUGIN);
			}
			default -> throw new IllegalArgumentException("Unsupported junit test kind: " + testKind.getId()); //$NON-NLS-1$
		}
	}

	private static Set<BundleDescription> addAbsentRequirements(Collection<String> requirements, Map<String, List<IPluginModelBase>> collectedModels, Map<IPluginModelBase, String> startLevelMap) throws CoreException {
		Set<BundleDescription> addedRequirements = new LinkedHashSet<>();
		for (String id : requirements) {
			List<IPluginModelBase> models = collectedModels.computeIfAbsent(id, k -> new ArrayList<>());
			if (models.stream().noneMatch(p -> p.getBundleDescription().isResolved())) {
				IPluginModelBase model = findRequiredPluginInTargetOrHost(PluginRegistry.findModel(id), plugins -> plugins.max(PDECore.VERSION), id);
				models.add(model);
				BundleLauncherHelper.addDefaultStartingBundle(startLevelMap, model);
				addedRequirements.add(model.getBundleDescription());
			}
		}
		return addedRequirements;
	}

	private static void addAbsentRequirements(Set<BundleDescription> requirements, Map<String, List<IPluginModelBase>> collectedModels, Map<IPluginModelBase, String> startLevelMap) throws CoreException {
		for (BundleRevision bundle : requirements) {
			String id = bundle.getSymbolicName();
			List<IPluginModelBase> models = collectedModels.computeIfAbsent(id, k -> new ArrayList<>());
			if (models.stream().map(IPluginModelBase::getBundleDescription).noneMatch(b -> b.isResolved() && b.getVersion().equals(bundle.getVersion()))) {
				IPluginModelBase model = findRequiredPluginInTargetOrHost(PluginRegistry.findModel(bundle), plgs -> plgs.filter(p -> p.getBundleDescription() == bundle).findFirst(), id);
				models.add(model);
				BundleLauncherHelper.addDefaultStartingBundle(startLevelMap, model);
			}
		}
	}

	private static IPluginModelBase findRequiredPluginInTargetOrHost(IPluginModelBase model, Function<Stream<IPluginModelBase>, Optional<IPluginModelBase>> pluginSelector, String id) throws CoreException {
		if (model == null || !model.getBundleDescription().isResolved()) {
			// prefer bundle from host over unresolved bundle from target
			model = pluginSelector.apply(PDECore.getDefault().findPluginsInHost(id)) //
					.orElseThrow(() -> new CoreException(Status.error(NLS.bind(PDEMessages.JUnitLaunchConfiguration_error_missingPlugin, id))));
		}
		return model;
	}

}
