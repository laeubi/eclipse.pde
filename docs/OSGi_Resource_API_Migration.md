# OSGi Resource/Wiring API Migration Guide

## Overview

Eclipse PDE currently uses the legacy Equinox-specific resolver/state API (`org.eclipse.osgi.service.resolver.*`), particularly `BundleDescription`. This API has been superseded by the standard OSGi Resource and Wiring APIs defined in the OSGi Core specification:

- **Resource API**: [OSGi Core 8.0 - Framework Resource](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.resource.html)
- **Wiring API**: [OSGi Core 8.0 - Framework Wiring](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.wiring.html)

The modern OSGi API provides:
- `org.osgi.resource.Resource` - Represents a resource in the resource layer
- `org.osgi.framework.wiring.BundleRevision` - Represents a bundle revision (extends Resource)
- `org.osgi.framework.wiring.BundleWiring` - Represents the wiring of a bundle revision

### Why Migrate?

1. **Standard API**: The OSGi Resource/Wiring API is the official standard, ensuring better compatibility across OSGi implementations
2. **Future-proof**: The Equinox resolver/state API is deprecated and may be removed in future releases
3. **Interoperability**: Standard API works seamlessly with modern OSGi tooling and frameworks
4. **Already Compatible**: `BundleDescription` implements both `BundleRevision` and `Resource`, making migration incremental

## Current Usage in PDE

As of this writing, `BundleDescription` appears approximately **1,181 times** across the PDE codebase. However, migration to the new API has already begun in several key areas:

### Already Using New API
- `DependencyManager` - Uses `BundleRevision`, `BundleWiring`, and `Resource`
- `ApiBaselineManager` - Uses `Resource` for resource handling
- `org.eclipse.pde.bnd.ui` package - Extensively uses `Resource` and `aQute.bnd.osgi.resource.ResourceUtils`

## API Mapping

### Basic Bundle Information

#### Symbolic Name
```java
// Old API
String symbolicName = bundleDescription.getSymbolicName();

// New API - BundleRevision
String symbolicName = bundleRevision.getSymbolicName();

// New API - Resource with ResourceUtils
String symbolicName = ResourceUtils.getIdentity(resource);
```

#### Version
```java
// Old API
Version version = bundleDescription.getVersion();

// New API - BundleRevision
Version version = bundleRevision.getVersion();

// New API - Resource with ResourceUtils
Version version = ResourceUtils.getVersion(resource);
```

#### Bundle ID
```java
// Old API
long bundleId = bundleDescription.getBundleId();

// New API - BundleRevision
Bundle bundle = bundleRevision.getBundle();
long bundleId = bundle != null ? bundle.getBundleId() : -1;
```

### Fragment and Host Relationships

#### Check if Fragment
```java
// Old API
boolean isFragment = bundleDescription.getHost() != null;

// New API - BundleRevision
boolean isFragment = (bundleRevision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0;
```

#### Get Host (for fragments)
```java
// Old API
HostSpecification host = bundleDescription.getHost();
BundleDescription hostBundle = host != null ? host.getSupplier() : null;

// New API - BundleWiring
BundleWiring wiring = bundleRevision.getWiring();
if (wiring != null) {
    List<BundleWire> hostWires = wiring.getRequiredWires(HostNamespace.HOST_NAMESPACE);
    if (!hostWires.isEmpty()) {
        BundleRevision host = hostWires.get(0).getProvider();
    }
}
```

#### Get Fragments (for hosts)
```java
// Old API
BundleDescription[] fragments = bundleDescription.getFragments();

// New API - BundleWiring
BundleWiring wiring = bundleRevision.getWiring();
if (wiring != null) {
    List<BundleWire> fragmentWires = wiring.getProvidedWires(HostNamespace.HOST_NAMESPACE);
    List<BundleRevision> fragments = fragmentWires.stream()
        .map(wire -> wire.getRequirer())
        .collect(Collectors.toList());
}
```

### Dependencies

