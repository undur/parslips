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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.PartInitException;
import org.objectstyle.wolips.componenteditor.ComponenteditorPlugin;
import org.objectstyle.wolips.editor.apiext.ApiextEditor;
import org.objectstyle.wolips.wodclipse.action.GenerateAPIAction;

/**
 * The component editor's API tab: the component's {@code .apiext}, in the {@link ApiextEditor}.
 *
 * <p>A component without an {@code .apiext} gets a page offering "Generate API" instead: it
 * writes the {@code .apiext} from the component's class, starting from its hand-written
 * {@code .api} when it has one ({@link GenerateAPIAction}), and the tab then shows it. An empty
 * editor isn't offered for that case, since it would invite rewriting by hand what the
 * generator carries over.
 */
public class ApiTab extends ComponentEditorTab {

	/** The embedded editor, once the component has an .apiext; null while the generate page shows. */
	private ApiextEditor _apiextEditor;

	/** The generate page's control, while it shows. */
	private Control _generatePage;

	private final IEditorInput _apiextInput;

	public ApiTab(ComponentEditorPart componentEditorPart, int tabIndex, IEditorInput apiextInput) {
		super(componentEditorPart, tabIndex);
		_apiextInput = apiextInput;
	}

	public IEditorPart getActiveEmbeddedEditor() {
		return _apiextEditor;
	}

	public void createTab() {
		final IFile apiextFile = ((IFileEditorInput) _apiextInput).getFile();
		if (apiextFile.exists()) {
			createEditor();
		}
		else {
			_generatePage = createGeneratePage(getParentSashForm(), apiextFile);
		}
	}

	private void createEditor() {
		_apiextEditor = new ApiextEditor();
		final IEditorSite site = getComponentEditorPart().publicCreateSite(_apiextEditor);
		try {
			_apiextEditor.init(site, _apiextInput);
		}
		catch (PartInitException e) {
			ComponenteditorPlugin.getDefault().log(e);
		}
		createInnerPartControl(getParentSashForm(), _apiextEditor);
		_apiextEditor.addPropertyListener(new IPropertyListener() {
			public void propertyChanged(Object source, int propertyId) {
				ApiTab.this.getComponentEditorPart().publicHandlePropertyChange(propertyId);
			}
		});
	}

	/** "This component has no .apiext yet", with a button that generates one and shows it. */
	private Control createGeneratePage(Composite parent, IFile apiextFile) {
		final Composite page = new Composite(parent, SWT.NONE);
		page.setLayout(new GridLayout(1, false));
		final Label text = new Label(page, SWT.WRAP);
		text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		text.setText(apiextFile.getName() + " doesn't exist yet. Generate API writes it from the component's class,"
				+ " starting from its .api if it has one. For a WebObjects component, the .api is then written from it.");
		final Button generate = new Button(page, SWT.PUSH);
		generate.setText("Generate API");
		final Label result = new Label(page, SWT.WRAP);
		result.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		generate.addListener(SWT.Selection, event -> {
			final IResource template = getComponentEditorPart().getComponentEditorInput().getTemplate();
			if (template == null) {
				result.setText("This component has no template to generate the API for.");
				page.layout(true);
				return;
			}
			final String report = GenerateAPIAction.generate(template);
			if (!apiextFile.exists()) {
				result.setText(report);
				page.layout(true);
				return;
			}
			// Generated: swap the page for the editor on the new file.
			_generatePage.dispose();
			_generatePage = null;
			createEditor();
			getParentSashForm().layout(true, true);
		});
		return page;
	}

	public void doSave(IProgressMonitor monitor) {
		if (_apiextEditor != null) {
			_apiextEditor.doSave(monitor);
		}
	}

	public void close(boolean save) {
		if (_apiextEditor != null) {
			_apiextEditor.close(save);
		}
	}

	@Override
	public void dispose() {
		if (_apiextEditor != null) {
			_apiextEditor.dispose();
		}
		super.dispose();
	}

	public IEditorInput getActiveEditorInput() {
		return _apiextInput;
	}

	public boolean isDirty() {
		return _apiextEditor != null && _apiextEditor.isDirty();
	}
}
