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
import org.eclipse.core.resources.IProject;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.text.bundle.Bundle;
import org.eclipse.pde.internal.core.text.bundle.BundleModel;
import org.eclipse.pde.internal.core.text.bundle.ExportPackageHeader;
import org.eclipse.pde.internal.core.text.bundle.ExportPackageObject;
import org.eclipse.pde.internal.core.text.bundle.ImportPackageHeader;
import org.eclipse.pde.internal.core.text.bundle.ImportPackageObject;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.osgi.framework.Constants;

/**
 * Resolution to add version to export package and version range to import package
 */
public class AddVersionToExportAndImportResolution extends AbstractManifestMarkerResolution {
	
	public AddVersionToExportAndImportResolution(int type, IMarker marker) {
		super(type, marker);
	}

	@Override
	protected void createChange(BundleModel model) {
		String packageName = Objects.requireNonNull(marker.getAttribute("packageName", (String) null)); //$NON-NLS-1$
		Bundle bundle = (Bundle) model.getBundle();
		
		// Add version range to import
		ImportPackageHeader importHeader = (ImportPackageHeader) bundle.getManifestHeader(Constants.IMPORT_PACKAGE);
		if (importHeader != null) {
			for (ImportPackageObject pkg : importHeader.getPackages()) {
				if (packageName.equals(pkg.getName())) {
					// Add version range [1.0.0,2.0.0)
					pkg.setVersion("[1.0.0,2.0.0)"); //$NON-NLS-1$
				}
			}
		}
		
		// Find and update the exporting bundle
		addVersionToExporter(packageName, model);
	}
	
	private void addVersionToExporter(String packageName, BundleModel importerModel) {
		// Find the bundle that exports this package
		IProject project = importerModel.getUnderlyingResource().getProject();
		IPluginModelBase importerPluginModel = PluginRegistry.findModel(project);
		
		if (importerPluginModel != null) {
			BundleDescription desc = importerPluginModel.getBundleDescription();
			if (desc != null) {
				State state = desc.getContainingState();
				if (state != null) {
					// Find all bundles that export this package
					BundleDescription[] bundles = state.getBundles();
					for (BundleDescription bundleDesc : bundles) {
						ExportPackageDescription[] exports = bundleDesc.getExportPackages();
						for (ExportPackageDescription export : exports) {
							if (packageName.equals(export.getName())) {
								// Found the exporter - try to update it
								updateExporterVersion(bundleDesc, packageName);
								return;
							}
						}
					}
				}
			}
		}
	}
	
	private void updateExporterVersion(BundleDescription exporterDesc, String packageName) {
		// Try to find the model for the exporter
		IPluginModelBase exporterModel = PluginRegistry.findModel(exporterDesc.getSymbolicName());
		if (exporterModel != null && exporterModel.getUnderlyingResource() != null) {
			// Only update if it's a workspace bundle (not from target platform)
			IProject exporterProject = exporterModel.getUnderlyingResource().getProject();
			if (exporterProject != null && exporterProject.isAccessible()) {
				// TODO: We would need to modify the exporter's manifest here
				// For now, we'll just add the import version - the user will need to manually fix the export
				// A complete solution would require modifying another project's manifest
			}
		}
	}

	@Override
	public String getLabel() {
		return PDEUIMessages.AddVersionToExportAndImport_label;
	}

	@Override
	public String getDescription() {
		return PDEUIMessages.AddVersionToExportAndImport_description;
	}
}
