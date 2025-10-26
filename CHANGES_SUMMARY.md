# Summary of Changes for Issue #785

## Problem Statement

The BaseApiAnalyzer in Eclipse PDE API Tools currently requires a full Java project to perform API analysis, particularly for features like:
- Checking @since tags in source code
- Reporting line numbers for compatibility problems

This creates challenges for use cases like:
- Maven/Gradle mojos that work directly with JAR files
- Standalone API analysis tools
- Analysis scenarios where recompilation with different settings is undesirable

## Solution Overview

Added a new `setComponentSource()` method to `BaseApiAnalyzer` that allows external provision of source code without requiring a workspace Java project. This enables API analysis on built artifacts (JAR files) when source is available separately.

## Key Changes

### 1. New Public API

**ISourceProvider Interface** (functional interface)
```java
@FunctionalInterface
public interface ISourceProvider {
    char[] getSource(String typeName);
}
```

**setComponentSource Method**
```java
public void setComponentSource(ISourceProvider sourceProvider, Map<String, String> compilerOptions)
```

### 2. Internal Implementation Changes

**New Fields:**
- `fSourceProvider` - Stores the source provider instance
- `fCompilerOptions` - Stores compiler options for AST parsing

**New Methods:**
- `createASTFromSource()` - Creates AST from source content without IJavaProject
- `checkSinceTagsWithSourceProvider()` - Fallback for since tag checking without Java project
- `checkSinceTagInAST()` - Common logic extracted for AST-based since tag checking

**Modified Methods:**
- `checkSinceTags()` - Now tries source provider when Java project is unavailable
- `createSinceTagProblem()` - Handles null member (when using source provider)
- `createCompatibilityProblem()` - Reports problems even without precise location info

### 3. Documentation

- Created `USAGE_SOURCE_PROVIDER.md` with comprehensive examples
- Examples include: file-based sources, JAR sources, Maven mojo integration
- Documents features, limitations, and default behavior

### 4. Tests

- Created `SourceProviderTest` with unit tests covering:
  - ISourceProvider interface usage
  - setComponentSource() method
  - AST parsing from source content
  - File-based source provider patterns

## Design Decisions

1. **Functional Interface**: ISourceProvider is a functional interface for easy lambda usage
2. **Optional Compiler Options**: Allows callers to specify Java version or use defaults (Java 17)
3. **Graceful Fallback**: When source provider is not set, falls back to traditional Java project approach
4. **Backward Compatibility**: Existing code continues to work without changes
5. **Null Handling**: All new code handles null gracefully to prevent NPEs
6. **Limited Precision**: When using source provider, line numbers may be less precise, but problems are still reported

## Benefits

1. **Enables New Use Cases**: Maven/Gradle mojos can analyze JAR files with separate sources
2. **No Recompilation**: Can analyze pre-built artifacts without rebuilding
3. **Flexible Source Location**: Source can come from files, JARs, databases, etc.
4. **Backward Compatible**: No breaking changes to existing API
5. **Testable**: Can be tested without full workspace setup

## Limitations

When using source provider instead of Java project:
- Line numbers may be less precise (no workspace resource information)
- Some features that require binary type information still need compiled classes
- Cross-references work best when all sources are available
- Problem markers cannot be attached to workspace resources

## Impact Analysis

**Risk Level**: Low
- All changes are additive (no breaking changes)
- Existing functionality preserved with null checks
- New code paths only activated when source provider is explicitly set

**Areas Affected:**
- BaseApiAnalyzer class (primary changes)
- @since tag checking logic
- Compatibility problem reporting

**Testing:**
- New unit tests added
- Existing tests should continue to pass (backward compatible)
- Need full test suite run to confirm no regressions

## Next Steps

1. **Code Review**: Review this implementation for correctness and design
2. **Integration Testing**: Validate with actual Maven mojo implementation
3. **Documentation Review**: Ensure documentation is clear and complete
4. **Performance Testing**: Verify no performance regression in existing workflows
5. **Consider Future Enhancements**: 
   - Support for reading line numbers from debug symbols (mentioned in issue comment)
   - More sophisticated AST caching if performance becomes an issue

## Files Changed

1. `apitools/org.eclipse.pde.api.tools/src/org/eclipse/pde/api/tools/internal/builder/BaseApiAnalyzer.java` - Core implementation
2. `apitools/org.eclipse.pde.api.tools/USAGE_SOURCE_PROVIDER.md` - Usage documentation
3. `apitools/org.eclipse.pde.api.tools.tests/src/org/eclipse/pde/api/tools/builder/tests/SourceProviderTest.java` - Unit tests

## Related Issues

- Issue #785: This issue
- Issue #782: Enhanced ApiTools Mojo that motivated this feature
