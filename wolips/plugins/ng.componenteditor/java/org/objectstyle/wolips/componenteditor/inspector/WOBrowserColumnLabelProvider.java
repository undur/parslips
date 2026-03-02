package org.objectstyle.wolips.componenteditor.inspector;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.bindings.wod.BindingValueKey;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;

/**
 * Label provider for WOBrowser columns. Three table columns:
 * <ol>
 *   <li>Key name (left-aligned)</li>
 *   <li>Declaring type name for inherited keys (right-aligned, gray)</li>
 *   <li>Navigation arrow icon for non-leaf types</li>
 * </ol>
 */
public class WOBrowserColumnLabelProvider extends StyledCellLabelProvider {
	private IType _type;

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
			// Navigation arrow icon for non-leaf types
			try {
				if (!key.isLeaf()) {
					Image image = ComponenteditorPlugin.getDefault().getImage(ComponenteditorPlugin.TO_ONE_ICON);
					cell.setImage(image);
				} else {
					cell.setImage(null);
				}
			} catch (JavaModelException e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
		}

		super.update(cell);
	}

}
