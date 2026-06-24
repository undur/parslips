/*
 * ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0
 * 
 * Copyright (c) 2006 The ObjectStyle Group and individual authors of the
 * software. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 1.
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. The end-user documentation
 * included with the redistribution, if any, must include the following
 * acknowlegement: "This product includes software developed by the ObjectStyle
 * Group (http://objectstyle.org/)." Alternately, this acknowlegement may
 * appear in the software itself, if and wherever such third-party
 * acknowlegements normally appear. 4. The names "ObjectStyle Group" and
 * "Cayenne" must not be used to endorse or promote products derived from this
 * software without prior written permission. For written permission, please
 * contact andrus@objectstyle.org. 5. Products derived from this software may
 * not be called "ObjectStyle" nor may "ObjectStyle" appear in their names
 * without prior written permission of the ObjectStyle Group.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * OBJECTSTYLE GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the ObjectStyle Group. For more information on the ObjectStyle
 * Group, please see <http://objectstyle.org/> .
 *  
 */
package org.objectstyle.wolips.editor.component;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.PartInitException;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.components.input.ComponentEditorFileEditorInput;
import org.objectstyle.wolips.editor.template.TemplateEditor;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.editor.wod.WodEditor;

public class HtmlWodTab extends ComponentEditorTab {
	private static final String SASH_WEIGHTS_KEY = "ng.componenteditor.sashWeights";

	private TemplateEditor templateEditor;

	private WodEditor wodEditor;

	/**
	 * Which side of the HTML/WOD composite is "active" (the one outline,
	 * focus, etc. follow). Defaults to true (HTML) so a component opened
	 * without an explicit WOD request starts on the template — the part you
	 * almost always want, since the WOD sidecar is usually empty for
	 * inline-syntax components. An explicit .wod open flips this to false via
	 * setWodActive() during the reveal step.
	 */
	private boolean htmlActive = true;

	private IEditorInput htmlInput;

	private IEditorInput wodInput;

	private Composite _templateContainer;

	private Composite _wodContainer;

	/** Paint/mouse handlers drawing the collapsed bar on the WOD container; null when expanded. */
	private org.eclipse.swt.events.PaintListener _barPainter;
	private MouseAdapter _barMouse;

	/** Whether the WOD pane is currently collapsed to its bar. */
	private boolean _wodCollapsed;

	/** Explicit grey fill / text colours for the collapsed bar (disposed with the tab). */
	private Color _barBg;
	private Color _barFg;

	/** Height of the collapsed WOD bar, in pixels. */
	private static final int COLLAPSED_BAR_HEIGHT = 24;

	public HtmlWodTab(ComponentEditorPart componentEditorPart, int tabIndex, IEditorInput htmlInput, IEditorInput wodInput) {
		super(componentEditorPart, tabIndex);
		this.htmlInput = htmlInput;
		this.wodInput = wodInput;
	}

	public IEditorPart getActiveEmbeddedEditor() {
		if (htmlActive) {
			return templateEditor;
		}
		return wodEditor;
	}
	