#### Get Required Bundles
```java
// Old API
BundleSpecification[] requiredBundles = bundleDescription.getRequiredBundles();

// New API - BundleWiring
BundleWiring wiring = bundleRevision.getWiring();
if (wiring != null) {
    List<BundleWire> requiredWires = wiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE);
    List<BundleRevision> requiredBundles = requiredWires.stream()
        .map(wire -> wire.getProvider())
        .collect(Collectors.toList());
}
```

#### Get All Dependencies
```java
// Old API
BundleSpecification[] specs = bundleDescription.getRequiredBundles();

// New API - BundleWiring (all namespaces)
BundleWiring wiring = bundleRevision.getWiring();
if (wiring != null) {
    List<BundleWire> allWires = wiring.getRequiredWires(null); // null = all namespaces
    // Process wires...
}
```

#### Check if Dependency is Optional
```java
// Old API
boolean isOptional = bundleSpec.isOptional();

// New API - BundleRequirement
boolean isOptional = Constants.RESOLUTION_OPTIONAL.equals(
    requirement.getDirectives().get(Constants.RESOLUTION_DIRECTIVE));
```

### Exported Packages

```java
// Old API
ExportPackageDescription[] exports = bundleDescription.getExportPackages();

// New API - BundleWiring
BundleWiring wiring = bundleRevision.getWiring();
if (wiring != null) {
    List<BundleCapability> packageCaps = wiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
    // Each capability represents an exported package
}
```

### Imported Packages

```java
// Old API
ImportPackageSpecification[] imports = bundleDescription.getImportPackages();

// New API - BundleWiring
BundleWiring wiring = bundleRevision.getWiring();
if (wiring != null) {
    List<BundleWire> packageWires = wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE);
    // Each wire represents a resolved package import
}
```

## Using aQute.bnd.osgi.resource.ResourceUtils

The bndlib library, already available in PDE's dependencies, provides helpful utility methods for working with Resources. This is particularly useful when you need to extract information from a `Resource` without casting to `BundleRevision`.

### Available in PDE
```java
import aQute.bnd.osgi.resource.ResourceUtils;
```

### Common Operations

#### Get Identity Information
```java
// Get identity capability (contains symbolic name, version, and type)
Capability identityCapability = ResourceUtils.getIdentityCapability(resource);

// Get symbolic name
String symbolicName = ResourceUtils.getIdentity(resource);

// Get version
Version version = ResourceUtils.getVersion(resource);

// Get identity from capability
String identity = ResourceUtils.getIdentity(identityCapability);
Version version = ResourceUtils.getVersion(identityCapability);
```

#### Get All Capabilities
```java
List<Capability> capabilities = ResourceUtils.getCapabilities(resource, namespace);
```

#### Get All Requirements
```java
List<Requirement> requirements = ResourceUtils.getRequirements(resource, namespace);
```

### Example: Getting Bundle Information from Resource
```java
import aQute.bnd.osgi.resource.ResourceUtils;
import org.osgi.resource.Resource;
import org.osgi.framework.Version;

public String getBundleInfo(Resource resource) {
    String symbolicName = ResourceUtils.getIdentity(resource);
    Version version = ResourceUtils.getVersion(resource);
    return symbolicName + " " + version;
}
```

## Migration Strategy

### Phase 1: Identify Migration Candidates

1. **Internal Methods**: Start with private/internal methods that don't affect public API
2. **New Utility Methods**: Create new methods using the Resource API alongside existing ones
3. **Deprecation**: Mark old methods as deprecated with proper `@deprecated` tags

### Phase 2: Add New Methods

For public APIs, add new methods accepting `Resource`/`BundleRevision` instead of `BundleDescription`:

```java
/**
 * @deprecated Use {@link #getDependencies(Resource, Options...)} instead
 */
@Deprecated
public static Set<BundleDescription> getDependencies(BundleDescription bundle, Options... options) {
    return getDependencies((Resource) bundle, options);
}

/**
 * Returns dependencies for the given resource.
 * @param resource the resource to analyze
 * @param options options for dependency resolution
 * @return set of dependent resources
 * @since 3.22
 */
public static Set<Resource> getDependencies(Resource resource, Options... options) {
    // New implementation using Resource API
}
```

