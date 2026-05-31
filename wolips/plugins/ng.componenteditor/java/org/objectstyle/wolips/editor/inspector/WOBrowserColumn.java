package org.objectstyle.wolips.editor.inspector;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableColumn;
import org.objectstyle.wolips.baseforuiplugins.utils.ListContentProvider;
import org.objectstyle.wolips.bindings.utils.BindingReflectionUtils;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

public class WOBrowserColumn extends Composite implements ISelectionProvider, ISelectionChangedListener, IElementChangedListener {
	/** Section header for action methods (return WOActionResults/NGActionResults). */
	static final String ACTIONS_HEADER = "\u26A1 Actions";

	/** Section header for regular keys (fields, getters, non-action methods). */
	static final String KEYS_HEADER = "\uD83D\uDD11 Keys";

	/** Empty row used as visual spacer before section headers. */
	static final String SPACER = "";

	/** Types whose return value marks a method as an action method. */
	private static final String[] ACTION_RESULTS_TYPES = {
		"com.webobjects.appserver.WOActionResults",
		"ng.appserver.NGActionResults"
	};

	private WOBrowser _browser;

	private List<ISelectionChangedListener> _listeners = new LinkedList<ISelectionChangedListener>();

	private IType _type;

	private TableViewer _keysViewer;

	private Font _typeNameFont;

	private BindingsDragHandler _lineDragHandler;

	private IWOBrowserDelegate _delegate;

	private List<Object> _bindingValueKeys;

	private TableColumnLayout _tableColumnLayout;

	private TableColumn _nameColumn;

	public WOBrowserColumn(WOBrowser browser, IType type, Composite parent, int style) throws JavaModelException {
		super(parent, style);
		setBackground(parent.getBackground());

		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.marginTop = 0;
		setLayout(layout);

		_browser = browser;
		_type = type;

		Label typeName = new Label(this, SWT.NONE);
		typeName.setBackground(getBackground());
		Font originalFont = typeName.getFont();
		FontData[] fontData = originalFont.getFontData();
		_typeNameFont = new Font(originalFont.getDevice(), fontData[0].getName(), fontData[0].getHeight(), SWT.BOLD);
		typeName.setFont(_typeNameFont);
		GridData typeNameData = new GridData(GridData.FILL_HORIZONTAL);
		typeNameData.horizontalIndent = 3;
		typeName.setLayoutData(typeNameData);

		typeName.setText(type.getElementName());

		Composite tableContainer = new Composite(this, SWT.NONE);
		tableContainer.setBackground(parent.getBackground());
		tableContainer.setLayoutData(new GridData(GridData.FILL_BOTH));

		_keysViewer = new TableViewer(tableContainer, SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER);
		_keysViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
		_keysViewer.addSelectionChangedListener(this);
		_keysViewer.setContentProvider(new ListContentProvider());
		_keysViewer.setLabelProvider(new WOBrowserColumnLabelProvider(_type));

		_tableColumnLayout = new TableColumnLayout();
		tableContainer.setLayout(_tableColumnLayout);

		// Name column: sized to fit the widest key name (updated in reload())
		_nameColumn = new TableColumn(_keysViewer.getTable(), SWT.NONE);
		_tableColumnLayout.setColumnData(_nameColumn, new ColumnPixelData(100, false));

		// Origin column: takes remaining space, left-aligned. Shows the
		// declaring type for inherited keys.
		TableColumn originColumn = new TableColumn(_keysViewer.getTable(), SWT.NONE);
		_tableColumnLayout.setColumnData(originColumn, new ColumnWeightData(1, 0));

		// Fixed-width icon column for navigation arrows.
		TableColumn iconColumn = new TableColumn(_keysViewer.getTable(), SWT.NONE);
		_tableColumnLayout.setColumnData(iconColumn, new ColumnPixelData(20, false));

		reload();

		_lineDragHandler = new BindingsDragHandler(this);

		JavaCore.addElementChangedListener(this, ElementChangedEvent.POST_CHANGE);
	}

