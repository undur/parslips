package org.objectstyle.wolips.componenteditor.inspector;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import java.util.Objects;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.bindings.wod.BindingValueKeyPath;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

public class WOBrowser extends ScrolledComposite implements ISelectionChangedListener, ISelectionProvider, KeyListener {
	/** Default width (in pixels) for each browser column. */
	static final int DEFAULT_COLUMN_WIDTH = 420;

	/** Minimum width a column can be resized to. */
	private static final int MIN_COLUMN_WIDTH = 120;

	private Composite _browserComposite;

	private List<WOBrowserColumn> _columns;

	/** Resize handles between adjacent columns. One fewer than _columns. */
	private List<Composite> _resizeHandles = new LinkedList<Composite>();

	private List<ISelectionChangedListener> _listeners = new LinkedList<ISelectionChangedListener>();

	private IWOBrowserDelegate _browserDelegate;

	private StringBuffer _keypathBuffer;

	private long _lastKeyTime;

	public WOBrowser(Composite parent, int style) {
		super(parent, SWT.H_SCROLL | style);
		_keypathBuffer = new StringBuffer();
		_columns = new LinkedList<WOBrowserColumn>();

		_browserComposite = new Composite(this, SWT.NONE);
		_browserComposite.setBackground(parent.getBackground());
		// _browser.setLayoutData(new GridData(GridData.FILL_BOTH));

		setContent(_browserComposite);
		setExpandVertical(true);

		GridLayout browserLayout = new GridLayout(1, false);
		browserLayout.horizontalSpacing = 0;
		browserLayout.marginWidth = 5;
		browserLayout.marginHeight = 5;
		browserLayout.horizontalSpacing = 5;
		_browserComposite.setLayout(browserLayout);
	}

	public void setBrowserDelegate(IWOBrowserDelegate browserDelegate) {
		_browserDelegate = browserDelegate;
		for (WOBrowserColumn column : _columns) {
			column.setDelegate(browserDelegate);
		}
	}

	public IWOBrowserDelegate getBrowserDelegate() {
		return _browserDelegate;
	}

	public WOBrowserColumn setRootType(IType type) throws JavaModelException {
		disposeToColumn(-1);
		return addType(type);
	}

	private WOBrowserColumn addType(IType type) throws JavaModelException {
		WOBrowserColumn newColumn = null;
		if (type != null) {
			newColumn = new WOBrowserColumn(this, type, _browserComposite, SWT.NONE);
			newColumn.getViewer().getTable().addKeyListener(this);
			newColumn.setDelegate(_browserDelegate);
			newColumn.addSelectionChangedListener(this);
			GridData columnLayoutData = new GridData(GridData.FILL_BOTH);
			columnLayoutData.widthHint = DEFAULT_COLUMN_WIDTH;
			newColumn.setLayoutData(columnLayoutData);
			_columns.add(newColumn);
			if (_browserDelegate != null) {
				_browserDelegate.browserColumnAdded(newColumn);
			}

			// Add a trailing resize handle after the column so every column
			// (including the last one) can be resized by dragging its right edge.
			Composite handle = createResizeHandle(_browserComposite);
			_resizeHandles.add(handle);

			// Grid cells = browser columns + one resize handle per column
			((GridLayout) _browserComposite.getLayout()).numColumns = _columns.size() + _resizeHandles.size();

			resizeBrowserComposite();

			for (WOBrowserColumn column : _columns) {
				Object selectedElement = ((IStructuredSelection) column.getSelection()).getFirstElement();
				if (selectedElement != null) {
					column.getViewer().reveal(selectedElement);
				}
			}

			getHorizontalBar().setSelection(getHorizontalBar().getMaximum());
			layout();
		}
		return newColumn;
	}

	public void disposeToColumn(WOBrowserColumn column) {
		int columnIndex = _columns.indexOf(column);
		if (columnIndex != -1) {
			disposeToColumn(columnIndex);
		}
	}

