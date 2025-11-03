/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     See git history
 *******************************************************************************/
package org.eclipse.pde.junit.runtime.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.util.Collections;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.pde.ui.tests.util.ProjectUtils;
import org.eclipse.pde.ui.tests.util.TargetPlatformUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Test for issue #2082: Host bundles (even org.eclipse.osgi) leak into test
 * launch config
 * <p>
 * This test verifies that when launching a JUnit plugin test without
 * org.eclipse.pde.feature.group in the target platform, the system bundle
 * (org.eclipse.osgi) from the host is not exposed to avoid conflicts with the
 * target platform's system bundle.
 */
public class Issue2082LaunchTest {

	@ClassRule
	public static final TestRule CLEAR_WORKSPACE = ProjectUtils.DELETE_ALL_WORKSPACE_PROJECTS_BEFORE_AND_AFTER;

	@BeforeClass
	public static void setupProjects() throws Exception {
		// Set up a target platform similar to issue #2082 scenario:
		// - Include Eclipse platform bundles
		// - Exclude org.eclipse.pde.feature.group to reproduce the issue scenario
		// - Filter out PDE bundles (except core bundles needed for the test to run)
		Pattern pdeIDs = Pattern.compile("org\\.eclipse\\.pde\\.(ui|build|launching|junit).*");
		TargetPlatformUtil.setRunningPlatformSubSetAsTarget("Issue2082_target", b -> {
			// Exclude PDE UI/build/launching bundles but keep PDE core and test infrastructure
			return !pdeIDs.matcher(b.getSymbolicName()).find();
		});

		Bundle bundle = FrameworkUtil.getBundle(Issue2082LaunchTest.class);

		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		for (URL resource : Collections.list(bundle.findEntries("test-bundles", "verification.tests.issue2082", false))) {
			ProjectUtils.importTestProject(FileLocator.toFileURL(resource));
		}
		workspaceRoot.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
	}

	@Test
	public void testIssue2082_systemBundleNotDuplicated() throws Exception {
		// Test for https://github.com/eclipse-pde/eclipse.pde/issues/2082
		// Verify that launching a JUnit plugin test without org.eclipse.pde.feature.group
		// in the target platform does not cause the host's org.eclipse.osgi to leak
		// into the launch configuration, which would result in duplicate system bundles
		// and launch errors.

		IJavaProject project = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject("verification.tests.issue2082"));
		IType testClass = project.findType("verification.tests", "Test");
		assertNotNull("Test class should exist", testClass);
		Assert.assertTrue("Test class should be valid", testClass.exists());

		// Launch the test - this should succeed without errors
		ITestRunSession session = TestExecutionUtil.runTest(testClass);

		// The test is expected to fail (as per the test code), but the important
		// part is that the launch itself succeeds without duplicate bundle errors
		assertThat(session.getChildren()).hasSize(1);
		Result testResult = session.getTestResult(true);
		// We expect FAILURE because the test code calls fail(), but we do NOT
		// expect ERROR which would indicate a launch problem
		assertThat(testResult).isIn(Result.FAILURE, Result.OK);
	}
}
