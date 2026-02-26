package ng.componenteditor.explorer;

import org.eclipse.core.resources.IFolder;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;

/**
 * Label decorator that replaces the simple folder name with the full
 * project-relative path for pulled-up source folders (e.g., shows
 * {@code src/main/components} instead of {@code components}).
 * <p>
 * Registered via {@code org.eclipse.ui.decorators} in plugin.xml with an
 * enablement filter matching {@code IFolder} elements. The decorator
 * checks internally whether the folder is actually a pulled-up source
 * folder — if not, it returns the text unchanged.
 */
public class SourceFolderDecorator implements ILabelDecorator {

	@Override
	public String decorateText(String text, Object element) {
		if (element instanceof IFolder) {
			IFolder folder = (IFolder) element;
			if (NGPackageExplorerContentProvider.isSourceFolder(folder)) {
				return folder.getProjectRelativePath().toString();
			}
		}
		return text;
	}

	@Override
	public Image decorateImage(Image image, Object element) {
		// No image decoration — just text
		return image;
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
		// nothing to dispose
	}
}
