package ng.componenteditor.explorer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.objectstyle.wolips.variables.BuildProperties;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Label decorator that adds a small overlay icon to projects recognized
 * as ng-objects or WebObjects projects.
 * <ul>
 *   <li><b>ng-objects</b> projects get a green "ng" badge (top-left)</li>
 *   <li><b>WebObjects</b> projects get a blue "wo" badge (top-left)</li>
 * </ul>
 * <p>
 * Detection priority:
 * <ol>
 *   <li>{@code base=ng} or {@code base=wo} in {@code build.properties}</li>
 *   <li>{@code project.name} in {@code build.properties} (implies WebObjects)</li>
 *   <li>Classpath probing for {@code NGElement} or {@code WOElement}</li>
 * </ol>
 * Projects that match none of the above are left undecorated.
 * <p>
 * Registered via {@code org.eclipse.ui.decorators} in plugin.xml with
 * {@code lightweight="false"} and an enablement filter matching
 * {@code IProject} and {@code IJavaProject} elements.
 */
public class ProjectDecorator implements ILabelDecorator {

	private static final String NG_OVERLAY_PATH = "icons/ovr16/ng_co.png";
	private static final String WO_OVERLAY_PATH = "icons/ovr16/wo_co.png";

	private ImageDescriptor _ngOverlay;
	private ImageDescriptor _woOverlay;

	/** Cache of composited images to avoid recreating them on every call. */
	private final Map<Image, Image> _ngImageCache = new HashMap<>();
	private final Map<Image, Image> _woImageCache = new HashMap<>();

	@Override
	public Image decorateImage(Image image, Object element) {
		if (image == null) {
			return null;
		}

		IProject project = toProject(element);
		if (project == null || !project.isOpen()) {
			return image;
		}

		int type = detectProjectType(project);
		if (type == 0) {
			return image;
		}

		boolean isNG = (type == 1);
		Map<Image, Image> cache = isNG ? _ngImageCache : _woImageCache;
		ImageDescriptor overlay = isNG ? getNGOverlay() : getWOOverlay();

		if (overlay == null) {
			return image;
		}

		// Check cache first
		Image decorated = cache.get(image);
		if (decorated != null && !decorated.isDisposed()) {
			return decorated;
		}

		// Composite the overlay onto the base image
		Point size = new Point(image.getBounds().width, image.getBounds().height);
		ImageDescriptor[] overlays = new ImageDescriptor[5];
		overlays[IDecoration.TOP_LEFT] = overlay;
		decorated = new DecorationOverlayIcon(image, overlays, size).createImage(false);

		if (decorated != null) {
			cache.put(image, decorated);
		}
		return decorated != null ? decorated : image;
	}

	@Override
	public String decorateText(String text, Object element) {
		return text;
	}

	/**
	 * Detects the project type.
	 * <ol>
	 *   <li>{@code base=ng} / {@code base=wo} in build.properties → immediate answer</li>
	 *   <li>{@code project.name} in build.properties → WebObjects</li>
	 *   <li>Classpath contains {@code NGElement} → ng-objects</li>
	 *   <li>Classpath contains {@code WOElement} → WebObjects</li>
	 * </ol>
	 *
	 * @return 1 for ng-objects, -1 for WebObjects, 0 for neither/unknown
	 */
	private int detectProjectType(IProject project) {
		// 1. Check build.properties if it exists
		if (project.getFile("build.properties").exists()) {
			BuildProperties bp = getBuildProperties(project);
			if (bp != null) {
				String base = bp.get(BuildProperties.Key.BASE);
				if ("ng".equals(base)) {
					return 1;
				}
				if ("wo".equals(base)) {
					return -1;
				}
				// project.name without explicit base → WebObjects
				if (bp.get(BuildProperties.Key.PROJECT_NAME) != null) {
					return -1;
				}
			}
		}

		// 2. Fall back to classpath probing
		return detectByClasspath(project);
	}

	/**
	 * Probes the classpath for framework marker classes.
	 *
	 * @return 1 for ng-objects, -1 for WebObjects, 0 for neither
	 */
	private int detectByClasspath(IProject project) {
		try {
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null || !javaProject.exists()) {
				return 0;
			}
			if (javaProject.findType(ParsleyProject.NG_ELEMENT_CLASS) != null) {
				return 1;
			}
			if (javaProject.findType(ParsleyProject.WO_ELEMENT_CLASS) != null) {
				return -1;
			}
		} catch (Exception e) {
			// ignore
		}
		return 0;
	}

	private static IProject toProject(Object element) {
		if (element instanceof IProject) {
			return (IProject) element;
		}
		if (element instanceof IJavaProject) {
			return ((IJavaProject) element).getProject();
		}
		return null;
	}

	private ImageDescriptor getNGOverlay() {
		if (_ngOverlay == null) {
			_ngOverlay = createPluginImageDescriptor(NG_OVERLAY_PATH);
		}
		return _ngOverlay;
	}

	private ImageDescriptor getWOOverlay() {
		if (_woOverlay == null) {
			_woOverlay = createPluginImageDescriptor(WO_OVERLAY_PATH);
		}
		return _woOverlay;
	}

	private BuildProperties getBuildProperties(IProject project) {
		try {
			return Platform.getAdapterManager().getAdapter(project, BuildProperties.class);
		} catch (Exception e) {
			return null;
		}
	}

	private static ImageDescriptor createPluginImageDescriptor(String path) {
		Bundle bundle = FrameworkUtil.getBundle(ProjectDecorator.class);
		if (bundle != null) {
			URL url = FileLocator.find(bundle, new Path(path), null);
			if (url != null) {
				return ImageDescriptor.createFromURL(url);
			}
		}
		return null;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
		// not needed
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// not needed
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void dispose() {
		for (Image img : _ngImageCache.values()) {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		}
		_ngImageCache.clear();
		for (Image img : _woImageCache.values()) {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		}
		_woImageCache.clear();
	}
}