	public void createTab() {
		templateEditor = new TemplateEditor();
		IEditorSite htmlSite = this.getComponentEditorPart().publicCreateSite(templateEditor);
		try {
			templateEditor.init(htmlSite, htmlInput);
		} catch (PartInitException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		_templateContainer = createInnerPartControl(getParentSashForm(), templateEditor);
		templateEditor.addPropertyListener(new IPropertyListener() {
			public void propertyChanged(Object source, int propertyId) {
				HtmlWodTab.this.getComponentEditorPart().publicHandlePropertyChange(propertyId);
			}
		});
		if (wodInput != null && ((ComponentEditorFileEditorInput)wodInput).getFile().exists()) {
			wodEditor = new WodEditor();
			IEditorSite wodSite = this.getComponentEditorPart().publicCreateSite(wodEditor);
			try {
				wodEditor.init(wodSite, wodInput);
			} catch (PartInitException e) {
				ComponenteditorPlugin.getDefault().log(e);
			}
			_wodContainer = createInnerPartControl(getParentSashForm(), wodEditor);
			_wodContainer.setBackground(_wodContainer.getDisplay().getSystemColor(SWT.COLOR_WHITE));
			wodEditor.addPropertyListener(new IPropertyListener() {
				public void propertyChanged(Object source, int propertyId) {
					HtmlWodTab.this.getComponentEditorPart().publicHandlePropertyChange(propertyId);
				}
			});
			_templateContainer.addListener(SWT.Activate, new Listener() {
				public void handleEvent(Event event) {
					setHtmlActive(true);
					HtmlWodTab.this.getComponentEditorPart().pageChange(HtmlWodTab.this.getTabIndex());
					HtmlWodTab.this.getComponentEditorPart().updateOutline();
				}
			});
			_wodContainer.addListener(SWT.Activate, new Listener() {
				public void handleEvent(Event event) {
					setHtmlActive(false);
					HtmlWodTab.this.getComponentEditorPart().pageChange(HtmlWodTab.this.getTabIndex());
					HtmlWodTab.this.getComponentEditorPart().updateOutline();
				}
			});
		}

		restoreSashWeights();

		// The WOD sidecar is usually empty for inline-binding (standalone) templates and
		// rarely edited even when present. So start the pane collapsed to a thin bar when
		// the .wod is empty, giving the template the full height; a .wod with content opens
		// expanded so existing bindings are visible. Either way the bar/sash lets the user
		// toggle it. (No _wodContainer at all means a standalone template — nothing to do.)
		if (_wodContainer != null && wodEditor != null && wodEditor.getWodEditDocument().getLength() == 0) {
			collapseWod();
		}

		_templateContainer.addControlListener(new ControlListener() {
			public void controlMoved(ControlEvent e) {
				// DO NOTHING
			}

			public void controlResized(ControlEvent e) {
				if (_wodCollapsed) {
					// Keep the collapsed WOD row pinned to the bar height as the editor
					// resizes (and on the first real layout, when the height is finally known).
					// Don't persist weights while collapsed — the bar height isn't a
					// user-chosen split and would otherwise overwrite the saved layout.
					HtmlWodTab.this.sizeWodRowToBar();
				} else {
					HtmlWodTab.this.saveSashWeights();
				}
			}
		});

		templateEditor.initEditorInteraction(this.getComponentEditorPart().getEditorInteraction());
		if (wodEditor != null) {
			wodEditor.initEditorInteraction(this.getComponentEditorPart().getEditorInteraction());
		}
	}

	public boolean isHtmlActive() {
		return this.htmlActive;
	}

	private void setHtmlActive(boolean htmlActive) {
		this.htmlActive = htmlActive;
	}

	/**
	 * Collapses the WOD pane to a thin clickable bar, giving the template the full height.
	 * The embedded WOD editor is hidden (not disposed) so expanding is instant. No-op for
	 * standalone templates (no WOD pane) or if already collapsed.
	 */
	private void collapseWod() {
		if (_wodContainer == null || _wodCollapsed || _wodContainer.isDisposed()) {
			return;
		}
		_wodCollapsed = true;

		// Hide the embedded editor (kept, not disposed, so expanding is instant) and draw the
		// bar directly onto the WOD container's own surface — no inset child Composite, so
		// there's no white container margin showing around it and nothing to mis-center.
		final Composite editorControl = (Composite) _wodContainer.getChildren()[0];
		editorControl.setVisible(false);
		installBarPainting();
		_wodContainer.redraw();

		// Size the WOD row to exactly the bar height. SashForm weights are proportional, not
		// pixels, so derive them from the sash's current height — otherwise a fixed small
		// weight clips the bar on a tall editor (what made it look "too collapsed").
		sizeWodRowToBar();

		// Focus belongs on the template when the WOD is tucked away.
		if (!isHtmlActive()) {
			_templateContainer.forceFocus();
		}
	}

	/** Sets the sash weights so the collapsed WOD row is exactly {@link #COLLAPSED_BAR_HEIGHT} tall. */
	private void sizeWodRowToBar() {
		final int sashHeight = getParentSashForm().getClientArea().height;
		if (sashHeight > COLLAPSED_BAR_HEIGHT * 2) {
			getParentSashForm().setWeights(new int[] { sashHeight - COLLAPSED_BAR_HEIGHT, COLLAPSED_BAR_HEIGHT });
		} else {
			// Sash not laid out yet (or tiny) — fall back to a small proportional weight.
			getParentSashForm().setWeights(new int[] { 95, 5 });
		}
	}

	/**
	 * Expands the WOD pane back to an editable split, restoring the embedded editor and the
	 * user's last sash weights. No-op for standalone templates or if already expanded.
	 */
	private void expandWod() {
		if (_wodContainer == null || !_wodCollapsed || _wodContainer.isDisposed()) {
			return;
		}
		_wodCollapsed = false;

		// Stop drawing the bar; show the editor again.
		removeBarPainting();
		final Composite editorControl = (Composite) _wodContainer.getChildren()[0];
		editorControl.setVisible(true);
		_wodContainer.setCursor(null);
		_wodContainer.layout(true, true);
		_wodContainer.redraw();

		// Restore a sensible split: the user's saved weights, or a default if none/too small.
		restoreSashWeights();
		int[] weights = getParentSashForm().getWeights();
		if (weights.length < 2 || weights[1] < 132) {
			getParentSashForm().setWeights(new int[] { 70, 30 });
		}
	}

	/**
	 * Draws the collapsed bar directly on the WOD container's surface — a flat grey strip
	 * (a fixed RGB, not a theme-dependent system colour, so it reads as inert chrome rather
	 * than the editable white of an editor) with a hairline top separator and a muted label.
	 *
	 * <p>Painting straight onto the container, rather than nesting a child Composite, avoids
	 * two macOS quirks at once: a child's {@code setBackground} not sticking (it stays white),
	 * and the container's own white showing as a margin around an inset child.
	 */
	private void installBarPainting() {
		if (_barBg == null) {
			_barBg = new Color(_wodContainer.getDisplay(), 0xEC, 0xEC, 0xEC);
		}
		if (_barFg == null) {
			_barFg = new Color(_wodContainer.getDisplay(), 0x70, 0x70, 0x70);
		}
		if (_barPainter == null) {
			_barPainter = new org.eclipse.swt.events.PaintListener() {
				public void paintControl(org.eclipse.swt.events.PaintEvent e) {
					final org.eclipse.swt.graphics.Rectangle area = _wodContainer.getClientArea();
					e.gc.setBackground(_barBg);
					e.gc.fillRectangle(area);
					final Color sep = new Color(e.gc.getDevice(), 0xCD, 0xCD, 0xCD);
					e.gc.setForeground(sep);
					e.gc.drawLine(0, 0, area.width, 0);
					sep.dispose();
					// Vertically-centered, left-padded label.
					e.gc.setForeground(_barFg);
					final String text = "No .wod bindings  —  click to add";
					final org.eclipse.swt.graphics.Point ext = e.gc.textExtent(text);
					e.gc.drawText(text, 10, (area.height - ext.y) / 2, true);
				}
			};
			_wodContainer.addPaintListener(_barPainter);
		}
		if (_barMouse == null) {
			_barMouse = new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					expandWod();
				}
			};
			_wodContainer.addMouseListener(_barMouse);
		}
		_wodContainer.setCursor(_wodContainer.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
	}

