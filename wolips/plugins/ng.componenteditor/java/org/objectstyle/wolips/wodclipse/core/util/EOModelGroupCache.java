package org.objectstyle.wolips.wodclipse.core.util;

import java.util.Set;

import org.eclipse.core.resources.IProject;

// FIXME: eomodeler removed — this class is now a no-op stub.
// It previously cached EOModelGroup instances per project for EO model lookups.
// When/if EO model support is needed, this should be reimplemented.

public class EOModelGroupCache {

  public EOModelGroupCache() {
  }

  public synchronized void clearCacheForProject(IProject project) {
    // no-op — eomodeler removed
  }

  public synchronized void clearCacheForProject(IProject project, Set<IProject> visitedProjects) {
    // no-op — eomodeler removed
  }

  public synchronized void clearCache() {
    // no-op — eomodeler removed
  }
}
