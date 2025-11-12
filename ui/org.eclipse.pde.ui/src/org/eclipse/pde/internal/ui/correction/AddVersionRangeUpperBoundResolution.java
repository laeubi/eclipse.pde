/*******************************************************************************
 *  Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     Eclipse Copilot - initial API and implementation
 *******************************************************************************/

package org.eclipse.pde.internal.ui.correction;

import java.util.Objects;

import org.eclipse.core.resources.IMarker;
import org.eclipse.pde.internal.core.text.bundle.Bundle;
import org.eclipse.pde.internal.core.text.bundle.BundleModel;
import org.eclipse.pde.internal.core.text.bundle.ImportPackageHeader;
import org.eclipse.pde.internal.core.text.bundle.ImportPackageObject;
import org.eclipse.pde.internal.core.text.bundle.RequireBundleHeader;
import org.eclipse.pde.internal.core.text.bundle.RequireBundleObject;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

/**
 * Resolution to add upper bound to version ranges following OSGi semantic versioning
 */
public class AddVersionRangeUpperBoundResolution extends AbstractManifestMarkerResolution {
	
	private final boolean isPackage;
	
	public AddVersionRangeUpperBoundResolution(int type, IMarker marker, boolean isPackage) {
		super(type, marker);
		this.isPackage = isPackage;
	}

	@Override
	protected void createChange(BundleModel model) {
		Bundle bundle = (Bundle) model.getBundle();
		
		if (isPackage) {
			String packageName = Objects.requireNonNull(marker.getAttribute("packageName", (String) null)); //$NON-NLS-1$
			ImportPackageHeader header = (ImportPackageHeader) bundle.getManifestHeader(Constants.IMPORT_PACKAGE);
			if (header != null) {
				for (ImportPackageObject pkg : header.getPackages()) {
					if (packageName.equals(pkg.getName())) {
						addUpperBoundToPackage(pkg);
					}
				}
			}
		} else {
			String bundleId = Objects.requireNonNull(marker.getAttribute("bundleId", (String) null)); //$NON-NLS-1$
			RequireBundleHeader header = (RequireBundleHeader) bundle.getManifestHeader(Constants.REQUIRE_BUNDLE);
			if (header != null) {
				for (RequireBundleObject requiredBundle : header.getRequiredBundles()) {
					if (bundleId.equals(requiredBundle.getId())) {
						addUpperBoundToBundle(requiredBundle);
					}
				}
			}
		}
	}
	
	private void addUpperBoundToPackage(ImportPackageObject pkg) {
		String currentVersion = pkg.getVersion();
		String newVersion = computeVersionRangeWithUpperBound(currentVersion);
		pkg.setVersion(newVersion);
	}
	
	private void addUpperBoundToBundle(RequireBundleObject bundle) {
		String currentVersion = bundle.getVersion();
		String newVersion = computeVersionRangeWithUpperBound(currentVersion);
		bundle.setVersion(newVersion);
	}
	
	/**
	 * Computes a version range with upper bound following OSGi semantic versioning.
	 * For version "1.2.3", returns "[1.2.3,2.0.0)"
	 * For version "[1.2.3,)", returns "[1.2.3,2.0.0)"
	 */
	private String computeVersionRangeWithUpperBound(String currentVersion) {
		if (currentVersion == null || currentVersion.trim().isEmpty()) {
			// No version at all - default to [1.0.0,2.0.0)
			return "[1.0.0,2.0.0)"; //$NON-NLS-1$
		}
		
		try {
			VersionRange range = new VersionRange(currentVersion);
			Version lower = range.getLeft();
			
			// Compute upper bound: next major version
			int newMajor = lower.getMajor() + 1;
			Version upper = new Version(newMajor, 0, 0);
			
			// Create new range: [lower, upper)
			return "[" + lower.toString() + "," + upper.toString() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		} catch (IllegalArgumentException e) {
			// Invalid version - default to [1.0.0,2.0.0)
			return "[1.0.0,2.0.0)"; //$NON-NLS-1$
		}
	}

	@Override
	public String getLabel() {
		return isPackage 
			? PDEUIMessages.AddVersionRangeUpperBound_ImportPackage
			: PDEUIMessages.AddVersionRangeUpperBound_RequireBundle;
	}

}
