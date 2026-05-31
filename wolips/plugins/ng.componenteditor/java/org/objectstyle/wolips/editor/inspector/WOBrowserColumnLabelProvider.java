package org.objectstyle.wolips.editor.inspector;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * Label provider for WOBrowser columns. Three table columns:
 * <ol>
 *   <li>Key name (left-aligned), or section header text for String elements</li>
 *   <li>Declaring type name for inherited keys (left-aligned, gray)</li>
 *   <li>Navigation arrow icon for non-leaf types</li>
 * </ol>
 *
 * String elements are rendered as section headers (bold, gray) in the first
 * column only. The other columns are left empty for header rows.
 */
public class WOBrowserColumnLabelProvider extends StyledCellLabelProvider {
	private IType _type;

	/** Bold font for section headers, created lazily and disposed with the table. */
	private Font _headerFont;

	/** Styler for the gray declaring-type text on inherited keys. */
	private static final Styler QUALIFIER_STYLER = new Styler() {
		@Override
		public void applyStyles(TextStyle textStyle) {
			textStyle.foreground = Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);
		}
	};

	public WOBrowserColumnLabelProvider(IType type) {
		_type = type;
	}

	@Override
	public void update(ViewerCell cell) {
		Object element = cell.getElement();

		if (element instanceof String) {
			updateSectionHeader(cell, (String) element);
			super.update(cell);
			return;
		}

		if (!(element instanceof BindingValueKey)) {
			super.update(cell);
			return;
		}

		BindingValueKey key = (BindingValueKey) element;
		int column = cell.getColumnIndex();

		if (column == 0) {
			// Key name, left-aligned
			cell.setText(key.getBindingName());
		} else if (column == 1) {
			// Declaring type for inherited keys, left-aligned in gray
			IType declaringType = key.getDeclaringType();
			if (declaringType != null && _type != null && !_type.equals(declaringType)) {
				StyledString styled = new StyledString(declaringType.getElementName(), QUALIFIER_STYLER);
				cell.setText(styled.toString());
				cell.setStyleRanges(styled.getStyleRanges());
			} else {
				cell.setText("");
			}
		} else if (column == 2) {
			// Navigation arrow — only shown if the key's return type has
			// visible binding keys after filtering. This prevents showing
			// arrows for types like WOActionResults whose only method
			// (generateResponse) is filtered out as a system binding.
			try {
				if (!key.isLeaf()) {
					IType nextType = key.getNextType();
					if (nextType != null && WodParserCache.getTypeCache().hasVisibleBindingKeys(nextType)) {
						Image image = ComponenteditorPlugin.getDefault().getImage(ComponenteditorPlugin.TO_ONE_ICON);
						cell.setImage(image);
					} else {
						cell.setImage(null);
					}
				} else {
					cell.setImage(null);
				}
			} catch (JavaModelException e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		super.update(cell);
	}

	/**
	 * Renders a section header row. Only the first column shows the header
	 * text (bold, dark gray); other columns are blank.
	 */
	private void updateSectionHeader(ViewerCell cell, String headerText) {
		int column = cell.getColumnIndex();
		if (column == 0) {
			cell.setText(headerText);
			cell.setFont(getHeaderFont(cell));
			cell.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY));
		} else {
			cell.setText("");
			cell.setImage(null);
		}
	}

	/**
	 * Returns a bold font for section headers, creating it lazily. The font
	 * is disposed when the table is disposed.
	 */
	private Font getHeaderFont(ViewerCell cell) {
		if (_headerFont == null || _headerFont.isDisposed()) {
			Font tableFont = cell.getControl().getFont();
			FontData[] fd = tableFont.getFontData();
			_headerFont = new Font(tableFont.getDevice(), fd[0].getName(), fd[0].getHeight(), SWT.BOLD);
			cell.getControl().addDisposeListener(e -> _headerFont.dispose());
		}
		return _headerFont;
	}
}
