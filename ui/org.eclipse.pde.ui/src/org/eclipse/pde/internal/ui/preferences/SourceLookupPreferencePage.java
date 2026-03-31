/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.preferences;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.core.target.TargetPlatformService;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.IPreferenceConstants;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.SWTFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

/**
 * Preference page for managing source lookup settings
 */
public class SourceLookupPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String ID = "org.eclipse.pde.ui.SourceLookupPreferencePage"; //$NON-NLS-1$

	// Source lookup strategy identifiers
	private static final String STRATEGY_REPOSITORIES = "REPOSITORIES"; //$NON-NLS-1$
	private static final String STRATEGY_TARGETS = "TARGETS"; //$NON-NLS-1$
	private static final String STRATEGY_INDEX = "INDEX"; //$NON-NLS-1$
	private static final String STRATEGY_SITES = "SITES"; //$NON-NLS-1$

	private Button fEnableSourceLookup;
	private TableViewer fOrderTableViewer;
	private Button fUpButton;
	private Button fDownButton;
	private CheckboxTableViewer fTargetsTableViewer;
	private CheckboxTableViewer fRepositoriesTableViewer;
	private Button fAddRepositoryButton;
	private Button fRemoveRepositoryButton;

	private List<String> fLookupOrder = new ArrayList<>();
	private List<ITargetDefinition> fTargets = new ArrayList<>();
	private List<String> fRepositories = new ArrayList<>();

	public SourceLookupPreferencePage() {
		setPreferenceStore(PDEPlugin.getDefault().getPreferenceStore());
		setDescription(PDEUIMessages.SourceLookupPreferencePage_description);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = SWTFactory.createComposite(parent, 1, 1, GridData.FILL_BOTH, 0, 0);
		((GridLayout) composite.getLayout()).verticalSpacing = 15;
		((GridLayout) composite.getLayout()).marginTop = 15;

		// Enable checkbox
		fEnableSourceLookup = new Button(composite, SWT.CHECK);
		fEnableSourceLookup.setText(PDEUIMessages.SourceLookupPreferencePage_enableSourceLookup);
		fEnableSourceLookup.setSelection(getPreferenceStore().getBoolean(IPreferenceConstants.SOURCE_LOOKUP_ENABLED));
		fEnableSourceLookup.addSelectionListener(widgetSelectedAdapter(e -> updateEnablement()));

		// Lookup order group
		Group orderGroup = SWTFactory.createGroup(composite, PDEUIMessages.SourceLookupPreferencePage_orderGroup, 2, 1, GridData.FILL_HORIZONTAL);

		Label orderDescription = new Label(orderGroup, SWT.WRAP);
		orderDescription.setText(PDEUIMessages.SourceLookupPreferencePage_orderDescription);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		gd.widthHint = 400;
		orderDescription.setLayoutData(gd);

		fOrderTableViewer = new TableViewer(orderGroup, SWT.BORDER | SWT.V_SCROLL);
		gd = new GridData(GridData.FILL_BOTH);
		gd.heightHint = 100;
		fOrderTableViewer.getControl().setLayoutData(gd);
		fOrderTableViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return getStrategyLabel((String) element);
			}
		});
		fOrderTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		fOrderTableViewer.addSelectionChangedListener(event -> updateOrderButtons());

		// Up/Down buttons
		Composite buttonComposite = SWTFactory.createComposite(orderGroup, 1, 1, GridData.FILL_VERTICAL | GridData.VERTICAL_ALIGN_BEGINNING, 0, 0);

		fUpButton = SWTFactory.createPushButton(buttonComposite, PDEUIMessages.SourceLookupPreferencePage_up, null);
		fUpButton.addSelectionListener(widgetSelectedAdapter(e -> handleMoveUp()));

		fDownButton = SWTFactory.createPushButton(buttonComposite, PDEUIMessages.SourceLookupPreferencePage_down, null);
		fDownButton.addSelectionListener(widgetSelectedAdapter(e -> handleMoveDown()));

		// Load lookup order
		loadLookupOrder();

		// Target platforms group
		Group targetsGroup = SWTFactory.createGroup(composite, PDEUIMessages.SourceLookupPreferencePage_targetsGroup, 1, 1, GridData.FILL_BOTH);
		targetsGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

		fTargetsTableViewer = CheckboxTableViewer.newCheckList(targetsGroup, SWT.BORDER | SWT.V_SCROLL);
		gd = new GridData(GridData.FILL_BOTH);
		gd.heightHint = 120;
		fTargetsTableViewer.getControl().setLayoutData(gd);
		fTargetsTableViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ITargetDefinition) {
					String name = ((ITargetDefinition) element).getName();
					return name != null ? name : PDEUIMessages.SourceLookupPreferencePage_unnamedTarget;
				}
				return super.getText(element);
			}
		});
		fTargetsTableViewer.setContentProvider(ArrayContentProvider.getInstance());

		// Load available targets
		loadTargets();

		// Repositories group
		Group repositoriesGroup = SWTFactory.createGroup(composite, PDEUIMessages.SourceLookupPreferencePage_repositoriesGroup, 2, 1, GridData.FILL_BOTH);
		repositoriesGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

		fRepositoriesTableViewer = CheckboxTableViewer.newCheckList(repositoriesGroup, SWT.BORDER | SWT.V_SCROLL);
		gd = new GridData(GridData.FILL_BOTH);
		gd.heightHint = 120;
		fRepositoriesTableViewer.getControl().setLayoutData(gd);
		fRepositoriesTableViewer.setLabelProvider(new LabelProvider());
		fRepositoriesTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		fRepositoriesTableViewer.addSelectionChangedListener(event -> updateRepositoryButtons());

		// Load repositories
		loadRepositories();

		// Repository buttons
		buttonComposite = SWTFactory.createComposite(repositoriesGroup, 1, 1, GridData.FILL_VERTICAL | GridData.VERTICAL_ALIGN_BEGINNING, 0, 0);

		fAddRepositoryButton = SWTFactory.createPushButton(buttonComposite, PDEUIMessages.SourceLookupPreferencePage_addRepository, null);
		fAddRepositoryButton.addSelectionListener(widgetSelectedAdapter(e -> handleAddRepository()));

		fRemoveRepositoryButton = SWTFactory.createPushButton(buttonComposite, PDEUIMessages.SourceLookupPreferencePage_removeRepository, null);
		fRemoveRepositoryButton.addSelectionListener(widgetSelectedAdapter(e -> handleRemoveRepository()));

		updateEnablement();
		updateRepositoryButtons();
		updateOrderButtons();

		return composite;
	}

	private String getStrategyLabel(String strategy) {
		switch (strategy) {
		case STRATEGY_REPOSITORIES:
			return PDEUIMessages.SourceLookupPreferencePage_strategy_REPOSITORIES;
		case STRATEGY_TARGETS:
			return PDEUIMessages.SourceLookupPreferencePage_strategy_TARGETS;
		case STRATEGY_INDEX:
			return PDEUIMessages.SourceLookupPreferencePage_strategy_INDEX;
		case STRATEGY_SITES:
			return PDEUIMessages.SourceLookupPreferencePage_strategy_SITES;
		default:
			return strategy;
		}
	}

	private void loadLookupOrder() {
		String order = getPreferenceStore().getString(IPreferenceConstants.SOURCE_LOOKUP_ORDER);
		if (order == null || order.trim().isEmpty()) {
			// Use default order
			fLookupOrder = new ArrayList<>(Arrays.asList(STRATEGY_REPOSITORIES, STRATEGY_TARGETS, STRATEGY_INDEX, STRATEGY_SITES));
		} else {
			fLookupOrder = new ArrayList<>(Arrays.asList(order.split(","))); //$NON-NLS-1$
		}
		fOrderTableViewer.setInput(fLookupOrder);
	}

	private void loadTargets() {
		ITargetPlatformService service = TargetPlatformService.getDefault();
		if (service != null) {
			ITargetHandle[] targets = service.getTargets(null);
			for (ITargetHandle target : targets) {
				try {
					fTargets.add(target.getTargetDefinition());
				} catch (CoreException e) {
					PDEPlugin.log(e);
				}
			}
			fTargetsTableViewer.setInput(fTargets);

			// Restore selected targets
			String selectedTargets = getPreferenceStore().getString(IPreferenceConstants.SOURCE_LOOKUP_SELECTED_TARGETS);
			if (!selectedTargets.isEmpty()) {
				List<String> selectedNames = Arrays.asList(selectedTargets.split(",")); //$NON-NLS-1$
				List<ITargetDefinition> toCheck = fTargets.stream()
						.filter(t -> t.getName() != null && selectedNames.contains(t.getName()))
						.collect(Collectors.toList());
				fTargetsTableViewer.setCheckedElements(toCheck.toArray());
			}
		}
	}

	private void loadRepositories() {
		String repositories = getPreferenceStore().getString(IPreferenceConstants.SOURCE_LOOKUP_REPOSITORIES);
		if (!repositories.isEmpty()) {
			fRepositories = new ArrayList<>(Arrays.asList(repositories.split(","))); //$NON-NLS-1$
			fRepositoriesTableViewer.setInput(fRepositories);
			fRepositoriesTableViewer.setAllChecked(true);
		}
	}

	private void handleMoveUp() {
		IStructuredSelection selection = (IStructuredSelection) fOrderTableViewer.getSelection();
		if (!selection.isEmpty()) {
			String strategy = (String) selection.getFirstElement();
			int index = fLookupOrder.indexOf(strategy);
			if (index > 0) {
				fLookupOrder.remove(index);
				fLookupOrder.add(index - 1, strategy);
				fOrderTableViewer.refresh();
				fOrderTableViewer.setSelection(selection);
				updateOrderButtons();
			}
		}
	}

	private void handleMoveDown() {
		IStructuredSelection selection = (IStructuredSelection) fOrderTableViewer.getSelection();
		if (!selection.isEmpty()) {
			String strategy = (String) selection.getFirstElement();
			int index = fLookupOrder.indexOf(strategy);
			if (index < fLookupOrder.size() - 1) {
				fLookupOrder.remove(index);
				fLookupOrder.add(index + 1, strategy);
				fOrderTableViewer.refresh();
				fOrderTableViewer.setSelection(selection);
				updateOrderButtons();
			}
		}
	}

	private void handleAddRepository() {
		InputDialog dialog = new InputDialog(getShell(), 
				PDEUIMessages.SourceLookupPreferencePage_addRepositoryTitle,
				PDEUIMessages.SourceLookupPreferencePage_addRepositoryMessage, 
				"", //$NON-NLS-1$
				null);
		if (dialog.open() == Window.OK) {
			String url = dialog.getValue().trim();
			if (!url.isEmpty() && !fRepositories.contains(url)) {
				fRepositories.add(url);
				fRepositoriesTableViewer.setInput(fRepositories);
				fRepositoriesTableViewer.setChecked(url, true);
			}
		}
	}

	private void handleRemoveRepository() {
		IStructuredSelection selection = (IStructuredSelection) fRepositoriesTableViewer.getSelection();
		if (!selection.isEmpty()) {
			Object[] elements = selection.toArray();
			for (Object element : elements) {
				fRepositories.remove(element);
			}
			fRepositoriesTableViewer.setInput(fRepositories);
		}
	}

	private void updateEnablement() {
		boolean enabled = fEnableSourceLookup.getSelection();
		fOrderTableViewer.getControl().setEnabled(enabled);
		fUpButton.setEnabled(enabled);
		fDownButton.setEnabled(enabled);
		fTargetsTableViewer.getControl().setEnabled(enabled);
		fRepositoriesTableViewer.getControl().setEnabled(enabled);
		fAddRepositoryButton.setEnabled(enabled);
		fRemoveRepositoryButton.setEnabled(enabled && !fRepositoriesTableViewer.getSelection().isEmpty());
		updateOrderButtons();
	}

	private void updateOrderButtons() {
		if (!fEnableSourceLookup.getSelection()) {
			fUpButton.setEnabled(false);
			fDownButton.setEnabled(false);
			return;
		}

		IStructuredSelection selection = (IStructuredSelection) fOrderTableViewer.getSelection();
		if (selection.isEmpty()) {
			fUpButton.setEnabled(false);
			fDownButton.setEnabled(false);
		} else {
			String strategy = (String) selection.getFirstElement();
			int index = fLookupOrder.indexOf(strategy);
			fUpButton.setEnabled(index > 0);
			fDownButton.setEnabled(index < fLookupOrder.size() - 1);
		}
	}

	private void updateRepositoryButtons() {
		fRemoveRepositoryButton.setEnabled(fEnableSourceLookup.getSelection() && !fRepositoriesTableViewer.getSelection().isEmpty());
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		Dialog.applyDialogFont(getControl());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IHelpContextIds.MAIN_PREFERENCE_PAGE);
	}

	@Override
	public boolean performOk() {
		getPreferenceStore().setValue(IPreferenceConstants.SOURCE_LOOKUP_ENABLED, fEnableSourceLookup.getSelection());

		// Save lookup order
		String order = String.join(",", fLookupOrder); //$NON-NLS-1$
		getPreferenceStore().setValue(IPreferenceConstants.SOURCE_LOOKUP_ORDER, order);

		// Save selected targets
		Object[] checkedTargets = fTargetsTableViewer.getCheckedElements();
		String selectedTargets = Arrays.stream(checkedTargets)
				.filter(t -> t instanceof ITargetDefinition)
				.map(t -> ((ITargetDefinition) t).getName())
				.filter(name -> name != null)
				.collect(Collectors.joining(",")); //$NON-NLS-1$
		getPreferenceStore().setValue(IPreferenceConstants.SOURCE_LOOKUP_SELECTED_TARGETS, selectedTargets);

		// Save repositories
		String repositories = String.join(",", fRepositories); //$NON-NLS-1$
		getPreferenceStore().setValue(IPreferenceConstants.SOURCE_LOOKUP_REPOSITORIES, repositories);

		return super.performOk();
	}

	@Override
	protected void performDefaults() {
		fEnableSourceLookup.setSelection(getPreferenceStore().getDefaultBoolean(IPreferenceConstants.SOURCE_LOOKUP_ENABLED));
		
		// Reset to default order
		fLookupOrder = new ArrayList<>(Arrays.asList(STRATEGY_REPOSITORIES, STRATEGY_TARGETS, STRATEGY_INDEX, STRATEGY_SITES));
		fOrderTableViewer.setInput(fLookupOrder);
		fOrderTableViewer.refresh();
		
		fTargetsTableViewer.setCheckedElements(new Object[0]);
		fRepositories.clear();
		fRepositoriesTableViewer.setInput(fRepositories);
		updateEnablement();
		updateRepositoryButtons();
		updateOrderButtons();
		super.performDefaults();
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
