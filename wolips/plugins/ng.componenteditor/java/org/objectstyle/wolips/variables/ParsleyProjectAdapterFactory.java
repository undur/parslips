package org.objectstyle.wolips.variables;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdapterFactory;

/**
 * Adapter factory that provides {@link ParsleyProject} and {@link BuildProperties}
 * for any {@link IProject}. Registered via plugin.xml.
 *
 * <p>Caches {@link ParsleyProject} instances per-project and reloads when the
 * build.properties file's modification stamp changes. When reloading,
 * workspace-level defaults (inline binding prefix/suffix, template strictness)
 * are copied from the previous {@link BuildProperties} to avoid re-reading
 * preferences.
 */
public class ParsleyProjectAdapterFactory implements IAdapterFactory {
	private Map<IProject, ParsleyProject> _cache = new HashMap<IProject, ParsleyProject>();

	public Object getAdapter(Object adaptableObject, Class adapterType) {
		if (adapterType != ParsleyProject.class && adapterType != BuildProperties.class) {
			return null;
		}

		IProject project = (IProject) adaptableObject;
		ParsleyProject cached = _cache.get(project);
		BuildProperties newBuildProperties = new BuildProperties(project);

		if (cached == null || cached.getBuildProperties().getModificationStamp() != newBuildProperties.getModificationStamp()) {
			if (cached != null) {
				newBuildProperties._copyDefaultsFrom(cached.getBuildProperties());
			}
			cached = new ParsleyProject(project, newBuildProperties);
			_cache.put(project, cached);
		}

		if (adapterType == ParsleyProject.class) {
			return cached;
		}
		// adapterType == BuildProperties.class
		return cached.getBuildProperties();
	}

	public Class[] getAdapterList() {
		return new Class[] { ParsleyProject.class, BuildProperties.class };
	}
}