	public void disposeToColumn(int columnIndex) {
		for (int columnNum = _columns.size() - 1; columnNum > columnIndex; columnNum--) {
			WOBrowserColumn column = _columns.get(columnNum);
			if (_browserDelegate != null) {
				_browserDelegate.browserColumnRemoved(column);
			}
			column.dispose();
			_columns.remove(columnNum);

			// Remove the trailing resize handle that belongs to this column
			if (columnNum < _resizeHandles.size()) {
				_resizeHandles.get(columnNum).dispose();
				_resizeHandles.remove(columnNum);
			}
		}

		resizeBrowserComposite();
	}

	/**
	 * Sizes the browser composite to fit its columns and handles based on
	 * their widthHint values, then triggers a layout pass. Unlike pack(),
	 * this does not ask children for preferred sizes, so inner
	 * TableColumnLayouts size their columns to fit within the widthHint
	 * rather than expanding to fit content.
	 */
	private void resizeBrowserComposite() {
		GridLayout gl = (GridLayout) _browserComposite.getLayout();
		int totalWidth = gl.marginWidth * 2;
		int numGaps = Math.max(0, _columns.size() + _resizeHandles.size() - 1);
		totalWidth += numGaps * gl.horizontalSpacing;

		for (WOBrowserColumn column : _columns) {
			GridData gd = (GridData) column.getLayoutData();
			totalWidth += gd.widthHint;
		}
		for (Composite handle : _resizeHandles) {
			GridData gd = (GridData) handle.getLayoutData();
			totalWidth += gd.widthHint;
		}

		int height = _browserComposite.getParent().getClientArea().height;
		if (height <= 0) {
			height = SWT.DEFAULT;
		}
		_browserComposite.setSize(totalWidth, height);
		_browserComposite.layout(true, true);
	}

	public WOBrowserColumn selectKeyInColumn(BindingValueKey selectedKey, WOBrowserColumn column) {
		WOBrowserColumn addedColumn = null;

		disposeToColumn(column);

		if (selectedKey == null) {
		} else {
			try {
				if (!selectedKey.isLeaf()) {
					IType nextType = selectedKey.getNextType();
					// Only create a new column if the type actually has
					// visible binding keys. This prevents empty columns
					// for types like WOActionResults whose methods are
					// all filtered out.
					if (nextType != null && WodParserCache.getTypeCache().hasVisibleBindingKeys(nextType)) {
						addedColumn = addType(nextType);
					}
				}
			} catch (JavaModelException e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		return addedColumn;
	}

	public void selectionChanged(SelectionChangedEvent event) {
		WOBrowserColumn selectedColumn = (WOBrowserColumn) event.getSource();

		IStructuredSelection selection = (IStructuredSelection) event.getSelection();
		Object selectedObject = selection.getFirstElement();
		if (selectedObject instanceof BindingValueKey) {
			BindingValueKey selectedKey = (BindingValueKey) selectedObject;
			selectKeyInColumn(selectedKey, selectedColumn);
		}
		else {
			selectKeyInColumn(null, selectedColumn);
		}

		SelectionChangedEvent wrappedEvent = new SelectionChangedEvent(this, getSelection());
		for (Iterator listeners = _listeners.iterator(); listeners.hasNext();) {
			ISelectionChangedListener listener = (ISelectionChangedListener) listeners.next();
			listener.selectionChanged(wrappedEvent);
		}
	}

	public WOBrowserColumn getFocusedColumn() {
		WOBrowserColumn focusedColumn = null;
		for (WOBrowserColumn column : _columns) {
			if (column.getViewer().getTable().isFocusControl()) {
				focusedColumn = column;
			}
		}
		return focusedColumn;
	}

	public WOBrowserColumn getSelectedColumn() {
		WOBrowserColumn selectedColumn = null;
		for (WOBrowserColumn column : _columns) {
			if (column.getSelectedKey() != null) {
				selectedColumn = column;
			}
		}
		return selectedColumn;
	}

	public String getSelectedKeyPath() {
		return getSelectedKeyPath(null);
	}

	private String getSelectedKeyPath(WOBrowserColumn throughColumn) {
		StringBuffer keyPath = new StringBuffer();
		for (WOBrowserColumn column : _columns) {
			BindingValueKey key = column.getSelectedKey();
			if (key != null) {
				if (keyPath.length() > 0) {
					keyPath.append('.');
				}
				keyPath.append(key.getBindingName());
				if (throughColumn == column) {
					break;
				}
			}
		}
		return keyPath.toString();
	}

	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		_listeners.add(listener);
	}

	public ISelection getSelection() {
		return new StructuredSelection(getSelectedKeyPath());
	}

	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		_listeners.remove(listener);
	}