### Phase 3: Internal Migration

Update internal code to use new APIs:

```java
// Before
private void processBundle(BundleDescription bundle) {
    String name = bundle.getSymbolicName();
    Version version = bundle.getVersion();
    // ...
}

// After
private void processBundle(Resource resource) {
    String name = ResourceUtils.getIdentity(resource);
    Version version = ResourceUtils.getVersion(resource);
    // ...
}
```

### Phase 4: Update Call Sites

Gradually update call sites to use new methods. Since `BundleDescription` implements `Resource`, most changes are safe:

```java
// Before
BundleDescription bundle = plugin.getBundleDescription();
processDependencies(bundle);

// After (safe cast since BundleDescription implements Resource)
Resource resource = plugin.getBundleDescription();
processDependencies(resource);
```

## Common Patterns

### Pattern 1: Safe Downcasting

Since `BundleDescription` implements both `BundleRevision` and `Resource`, you can safely cast:

```java
BundleDescription bundle = plugin.getBundleDescription();

// Safe casts
Resource resource = bundle;
BundleRevision revision = bundle;
```

### Pattern 2: Adapter Pattern

Use adapters when working with mixed types:

```java
public <T> T getAdapter(Class<T> adapter, Object object) {
    if (adapter == BundleDescription.class && object instanceof BundleDescription) {
        return adapter.cast(object);
    }
    if (adapter == Resource.class && object instanceof Resource) {
        return adapter.cast(object);
    }
    // ... other adapters
    return null;
}
```

### Pattern 3: Defensive Checks

Always check for null when working with wiring:

```java
BundleWiring wiring = bundleRevision.getWiring();
if (wiring == null || !wiring.isInUse()) {
    // Handle unwired or removed bundle
    return Collections.emptyList();
}
```

### Pattern 4: Working with Fragments

```java
private static boolean isFragment(BundleRevision bundle) {
    return (bundle.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0;
}

private static List<BundleRevision> getFragments(BundleRevision host) {
    BundleWiring wiring = host.getWiring();
    if (wiring == null) {
        return Collections.emptyList();
    }
    return wiring.getProvidedWires(HostNamespace.HOST_NAMESPACE).stream()
        .map(BundleWire::getRequirer)
        .collect(Collectors.toList());
}
```

## Best Practices

### 1. Use ResourceUtils for Simple Operations
When working with `Resource` and you only need basic information (identity, version), use `ResourceUtils`:

```java
// Preferred
String name = ResourceUtils.getIdentity(resource);

// Less preferred (requires cast)
String name = ((BundleRevision) resource).getSymbolicName();
```

### 2. Check Wiring Availability
Always verify wiring exists before using it:

```java
BundleWiring wiring = revision.getWiring();
if (wiring != null && wiring.isInUse()) {
    // Safe to use wiring
}
```

### 3. Use Appropriate Namespaces
Be explicit about which namespace you're querying:

```java
// Get only bundle dependencies
wiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE);

// Get only package imports
wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE);

// Get all dependencies (all namespaces)
wiring.getRequiredWires(null);
```

### 4. Stream Processing
Use Java streams for cleaner code:

```java
List<BundleRevision> dependencies = wiring.getRequiredWires(null).stream()
    .map(wire -> wire.getProvider())
    .filter(BundleRevision.class::isInstance)
    .map(BundleRevision.class::cast)
    .distinct()
    .collect(Collectors.toList());
```

### 5. Maintain Backward Compatibility
For public APIs, don't remove or change signatures. Add new methods instead:

