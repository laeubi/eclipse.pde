/*******************************************************************************
 *  Copyright (c) 2021, 2023 Julian Honnen
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     Julian Honnen <julian.honnen@vector.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.core.tests.internal.core.builders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.internal.core.builders.PDEMarkerFactory;
import org.eclipse.pde.internal.core.ibundle.IBundle;
import org.eclipse.pde.internal.core.ibundle.IBundlePluginModelBase;
import org.eclipse.pde.internal.ui.util.ModelModification;
import org.eclipse.pde.internal.ui.util.PDEModelUtility;
import org.eclipse.pde.ui.tests.util.ProjectUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Constants;

public class BundleErrorReporterTest {

	private IFile manifest;

	@Before
	public void setup() throws Exception {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(getClass().getName());
		manifest = project.getFile("META-INF/MANIFEST.MF");
	}

	@Test
	public void testErrorOnUnresolvedJrePackage() throws Exception {
		IProject project = ProjectUtils.createPluginProject(manifest.getProject().getName(),
				JavaRuntime.getExecutionEnvironmentsManager().getEnvironment("JavaSE-1.8")).getProject();

		IFile manifest = project.getFile("META-INF/MANIFEST.MF");
		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				bundle.setHeader(Constants.IMPORT_PACKAGE, "java.lang.module");
			}
		}, null);

		assertThat(findUnresolvedImportsMarkers()).hasSize(1);

		ModelModification modification = new ModelModification(manifest) {
			@SuppressWarnings("deprecation")
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				bundle.setHeader(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT, "JavaSE-11");
			}
		};
		PDEModelUtility.modifyModel(modification, null);

		assertThat(findUnresolvedImportsMarkers()).isEmpty();
	}

	private List<IMarker> findUnresolvedImportsMarkers() throws CoreException {
		manifest.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		return Arrays.stream(manifest.findMarkers(PDEMarkerFactory.MARKER_ID, false, 0))
				.filter(m -> m.getAttribute(IMarker.SEVERITY, -1) == IMarker.SEVERITY_ERROR).toList();
	}

	@After
	public void tearDown() throws Exception {
		if (manifest.getProject().exists()) {
			manifest.getProject().delete(true, null);
		}
	}

	@Test
	public void testWarningOnMissingUpperBoundForRequireBundle() throws Exception {
		ProjectUtils.createPluginProject(manifest.getProject().getName());

		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				// Set a version range without upper bound
				bundle.setHeader(Constants.REQUIRE_BUNDLE, "org.eclipse.core.runtime;bundle-version=\"3.0.0\"");
			}
		}, null);

		List<IMarker> markers = findMarkersWithCompilerKey("compilers.p.missing-upper-version-bound-require-bundle");
		assertThat(markers).hasSize(1);
		assertThat(markers.get(0).getAttribute(IMarker.MESSAGE, "")).contains("missing an upper bound");
	}

	@Test
	public void testNoWarningWhenRequireBundleHasUpperBound() throws Exception {
		ProjectUtils.createPluginProject(manifest.getProject().getName());

		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				// Set a proper version range with upper bound
				bundle.setHeader(Constants.REQUIRE_BUNDLE, "org.eclipse.core.runtime;bundle-version=\"[3.0.0,4.0.0)\"");
			}
		}, null);

		List<IMarker> markers = findMarkersWithCompilerKey("compilers.p.missing-upper-version-bound-require-bundle");
		assertThat(markers).isEmpty();
	}

	@Test
	public void testWarningOnMissingUpperBoundForImportPackage() throws Exception {
		ProjectUtils.createPluginProject(manifest.getProject().getName());

		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				// Set a version range without upper bound
				bundle.setHeader(Constants.IMPORT_PACKAGE, "org.osgi.framework;version=\"1.0.0\"");
			}
		}, null);

		List<IMarker> markers = findMarkersWithCompilerKey("compilers.p.missing-upper-version-bound-import-package");
		assertThat(markers).hasSize(1);
		assertThat(markers.get(0).getAttribute(IMarker.MESSAGE, "")).contains("missing an upper bound");
	}

	@Test
	public void testNoWarningWhenImportPackageHasUpperBound() throws Exception {
		ProjectUtils.createPluginProject(manifest.getProject().getName());

		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				// Set a proper version range with upper bound
				bundle.setHeader(Constants.IMPORT_PACKAGE, "org.osgi.framework;version=\"[1.0.0,2.0.0)\"");
			}
		}, null);

		List<IMarker> markers = findMarkersWithCompilerKey("compilers.p.missing-upper-version-bound-import-package");
		assertThat(markers).isEmpty();
	}

	@Test
	public void testWarningOnRequireBundleWithOpenEndedRange() throws Exception {
		ProjectUtils.createPluginProject(manifest.getProject().getName());

		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				// Set an open-ended range [1.0.0,)
				bundle.setHeader(Constants.REQUIRE_BUNDLE, "org.eclipse.core.runtime;bundle-version=\"[1.0.0,)\"");
			}
		}, null);

		List<IMarker> markers = findMarkersWithCompilerKey("compilers.p.missing-upper-version-bound-require-bundle");
		assertThat(markers).hasSize(1);
	}

	@Test
	public void testWarningOnImportPackageWithOpenEndedRange() throws Exception {
		ProjectUtils.createPluginProject(manifest.getProject().getName());

		PDEModelUtility.modifyModel(new ModelModification(manifest) {
			@Override
			protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
				IBundlePluginModelBase modelBase = (IBundlePluginModelBase) model;
				IBundle bundle = modelBase.getBundleModel().getBundle();
				// Set an open-ended range [1.0.0,)
				bundle.setHeader(Constants.IMPORT_PACKAGE, "org.osgi.framework;version=\"[1.0.0,)\"");
			}
		}, null);

		List<IMarker> markers = findMarkersWithCompilerKey("compilers.p.missing-upper-version-bound-import-package");
		assertThat(markers).hasSize(1);
	}

	private List<IMarker> findMarkersWithCompilerKey(String compilerKey) throws CoreException {
		manifest.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		return Arrays.stream(manifest.findMarkers(PDEMarkerFactory.MARKER_ID, true, 0))
				.filter(m -> compilerKey.equals(m.getAttribute("compilerKey", null))).toList();
	}

}
