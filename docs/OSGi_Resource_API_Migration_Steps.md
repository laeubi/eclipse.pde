# OSGi Resource API Migration - Practical Steps

## Overview

This document provides a practical, step-by-step approach for migrating Eclipse PDE code from the legacy `BundleDescription` API to the standard OSGi `Resource`/`BundleRevision` API. This is a supplement to the comprehensive [OSGi Resource API Migration Guide](OSGi_Resource_API_Migration.md).

## Quick Reference

### When to Migrate

**DO migrate:**
- When adding new functionality
- When refactoring existing code
- When fixing bugs in code that uses BundleDescription
- For internal/private methods

**DON'T break:**
- Existing public APIs (add new methods instead)
- API compatibility without proper deprecation
- Existing tests without updating them

### Quick API Lookup

| Operation | Old API (BundleDescription) | New API (Resource/BundleRevision) |
|-----------|----------------------------|-----------------------------------|
| Symbolic Name | `bd.getSymbolicName()` | `ResourceUtils.getIdentity(r)` or `br.getSymbolicName()` |
| Version | `bd.getVersion()` | `ResourceUtils.getVersion(r)` or `br.getVersion()` |
| Is Fragment | `bd.getHost() != null` | `(br.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0` |
| Get Fragments | `bd.getFragments()` | `wiring.getProvidedWires(HostNamespace.HOST_NAMESPACE)` |
| Dependencies | `bd.getRequiredBundles()` | `wiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE)` |

## Migration Steps by Area

### Step 1: Inventory and Planning

1. **Identify Target Code Areas**
   ```bash
   # Find all uses of BundleDescription in a module
   grep -r "BundleDescription" --include="*.java" ui/org.eclipse.pde.core/src/
   
   # Focus on high-impact areas first
   # Priority: Utility classes > Internal classes > Public APIs
   ```

2. **Check Dependencies**
   - Verify `aQute.bnd.osgi.resource` is in MANIFEST.MF imports
   - Confirm `org.osgi.resource` and `org.osgi.framework.wiring` packages are available
   - Review existing usages in `DependencyManager` for patterns

3. **Create Migration Checklist**
   - List all classes that use BundleDescription
   - Categorize by public/internal API
   - Prioritize based on usage frequency and impact

### Step 2: Update Internal Utility Methods

Start with internal utility methods that don't affect public API:

**Example: Fragment Detection**

```java
// BEFORE (internal method)
private static boolean isFragment(BundleDescription bundle) {
    return bundle.getHost() != null;
}

// AFTER (internal method)
private static boolean isFragment(BundleRevision revision) {
    return (revision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0;
}
```

**Checklist for Internal Methods:**
- [ ] Identify all internal/private methods using BundleDescription
- [ ] Update method signatures to accept Resource/BundleRevision
- [ ] Update method implementations to use new API
- [ ] Update internal call sites
- [ ] Run tests to verify behavior

### Step 3: Add New Public Methods (Don't Break Existing)

For public APIs, add new methods alongside existing ones:

**Example: Adding Resource-based Method**

```java
/**
 * Gets dependencies for the given bundle.
 * @param bundle the bundle description
 * @return set of dependency bundles
 * @deprecated Use {@link #getDependencies(Resource)} instead
 */
@Deprecated
public static Set<BundleDescription> getDependencies(BundleDescription bundle) {
    // Delegate to new implementation
    return getDependencies((Resource) bundle).stream()
        .filter(BundleDescription.class::isInstance)
        .map(BundleDescription.class::cast)
        .collect(Collectors.toSet());
}

/**
 * Gets dependencies for the given resource.
 * @param resource the bundle resource
 * @return set of dependency resources
 * @since 3.22
 */
public static Set<Resource> getDependencies(Resource resource) {
    // New implementation using Resource API
    if (!(resource instanceof BundleRevision)) {
        return Collections.emptySet();
    }
    BundleRevision revision = (BundleRevision) resource;
    BundleWiring wiring = revision.getWiring();
    if (wiring == null) {
        return Collections.emptySet();
    }
    return wiring.getRequiredWires(null).stream()
        .map(wire -> wire.getProvider())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
}
```

