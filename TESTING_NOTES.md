# Manual Testing Notes for Issue #2049

## Feature: OSGi Semantic Versioning for Dependency Version Ranges

### Test Scenario 1: Add upper bound when none exists
**Setup:** 
- Open PDE Manifest Editor for a plugin
- Add or edit a Required Bundle dependency
- Open the Properties dialog for the dependency
- Select a version from the workspace list (single selection)

**Expected Behavior:**
- If the selected version is `1.2.3`, the version range should be set to `[1.2.3, 2.0.0)`
- If the selected version is `2.5.0`, the version range should be set to `[2.5.0, 3.0.0)`
- The upper bound should exclude the next major version following OSGi semantic versioning

### Test Scenario 2: Preserve upper bound when major version unchanged
**Setup:**
- Open PDE Manifest Editor for a plugin
- Add or edit a Required Bundle dependency with existing range `[1.2.3, 2.0.0)`
- Open the Properties dialog for the dependency
- Select a version `1.6.0` from the workspace list (single selection)

**Expected Behavior:**
- The version range should be updated to `[1.6.0, 2.0.0)`
- The upper bound `2.0.0` is preserved since the new lower bound major version (1) is still less than the upper bound major version (2)

### Test Scenario 3: Update upper bound when major version changes
**Setup:**
- Open PDE Manifest Editor for a plugin
- Add or edit a Required Bundle dependency with existing range `[1.2.3, 2.0.0)`
- Open the Properties dialog for the dependency
- Select a version `2.3.0` from the workspace list (single selection)

**Expected Behavior:**
- The version range should be updated to `[2.3.0, 3.0.0)`
- The upper bound is updated to `3.0.0` since the new lower bound major version (2) equals the old upper bound major version (2)

### Test Scenario 4: Multiple version selection
**Setup:**
- Open PDE Manifest Editor for a plugin
- Add or edit a Required Bundle dependency
- Open the Properties dialog for the dependency
- Select multiple versions from the workspace list (e.g., `1.2.0` and `1.8.0`)

**Expected Behavior:**
- The version range should be set based on the minimum selected version and a computed upper bound
- For example, selecting `1.2.0` and `1.8.0` should result in `[1.2.0, 2.0.0)` (not `[1.2.0, 1.8.0)`)
- This ensures all selected versions are included in the range (inclusive of both selected versions)
- The upper bound follows OSGi semantic versioning (next major version, exclusive)
- **Note:** This is an improvement over the original behavior which would set `[1.2.0, 1.8.0)` excluding version `1.8.0`

### Test Scenario 5: Import Package dependencies
**Setup:**
- Open PDE Manifest Editor for a plugin
- Add or edit an Import-Package dependency
- Open the Properties dialog for the dependency
- Select a version from the workspace list

**Expected Behavior:**
- Same behavior as Required Bundle dependencies
- Version ranges should follow OSGi semantic versioning rules

## Implementation Details

### Modified File
- `ui/org.eclipse.pde.ui/src/org/eclipse/pde/internal/ui/parts/PluginVersionPart.java`

### Key Changes
1. Added `getCurrentUpperBound()` method to retrieve existing upper bound from version range
2. Added `computeUpperBound(newLowerBound, existingUpperBound)` method that:
   - Preserves existing upper bound if new lower bound major version < existing upper bound major version
   - Updates to next major version if lower bound major version >= existing upper bound major version
   - Adds new upper bound (next major) following OSGi semantic versioning when none exists
3. Updated `buttonSelected()` to use new logic for both single and multiple selections

### OSGi Semantic Versioning Rules Applied
- Version format: `major.minor.micro.qualifier`
- Breaking changes increment the major version
- Version ranges use inclusive lower bound `[` and exclusive upper bound `)`
- Upper bound should exclude the next major version to prevent automatic breaking changes
- Example: `1.2.3` → `[1.2.3, 2.0.0)` accepts any 1.x version but excludes 2.x
