package org.objectstyle.wolips.componenteditor.inspector;

import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.objectstyle.wolips.bindings.wod.IWodBinding;
import org.objectstyle.wolips.bindings.wod.IWodElement;
import org.objectstyle.wolips.bindings.wod.VisibleBinding;
import org.objectstyle.wolips.bindings.wod.WodProblem;
import org.objectstyle.wolips.wodclipse.core.util.WodModelUtils;

public class BindingsLabelProvider extends ColumnLabelProvider implements ITableLabelProvider, ITableColorProvider, ITableFontProvider {
	private int _column;
	
	private IWodElement _wodElement;

	private List<WodProblem> _problems;

	public BindingsLabelProvider(int column) {
		_column = column;
	}
	
	public void setContext(IWodElement wodElement, List<WodProblem> problems) {
		_wodElement = wodElement;
		_problems = problems;
	}

	public Image getColumnImage(Object element, int columnIndex) {
		return null;
	}

	@Override
	public String getText(Object element) {
		return getColumnText(element, _column);
	}

	public String getColumnText(Object element, int columnIndex) {
		VisibleBinding vb = (VisibleBinding) element;
		String text = null;
		if (columnIndex == 0) {
			text = vb.getName();
		} else if (columnIndex == 1) {
			IWodBinding wodBinding = _wodElement.getBindingNamed(vb.getName());
			if (wodBinding != null) {
				text = wodBinding.getValue();
			}
		}
		return text;
	}

	public void addListener(ILabelProviderListener listener) {
		// DO NOTHING
	}

	public void dispose() {
		// DO NOTHING
	}

	public boolean isLabelProperty(Object element, String property) {
		return true;
	}

	public void removeListener(ILabelProviderListener listener) {
		// DO NOTHING
	}

	public Color getBackground(Object element, int columnIndex) {
		return null;
	}

	@Override
	public Color getForeground(Object element) {
		return getForeground(element, _column);
	}

	public Color getForeground(Object element, int columnIndex) {
		Color color = null;
		VisibleBinding vb = (VisibleBinding) element;
		if (WodModelUtils.hasValidationProblem(vb.getName(), _problems)) {
			color = Display.getCurrent().getSystemColor(SWT.COLOR_RED);
		}
		return color;
	}

	@Override
	public Font getFont(Object element) {
		return getFont(element, _column);
	}

	/**
	 * Returns bold font for bindings not defined in the API (i.e., "extra"
	 * bindings added by the user in the WOD that have no {@code .api}
	 * definition).
	 */
	public Font getFont(Object element, int columnIndex) {
		Font font = null;
		if (columnIndex == 0) {
			VisibleBinding vb = (VisibleBinding) element;
			if (!vb.isDefinedInApi()) {
				font = JFaceResources.getFontRegistry().getBold(JFaceResources.DIALOG_FONT);
			}
		}
		return font;
	}
}
