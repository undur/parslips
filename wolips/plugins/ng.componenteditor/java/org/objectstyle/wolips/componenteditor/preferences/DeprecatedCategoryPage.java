package org.objectstyle.wolips.componenteditor.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Landing page for the "Zombies" preference category.
 *
 * <p>This page has no settings of its own. It exists to explain why the pages
 * nested beneath it are here, so that anyone who stumbles into this part of the
 * preferences tree during daily use understands they're looking at a triage
 * worklist rather than supported configuration.
 *
 * <p>The pages under this category were inherited from the Amateras HTML editor
 * (the codebase Parsley was extracted from) and were never wired into the UI.
 * Rather than leave them invisible — where dead weight rots unnoticed — we
 * surface them so each can be deliberately dealt with: deleted outright,
 * salvaged into something Parsley-shaped, or (rarely) promoted back to a real
 * setting. Several still control behaviour that runs today (e.g. the legacy
 * HTML/JS validators); others control features we no longer ship (JSP).
 *
 * <p>The category is named "Zombies" because that's what these are: the living
 * dead — code that still shambles along (some of it running) but has no place
 * in Parsley's future. Naming it "Zombies" also sorts it last in the
 * preferences tree (Eclipse orders sibling pages alphabetically).
 *
 * <p>Nothing under this category should be treated as a supported setting.
 */
public class DeprecatedCategoryPage extends PreferencePage implements IWorkbenchPreferencePage {

	public DeprecatedCategoryPage() {
		noDefaultAndApplyButton();
	}

	@Override
	public void init(IWorkbench workbench) {
		// No preference store — this page has no settings.
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Label heading = new Label(composite, SWT.WRAP);
		heading.setText("Zombies — the living dead (triage worklist, not settings)");
		GridData headingData = new GridData(SWT.FILL, SWT.TOP, true, false);
		heading.setLayoutData(headingData);

		Label body = new Label(composite, SWT.WRAP);
		body.setText(
				"The pages nested under this category were inherited from the "
				+ "Amateras HTML editor that Parsley was extracted from, and were "
				+ "never wired into the UI.\n\n"
				+ "They are surfaced here on purpose. Leaving dead or half-dead "
				+ "code invisible means it rots unnoticed; keeping it in view "
				+ "means we keep bumping into it and can decide each one's fate:\n\n"
				+ "    •  delete it outright, or\n"
				+ "    •  salvage the idea into something Parsley-shaped, or\n"
				+ "    •  (rarely) promote it back to a real, supported setting.\n\n"
				+ "Some of these pages still control behaviour that runs today "
				+ "(for example the legacy HTML/JavaScript validators). Others "
				+ "control features Parsley no longer ships at all (JSP). They "
				+ "shamble along, neither alive nor properly buried — hence the "
				+ "name.\n\n"
				+ "Nothing under this category should be treated as a supported "
				+ "setting.");
		GridData bodyData = new GridData(SWT.FILL, SWT.FILL, true, true);
		bodyData.widthHint = 420;
		body.setLayoutData(bodyData);

		return composite;
	}
}
