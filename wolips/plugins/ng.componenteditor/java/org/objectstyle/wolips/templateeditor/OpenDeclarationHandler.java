package org.objectstyle.wolips.templateeditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.objectstyle.wolips.bindings.wod.SimpleWodElement;
import org.objectstyle.wolips.componenteditor.part.ComponentEditor;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.wodclipse.core.Activator;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;
import org.objectstyle.wolips.wodclipse.core.document.WodElementTypeHyperlink;
import org.objectstyle.wolips.wodclipse.core.util.FuzzyXMLWodElement;
import org.objectstyle.wolips.wodclipse.core.util.WodHtmlUtils;

import jp.aonir.fuzzyxml.FuzzyXMLElement;

/**
 * Command handler for "Open Declaration" (F3) in the template editor.
 * When the cursor is on a {@code <wo:ComponentName>} tag's type name,
 * opens the Java class declaration — the same navigation that Cmd+click
 * performs via the hyperlink infrastructure.
 */
public class OpenDeclarationHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor == null) {
			return null;
		}

		TemplateEditor templateEditor = getTemplateEditor(editor);
		if (templateEditor == null) {
			return null;
		}

		try {
			TemplateSourceEditor sourceEditor = templateEditor.getSourceEditor();
			ISelection selection = sourceEditor.getSelectionProvider().getSelection();
			if (!(selection instanceof ITextSelection)) {
				return null;
			}

			int offset = ((ITextSelection) selection).getOffset();
			FuzzyXMLElement element = sourceEditor.getElementAtOffset(offset, true);
			if (element == null || !WodHtmlUtils.isWOTag(element.getName()) || !WodHtmlUtils.isInline(element.getName())) {
				return null;
			}

			WodParserCache cache = sourceEditor.getParserCache();
			ParsleyProject parsleyProject = (ParsleyProject) cache.getProject().getAdapter(ParsleyProject.class);
			SimpleWodElement wodElement = new FuzzyXMLWodElement(element, parsleyProject);

			if (!wodElement.isTypeWithin(new Region(offset, 0))) {
				return null;
			}

			IHyperlink hyperlink = WodElementTypeHyperlink.toElementTypeHyperlink(wodElement, cache);
			if (hyperlink != null) {
				hyperlink.open();
			}
		} catch (Exception e) {
			Activator.getDefault().log(e);
		}

		return null;
	}

	/**
	 * Extracts the {@link TemplateEditor} from the active editor, handling
	 * both the multi-tab {@link ComponentEditor} (where the template is an
	 * embedded tab) and the standalone {@link TemplateEditor}.
	 */
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
