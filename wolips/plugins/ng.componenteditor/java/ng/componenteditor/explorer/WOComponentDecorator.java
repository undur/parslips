package ng.componenteditor.explorer;

import java.net.URL;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Label decorator that replaces the default folder icon for .wo component
 * bundles with a custom component icon. Also applies Eclipse's standard
 * problem decoration (error/warning overlay markers).
 * <p>
 * Registered via {@code org.eclipse.ui.decorators} in plugin.xml, so the
 * icon appears in ALL views (Parsley Explorer, Package Explorer, Project Explorer,
 * etc.), not just the Parsley Explorer.
 */
public class WOComponentDecorator implements ILabelDecorator {
	private boolean _decorating;
	private ILabelDecorator _problemsLabelDecorator;
	private Image _componentBundleImage;

	public WOComponentDecorator() {
		_problemsLabelDecorator = new ProblemsLabelDecorator(null);
	}

	@Override
	public synchronized Image decorateImage(Image image, Object element) {
		if (_decorating) {
			// Prevent re-entrant decoration
			return image;
		}
		_decorating = true;
		try {
			if (_componentBundleImage == null) {
				_componentBundleImage = createPluginImageDescriptor("icons/WOComponentBundle.png").createImage(false);
			}
			Image decoratedImage = _componentBundleImage;

			// Apply platform-level decorations (e.g. team provider overlays)
			decoratedImage = PlatformUI.getWorkbench().getDecoratorManager()
					.getLabelDecorator().decorateImage(decoratedImage, element);

			// Apply problem marker overlays (error/warning triangles)
			decoratedImage = _problemsLabelDecorator.decorateImage(decoratedImage, element);

			return decoratedImage;
		} finally {
			_decorating = false;
		}
	}

	@Override
	public String decorateText(String text, Object element) {
		return text;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
		// not needed
	}

	@Override
	public void dispose() {
		if (_componentBundleImage != null) {
			_componentBundleImage.dispose();
			_componentBundleImage = null;
		}
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// not needed
	}

	private static ImageDescriptor createPluginImageDescriptor(String path) {
		Bundle bundle = FrameworkUtil.getBundle(WOComponentDecorator.class);
		if (bundle != null) {
			URL url = FileLocator.find(bundle, new Path(path), null);
			if (url != null) {
				return ImageDescriptor.createFromURL(url);
			}
		}
		return ImageDescriptor.getMissingImageDescriptor();
	}
}
