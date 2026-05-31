package org.objectstyle.wolips.preferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Preference page for configuring Maven settings related to WebObjects
 * development.
 *
 * <p>Detects whether the user's {@code settings.xml} already contains the
 * WOCommunity repository configuration. If not, offers a one-click button to
 * add it. This is required for resolving WebObjects and Project Wonder
 * dependencies, which are not in Maven Central.
 *
 * <p>This is not a traditional preference page — it has no persistent preferences
 * to save. Instead, it provides an action button that modifies the user's Maven
 * {@code settings.xml} and displays status information. The page extends
 * {@link PreferencePage} directly (not {@code FieldEditorPreferencePage}).
 *
 * <p>The settings.xml path is determined by m2e's
 * {@link MavenPlugin#getMavenConfiguration()}, falling back to
 * {@code ~/.m2/settings.xml} if m2e returns null.
 *
 * <p><b>Formatting preservation:</b> When modifying settings.xml, this page
 * uses string splicing rather than DOM serialization. The DOM API is only used
 * for read-only detection (checking if the repo is already configured). For
 * writes, the raw file content is read as a string, an insertion point is
 * located, and the new profile XML is spliced in. This preserves the user's
 * existing formatting, comments, and whitespace perfectly.
 */
public class MavenPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	/** Hostname substring used to detect existing WOCommunity configuration. */
	private static final String WOCOMMUNITY_HOST = "maven.wocommunity.org";

	/**
	 * The WOCommunity profile XML to insert into settings.xml.
	 * Indented with tabs to match typical Maven settings formatting.
	 */
	private static final String WOCOMMUNITY_PROFILE_XML = """
		<profile>
			<id>wocommunity</id>
			<activation>
				<activeByDefault>true</activeByDefault>
			</activation>
			<repositories>
				<repository>
					<id>wocommunity.releases</id>
					<name>WOCommunity Releases Repository</name>
					<url>https://maven.wocommunity.org/content/groups/public</url>
					<releases>
						<enabled>true</enabled>
					</releases>
					<snapshots>
						<enabled>false</enabled>
					</snapshots>
				</repository>
				<repository>
					<id>wocommunity.snapshots</id>
					<name>WOCommunity Snapshots Repository</name>
					<url>https://maven.wocommunity.org/content/groups/public-snapshots</url>
					<releases>
						<enabled>false</enabled>
					</releases>
					<snapshots>
						<enabled>true</enabled>
					</snapshots>
				</repository>
			</repositories>
			<pluginRepositories>
				<pluginRepository>
					<id>wocommunity.releases</id>
					<name>WOCommunity Releases Repository</name>
					<url>https://maven.wocommunity.org/content/groups/public</url>
					<releases>
						<enabled>true</enabled>
					</releases>
					<snapshots>
						<enabled>false</enabled>
					</snapshots>
				</pluginRepository>
				<pluginRepository>
					<id>wocommunity.snapshots</id>
					<name>WOCommunity Snapshots Repository</name>
					<url>https://maven.wocommunity.org/content/groups/public-snapshots</url>
					<releases>
						<enabled>false</enabled>
					</releases>
					<snapshots>
						<enabled>true</enabled>
					</snapshots>
				</pluginRepository>
			</pluginRepositories>
		</profile>""";

	/**
	 * A complete minimal settings.xml file with the WOCommunity profile.
	 * Used when no settings.xml exists yet.
	 */
	private static final String NEW_SETTINGS_XML = """
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
	<profiles>
""" + WOCOMMUNITY_PROFILE_XML + "\n\t</profiles>\n</settings>\n";

	// --- UI fields ---

	/** Status label showing "Configured" or "Not configured". */
	private Label _repoStatusLabel;

	/** Label showing the settings.xml file path being checked. */
	private Label _settingsPathLabel;

	/** Button to add the WOCommunity repository to settings.xml. */
	private Button _addRepoButton;

	public MavenPreferencePage() {
		setDescription("Configure Maven settings for WebObjects development.");
		noDefaultAndApplyButton();
	}

	@Override
	public void init(IWorkbench workbench) {
		// No workbench initialization needed — this page has no persistent preferences.
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite main = new Composite(parent, SWT.NONE);
		main.setLayout(new GridLayout(1, false));
		main.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		createRepositorySection(main);

		refreshStatus();

		return main;
	}

	/**
	 * Creates the "WOCommunity Maven Repository" group with status display
	 * and an "Add Repository" action button.
	 */
	private void createRepositorySection(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("WOCommunity Maven Repository");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		_repoStatusLabel = new Label(group, SWT.NONE);
		_repoStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		_settingsPathLabel = new Label(group, SWT.NONE);
		_settingsPathLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		_addRepoButton = new Button(group, SWT.PUSH);
		_addRepoButton.setText("Add WOCommunity Repository");
		_addRepoButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		_addRepoButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				addWOCommunityRepository();
			}
		});
	}

	// -----------------------------------------------------------------------
	// Status detection
	// -----------------------------------------------------------------------

	/**
	 * Re-checks the WOCommunity repository configuration state and updates
	 * the UI accordingly. Called on initial display and after the "Add" action.
	 */
	private void refreshStatus() {
		Path settingsPath = getSettingsFilePath();
		_settingsPathLabel.setText("Settings file: " + settingsPath);

		boolean configured = isWOCommunityRepositoryConfigured(settingsPath);

		if (configured) {
			_repoStatusLabel.setText("\u2713 WOCommunity repository is configured");
			_addRepoButton.setEnabled(false);
		}
		else {
			_repoStatusLabel.setText("\u2717 WOCommunity repository is not configured");
			_addRepoButton.setEnabled(true);
		}

		// Force the group to re-layout in case label text changed width
		_repoStatusLabel.getParent().layout(true);
	}

	/**
	 * Returns the path to the Maven user settings file. Uses m2e's configured
	 * path if available, otherwise falls back to {@code ~/.m2/settings.xml}.
	 */
	private Path getSettingsFilePath() {
		try {
			String userSettings = MavenPlugin.getMavenConfiguration().getUserSettingsFile();
			if (userSettings != null && !userSettings.isBlank()) {
				return Path.of(userSettings);
			}
		}
		catch (Exception e) {
			// m2e not available or misconfigured — fall back to default
		}

		return Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
	}

	/**
	 * Checks whether any {@code <url>} element in the settings file contains
	 * the WOCommunity hostname. Uses DOM parsing for read-only detection —
	 * this is safe because we never serialize the DOM back to disk.
	 */
	private boolean isWOCommunityRepositoryConfigured(Path settingsPath) {
		if (!Files.exists(settingsPath)) {
			return false;
		}

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// Namespace-unaware to handle both namespaced and plain settings files
			factory.setNamespaceAware(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(settingsPath.toFile());

			NodeList urls = doc.getElementsByTagName("url");
			for (int i = 0; i < urls.getLength(); i++) {
				String url = urls.item(i).getTextContent().trim();
				if (url.contains(WOCOMMUNITY_HOST)) {
					return true;
				}
			}

			return false;
		}
		catch (Exception e) {
			// If we can't parse it, report as not configured — the user will see
			// a parse error if they try to add the repo
			return false;
		}
	}

	// -----------------------------------------------------------------------
	// settings.xml modification (string splicing to preserve formatting)
	// -----------------------------------------------------------------------

	/**
	 * Adds the WOCommunity repository profile to the user's settings.xml.
	 *
	 * <p>Uses string splicing rather than DOM serialization to preserve the
	 * user's existing formatting. Three cases:
	 * <ol>
	 *   <li><b>No settings.xml:</b> writes a complete new file from the
	 *       {@link #NEW_SETTINGS_XML} template.</li>
	 *   <li><b>Has {@code <profiles>}:</b> inserts the profile XML just before
	 *       the closing {@code </profiles>} tag.</li>
	 *   <li><b>No {@code <profiles>}:</b> inserts a {@code <profiles>} block
	 *       just before the closing {@code </settings>} tag.</li>
	 * </ol>
	 *
	 * <p>If the file exists, it is backed up to {@code settings.xml.bak} before
	 * modification.
	 */
	private void addWOCommunityRepository() {
		Path settingsPath = getSettingsFilePath();

		try {
			// Ensure parent directory exists (e.g. ~/.m2/)
			Files.createDirectories(settingsPath.getParent());

			if (!Files.exists(settingsPath) || Files.size(settingsPath) == 0) {
				// No settings file — write a complete new one
				Files.writeString(settingsPath, NEW_SETTINGS_XML);
			}
			else {
				// Back up existing file before modifying
				Path backupPath = settingsPath.resolveSibling("settings.xml.bak");
				Files.copy(settingsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

				// Read the raw content and splice in the profile
				String content = Files.readString(settingsPath);
				String modified = spliceProfileIntoSettings(content);
				Files.writeString(settingsPath, modified);
			}

			refreshStatus();

			MessageDialog.openInformation(getShell(), "Repository Added",
					"The WOCommunity Maven repository has been added to:\n" + settingsPath
					+ "\n\nA backup was saved to settings.xml.bak.");
		}
		catch (Exception e) {
			MessageDialog.openError(getShell(), "Error Updating Maven Settings",
					"Failed to update " + settingsPath + ":\n\n" + e.getMessage()
					+ "\n\nPlease check the file manually.");
		}
	}

	/**
	 * Splices the WOCommunity profile XML into the raw settings.xml content.
	 * Finds the right insertion point based on existing structure:
	 *
	 * <ul>
	 *   <li>If {@code </profiles>} exists, inserts just before it.</li>
	 *   <li>Otherwise, inserts a full {@code <profiles>} block just before
	 *       {@code </settings>}.</li>
	 * </ul>
	 *
	 * @throws IllegalStateException if no insertion point can be found
	 */
	private String spliceProfileIntoSettings(String content) {
		// Case 1: <profiles> already exists — insert before </profiles>
		int profilesClose = content.indexOf("</profiles>");
		if (profilesClose >= 0) {
			return content.substring(0, profilesClose)
					+ WOCOMMUNITY_PROFILE_XML + "\n\t"
					+ content.substring(profilesClose);
		}

		// Case 2: No <profiles> — insert a profiles block before </settings>
		int settingsClose = content.indexOf("</settings>");
		if (settingsClose >= 0) {
			return content.substring(0, settingsClose)
					+ "\t<profiles>\n" + WOCOMMUNITY_PROFILE_XML + "\n\t</profiles>\n"
					+ content.substring(settingsClose);
		}

		throw new IllegalStateException(
				"Could not find </profiles> or </settings> in settings.xml. "
				+ "The file may be malformed.");
	}

	// -----------------------------------------------------------------------
	// No persistent preferences — performOk is a no-op
	// -----------------------------------------------------------------------

	@Override
	public boolean performOk() {
		return true;
	}
}