**Checklist for Public APIs:**
- [ ] Add new method with Resource/BundleRevision parameter
- [ ] Add @since tag with next version number
- [ ] Deprecate old method with @deprecated tag pointing to new method
- [ ] Implement old method by delegating to new one (if possible)
- [ ] Update documentation to mention new method

### Step 4: Migrate Complex Dependency Logic

For code that works with dependencies and wiring:

**Example: Getting Required Bundles**

```java
// BEFORE
private List<BundleDescription> getRequiredBundles(BundleDescription bundle) {
    BundleSpecification[] specs = bundle.getRequiredBundles();
    List<BundleDescription> result = new ArrayList<>();
    for (BundleSpecification spec : specs) {
        BundleDescription supplier = spec.getSupplier();
        if (supplier != null && supplier.isResolved()) {
            result.add(supplier);
        }
    }
    return result;
}

// AFTER
private List<BundleRevision> getRequiredBundles(BundleRevision revision) {
    BundleWiring wiring = revision.getWiring();
    if (wiring == null) {
        return Collections.emptyList();
    }
    return wiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE).stream()
        .map(BundleWire::getProvider)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
}
```

**Checklist for Dependency Logic:**
- [ ] Replace BundleSpecification with BundleWire/BundleRequirement
- [ ] Use appropriate namespace (BundleNamespace, PackageNamespace, etc.)
- [ ] Add null checks for wiring (unresolved bundles)
- [ ] Use streams for cleaner code
- [ ] Test with both resolved and unresolved bundles

### Step 5: Handle Fragment Relationships

Fragments require special attention:

**Example: Getting Fragments for a Host**

```java
// BEFORE
private List<BundleDescription> getFragments(BundleDescription host) {
    return Arrays.asList(host.getFragments());
}

// AFTER
private List<BundleRevision> getFragments(BundleRevision host) {
    BundleWiring wiring = host.getWiring();
    if (wiring == null) {
        return Collections.emptyList();
    }
    return wiring.getProvidedWires(HostNamespace.HOST_NAMESPACE).stream()
        .map(BundleWire::getRequirer)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
}
```

**Checklist for Fragments:**
- [ ] Replace getFragments() with wiring query
- [ ] Use HostNamespace.HOST_NAMESPACE
- [ ] For host: use getProvidedWires() and get requirers
- [ ] For fragment: use getRequiredWires() and get providers
- [ ] Handle null wiring case

### Step 6: Use ResourceUtils for Simple Operations

For basic information extraction, use ResourceUtils:

**Example: Getting Bundle Identity**

```java
// BEFORE
private String getBundleInfo(BundleDescription bundle) {
    String name = bundle.getSymbolicName();
    Version version = bundle.getVersion();
    return name + " " + version;
}

// AFTER
import aQute.bnd.osgi.resource.ResourceUtils;

private String getBundleInfo(Resource resource) {
    String name = ResourceUtils.getIdentity(resource);
    Version version = ResourceUtils.getVersion(resource);
    return name + " " + version;
}
```

**Checklist for ResourceUtils:**
- [ ] Add import for `aQute.bnd.osgi.resource.ResourceUtils`
- [ ] Use `ResourceUtils.getIdentity()` for symbolic name
- [ ] Use `ResourceUtils.getVersion()` for version
- [ ] Use `ResourceUtils.getIdentityCapability()` for full identity info

### Step 7: Update Tests

Update or add tests for new methods:

```java
@Test
public void testGetDependencies_Resource() {
    // Create or mock a Resource
    Resource resource = createTestResource("test.bundle", "1.0.0");
    
    // Test the new method
    Set<Resource> dependencies = MyClass.getDependencies(resource);
    
    // Assertions
    assertNotNull(dependencies);
    assertTrue(dependencies.size() > 0);
}

@Test
public void testGetDependencies_UnresolvedBundle() {
    // Test with unresolved bundle (null wiring)
    BundleRevision revision = mock(BundleRevision.class);
    when(revision.getWiring()).thenReturn(null);
    
    Set<Resource> dependencies = MyClass.getDependencies(revision);
    
    assertTrue(dependencies.isEmpty()); // Should handle gracefully
}
```

**Test Checklist:**
- [ ] Add tests for new Resource/BundleRevision methods
- [ ] Test with resolved and unresolved bundles
- [ ] Test with fragments and hosts
- [ ] Test with optional dependencies
- [ ] Test edge cases (null wiring, empty collections)
- [ ] Update existing tests to use new methods where appropriate

