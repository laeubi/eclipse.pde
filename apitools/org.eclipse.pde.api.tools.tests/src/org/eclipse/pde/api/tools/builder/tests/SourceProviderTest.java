/*******************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation for issue #785
 *******************************************************************************/
package org.eclipse.pde.api.tools.builder.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.pde.api.tools.internal.builder.BaseApiAnalyzer;
import org.junit.Test;

/**
 * Tests for the BaseApiAnalyzer source provider functionality (issue #785)
 */
public class SourceProviderTest {

	/**
	 * Tests that ISourceProvider can be created and used
	 */
	@Test
	public void testSourceProviderInterface() {
		// Simple source provider implementation
		BaseApiAnalyzer.ISourceProvider provider = typeName -> {
			if ("test.MyClass".equals(typeName)) {
				return "package test; public class MyClass { }".toCharArray();
			}
			return null;
		};
		
		char[] source = provider.getSource("test.MyClass");
		assertNotNull("Source should be returned", source);
		assertTrue("Source should contain class definition", new String(source).contains("class MyClass"));
	}

	/**
	 * Tests that setComponentSource can be called with null
	 */
	@Test
	public void testSetComponentSourceNull() {
		BaseApiAnalyzer analyzer = new BaseApiAnalyzer();
		
		// Should not throw exception
		analyzer.setComponentSource(null, null);
	}

	/**
	 * Tests that setComponentSource can be called with a provider
	 */
	@Test
	public void testSetComponentSourceWithProvider() {
		BaseApiAnalyzer analyzer = new BaseApiAnalyzer();
		
		BaseApiAnalyzer.ISourceProvider provider = typeName -> null;
		Map<String, String> options = new HashMap<>();
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
		
		// Should not throw exception
		analyzer.setComponentSource(provider, options);
	}

	/**
	 * Tests that AST can be created from source content directly
	 */
	@Test
	public void testASTParsingFromSource() {
		String sourceCode = """
			package org.example;
			
			/**
			 * Test class
			 * @since 1.0
			 */
			public class TestClass {
				public void method() {
				}
			}
			""";
		
		// Create AST parser similar to how BaseApiAnalyzer does it
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(sourceCode.toCharArray());
		parser.setResolveBindings(false);
		
		Map<String, String> options = new HashMap<>();
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);
		options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
		parser.setCompilerOptions(options);
		parser.setUnitName("TestClass.java");
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		assertNotNull("Compilation unit should be created", cu);
		
		// Verify we can navigate the AST
		assertTrue("Should have at least one type declaration", cu.types().size() > 0);
	}

	/**
	 * Tests a realistic source provider that simulates file-based lookup
	 */
	@Test
	public void testFileBasedSourceProvider() {
		// Simulate a source provider that would read from files
		Map<String, String> mockSourceFiles = new HashMap<>();
		mockSourceFiles.put("org.example.ClassA", 
			"package org.example;\npublic class ClassA { }");
		mockSourceFiles.put("org.example.ClassB", 
			"package org.example;\npublic class ClassB { }");
		
		BaseApiAnalyzer.ISourceProvider provider = typeName -> {
			String source = mockSourceFiles.get(typeName);
			return source != null ? source.toCharArray() : null;
		};
		
		// Test retrieval
		assertNotNull("Should find ClassA", provider.getSource("org.example.ClassA"));
		assertNotNull("Should find ClassB", provider.getSource("org.example.ClassB"));
		assertTrue("Should return null for unknown class", 
			provider.getSource("org.example.Unknown") == null);
	}
}
