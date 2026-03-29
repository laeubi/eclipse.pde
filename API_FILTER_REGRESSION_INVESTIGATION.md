# API Filter "Unused Filter" Regression Investigation

## Issue
https://github.com/eclipse-pde/eclipse.pde/issues/2096

JDT reports that API filters are being reported as unused when they shouldn't be.

## Background

### JDT Scenario
JDT has classes that extend `ASTNode` (@noextend) and implement `IDocElement` (@noextend, @noimplement).
These violations are intentional and filtered using `.api_filters`.

Example from JDT's .api_filters:
```xml
<resource path="dom/org/eclipse/jdt/core/dom/AbstractTagElement.java" type="org.eclipse.jdt.core.dom.AbstractTagElement">
    <filter id="576725006">
        <message_arguments>
            <message_argument value="IDocElement"/>
            <message_argument value="AbstractTagElement"/>
        </message_arguments>
    </filter>
    <filter id="576778288">
        <message_arguments>
            <message_argument value="ASTNode"/>
            <message_argument value="AbstractTagElement"/>
        </message_arguments>
    </filter>
</resource>
```

### Filter Storage Format
- **Type name**: Fully qualified (e.g., `org.eclipse.jdt.core.dom.AbstractTagElement`)
- **Message arguments**: Simple names (e.g., `IDocElement`, `ASTNode`)
- **Resource path**: Relative to project (e.g., `dom/org/eclipse/jdt/core/dom/AbstractTagElement.java`)

### Problem Generation
Two code paths:
1. **IDE** (`AbstractProblemDetector.createProblemWithContext`):
   - Uses `getMessageArgs()` -> returns simple names
   - Used when workspace resources exist

2. **Headless** (`AbstractProblemDetector.createProblem`):
   - Uses `getQualifiedMessageArgs()` -> returns fully qualified names
   - Used in headless builds/API analysis

### Filter Matching Logic
`FilterStore.argumentsEquals()` compares message arguments:

```java
private boolean argumentsEquals(String[] problemMessageArguments, String[] filterProblemMessageArguments) {
    // filter problems message arguments are always simple name
    // problem message arguments are fully qualified name outside the IDE
    int length = problemMessageArguments.length;
    if (length == filterProblemMessageArguments.length) {
        for (int i = 0; i < length; i++) {
            String problemMessageArgument = problemMessageArguments[i];
            String filterProblemMessageArgument = filterProblemMessageArguments[i];
            if (problemMessageArgument.equals(filterProblemMessageArgument)) {
                continue;
            }
            int index = problemMessageArgument.lastIndexOf('.');
            int filterProblemIndex = filterProblemMessageArgument.lastIndexOf('.');
            if (index == -1) {
                // Problem is simple name
                if (filterProblemIndex == -1) {
                    return false; // Both simple, should have matched above
                }
                // Filter is FQ, extract simple name
                if (!filterProblemMessageArgument.substring(filterProblemIndex + 1)
                        .equals(problemMessageArgument)) {
                    return false;
                }
            } else if (filterProblemIndex != -1) {
                // Both FQ but didn't match -> different types
                return false;
            } else {
                // Problem is FQ, filter is simple - extract and compare
                if (!problemMessageArgument.substring(index + 1).equals(filterProblemMessageArgument)) {
                    return false;
                }
            }
        }
        return true;
    }
    return false;
}
```

**Expected behavior:**
- Simple vs Simple: exact match
- FQ vs FQ: exact match  
- FQ vs Simple: extract simple name from FQ, then match
- Simple vs FQ: extract simple name from FQ, then match

This logic should handle both IDE (simple names) and headless (FQ names) scenarios correctly.

## Investigation Results

### Problem IDs
JDT filter IDs decoded:
- `576725006` (0x2260200E): USAGE category, TYPE element, API_LEAK kind - for implementing IDocElement
- `576778288` (0x22602210): USAGE category, TYPE element, API_LEAK kind - for extending ASTNode

### Expected Matching
For headless build:
- **Filter**: `["IDocElement", "AbstractTagElement"]` (simple)
- **Problem**: `["org.eclipse.jdt.core.dom.IDocElement", "org.eclipse.jdt.core.dom.AbstractTagElement"]` (FQ)
- **Should match**: Yes, via lines 265-268 of argumentsEquals

For IDE build:
- **Filter**: `["IDocElement", "AbstractTagElement"]` (simple)
- **Problem**: `["IDocElement", "AbstractTagElement"]` (simple)
- **Should match**: Yes, via line 250 of argumentsEquals

## Hypothesis

The matching logic in `FilterStore.argumentsEquals()` appears correct. Possible causes:

1. **Problem ID mismatch**: Filter IDs don't match generated problem IDs
2. **Type name mismatch**: Type names don't match exactly
3. **Resource path mismatch**: Resource paths differ
4. **Code path change**: IDE vs headless code path selection changed
5. **Message args generation change**: getQualifiedMessageArgs() behavior changed

## Test Cases Created

1. **FilterMatchingRegressionTests.java**: Unit tests for filter matching logic
   - Tests simple vs simple matching
   - Tests FQ vs simple matching
   - Uses JDT-like scenarios

2. **UnusedFilterRegressionTests.java**: Integration test skeleton
   - Framework for full workspace test
   - Needs test project setup to run

## Next Steps

1. **Run FilterMatchingRegressionTests** in proper Eclipse/Tycho build to verify matching logic
2. **Enable debug logging**: Set `ApiPlugin.DEBUG_FILTER_STORE = true` in JDT build to see:
   - Which filters are checked
   - Which problems are generated
   - Why filters don't match
3. **Compare problem generation**: Check if JDT problems have expected IDs, paths, type names, and message args
4. **Check for recent changes**: Look for commits in eclipse-pde that changed:
   - Problem ID generation
   - Message argument generation  
   - Filter matching logic
   - Problem path handling

## Files Modified

- `apitools/org.eclipse.pde.api.tools.tests/src/org/eclipse/pde/api/tools/model/tests/FilterMatchingRegressionTests.java` - Unit test for filter matching
- `apitools/org.eclipse.pde.api.tools.tests/src/org/eclipse/pde/api/tools/builder/tests/usage/UnusedFilterRegressionTests.java` - Integration test skeleton
