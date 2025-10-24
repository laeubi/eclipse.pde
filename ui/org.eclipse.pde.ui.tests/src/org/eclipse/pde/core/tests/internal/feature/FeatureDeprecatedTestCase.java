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
package org.eclipse.pde.core.tests.internal.feature;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;

import org.eclipse.core.internal.runtime.XmlProcessorFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.core.feature.Feature;
import org.eclipse.pde.internal.core.feature.WorkspaceFeatureModel;
import org.eclipse.pde.internal.core.ifeature.IFeature;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class FeatureDeprecatedTestCase {

	@Test
	public void testDeprecatedAttributeWithMessage() throws Exception {
		IFeatureModel model = new WorkspaceFeatureModel();
		model.load();
		
		Feature feature = (Feature) model.getFeature();
		feature.setId("test.feature");
		feature.setVersion("1.0.0");
		feature.setDeprecated("This feature is deprecated. Use test.feature.v2 instead.");
		
		String xml = toXml(feature);
		
		// Verify the XML contains the deprecated attribute
		assert xml.contains("deprecated=\"This feature is deprecated. Use test.feature.v2 instead.\"");
		
		// Parse it back
		Feature parsedFeature = fromXml(xml);
		assertEquals("This feature is deprecated. Use test.feature.v2 instead.", parsedFeature.getDeprecated());
	}

	@Test
	public void testDeprecatedAttributeTrue() throws Exception {
		IFeatureModel model = new WorkspaceFeatureModel();
		model.load();
		
		Feature feature = (Feature) model.getFeature();
		feature.setId("test.feature");
		feature.setVersion("1.0.0");
		feature.setDeprecated("true");
		
		String xml = toXml(feature);
		
		// Verify the XML contains the deprecated attribute
		assert xml.contains("deprecated=\"true\"");
		
		// Parse it back
		Feature parsedFeature = fromXml(xml);
		assertEquals("true", parsedFeature.getDeprecated());
	}

	@Test
	public void testNoDeprecatedAttribute() throws Exception {
		IFeatureModel model = new WorkspaceFeatureModel();
		model.load();
		
		Feature feature = (Feature) model.getFeature();
		feature.setId("test.feature");
		feature.setVersion("1.0.0");
		// Don't set deprecated
		
		String xml = toXml(feature);
		
		// Verify the XML does not contain the deprecated attribute
		assert !xml.contains("deprecated=");
		
		// Parse it back
		Feature parsedFeature = fromXml(xml);
		assertNull(parsedFeature.getDeprecated());
	}

	@Test
	public void testDeprecatedAttributeClearing() throws Exception {
		IFeatureModel model = new WorkspaceFeatureModel();
		model.load();
		
		Feature feature = (Feature) model.getFeature();
		feature.setId("test.feature");
		feature.setVersion("1.0.0");
		feature.setDeprecated("Deprecated message");
		
		// Clear the deprecation
		feature.setDeprecated(null);
		
		String xml = toXml(feature);
		
		// Verify the XML does not contain the deprecated attribute
		assert !xml.contains("deprecated=");
		
		// Parse it back
		Feature parsedFeature = fromXml(xml);
		assertNull(parsedFeature.getDeprecated());
	}

	private String toXml(IFeature feature) {
		StringWriter sw = new StringWriter();
		feature.write("", new PrintWriter(sw));
		return sw.toString();
	}

	private Feature fromXml(String xml) throws Exception {
		DocumentBuilder builder = XmlProcessorFactory.createDocumentBuilderWithErrorOnDOCTYPE();
		InputSource is = new InputSource(new StringReader(xml));
		Document doc = builder.parse(is);

		assertNotNull(doc);

		NodeList nodes = doc.getElementsByTagName("feature");
		assertEquals(1, nodes.getLength());
		Node node = nodes.item(0);

		FeatureForTesting feature = new FeatureForTesting();
		IFeatureModel model = new WorkspaceFeatureModel();
		feature.setModel(model);
		feature.parse(node);
		return feature;
	}

	@SuppressWarnings("serial")
	public static class FeatureForTesting extends Feature {
		@Override
		public void parse(Node node) {
			super.parse(node);
		}
	}
}
