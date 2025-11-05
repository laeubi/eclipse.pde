# API Filter Regression - Summary and Recommendations

## Issue Reference
https://github.com/eclipse-pde/eclipse.pde/issues/2096

## Problem Statement
JDT Core build reports API filters as "unused" when they should be matching actual API problems. The filters are for classes that extend `ASTNode` (@noextend) and implement `IDocElement` (@noextend/@noimplement).

## Work Completed

### 1. Test Cases Created
- **FilterMatchingRegressionTests.java**: Unit tests for filter matching logic
  - Verifies simple vs simple name matching works
  - Verifies fully qualified vs simple name matching works
  - Uses realistic JDT problem scenarios

- **UnusedFilterRegressionTests.java**: Integration test skeleton
  - Framework for workspace-based testing
  - Ready for expansion with actual test projects

### 2. Investigation Documentation
- **API_FILTER_REGRESSION_INVESTIGATION.md**: Comprehensive analysis including:
  - JDT scenario breakdown
  - Filter storage format details
  - Problem generation code paths
  - Filter matching algorithm explanation
  - Problem ID decoding
  - Multiple hypotheses for root cause

### 3. Testing Helper
- **test-filter-matching.sh**: Script demonstrating expected matching behavior
  - Shows all matching scenarios
  - Provides debugging instructions

## Key Technical Findings

### Filter Matching Logic Analysis
The `FilterStore.argumentsEquals()` method correctly handles:
1. **Simple vs Simple**: Direct string comparison
2. **FQ vs FQ**: Direct string comparison
3. **FQ vs Simple**: Extracts simple name from FQ, then compares
4. **Simple vs FQ**: Extracts simple name from FQ, then compares

This logic **should work correctly** for both IDE and headless builds.

### JDT Filter Format
```xml
<filter id="576725006">
    <message_arguments>
        <message_argument value="IDocElement"/>  <!-- SIMPLE NAME -->
        <message_argument value="AbstractTagElement"/>  <!-- SIMPLE NAME -->
    </message_arguments>
</filter>
```

### Problem Generation
- **IDE builds**: Generate problems with simple names via `getMessageArgs()`
- **Headless builds**: Generate problems with FQ names via `getQualifiedMessageArgs()`

Both should match the filters correctly.

## Root Cause Hypotheses

Since the matching logic appears correct, the issue likely lies in one of these areas:

1. **Problem ID Generation Changed**
   - Filter IDs don't match newly generated problem IDs
   - Check if problem ID calculation has been modified

2. **Resource Path Mismatch**
   - Problem resource paths don't match filter resource paths
   - Could be path format changes (forward vs back slash, relative vs absolute)

3. **Type Name Mismatch**
   - Problem type names don't match filter type names
   - Could be package name changes or type name format issues

4. **Code Path Selection Changed**
   - Builds that used to be "headless" now use "IDE" path or vice versa
   - Different message arg format than expected

5. **Message Arguments Not Being Set**
   - Problems generated without message arguments
   - Empty arrays would fail length check

## Recommended Next Steps

### Immediate Actions
1. **Run FilterMatchingRegressionTests**
   - Requires proper Eclipse/Tycho build environment
   - Will verify matching logic works as expected

2. **Enable Debug Logging in JDT Build**
   ```java
   ApiPlugin.DEBUG_FILTER_STORE = true
   ```
   Or add to JDT build VM args:
   ```
   -DApiPlugin.DEBUG_FILTER_STORE=true
   ```

3. **Analyze Debug Output**
   Look for these messages in JDT build logs:
   - `"no resource exists: [...]"` - Resource path issue
   - `"no filters defined for [...]"` - Filter loading issue
   - `"no filter defined for problem: [...]"` - Matching failed, shows problem details
   - `"filter used: [...]"` - Matching succeeded (should see this)

### Deep Investigation
1. **Compare Actual vs Expected Values**
   - Problem ID: Should be 576725006 or 576778288
   - Resource path: Should match filter's `path` attribute
   - Type name: Should be fully qualified
   - Message args: Should be FQ in headless, simple in IDE

2. **Check Recent Commits**
   - Search eclipse-pde repository for changes to:
     - `FilterStore.java`
     - `ApiFilterStore.java`
     - `AbstractProblemDetector.java`
     - `ApiProblemFactory.java`

3. **Compare with Working Version**
   - Identify last known working PDE version for JDT
   - Compare filter matching and problem generation code

### If Matching Logic Is Broken
If tests show matching logic is actually broken:

1. **Fix `FilterStore.argumentsEquals()`**
   - Current logic at lines 242-274
   - Ensure all four scenarios work correctly

2. **Add More Test Coverage**
   - Test edge cases (null, empty, mixed formats)
   - Test with actual JDT filter XML

3. **Update Documentation**
   - Clarify expected behavior
   - Document IDE vs headless differences

### If Matching Logic Is Correct
If tests show matching works but JDT still fails:

1. **Problem Generation Issue**
   - Check if JDT problems have correct problem IDs
   - Verify message arguments are being set
   - Confirm resource paths match filter format

2. **Filter Loading Issue**
   - Verify .api_filters file is being read
   - Check filter parsing doesn't corrupt data
   - Ensure filters are added to correct resource

3. **Build Environment Issue**
   - Wrong code path being used
   - Missing baseline causing short-circuit
   - Version/API changes in problem generation

## Expected Outcomes

### Success Criteria
- FilterMatchingRegressionTests pass
- JDT build no longer reports unused filters
- Filters correctly suppress API violations

### Deliverables
- Working test cases demonstrating correct behavior
- Fix for identified root cause (if found in PDE)
- Or workaround/fix for JDT (if issue is in JDT setup)
- Documentation of proper filter format and usage

## Files Provided

1. `FilterMatchingRegressionTests.java` - Unit tests
2. `UnusedFilterRegressionTests.java` - Integration test skeleton
3. `API_FILTER_REGRESSION_INVESTIGATION.md` - Detailed analysis
4. `test-filter-matching.sh` - Testing helper script
5. `SUMMARY.md` - This file

## Contact/Follow-up

For questions or to report findings:
- Reference issue: https://github.com/eclipse-pde/eclipse.pde/issues/2096
- Provide debug logs with `DEBUG_FILTER_STORE=true`
- Share test results from FilterMatchingRegressionTests
- Report which hypothesis matches actual findings
