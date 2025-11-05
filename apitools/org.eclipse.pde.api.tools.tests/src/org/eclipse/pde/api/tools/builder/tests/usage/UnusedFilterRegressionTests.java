/*******************************************************************************
 * Copyright (c) 2024 Eclipse contributors and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eclipse contributors - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.builder.tests.usage;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.pde.api.tools.internal.model.ApiModelFactory;
import org.eclipse.pde.api.tools.internal.problems.ApiProblemFactory;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.IApiBaselineManager;
import org.eclipse.pde.api.tools.internal.provisional.IApiFilterStore;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IElementDescriptor;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiBaseline;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblem;

import junit.framework.Test;

/**
 * Tests for regression in unused API problem filter detection
 * 
 * This test case is designed to reproduce the issue where API filters
 * are incorrectly reported as unused when they should be matching
 * actual problems.
 * 
 * @see https://github.com/eclipse-pde/eclipse.pde/issues/2096
 */
public class UnusedFilterRegressionTests extends UsageTest {

	public UnusedFilterRegressionTests(String name) {
		super(name);
	}

	/**
	 * @return the tests for this class
	 */
	public static Test suite() {
		return buildTestSuite(UnusedFilterRegressionTests.class);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// Create a baseline for comparison
		assertStubBaseline(BASELINE);
	}

	/**
	 * Asserts a stub {@link IApiBaseline} that contains all of the workspace
	 * projects as API components
	 *
	 * @param name the name for the baseline
	 */
	protected void assertStubBaseline(String name) {
		IApiBaselineManager manager = ApiPlugin.getDefault().getApiBaselineManager();
		IApiBaseline baseline = manager.getDefaultApiBaseline();
		if (baseline == null) {
			baseline = ApiModelFactory.newApiBaseline(name);
			manager.addApiBaseline(baseline);
			manager.setDefaultApiBaseline(baseline.getName());
		}
	}

	@Override
	protected void tearDown() throws Exception {
		removeBaseline(BASELINE);
		super.tearDown();
	}

	/**
	 * Removes the baseline with the given name
	 */
	private void removeBaseline(String name) {
		IApiBaselineManager manager = ApiPlugin.getDefault().getApiBaselineManager();
		IApiBaseline baseline = manager.getDefaultApiBaseline();
		if (baseline != null) {
			assertEquals("The given name should be the default baseline name", baseline.getName(), name);
			assertTrue("The baseline [" + name + "] should have been removed", manager.removeApiBaseline(name));
		}
	}

	@Override
	protected void setBuilderOptions() {
		super.setBuilderOptions();
		enableLeakOptions(true);
	}

	@Override
	protected int getDefaultProblemId() {
		return ApiProblemFactory.createProblemId(
				IApiProblem.CATEGORY_USAGE, 
				IElementDescriptor.TYPE,
				IApiProblem.UNSUPPORTED_TAG_USE, 
				IApiProblem.NO_FLAGS);
	}

	/**
	 * Test that API filters for extend restrictions are not incorrectly
	 * reported as unused.
	 * 
	 * This test simulates the scenario from JDT where classes extend
	 * ASTNode (which has @noextend) and have filters for this.
	 */
	public void testExtendRestrictionsFilterNotReportedUnused() throws Exception {
		// This is a regression test for issue #2096
		// We need to verify that when a class extends a restricted type
		// and has a filter for it, the filter is not reported as unused
		
		// For now, this is a placeholder that demonstrates the test structure
		// The actual implementation would require:
		// 1. Setting up baseline with restricted type
		// 2. Creating a workspace project with a class extending it
		// 3. Adding an API filter
		// 4. Running the builder
		// 5. Verifying no unused filter markers are created
		
		// TODO: Implement full test case
	}

	/**
	 * Test that API filters for implement restrictions are not incorrectly
	 * reported as unused.
	 * 
	 * This test simulates the scenario from JDT where classes implement
	 * interfaces with restrictions and have filters for this.
	 */
	public void testImplementRestrictionsFilterNotReportedUnused() throws Exception {
		// This is a regression test for issue #2096
		// Similar to testExtendRestrictionsFilterNotReportedUnused but for
		// implement restrictions
		
		// TODO: Implement full test case
	}
}
