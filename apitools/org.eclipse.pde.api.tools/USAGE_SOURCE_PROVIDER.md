# BaseApiAnalyzer Source Provider Usage

This document describes how to use the `BaseApiAnalyzer.setComponentSource()` method to provide source code for API analysis without requiring a full Java project.

## Overview

The `BaseApiAnalyzer` can now work with source code provided externally, without requiring a workspace Java project. This is useful for scenarios like:
- Analyzing JAR files with separate source archives
- Maven/Gradle mojos that work directly with build artifacts
- Standalone API analysis tools

## API

### ISourceProvider Interface

```java
@FunctionalInterface
public interface ISourceProvider {
    /**
     * Returns the source content for the given fully qualified type name.
     * 
     * @param typeName the fully qualified type name (e.g., "org.example.MyClass")
     * @return the source content as a character array, or null if source is not available
     */
    char[] getSource(String typeName);
}
```

### setComponentSource Method

```java
public void setComponentSource(ISourceProvider sourceProvider, Map<String, String> compilerOptions)
```

Parameters:
- `sourceProvider`: Implementation that provides source content for type names (can be null to clear)
- `compilerOptions`: Compiler options for AST parsing (can be null for defaults). Common options:
  - `JavaCore.COMPILER_SOURCE`
  - `JavaCore.COMPILER_COMPLIANCE`
  - `JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM`

## Example Usage

### Basic Example with File-based Source

```java
import org.eclipse.pde.api.tools.internal.builder.BaseApiAnalyzer;
import org.eclipse.jdt.core.JavaCore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

// Create analyzer
BaseApiAnalyzer analyzer = new BaseApiAnalyzer();

// Configure source provider
File sourceRoot = new File("/path/to/source");
BaseApiAnalyzer.ISourceProvider sourceProvider = typeName -> {
    // Convert type name to file path: org.example.MyClass -> org/example/MyClass.java
    String filePath = typeName.replace('.', '/') + ".java";
    File sourceFile = new File(sourceRoot, filePath);
    
    if (!sourceFile.exists()) {
        return null;
    }
    
    try {
        String content = Files.readString(sourceFile.toPath());
        return content.toCharArray();
    } catch (IOException e) {
        e.printStackTrace();
        return null;
    }
};

// Configure compiler options (Java 17)
Map<String, String> compilerOptions = new HashMap<>();
compilerOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
compilerOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);
compilerOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_17);

// Set the source provider
analyzer.setComponentSource(sourceProvider, compilerOptions);

// Now use the analyzer normally
// analyzer.analyzeComponent(...);
```

### Example with Source JAR

```java
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

File sourceJar = new File("/path/to/project-sources.jar");
JarFile jarFile = new JarFile(sourceJar);

BaseApiAnalyzer.ISourceProvider sourceProvider = typeName -> {
    // Convert type name to JAR entry path
    String entryPath = typeName.replace('.', '/') + ".java";
    JarEntry entry = jarFile.getJarEntry(entryPath);
    
    if (entry == null) {
        return null;
    }
    
    try {
        byte[] bytes = jarFile.getInputStream(entry).readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8).toCharArray();
    } catch (IOException e) {
        e.printStackTrace();
        return null;
    }
};

analyzer.setComponentSource(sourceProvider, compilerOptions);
```

### Maven Mojo Example

```java
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;

public class ApiAnalysisMojo extends AbstractMojo {
    
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    
    public void execute() throws MojoExecutionException {
        BaseApiAnalyzer analyzer = new BaseApiAnalyzer();
        
        // Get source directories from Maven project
        List<String> sourceRoots = project.getCompileSourceRoots();
        
        BaseApiAnalyzer.ISourceProvider sourceProvider = typeName -> {
            String filePath = typeName.replace('.', '/') + ".java";
            
            for (String sourceRoot : sourceRoots) {
                File sourceFile = new File(sourceRoot, filePath);
                if (sourceFile.exists()) {
                    try {
                        return Files.readString(sourceFile.toPath()).toCharArray();
                    } catch (IOException e) {
                        // Try next source root
                    }
                }
            }
            return null;
        };
        
        // Configure compiler options from Maven properties
        Map<String, String> compilerOptions = new HashMap<>();
        compilerOptions.put(JavaCore.COMPILER_SOURCE, project.getProperties().getProperty("maven.compiler.source", "17"));
        compilerOptions.put(JavaCore.COMPILER_COMPLIANCE, project.getProperties().getProperty("maven.compiler.source", "17"));
        
        analyzer.setComponentSource(sourceProvider, compilerOptions);
        
        // Perform analysis...
    }
}
```

## Features Enabled

When a source provider is configured, the following features work without a Java project:

1. **@since Tag Checking**: The analyzer can parse source files to check for missing or incorrect `@since` tags
2. **Compatibility Problem Reporting**: Basic compatibility problems are reported with type-level information
3. **AST-based Analysis**: Any analysis that requires parsing source code can work

## Limitations

When using source provider instead of a Java project:

1. **Location Precision**: Line numbers and character positions may be less precise without workspace resources
2. **Binary-only Analysis**: Some features that require binary type information still need the compiled classes
3. **Cross-references**: Resolving references between types works best when all sources are available
4. **Resource Information**: Problem markers cannot be attached to workspace resources

## Default Behavior

If `setComponentSource` is not called or is called with `null`, the analyzer falls back to the traditional Java project-based approach. This ensures backward compatibility with existing code.

## Thread Safety

The source provider should be thread-safe if the analyzer will be used from multiple threads.

## Related Issues

- Issue #785: Initial feature request for source provider support
- Issue #782: Enhanced ApiTools Mojo that motivated this feature
