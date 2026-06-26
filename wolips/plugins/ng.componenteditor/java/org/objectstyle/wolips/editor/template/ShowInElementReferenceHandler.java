package org.objectstyle.wolips.editor.template;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.objectstyle.wolips.bindings.api.ApiCache;
import org.objectstyle.wolips.bindings.api.ParsleyTagAliasResolver;
import org.objectstyle.wolips.bindings.wod.TagShortcut;
import org.objectstyle.wolips.editor.component.ComponentEditor;
import org.objectstyle.wolips.editor.help.ElementHelpView;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

import jp.aonir.fuzzyxml.FuzzyXMLElement;

/**
 * Command handler that reveals the {@code <wo:…>} element currently under the template
 * editor's cursor in the Element Reference view — opening/focusing the view and selecting
 * that element so its documentation card is shown.
 *
 * <p>The element is resolved from the caret offset the same way "Open Declaration" does
 * (the innermost WO tag enclosing the cursor); being anywhere inside the tag is enough
 * (you don't have to be on the type name).
 */
public class ShowInElementReferenceHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final IEditorPart editor = HandlerUtil.getActiveEditor(event);
		final TemplateEditor templateEditor = getTemplateEditor(editor);
		if (templateEditor == null) {
			return null;
		}

		try {
			final TemplateSourceEditor sourceEditor = templateEditor.getSourceEditor();
			final ISelection selection = sourceEditor.getSelectionProvider().getSelection();
			if (!(selection instanceof ITextSelection)) {
				return null;
			}

			final int offset = ((ITextSelection) selection).getOffset();
			final FuzzyXMLElement element = sourceEditor.getElementAtOffset(offset, true);
			if (element == null || !WodHtmlUtils.isWOTag(element.getName())) {
				return null;
			}

			// The element name as written in the template — a class name ("wo:WORepetition")
			// or a tag shortcut ("wo:repetition"). Strip the "wo:" prefix, then resolve a
			// shortcut to its actual class so the view (which lists by class name) can match it.
			String elementName = element.getName();
			final int colon = elementName.indexOf(':');
			if (colon >= 0) {
				elementName = elementName.substring(colon + 1);
			}

			// Resolve the tag to its actual element. When the project declares Parsley tag
			// aliases (the new mechanism), resolve recursively through them — matching the
			// runtime — instead of the legacy WOLips tag-shortcut preference.
			final org.eclipse.jdt.core.IJavaProject jp = aliasProject(sourceEditor);
			if (jp != null && ParsleyTagAliasResolver.isActiveFor(jp)) {
				final String resolved = ParsleyTagAliasResolver.resolve(jp, elementName);
				final int dot = resolved.lastIndexOf('.');
				elementName = dot >= 0 ? resolved.substring(dot + 1) : resolved;
			}
			else {
				final TagShortcut shortcut = ApiCache.getTagShortcutNamed(elementName);
				if (shortcut != null && shortcut.getActual() != null) {
					// getActual() may be fully-qualified; the view matches on the simple name.
					final String actual = shortcut.getActual();
					final int dot = actual.lastIndexOf('.');
					elementName = dot >= 0 ? actual.substring(dot + 1) : actual;
				}
			}

			// Show the view WITHOUT moving focus (VIEW_VISIBLE), so the editing flow in the
			// template isn't interrupted — the caret stays where it was.
			final IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			final ElementHelpView view = (ElementHelpView) page.showView(
					ElementHelpView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
			if (view != null) {
				view.revealElement(elementName);
			}
		} catch (Exception e) {
			Activator.getDefault().log(e);
		}

		return null;
	}

	/** The Java project backing the editor's template, for alias resolution, or null. */
	private static org.eclipse.jdt.core.IJavaProject aliasProject(TemplateSourceEditor sourceEditor) {
		try {
			return org.eclipse.jdt.core.JavaCore.create(sourceEditor.getParserCache().getProject());
		} catch (Exception e) {
			return null;
		}
	}

	private TemplateEditor getTemplateEditor(IEditorPart editor) {
		if (editor instanceof TemplateEditor) {
			return (TemplateEditor) editor;
		}
		if (editor instanceof ComponentEditor) {
			return ((ComponentEditor) editor).getTemplateEditor();
		}
		return null;
	}
}
