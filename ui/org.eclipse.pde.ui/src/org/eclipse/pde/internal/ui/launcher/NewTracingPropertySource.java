/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.launcher;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.forms.widgets.*;
/**
 */
public class NewTracingPropertySource {
	private IPluginModelBase fModel;
	private Vector fDescriptors;
	private Hashtable fTemplate;
	private Hashtable fValues;
	private Hashtable fDvalues;
	private static final String[] fBooleanChoices = {"false", "true"};
	private Properties fMasterOptions;
	private boolean fModified;
	private NewTracingLauncherTab fTab;
	private abstract class PropertyEditor {
		private String key;
		private String label;
		public PropertyEditor(String key, String label) {
			this.key = key;
			this.label = label;
		}
		public String getKey() {
			return key;
		}
		public String getLabel() {
			return label;
		}
		abstract void create(Composite parent);
		abstract void update();
		abstract void initialize();
		protected void valueModified(Object value) {
			fValues.put(getKey(), value);
			fModified = true;
			fTab.updateLaunchConfigurationDialog();
		}
	}
	private class BooleanEditor extends PropertyEditor {
		private Button checkbox;
		public BooleanEditor(String key, String label) {
			super(key, label);
		}
		public void create(Composite parent) {
			checkbox = fTab.getToolkit().createButton(parent, getLabel(),
					SWT.CHECK);
			TableWrapData td = new TableWrapData();
			td.colspan = 2;
			checkbox.setLayoutData(td);
		}
		public void update() {
			Integer value = (Integer) fValues.get(getKey());
			checkbox.setSelection(value.intValue() == 1);
		}
		public void initialize() {
			update();
			checkbox.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					int value = checkbox.getSelection() ? 1 : 0;
					valueModified(new Integer(value));
				}
			});
		}
	}
	private class TextEditor extends PropertyEditor {
		private Text text;
		public TextEditor(String key, String label) {
			super(key, label);
		}
		public void create(Composite parent) {
			Label label = fTab.getToolkit().createLabel(parent, getLabel());
			TableWrapData td = new TableWrapData();
			td.valign = TableWrapData.MIDDLE;
			label.setLayoutData(td);
			text = fTab.getToolkit().createText(parent, "");
			td = new TableWrapData(TableWrapData.FILL_GRAB);
			//gd.widthHint = 100;
			text.setLayoutData(td);
		}
		public void update() {
			String value = (String) fValues.get(getKey());
			text.setText(value);
		}
		public void initialize() {
			update();
			text.addModifyListener(new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					valueModified(text.getText());
				}
			});
		}
	}
	public NewTracingPropertySource(IPluginModelBase model,
			Properties masterOptions, Hashtable template,
			NewTracingLauncherTab tab) {
		this.fModel = model;
		this.fMasterOptions = masterOptions;
		this.fTemplate = template;
		this.fTab = tab;
		fValues = new Hashtable();
		fDvalues = new Hashtable();
	}
	public IPluginModelBase getModel() {
		return fModel;
	}
	private Object[] getSortedKeys(int size, Enumeration keys) {
		Object[] keyArray = new Object[size];
		int i = 0;
		for (Enumeration enum = fTemplate.keys(); enum.hasMoreElements();) {
			String key = (String) enum.nextElement();
			keyArray[i++] = key;
		}
		Arrays.sort(keyArray, new Comparator() {
			public int compare(Object o1, Object o2) {
				return compareKeys(o1, o2);
			}
		});
		return keyArray;
	}
	private int compareKeys(Object o1, Object o2) {
		String s1 = (String) o1;
		String s2 = (String) o2;
		int sc1 = getNumberOfSlashes(s1);
		int sc2 = getNumberOfSlashes(s2);
		if (sc1 < sc2)
			return -1;
		if (sc1 > sc2)
			return 1;
		// equal
		return s1.compareTo(s2);
	}
	private int getNumberOfSlashes(String s) {
		int count = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '/')
				count++;
		}
		return count;
	}
	public void createContents(Composite parent) {
		fDescriptors = new Vector();
		TableWrapLayout layout = new TableWrapLayout();
		layout.numColumns = 2;
		parent.setLayout(layout);
		Object[] sortedKeys = getSortedKeys(fTemplate.size(), fTemplate.keys());
		for (int i = 0; i < sortedKeys.length; i++) {
			String key = (String) sortedKeys[i];
			IPath path = new Path(key);
			path = path.removeFirstSegments(1);
			String shortKey = path.toString();
			String value = (String) fTemplate.get(key);
			String lvalue = null;
			String masterValue = fMasterOptions.getProperty(key);
			PropertyEditor editor;
			if (value != null)
				lvalue = value.toLowerCase();
			if (lvalue != null
					&& (lvalue.equals("true") || lvalue.equals("false"))) {
				editor = new BooleanEditor(shortKey, shortKey);
				Integer dvalue = new Integer(lvalue.equals("true") ? 1 : 0);
				fDvalues.put(shortKey, dvalue);
				if (masterValue != null) {
					Integer mvalue = new Integer(masterValue.equals("true")
							? 1
							: 0);
					fValues.put(shortKey, mvalue);
				}
			} else {
				editor = new TextEditor(shortKey, shortKey);
				fDvalues.put(shortKey, value != null ? value : "");
				if (masterValue != null) {
					fValues.put(shortKey, masterValue);
				}
			}
			editor.create(parent);
			editor.initialize();
			fDescriptors.add(editor);
		}
	}

	/**
	 */
	public void save() {
		String pid = fModel.getPluginBase().getId();
		for (Enumeration enum = fValues.keys(); enum.hasMoreElements();) {
			String shortKey = (String) enum.nextElement();
			Object value = fValues.get(shortKey);
			String svalue = value.toString();
			if (value instanceof Integer)
				svalue = fBooleanChoices[((Integer) value).intValue()];
			IPath path = new Path(pid).append(shortKey);
			fMasterOptions.setProperty(path.toString(), svalue);
		}
		fModified = false;
	}
	public void dispose() {
	}
	public boolean isModified() {
		return fModified;
	}
}