	/** Removes the bar's paint/mouse handlers from the WOD container (on expand). */
	private void removeBarPainting() {
		if (_barPainter != null) {
			_wodContainer.removePaintListener(_barPainter);
			_barPainter = null;
		}
		if (_barMouse != null) {
			_wodContainer.removeMouseListener(_barMouse);
			_barMouse = null;
		}
	}

	private void restoreSashWeights() {
		String sashWeightsStr = Activator.getDefault().getPluginPreferences().getString(HtmlWodTab.SASH_WEIGHTS_KEY);
		if (sashWeightsStr != null && sashWeightsStr.length() > 0) {
			String[] sashWeightStrs = sashWeightsStr.split(",");
			int[] sashWeights = new int[sashWeightStrs.length];
			for (int sashWeightNum = 0; sashWeightNum < sashWeightStrs.length; sashWeightNum++) {
				sashWeights[sashWeightNum] = Integer.parseInt(sashWeightStrs[sashWeightNum]);
			}
			if (sashWeights.length > 1 && sashWeights.length == getParentSashForm().getWeights().length) {
				if (sashWeights[1] / (float)sashWeights[0] < 0.15) {
					sashWeights[0] = 85;
					sashWeights[1] = 15;
				}
				getParentSashForm().setWeights(sashWeights);
			}
		}
	}

	private void saveSashWeights() {
		int[] weights = getParentSashForm().getWeights();
		StringBuffer weightsBuffer = new StringBuffer();
		for (int weight : weights) {
			weightsBuffer.append(weight);
			weightsBuffer.append(",");
		}
		if (weightsBuffer.length() > 0) {
			weightsBuffer.setLength(weightsBuffer.length() - 1);
		}
		Activator.getDefault().getPluginPreferences().setValue(HtmlWodTab.SASH_WEIGHTS_KEY, weightsBuffer.toString());
	}

	/**
	 * @return the template editor for this html/wod composite.
	 */

	public TemplateEditor templateEditor() {
		return templateEditor;
	}

	/**
	 * @return the wod editor for this html/wod composite.
	 */

	public WodEditor wodEditor() {
		return wodEditor;
	}

	public void doSave(IProgressMonitor monitor) {
		if (wodEditor != null && wodEditor.isDirty()) {
			wodEditor.doSave(monitor);
		}
		if (templateEditor.isDirty()) {
			templateEditor.doSave(monitor);
		}
	}

	public void close(boolean save) {
		if (wodEditor != null) {
			wodEditor.close(save);
		}
	}

	@Override
	public void dispose() {
		if (wodEditor != null) {
			wodEditor.dispose();
		}
		templateEditor.dispose();
		if (_barBg != null && !_barBg.isDisposed()) {
			_barBg.dispose();
		}
		if (_barFg != null && !_barFg.isDisposed()) {
			_barFg.dispose();
		}
		super.dispose();
	}

	public boolean isDirty() {
		return (wodEditor != null && wodEditor.isDirty()) || templateEditor.isDirty();
	}

	public void setHtmlActive() {
		setHtmlActive(true);
	}

	public void setWodActive() {
		setHtmlActive(false);
	}

	public IEditorInput getActiveEditorInput() {
		if (this.htmlActive) {
			return this.htmlInput;
		}
		return this.wodInput;
	}

}
