package ng.componenteditor.explorer;

import java.net.URL;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerContentProvider;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerLabelProvider;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Label provider that shows a custom icon for .wo component bundle folders.
 */
public class NGPackageExplorerLabelProvider extends PackageExplorerLabelProvider {
	private Image _wocomponentImage;

	public NGPackageExplorerLabelProvider(PackageExplorerContentProvider cp) {
		super(cp);
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof IFolder) {
			IFolder folder = (IFolder) element;
			if (NGPackageExplorerContentProvider.isComponentBundle(folder)) {
				if (_wocomponentImage == null) {
					_wocomponentImage = createPluginImageDescriptor("icons/wobuilder/WOComponentBundle.png").createImage(false);
				}
				return _wocomponentImage;
			}
		}
		return super.getImage(element);
	}

	/**
	 * Creates an ImageDescriptor for a path relative to this plugin's bundle.
	 */
	private static ImageDescriptor createPluginImageDescriptor(String path) {
		Bundle bundle = FrameworkUtil.getBundle(NGPackageExplorerLabelProvider.class);
		if (bundle != null) {
			URL url = FileLocator.find(bundle, new Path(path), null);
			if (url != null) {
				return ImageDescriptor.createFromURL(url);
			}
		}
		return ImageDescriptor.getMissingImageDescriptor();
	}
}
