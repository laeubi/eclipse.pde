package org.eclipse.pde.internal.core.schema;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.File;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.pde.core.IModel;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.ischema.*;

public class SchemaRegistry
	implements IModelProviderListener, IResourceChangeListener, IResourceDeltaVisitor {
	public static final String PLUGIN_POINT = "schemaMap";
	public static final String TAG_MAP = "map";
	private Hashtable workspaceDescriptors;
	private Hashtable externalDescriptors;
	private Vector dirtyWorkspaceModels;

	public SchemaRegistry() {
	}
	private void addExtensionPoint(IFile file) {
		FileSchemaDescriptor sd = new FileSchemaDescriptor(file);
		workspaceDescriptors.put(sd.getPointId(), sd);
	}

	private AbstractSchemaDescriptor getSchemaDescriptor(String extensionPointId) {
		if (workspaceDescriptors == null) {
			initializeDescriptors();
		}
		if (dirtyWorkspaceModels != null && dirtyWorkspaceModels.size() > 0) {
			updateWorkspaceDescriptors();
		}
		AbstractSchemaDescriptor descriptor =
			(AbstractSchemaDescriptor) workspaceDescriptors.get(
				extensionPointId);
		if (descriptor == null) {
			// try external
			descriptor =
				(AbstractSchemaDescriptor) externalDescriptors.get(
					extensionPointId);
		}
		if (descriptor != null && descriptor.isEnabled())
			return descriptor;
		return null;
	}

	public ISchema getSchema(String extensionPointId) {
		AbstractSchemaDescriptor descriptor =
			getSchemaDescriptor(extensionPointId);
		if (descriptor == null)
			return null;
		return descriptor.getSchema();
	}
	
	private void initializeDescriptors() {
		workspaceDescriptors = new Hashtable();
		externalDescriptors = new Hashtable();
		// Check workspace plug-ins
		loadWorkspaceDescriptors();
		// Check external plug-ins
		loadExternalDescriptors();
		// Now read the registry and accept schema maps
		loadMappedDescriptors();
		// Register for further changes
		PDECore
			.getDefault()
			.getWorkspaceModelManager()
			.addModelProviderListener(
			this);
		PDECore.getWorkspace().addResourceChangeListener(this);
	}
	private void loadExternalDescriptors() {
		ExternalModelManager registry =
			PDECore.getDefault().getExternalModelManager();
		IPluginModel[] models = registry.getModels();
		for (int i = 0; i < models.length; i++) {
			IPlugin pluginInfo = models[i].getPlugin();
			IPluginExtensionPoint[] points = pluginInfo.getExtensionPoints();
			for (int j = 0; j < points.length; j++) {
				IPluginExtensionPoint point = points[j];
				if (point.getSchema() != null) {
					ExternalSchemaDescriptor desc =
						new ExternalSchemaDescriptor(point);
					externalDescriptors.put(point.getFullId(), desc);
				}
			}

		}
	}
	private void loadMappedDescriptors() {
		IPluginRegistry registry = Platform.getPluginRegistry();
		org.eclipse.core.runtime.IExtensionPoint point =
			registry.getExtensionPoint(PDECore.getPluginId(), PLUGIN_POINT);
		if (point == null)
			return;

		IExtension[] extensions = point.getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			IConfigurationElement[] elements =
				extensions[i].getConfigurationElements();
			for (int j = 0; j < elements.length; j++) {
				IConfigurationElement config = elements[j];
				processMapElement(config);
			}
		}
	}
	private void loadWorkspaceDescriptor(IPluginModelBase model) {
		IPluginBase pluginInfo = model.getPluginBase();
		IPluginExtensionPoint[] points = pluginInfo.getExtensionPoints();
		for (int j = 0; j < points.length; j++) {
			IPluginExtensionPoint point = points[j];
			if (point.getSchema() != null) {
				Object schemaFile = getSchemaFile(point);

				if (schemaFile instanceof IFile) {
					FileSchemaDescriptor desc =
						new FileSchemaDescriptor((IFile) schemaFile);
					workspaceDescriptors.put(point.getFullId(), desc);
				} else if (schemaFile instanceof File) {
					ExternalSchemaDescriptor desc =
						new ExternalSchemaDescriptor(
							(File) schemaFile,
							point.getFullId(),
							true);
					workspaceDescriptors.put(point.getFullId(), desc);
				}
			}
		}
	}
	private void loadWorkspaceDescriptors() {
		WorkspaceModelManager manager =
			PDECore.getDefault().getWorkspaceModelManager();
		IPluginModel[] models = manager.getWorkspacePluginModels();
		for (int i = 0; i < models.length; i++) {
			IPluginModel model = models[i];
			loadWorkspaceDescriptor(model);
		}
		IFragmentModel[] fmodels = manager.getWorkspaceFragmentModels();
		for (int i = 0; i < fmodels.length; i++) {
			IFragmentModel fmodel = fmodels[i];
			loadWorkspaceDescriptor(fmodel);
		}
	}
	private void loadWorkspaceDescriptors(IPluginModelBase model) {
		IPluginBase pluginInfo = model.getPluginBase();
		IPluginExtensionPoint[] points = pluginInfo.getExtensionPoints();
		for (int j = 0; j < points.length; j++) {
			IPluginExtensionPoint point = points[j];
			if (point.getSchema() != null) {
				Object schemaFile = getSchemaFile(point);

				if (schemaFile instanceof IFile) {
					FileSchemaDescriptor desc =
						new FileSchemaDescriptor((IFile) schemaFile);
					workspaceDescriptors.put(point.getFullId(), desc);
				} else if (schemaFile instanceof File) {
					ExternalSchemaDescriptor desc =
						new ExternalSchemaDescriptor(
							(File) schemaFile,
							point.getFullId(),
							true);
					workspaceDescriptors.put(point.getFullId(), desc);
				}
			}
		}
	}

	private Object getSchemaFile(IPluginExtensionPoint point) {
		IPluginModelBase model = point.getModel();
		IFile pluginFile = (IFile) model.getUnderlyingResource();
		IPath path = pluginFile.getProject().getFullPath();
		path = path.append(point.getSchema());
		IFile schemaFile = pluginFile.getWorkspace().getRoot().getFile(path);
		if (schemaFile.exists())
			return schemaFile;
		// Does not exist in the plug-in itself - try source location
		SourceLocationManager sourceManager =
			PDECore.getDefault().getSourceLocationManager();
		return sourceManager.findSourceFile(
			model.getPluginBase(),
			new Path(point.getSchema()));
	}

	public void modelsChanged(IModelProviderEvent e) {

		int type = e.getEventTypes();

		if ((type & IModelProviderEvent.MODELS_ADDED) != 0) {
			IModel[] added = e.getAddedModels();
			for (int i = 0; i < added.length; i++) {
				IModel model = added[i];
				if (!(model instanceof IPluginModelBase))
					continue;
				loadWorkspaceDescriptors((IPluginModelBase) model);
			}
		}
		if ((type & IModelProviderEvent.MODELS_REMOVED) != 0) {
			IModel[] removed = e.getRemovedModels();

			for (int i = 0; i < removed.length; i++) {
				IModel model = removed[i];
				if (!(model instanceof IPluginModelBase))
					continue;
				removeWorkspaceDescriptors((IPluginModelBase) model);
			}
		}
		if ((type & IModelProviderEvent.MODELS_CHANGED) != 0) {
			IModel[] changed = e.getChangedModels();
			if (dirtyWorkspaceModels == null)
				dirtyWorkspaceModels = new Vector();
			for (int i = 0; i < changed.length; i++) {
				IModel model = changed[i];
				if (!(model instanceof IPluginModelBase))
					continue;
				dirtyWorkspaceModels.add((IPluginModelBase) model);
			}
		}
	}

	private void processMapElement(IConfigurationElement element) {
		String tag = element.getName();
		if (tag.equals(TAG_MAP)) {
			String point =
				element.getAttribute(MappedSchemaDescriptor.ATT_POINT);
			String schema =
				element.getAttribute(MappedSchemaDescriptor.ATT_SCHEMA);
			if (point == null || schema == null) {
				//System.out.println("Schema map: point or schema null");
				return;
			}
			if (getSchemaDescriptor(point) == null) {
				MappedSchemaDescriptor desc =
					new MappedSchemaDescriptor(element);
				externalDescriptors.put(point, desc);
			}
		}
	}

	private void removeExtensionPoint(IFile file) {
		for (Enumeration enum = workspaceDescriptors.keys();
			enum.hasMoreElements();
			) {
			String pointId = (String) enum.nextElement();
			Object desc = workspaceDescriptors.get(pointId);
			if (desc instanceof FileSchemaDescriptor) {
				FileSchemaDescriptor fd = (FileSchemaDescriptor) desc;
				if (fd.getFile().equals(file)) {
					workspaceDescriptors.remove(pointId);
					fd.getSchema().dispose();
				}
			}
		}
	}
	private void removeWorkspaceDescriptors(IPluginModelBase model) {
		IPluginBase pluginInfo = model.getPluginBase();
		IPluginExtensionPoint[] points = pluginInfo.getExtensionPoints();
		for (int i = 0; i < points.length; i++) {
			IPluginExtensionPoint point = points[i];
			Object descObj = workspaceDescriptors.get(point.getFullId());
			if (descObj != null && descObj instanceof FileSchemaDescriptor) {
				FileSchemaDescriptor desc = (FileSchemaDescriptor) descObj;
				IFile schemaFile = desc.getFile();
				if (model
					.getUnderlyingResource()
					.getProject()
					.equals(schemaFile.getProject())) {
					// same project - remove
					workspaceDescriptors.remove(point.getFullId());
				}
			}
		}
	}
	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
			IResourceDelta delta = event.getDelta();
			if (delta != null) {
				try {
					delta.accept(this);
				} catch (CoreException e) {
					PDECore.logException(e);
				}
			}
		}
	}
	public void shutdown() {
		if (workspaceDescriptors == null)
			return;
		disposeDescriptors(workspaceDescriptors);
		disposeDescriptors(externalDescriptors);
		workspaceDescriptors = null;
		externalDescriptors = null;
		dirtyWorkspaceModels = null;
		PDECore
			.getDefault()
			.getWorkspaceModelManager()
			.removeModelProviderListener(
			this);
		PDECore.getWorkspace().removeResourceChangeListener(this);
	}

	private void disposeDescriptors(Hashtable descriptors) {
		for (Iterator iter = descriptors.values().iterator();
			iter.hasNext();
			) {
			AbstractSchemaDescriptor desc =
				(AbstractSchemaDescriptor) iter.next();
			desc.dispose();
		}
		descriptors.clear();
	}

	private void updateExtensionPoint(IFile file) {
		for (Iterator iter = workspaceDescriptors.values().iterator();
			iter.hasNext();
			) {
			AbstractSchemaDescriptor sd =
				(AbstractSchemaDescriptor) iter.next();
			if (sd instanceof FileSchemaDescriptor) {
				IFile schemaFile = ((FileSchemaDescriptor) sd).getFile();
				if (schemaFile.equals(file)) {
					sd.dispose();
					break;
				}
			}
		}
	}
	private void updateWorkspaceDescriptors() {
		for (int i = 0; i < dirtyWorkspaceModels.size(); i++) {
			IPluginModelBase model =
				(IPluginModelBase) dirtyWorkspaceModels.elementAt(i);
			updateWorkspaceDescriptors(model);
		}
		dirtyWorkspaceModels.clear();
	}
	private void updateWorkspaceDescriptors(IPluginModelBase model) {
		removeWorkspaceDescriptors(model);
		loadWorkspaceDescriptors(model);
	}
	public boolean visit(IResourceDelta delta) throws CoreException {
		IResource resource = delta.getResource();
		if (resource instanceof IFile) {
			IFile file = (IFile) resource;
			String fileName = file.getName().toLowerCase();
			if (!(fileName.endsWith(".exsd") || fileName.endsWith(".mxsd")))
				return true;
			if (WorkspaceModelManager.isPluginProject(file.getProject())
				== false)
				return true;
			if (delta.getKind() == IResourceDelta.CHANGED) {
				if ((IResourceDelta.CONTENT & delta.getFlags()) != 0) {
					updateExtensionPoint(file);
				}
			} else if (delta.getKind() == IResourceDelta.ADDED) {
				addExtensionPoint(file);
			} else if (delta.getKind() == IResourceDelta.REMOVED) {
				removeExtensionPoint(file);
			}
		}
		return true;
	}
}