	public void setSelection(ISelection selection) {
		Object selectedObject = ((IStructuredSelection) selection).getFirstElement();
		if (selectedObject == null || selectedObject instanceof String) {
			String selectedKeyPath = (String) selectedObject;
			if (selectedKeyPath == null) {
				WOBrowserColumn column = _columns.get(0);
				selectKeyInColumn(null, column);
				column.setSelection(new StructuredSelection());
			} else {
				try {
					if (!Objects.equals(selectedKeyPath, getSelectedKeyPath())) {
						BindingValueKeyPath bindingValueKeyPath = new BindingValueKeyPath(selectedKeyPath, _columns.get(0).getType(), WodParserCache.getTypeCache());
						disposeToColumn(0);
						if (bindingValueKeyPath != null && bindingValueKeyPath.isValid()) {
							BindingValueKey[] bindingKeys = bindingValueKeyPath.getBindingKeys();
							if (bindingKeys != null) {
								for (BindingValueKey bindingValueKey : bindingKeys) {
									WOBrowserColumn column = _columns.get(_columns.size() - 1);
									column.setSelection(new StructuredSelection(bindingValueKey));
		
									WOBrowserColumn newColumn = selectKeyInColumn(bindingValueKey, column);
									if (newColumn == null) {
										break;
									}
								}
							}
						}
					}
				} catch (JavaModelException e) {
					ComponenteditorPlugin.getDefault().log(e);
				}
			}
		}
	}

	private String getPreviousSelectedKeyPath() {
		String previousSelectedKeyPath = null;
		String selectedKey = getSelectedKeyPath();
		if (selectedKey.length() > 0) {
			int dotIndex = selectedKey.lastIndexOf('.');
			if (dotIndex != -1) {
				previousSelectedKeyPath = selectedKey.substring(0, dotIndex);
			}
		}
		return previousSelectedKeyPath;
	}

	public void selectPreviousColumn() {
		String previousSelectedKeyPath = getPreviousSelectedKeyPath();
		if (previousSelectedKeyPath != null) {
			setSelection(new StructuredSelection(previousSelectedKeyPath));
			WOBrowserColumn previousColumn = getSelectedColumn();
			if (previousColumn != null) {
				previousColumn.setFocus();
			}
		}
		clearKeyBuffer();
	}

	public void selectNextColumn() {
		WOBrowserColumn column = getSelectedColumn();
		if (column != null) {
			int columnIndex = _columns.indexOf(column);
			if (columnIndex < _columns.size() - 1) {
				WOBrowserColumn nextColumn = _columns.get(_columns.size() - 1);
				Object firstElement = nextColumn.getViewer().getElementAt(0);
				nextColumn.setSelection(new StructuredSelection(firstElement));
				nextColumn.setFocus();
			}
		}
		clearKeyBuffer();
	}

	private void clearKeyBuffer() {
		_keypathBuffer.setLength(0);
	}

	private void selectFromKeyBuffer() {
		if (_keypathBuffer.length() > 0) {
			WOBrowserColumn focusedColumn = getFocusedColumn();
			if (focusedColumn != null) {
				BindingValueKey matchingKey = focusedColumn.getBindingValueKeyStartingWith(_keypathBuffer.toString());
				disposeToColumn(focusedColumn);
				if (matchingKey != null) {
					focusedColumn.setSelection(new StructuredSelection(matchingKey));
				}
			}
		}
	}