### Step 8: Documentation and Code Review

Final steps before committing:

**Documentation Checklist:**
- [ ] Add Javadoc to new methods
- [ ] Include @since tags
- [ ] Update class-level documentation if needed
- [ ] Add @deprecated tags with migration guidance
- [ ] Update any related user documentation

**Code Review Checklist:**
- [ ] New methods follow existing patterns
- [ ] No breaking changes to public API
- [ ] Proper null handling for wiring
- [ ] Consistent use of streams/collections
- [ ] Tests cover new functionality
- [ ] No regressions in existing tests

## Area-Specific Migration Guides

### API Tools Module

The API Tools module uses BundleDescription extensively. Migration priority:

1. **ApiBaseline** - Already partially migrated, continue the effort
2. **BundleComponent** - Core component class
3. **ApiModelFactory** - Factory methods need update

### PDE Core Module

1. **DependencyManager** - Good example, already uses new API
2. **PluginModelManager** - Central model management
3. **Target platform classes** - Handle bundle resolution

### PDE UI Module

1. **PDELabelProvider** - Display logic
2. **Refactoring classes** - Need careful migration
3. **Wizards** - Update to support new API

## Common Patterns and Solutions

### Pattern: Null Safety with Wiring

```java
// Always check wiring before use
private List<BundleRevision> getDependencies(BundleRevision revision) {
    BundleWiring wiring = revision.getWiring();
    if (wiring == null || !wiring.isInUse()) {
        return Collections.emptyList(); // Safe default
    }
    // Use wiring...
}
```

### Pattern: Type Checking

```java
// Check resource type before operations
private boolean canProcess(Resource resource) {
    if (!(resource instanceof BundleRevision)) {
        return false;
    }
    BundleRevision revision = (BundleRevision) resource;
    // Continue processing...
}
```

### Pattern: Stream Processing

```java
// Use streams for cleaner code
private Set<String> getExportedPackages(BundleRevision revision) {
    BundleWiring wiring = revision.getWiring();
    if (wiring == null) {
        return Collections.emptySet();
    }
    return wiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE).stream()
        .map(cap -> cap.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE))
        .filter(Objects::nonNull)
        .map(String.class::cast)
        .collect(Collectors.toSet());
}
```

## Validation and Testing

### Pre-Commit Validation

1. **Build Successfully**
   ```bash
   mvn clean verify
   ```

2. **Run Specific Tests**
   ```bash
   mvn test -Dtest=MyClassTest
   ```

3. **Check API Baseline**
   ```bash
   mvn verify -Papi-check
   ```

4. **Review Warnings**
   - Check for new deprecation warnings
   - Verify no new API baseline violations
   - Review compiler warnings

### Integration Testing

1. Test in a full Eclipse IDE installation
2. Verify workspace builds work correctly
3. Test target platform resolution
4. Verify API Tools functionality

## Progress Tracking

Use this template to track migration progress:

### Module: [Module Name]

| Class/Method | Priority | Status | Notes |
|--------------|----------|--------|-------|
| Example.methodA() | High | ✅ Done | Migrated to Resource |
| Example.methodB() | Medium | 🔄 In Progress | Waiting on API approval |
| Example.methodC() | Low | ⏳ Pending | Low usage |

**Legend:**
- ✅ Done - Migrated and tested
- 🔄 In Progress - Work started
- ⏳ Pending - Not started
- ⛔ Blocked - Cannot migrate yet

## Getting Help

- **Reference Documentation**: [OSGi Resource API Migration Guide](OSGi_Resource_API_Migration.md)
- **Example Code**: See `DependencyManager` in ui/org.eclipse.pde.core
- **OSGi Specs**: [OSGi Core Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/)
- **Bnd Resources**: [aQute.bnd.osgi.resource package](https://github.com/bndtools/bnd)

## Summary

Migration to the OSGi Resource/Wiring API is an incremental process:

1. Start with internal methods
2. Add new public methods (don't break existing ones)
3. Use ResourceUtils for simple operations
4. Handle wiring carefully (check for null)
5. Test thoroughly
6. Document changes

The key is to make small, incremental changes that maintain backward compatibility while moving toward the standard OSGi API.
