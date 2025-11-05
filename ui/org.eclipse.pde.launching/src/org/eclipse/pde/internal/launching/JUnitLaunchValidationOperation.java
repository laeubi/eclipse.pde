/*******************************************************************************
 * Copyright (c) 2025 Simeon Andreev and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Simeon Andreev - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.launching;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.junit.launcher.TestKindRegistry;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.launching.launcher.LaunchValidationOperation;
import org.osgi.framework.Version;

public class JUnitLaunchValidationOperation extends LaunchValidationOperation {

	private final Map<Object, Object[]> fErrors = new HashMap<>(2);

	public JUnitLaunchValidationOperation(ILaunchConfiguration configuration, Set<IPluginModelBase> models) {
		super(configuration, models, null);
	}

	@Override
	public void run(IProgressMonitor monitor) throws CoreException {
		// First run parent validation to resolve the state
		super.run(monitor);
		
		// Then check for JUnit version conflicts in the resolved state
		try {
			checkJunitVersionConflicts(fLaunchConfiguration);
		} catch (CoreException e) {
			PDELaunchingPlugin.log(e);
		}
	}

	@SuppressWarnings("restriction")
	private void checkJunitVersionConflicts(ILaunchConfiguration configuration) throws CoreException {
		org.eclipse.jdt.internal.junit.launcher.ITestKind testKind = org.eclipse.jdt.internal.junit.launcher.JUnitLaunchConfigurationConstants.getTestRunnerKind(configuration);
		if (testKind.isNull()) {
			return;
		}
		
		String testKindId = testKind.getId();
		switch (testKindId) {
			case TestKindRegistry.JUNIT3_TEST_KIND_ID, TestKindRegistry.JUNIT4_TEST_KIND_ID -> {
				// nothing to check for JUnit 3 and 4
			}
			case TestKindRegistry.JUNIT5_TEST_KIND_ID -> {
				checkForJUnit6InJUnit5Launch();
			}
			default -> throw new CoreException(Status.error("Unsupported test kind: " + testKindId)); //$NON-NLS-1$
		}
	}
	
	/**
	 * Check if JUnit 6+ bundles are included in a JUnit 5 launch.
	 * This can cause runtime errors like "org.junit.jupiter.engine.JupiterTestEngine not a subtype".
	 */
	private void checkForJUnit6InJUnit5Launch() {
		if (getState() == null) {
			return;
		}
		
		// Get all bundles in the resolved state
		BundleDescription[] bundles = getState().getBundles();
		
		// Find all junit-jupiter-engine bundles with major version >= 6
		for (BundleDescription bundle : bundles) {
			if ("junit-jupiter-engine".equals(bundle.getSymbolicName())) { //$NON-NLS-1$
				Version version = bundle.getVersion();
				if (version.getMajor() >= 6) {
					// Found a JUnit 6+ bundle, now find which bundle required it
					String requiringBundle = findBundleRequiringJUnit6(bundles, bundle);
					String message;
					if (requiringBundle != null) {
						message = NLS.bind(PDEMessages.JUnitLaunchConfiguration_error_JUnitLaunchAndRuntimeMissmatch_withRequiringBundle, 
								new Object[] { Integer.valueOf(5), Integer.valueOf(version.getMajor()), requiringBundle });
					} else {
						message = NLS.bind(PDEMessages.JUnitLaunchConfiguration_error_JUnitLaunchAndRuntimeMissmatch, 
								Integer.valueOf(5), Integer.valueOf(version.getMajor()));
					}
					addError(message);
				}
			}
		}
	}
	
	/**
	 * Find which bundle is requiring a JUnit 6 bundle.
	 * This helps users understand why JUnit 6 was included in their JUnit 5 launch.
	 * 
	 * @param bundles all bundles in the resolved state
	 * @param junitBundle the JUnit 6 bundle to find requirements for
	 * @return the symbolic name of the bundle requiring the JUnit 6 bundle, or null if not found
	 */
	private String findBundleRequiringJUnit6(BundleDescription[] bundles, BundleDescription junitBundle) {
		for (BundleDescription bundle : bundles) {
			if (bundle.isResolved()) {
				// Check Require-Bundle dependencies
				BundleDescription[] requiredBundles = bundle.getResolvedRequires();
				for (BundleDescription required : requiredBundles) {
					if (required.equals(junitBundle)) {
						return bundle.getSymbolicName();
					}
				}
				
				// Check Import-Package dependencies
				// If a bundle imports packages from junit-jupiter-engine, it contributes to pulling it in
				org.eclipse.osgi.service.resolver.ExportPackageDescription[] imports = bundle.getResolvedImports();
				for (org.eclipse.osgi.service.resolver.ExportPackageDescription export : imports) {
					if (export.getExporter().equals(junitBundle)) {
						return bundle.getSymbolicName();
					}
				}
			}
		}
		return null;
	}

	private void addError(String message) {
		fErrors.put(message.replaceAll("\\R", " "), null); //$NON-NLS-1$//$NON-NLS-2$
	}

	@Override
	public boolean hasErrors() {
		return super.hasErrors() || !fErrors.isEmpty();
	}

	@Override
	public Map<Object, Object[]> getInput() {
		Map<Object, Object[]> map = new LinkedHashMap<>();
		// Add parent validation errors first
		map.putAll(super.getInput());
		// Then add JUnit-specific validation errors
		map.putAll(fErrors);
		return map;
	}
}
