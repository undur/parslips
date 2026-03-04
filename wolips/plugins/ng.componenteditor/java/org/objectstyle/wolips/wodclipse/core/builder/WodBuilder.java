/*
 * ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2005 - 2006 The ObjectStyle Group and individual authors of the
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
package org.objectstyle.wolips.wodclipse.core.builder;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.objectstyle.wolips.core.resources.types.WOHierarchyScope;
import org.objectstyle.wolips.wodclipse.WodclipsePlugin;
import org.objectstyle.wolips.wodclipse.core.completion.WodParserCache;

/**
 * Static utility for validating WO/ng-objects component templates.
 *
 * <p>Provides two entry points:
 * <ul>
 *   <li>{@link #validateComponent(IResource, boolean, IProgressMonitor)} — validates
 *       a component resource, optionally dispatching to a thread pool.</li>
 *   <li>{@link #_validateComponent(IResource, IProgressMonitor, boolean)} — synchronous
 *       validation with optional progress reporting.</li>
 * </ul>
 *
 * <p>Called from {@code JavaChangeRevalidator} (when Java/API files change) and
 * {@code WodParserCache} (when templates are edited).
 */
public class WodBuilder {
	private static ExecutorService validationThreadPool;
	private static ValidationProgressJob _validationJob;

	static {
		WodBuilder._validationJob = new ValidationProgressJob();
		WodBuilder.validationThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
	}

	public static void validateComponent(IResource resource, boolean threaded, IProgressMonitor progressMonitor) {
		if (threaded) {
			WodBuilder.validationThreadPool.execute(new ValidatingComponent(resource, progressMonitor));
		}
		else {
			WodBuilder._validateComponent(resource, progressMonitor, true);
		}
	}

	public static void _validateComponent(IResource resource, IProgressMonitor progressMonitor, boolean showProgress) {
		if (resource != null) {
			String resourceName = resource.getName();
			if (progressMonitor != null) {
				if (showProgress) {
					progressMonitor.subTask("Locating components for " + resourceName + " ...");
				}
			}
			try {
				WodParserCache cache = WodParserCache.parser(resource);
				if (progressMonitor != null && cache.getWodEntry().getFile() != null) {
					if (showProgress) {
						progressMonitor.subTask("Building WO " + cache.getWodEntry().getFile().getName() + " ...");
					}
				}
				cache.clearParserCache();
				cache.parse();
				cache.validate(true, false);
			}
			catch (Throwable t) {
				WodclipsePlugin.getDefault().log(t);
			}
		}
	}

	public static class ValidationProgressJob extends Job {
		private ConcurrentLinkedQueue<IResource> _validatingResources;
		private Object _lock = new Object();

		public ValidationProgressJob() {
			super("Component Validation");
			setPriority(Job.LONG);
			setUser(false);

			_validatingResources = new ConcurrentLinkedQueue<IResource>();
		}

		public void start(IResource resource) {
			_validatingResources.add(resource);
			WOHierarchyScope.incrementReferenceCountForProject(resource.getProject());
			synchronized (_lock) {
				_lock.notifyAll();
			}
			schedule();
		}

		public void finish(IResource resource) {
			_validatingResources.remove(resource);
			WOHierarchyScope.decrementReferenceCountForProject(resource.getProject());
			synchronized (_lock) {
				_lock.notifyAll();
			}
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			IStatus status = null;
			long completedAtStart = ((ThreadPoolExecutor) WodBuilder.validationThreadPool).getCompletedTaskCount();
			while (!monitor.isCanceled() && status == null) {
				synchronized (_lock) {
					if (_validatingResources.size() == 0) {
						monitor.done();
						status = Status.OK_STATUS;
					}
					else {
						setName("Component Validation");

						try {
							_lock.wait();
						}
						catch (InterruptedException e) {
							WodclipsePlugin.getDefault().log(e);
						}
					}
				}

				if (_validatingResources.size() > 0 && !monitor.isCanceled()) {
					monitor.beginTask("Validating ...", IProgressMonitor.UNKNOWN);
					StringBuffer sb = new StringBuffer();
					sb.append("Validating ");
					Iterator<IResource> resourceIter = _validatingResources.iterator();
					while (resourceIter.hasNext()) {
						IResource resource = resourceIter.next();
						sb.append(resource.getName());
						if (resourceIter.hasNext()) {
							sb.append(", ");
						}
					}

					long taskCount = ((ThreadPoolExecutor) WodBuilder.validationThreadPool).getTaskCount() - completedAtStart;
					long completedTaskCount = ((ThreadPoolExecutor) WodBuilder.validationThreadPool).getCompletedTaskCount() - completedAtStart;
					sb.append(" (" + completedTaskCount + " of " + taskCount + ")");

					sb.append("...");
					monitor.setTaskName(sb.toString());

					monitor.beginTask(sb.toString(), (int) taskCount);
					monitor.worked((int) completedTaskCount);
				}
			}
			return monitor.isCanceled() ? Status.CANCEL_STATUS : status;
		}
	}

	public static class ValidatingComponent implements Runnable {
		private IResource _resource;
		private IProgressMonitor _monitor;

		public ValidatingComponent(IResource resource, IProgressMonitor monitor) {
			_resource = resource;
			_monitor = monitor;
		}

		public void run() {
			if (_monitor == null || !_monitor.isCanceled()) {
				_validationJob.start(_resource);
				try {
					WodBuilder._validateComponent(_resource, _monitor, false);
				}
				finally {
					_validationJob.finish(_resource);
				}
			}
		}
	}
}