	private void deleteFromKeyBuffer() {
		if (_keypathBuffer.length() > 0) {
			_keypathBuffer.setLength(_keypathBuffer.length() - 1);
		}
		selectFromKeyBuffer();
		_lastKeyTime = System.currentTimeMillis();
	}

	private void appendToKeyBuffer(char ch) {
		long keyTime = System.currentTimeMillis();
		if ((keyTime - _lastKeyTime) > 1000 || _keypathBuffer.length() == 0) {
			_keypathBuffer.setLength(0);
		}
		_keypathBuffer.append(ch);

		selectFromKeyBuffer();

		_lastKeyTime = keyTime;
	}

	public void keyPressed(KeyEvent e) {
		if (e.keyCode == SWT.ARROW_LEFT) {
			selectPreviousColumn();
		} else if (e.keyCode == SWT.ARROW_RIGHT) {
			selectNextColumn();
		} else if (e.keyCode == SWT.ESC) {
			clearKeyBuffer();
		} else if (e.character == '.') {
			selectNextColumn();
		} else if (e.keyCode == SWT.BS || e.keyCode == SWT.DEL) {
			deleteFromKeyBuffer();
		} else if (e.character != 0) {
			appendToKeyBuffer(e.character);
		}
	}

	public void keyReleased(KeyEvent e) {
		// no-op — only keyPressed is handled
	}

	/**
	 * Creates a thin vertical resize handle placed after a browser column.
	 * Dragging resizes the column at the same index. Uses a 4px-wide
	 * composite with a SIZEWE cursor. Double-click resets to default width.
	 */
	private Composite createResizeHandle(Composite parent) {
		Composite handle = new Composite(parent, SWT.NONE);
		GridData handleData = new GridData(GridData.FILL_VERTICAL);
		handleData.widthHint = 4;
		handle.setLayoutData(handleData);
		handle.setBackground(parent.getBackground());

		Cursor resizeCursor = new Cursor(getDisplay(), SWT.CURSOR_SIZEWE);
		handle.setCursor(resizeCursor);
		handle.addDisposeListener(e -> resizeCursor.dispose());

		handle.addMouseListener(new MouseListener() {
			private int _startX;

			public void mouseDown(MouseEvent e) {
				_startX = handle.toDisplay(e.x, 0).x;
				handle.setData("_startX", _startX);
			}

			public void mouseUp(MouseEvent e) {
				handle.setData("_startX", null);
			}

			public void mouseDoubleClick(MouseEvent e) {
				// Double-click resets to default width
				int handleIndex = _resizeHandles.indexOf(handle);
				if (handleIndex >= 0 && handleIndex < _columns.size()) {
					WOBrowserColumn column = _columns.get(handleIndex);
					GridData gd = (GridData) column.getLayoutData();
					gd.widthHint = DEFAULT_COLUMN_WIDTH;
					resizeBrowserComposite();
					layout();
				}
			}
		});

		handle.addMouseMoveListener(new MouseMoveListener() {
			public void mouseMove(MouseEvent e) {
				Object startXObj = handle.getData("_startX");
				if (startXObj == null) {
					return;
				}
				int startX = (Integer) startXObj;
				int currentX = handle.toDisplay(e.x, 0).x;
				int delta = currentX - startX;
				handle.setData("_startX", currentX);

				// Resize the column at this handle's index
				int handleIndex = _resizeHandles.indexOf(handle);
				if (handleIndex >= 0 && handleIndex < _columns.size()) {
					WOBrowserColumn column = _columns.get(handleIndex);
					GridData gd = (GridData) column.getLayoutData();
					int newWidth = Math.max(MIN_COLUMN_WIDTH, gd.widthHint + delta);
					gd.widthHint = newWidth;
					resizeBrowserComposite();
					layout();
				}
			}
		});

		return handle;
	}

}