	public void elementChanged(ElementChangedEvent event) {
		for (IJavaElementDelta delta : event.getDelta().getAffectedChildren()) {
			if ((delta.getFlags() & IJavaElementDelta.F_CHILDREN) != 0) {
				IJavaElement element = delta.getElement();
				if (element != null) {
					IJavaProject project = element.getJavaProject();
					if (project != null && project.equals(_type.getJavaProject())) {
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								try {
									reload();
								} catch (JavaModelException e) {
									ComponenteditorPlugin.getDefault().log(e);
								}
							}
						});
					}
				}
			}
		}
	}

	/**
	 * Reloads the column's key list by introspecting the type. All keys from
	 * the type hierarchy are collected into a single flat alphabetized list
	 * (BindingValueKey implements Comparable), then partitioned into action
	 * methods and regular keys with section headers. Inherited keys are
	 * distinguished from local keys by the label provider (gray suffix
	 * showing origin class).
	 */
	public void reload() throws JavaModelException {
		Map<IType, Set<BindingValueKey>> typeKeys = BindingReflectionUtils.getGroupedBindingValueKeys("", _type, WodParserCache.getTypeCache());

		// Collect all keys and partition into actions vs. regular keys
		List<BindingValueKey> actions = new LinkedList<BindingValueKey>();
		List<BindingValueKey> keys = new LinkedList<BindingValueKey>();
		for (Set<BindingValueKey> keySet : typeKeys.values()) {
			for (BindingValueKey key : keySet) {
				if (isActionMethod(key)) {
					actions.add(key);
				} else {
					keys.add(key);
				}
			}
		}
		actions.sort(null);
		keys.sort(null);

		// Build the combined list with section headers. A blank spacer row
		// before each header adds a bit of vertical breathing room.
		_bindingValueKeys = new LinkedList<Object>();
		if (!actions.isEmpty()) {
			_bindingValueKeys.add(ACTIONS_HEADER);
			_bindingValueKeys.addAll(actions);
		}
		if (!keys.isEmpty()) {
			if (!_bindingValueKeys.isEmpty()) {
				_bindingValueKeys.add(SPACER);
			}
			_bindingValueKeys.add(KEYS_HEADER);
			_bindingValueKeys.addAll(keys);
		}

		if (_keysViewer.getContentProvider() != null) {
			_keysViewer.setInput(_bindingValueKeys);
		}

		// Size the name column to exactly fit the widest key name, so the
		// origin column gets maximum space for the declaring type text.
		if (_nameColumn != null && !_nameColumn.isDisposed()) {
			int maxWidth = 0;
			GC gc = new GC(_keysViewer.getTable());
			try {
				gc.setFont(_keysViewer.getTable().getFont());
				for (Object keyObj : _bindingValueKeys) {
					if (keyObj instanceof BindingValueKey) {
						int w = gc.textExtent(((BindingValueKey) keyObj).getBindingName()).x;
						if (w > maxWidth) {
							maxWidth = w;
						}
					} else if (keyObj instanceof String) {
						int w = gc.textExtent((String) keyObj).x;
						if (w > maxWidth) {
							maxWidth = w;
						}
					}
				}
			} finally {
				gc.dispose();
			}
			// Add a small margin for cell padding
			_tableColumnLayout.setColumnData(_nameColumn, new ColumnPixelData(maxWidth + 10, false));
		}
	}

	/**
	 * Checks if a binding value key is an action method — a no-argument method
	 * whose return type implements WOActionResults or NGActionResults.
	 */
	private boolean isActionMethod(BindingValueKey key) {
		IMember member = key.getBindingMember();
		if (!(member instanceof IMethod)) {
			return false;
		}
		try {
			IType returnType = key.getNextType();
			if (returnType == null) {
				return false;
			}
			return BindingReflectionUtils.isType(returnType, ACTION_RESULTS_TYPES, WodParserCache.getTypeCache());
		} catch (JavaModelException e) {
			ComponenteditorPlugin.getDefault().log(e);
			return false;
		}
	}
	
	public BindingValueKey getBindingValueKeyStartingWith(String partialKeyPath) {
		BindingValueKey matchingKey = null;
		for (Object keyObj : _bindingValueKeys) {
			if (keyObj instanceof BindingValueKey) {
				BindingValueKey key = (BindingValueKey)keyObj;
				if (key.getBindingName().startsWith(partialKeyPath)) {
					matchingKey = key;
					break;
				}
			}
		}
		return matchingKey;
	}

	public WOBrowser getBrowser() {
		return _browser;
	}

	public void setDelegate(IWOBrowserDelegate delegate) {
		_delegate = delegate;
	}

	public IWOBrowserDelegate getDelegate() {
		return _delegate;
	}

	@Override
	public void dispose() {
		JavaCore.removeElementChangedListener(this);
		_typeNameFont.dispose();
		if (_lineDragHandler != null) {
			_lineDragHandler.dispose();
			_lineDragHandler = null;
		}
		super.dispose();
	}

	@Override
	public boolean setFocus() {
		return _keysViewer.getControl().setFocus();
	}

	public IType getType() {
		return _type;
	}

	public TableViewer getViewer() {
		return _keysViewer;
	}

	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		_listeners.add(listener);
	}

	public ISelection getSelection() {
		return _keysViewer.getSelection();
	}

	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		_listeners.remove(listener);
	}

	public void setSelection(ISelection selection) {
		_keysViewer.setSelection(selection, true);
	}

	public void selectionChanged(SelectionChangedEvent event) {
		SelectionChangedEvent wrappedEvent = new SelectionChangedEvent(this, event.getSelection());
		for (Iterator listeners = _listeners.iterator(); listeners.hasNext();) {
			ISelectionChangedListener listener = (ISelectionChangedListener) listeners.next();
			listener.selectionChanged(wrappedEvent);
		}
	}

	public String getSelectedKeyPath() {
		String keyPath;
		if (_browser == null) {
			BindingValueKey bindingValueKey = getSelectedKey();
			if (bindingValueKey == null) {
				keyPath = null;
			} else {
				keyPath = bindingValueKey.getBindingName();
			}
		} else {
			keyPath = _browser.getSelectedKeyPath();
		}
		return keyPath;
	}

	public BindingValueKey getSelectedKey() {
		BindingValueKey selectedKey = null;
		IStructuredSelection selection = (IStructuredSelection) getSelection();
		if (selection != null) {
			Object selectedObject = selection.getFirstElement();
			if (selectedObject instanceof BindingValueKey) {
				selectedKey = (BindingValueKey) selectedObject;
			}
		}
		return selectedKey;
	}
}