# Extending PDE Multi-Page Editors

This document describes how to add support for additional file types to the PDE multi-page editors (Manifest Editor and Feature Editor). It is intended for developers who want to integrate new file types similar to how `build.properties`, `pde.bnd`, and `p2.inf` are integrated.

## Overview

PDE provides multi-page editors for managing Eclipse plug-in and feature projects. These editors display multiple related files in a tabbed interface:

- **Manifest Editor**: Shows MANIFEST.MF, plugin.xml, build.properties, pde.bnd, and p2.inf
- **Feature Editor**: Shows feature.xml, build.properties, and p2.inf

The architecture consists of three main components:
1. **InputContext** - Manages the editing lifecycle of a file
2. **Model** - Represents the file content in memory
3. **Source Page** - Provides the UI for editing the file

## Architecture

### InputContext

The `InputContext` class manages a single file within the multi-page editor. It is responsible for:
- Creating and managing the model for the file
- Handling document changes and reconciliation
- Managing save operations
- Tracking dirty state

Key methods to implement:
```java
public class MyInputContext extends InputContext {
    public static final String CONTEXT_ID = "my-context"; //$NON-NLS-1$
    
    public MyInputContext(PDEFormEditor editor, IEditorInput input, boolean primary) {
        super(editor, input, primary);
        create(); // Initialize the context
    }
    
    @Override
    protected Charset getDefaultCharset() {
        return StandardCharsets.UTF_8;
    }
    
    @Override
    protected IBaseModel createModel(IEditorInput input) throws CoreException {
        IDocument document = getDocumentProvider().getDocument(input);
        MyModel model = new MyModel(document);
        model.load();
        return model;
    }
    
    @Override
    public String getId() {
        return CONTEXT_ID;
    }
    
    @Override
    protected void addTextEditOperation(ArrayList<TextEdit> ops, IModelChangedEvent event) {
        // Handle model-to-document synchronization if needed
    }
    
    @Override
    public void doRevert() {
        // Reload the model from the document
    }
    
    @Override
    protected String getPartitionName() {
        return "___my_partition"; //$NON-NLS-1$
    }
}
```

### Model

The model represents the file content in memory. It implements `IBaseModel` and optionally `IModelChangeProvider`:

```java
public class MyModel implements IBaseModel, IModelChangeProvider {
    private final IDocument document;
    private volatile boolean valid;
    private volatile boolean disposed;
    
    public MyModel(IDocument document) {
        this.document = document;
    }
    
    public void load() {
        // Parse the document content and populate the model
        valid = true;
    }
    
    @Override
    public boolean isEditable() {
        return true;
    }
    
    @Override
    public boolean isValid() {
        return valid;
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    @Override
    public void dispose() {
        disposed = true;
    }
    
    // IModelChangeProvider methods for change notification
}
```

### Source Page

The source page provides the text editor view for the file. For simple cases, extend `GenericSourcePage`:

```java
public class MySourcePage extends GenericSourcePage {
    public MySourcePage(PDEFormEditor editor, String id, String title) {
        super(editor, id, title);
    }
}
```

For more advanced features (syntax highlighting, content assist), you can override methods from `PDESourcePage` or create a custom source viewer configuration.

## Integration Points

### Adding Constants

Add file name constants to `ICoreConstants` in `org.eclipse.pde.core`:

```java
/** Constant for the file name */
String MY_FILE_NAME = "my.file"; //$NON-NLS-1$

/** Path for the file relative to project root */
IPath MY_FILE_PATH = IPath.fromOSString(MY_FILE_NAME);
```

### Adding Project Helpers

Add helper methods to `PDEProject` to retrieve the file:

```java
public static IFile getMyFile(IProject project) {
    return getBundleRelativeFile(project, ICoreConstants.MY_FILE_PATH);
}
```

### Integrating with ManifestEditor

In `ManifestEditor`, modify these methods:

1. **createResourceContexts()** - Create and register the context:
```java
IFile myFile = container.getFile(ICoreConstants.MY_FILE_PATH);
if (myFile != null && myFile.exists()) {
    FileEditorInput in = new FileEditorInput(myFile);
    manager.putContext(in, new MyInputContext(this, in, false));
}
manager.monitorFile(myFile);
```

2. **monitoredFileAdded()** - Handle dynamically added files:
```java
else if (name.equalsIgnoreCase(ICoreConstants.MY_FILE_NAME)) {
    if (!fInputContextManager.hasContext(MyInputContext.CONTEXT_ID)) {
        IEditorInput in = new FileEditorInput(file);
        fInputContextManager.putContext(in, new MyInputContext(this, in, false));
    }
}
```

3. **addEditorPages()** - Add the source page:
```java
addSourcePage(MyInputContext.CONTEXT_ID);
```

4. **isSourcePageID()** - Register the context ID:
```java
case MyInputContext.CONTEXT_ID: // my.file
    return true;
```

5. **createSourcePage()** - Create the source page instance:
```java
if (contextId.equals(MyInputContext.CONTEXT_ID)) {
    return new MySourcePage(editor, contextId, title);
}
```

### Integrating with FeatureEditor

Similar integration is needed in `FeatureEditor` if the file type is relevant to feature projects.

## Example: p2.inf Integration

The `p2.inf` file is integrated as follows:

### File Locations
- **Plugin projects**: `META-INF/p2.inf`
- **Feature projects**: `p2.inf` (in project root)

### Classes Created
- `P2InfModel` - Model for p2.inf content
- `P2InfInputContext` - Input context managing p2.inf editing
- `P2InfSourcePage` - Source page for editing p2.inf

### Integration
- Added constants to `ICoreConstants`: `P2_INF_FILENAME`, `P2_INF_BUNDLE_PATH`, `P2_INF_FEATURE_PATH`
- Added helpers to `PDEProject`: `getBundleP2Inf()`, `getFeatureP2Inf()`
- Modified `ManifestEditor` to support p2.inf as a tab
- Modified `FeatureEditor` to support p2.inf as a tab

## Future Enhancements

When extending the editors, consider adding:
1. **Form-based pages** - Create `PDEFormPage` subclasses for structured editing
2. **Content assist** - Implement `IContentAssistProcessor` for auto-completion
3. **Syntax highlighting** - Create a custom `SourceViewerConfiguration`
4. **Validation** - Add model validation and error markers
5. **Quick fixes** - Implement quick fix proposals for common issues

## References

- PDE Source Code: `ui/org.eclipse.pde.ui/src/org/eclipse/pde/internal/ui/editor/`
- Build properties integration: `editor/build/BuildInputContext.java`
- BND integration: `editor/bnd/BndInputContext.java`
- p2.inf integration: `editor/p2inf/P2InfInputContext.java`
- [p2 Customizing Metadata Documentation](https://github.com/eclipse-equinox/p2/blob/master/docs/Customizing_Metadata.md)