```java
// Keep old method, delegate to new one
@Deprecated
public Set<BundleDescription> getDeps(BundleDescription bd) {
    return getDeps((Resource) bd).stream()
        .filter(BundleDescription.class::isInstance)
        .map(BundleDescription.class::cast)
        .collect(Collectors.toSet());
}

// New method using Resource
public Set<Resource> getDeps(Resource resource) {
    // Implementation
}
```

## Key Differences and Gotchas

### 1. Wiring vs Description
- `BundleDescription`: Static metadata from manifest
- `BundleWiring`: Dynamic runtime resolution state
- A bundle can have metadata but no wiring (if not resolved)

### 2. Fragments Handling
- Old API: `getFragments()` returns array directly
- New API: Must query wiring's provided wires in `HostNamespace.HOST_NAMESPACE`

### 3. Optional Dependencies
- Old API: `isOptional()` method on specification
- New API: Check directive on requirement using `Constants.RESOLUTION_DIRECTIVE`

### 4. Unresolved Bundles
- Old API: Methods return empty arrays/nulls
- New API: `getWiring()` returns `null` for unresolved bundles

### 5. Type Checking
- Old API: Separate classes for fragments and hosts
- New API: Use type flags (`BundleRevision.TYPE_FRAGMENT`)

## Testing Migration

### Unit Tests
When updating code, ensure tests cover:

1. **Null Safety**: Test with unresolved bundles (null wiring)
2. **Fragments**: Test both host and fragment perspectives
3. **Optional Dependencies**: Test with both required and optional dependencies
4. **Edge Cases**: Empty lists, unresolved bundles, removed bundles

### Example Test Structure
```java
@Test
public void testGetDependencies_WithResource() {
    Resource resource = createMockResource("test.bundle", "1.0.0");
    Set<Resource> deps = DependencyManager.getDependencies(resource);
    assertNotNull(deps);
    // ... assertions
}

@Test
public void testGetDependencies_WithUnresolvedResource() {
    BundleRevision revision = createUnresolvedMockRevision();
    when(revision.getWiring()).thenReturn(null);
    Set<Resource> deps = DependencyManager.getDependencies(revision);
    assertTrue(deps.isEmpty()); // Should handle gracefully
}
```

## Reference Examples in PDE Codebase

### Good Examples to Follow

1. **DependencyManager** (`org.eclipse.pde.internal.core.DependencyManager`)
   - Demonstrates proper use of `BundleRevision`, `BundleWiring`, `BundleWire`
   - Shows how to handle fragments using new API
   - Illustrates defensive checks for wiring

2. **ApiBaselineManager** (`org.eclipse.pde.api.tools.internal.ApiBaselineManager`)
   - Uses `Resource` alongside `BundleDescription`
   - Shows gradual migration approach

3. **org.eclipse.pde.bnd.ui package**
   - Extensive use of `ResourceUtils` from bndlib
   - Clean resource handling patterns

## Additional Resources

### OSGi Specifications
- [OSGi Core R8 Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/)
- [Framework Resource API](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.resource.html)
- [Framework Wiring API](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.wiring.html)

### Equinox Documentation
- [Equinox Repository on GitHub](https://github.com/eclipse-equinox/equinox)
- [OSGi Implementation in Equinox](https://github.com/eclipse-equinox/equinox/tree/master/bundles/org.eclipse.osgi)

### Bnd Resources
- [Bnd OSGi Resource Utilities](https://github.com/bndtools/bnd)

## Summary

Migrating from `BundleDescription` to `Resource`/`BundleRevision` is an incremental process that improves PDE's alignment with OSGi standards. Key points:

1. **Start Small**: Begin with internal methods and utility functions
2. **Use Utilities**: Leverage `ResourceUtils` from bndlib for common operations
3. **Add, Don't Replace**: Add new methods rather than breaking existing APIs
4. **Test Thoroughly**: Ensure proper handling of edge cases (null wiring, fragments, etc.)
5. **Follow Examples**: Reference `DependencyManager` and other migrated code

The migration enhances PDE's compatibility with modern OSGi practices while maintaining backward compatibility for existing consumers.
