package parslips.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Handles workspace-level LSP requests for the Parslips LSP server.
 *
 * <p>Currently a no-op stub. Future workspace capabilities might include:
 * <ul>
 *   <li>Configuration changes (e.g., project-specific element registries)</li>
 *   <li>Watched file notifications (e.g., when a .api file changes)</li>
 *   <li>Workspace-wide symbol search</li>
 * </ul>
 */
public class ParslipsWorkspaceService implements WorkspaceService {

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		// TODO: React to configuration changes (e.g., reload element registry)
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		// TODO: React to file system changes (e.g., invalidate caches when .api files change)
	}
}
