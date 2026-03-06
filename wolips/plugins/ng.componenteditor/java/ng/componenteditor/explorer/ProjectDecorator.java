package ng.componenteditor.explorer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.objectstyle.wolips.variables.ParsleyProject;
import org.objectstyle.wolips.variables.ParsleyProject.ProjectType;
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
 * Framework detection is delegated to {@link ParsleyProject#getProjectType()}.
 * Projects with {@link ProjectType#UNKNOWN} are left undecorated.
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

		// When WOLips is installed, only decorate projects that have
		// explicitly opted in via project.base in build.properties.
		if (!ParsleyProject.isParsleyProject(project)) {
			return image;
		}

		ParsleyProject pp = (ParsleyProject) project.getAdapter(ParsleyProject.class);
		ProjectType projectType = pp.getProjectType();
		if (projectType == ProjectType.UNKNOWN) {
			return image;
		}

		boolean isNG = (projectType == ProjectType.NG);
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
