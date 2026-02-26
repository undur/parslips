package ng.componenteditor.explorer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Installs a label provider wrapper on the tree viewer that overrides the
 * rendered text and icon for pulled-up source folders.
 * <p>
 * <b>Text:</b> Replaces just the folder name with the full project-relative
 * path (e.g. {@code src/main/components}) in a uniform style — no grey
 * qualifier prefix.
 * <p>
 * <b>Icon:</b> Adds a small "wo" overlay badge (bottom-left) to the folder
 * icon, similar to how JDT marks Java source folders with a package badge.
 * <p>
 * Because this wrapper replaces the viewer's label provider, Eclipse's
 * decorator manager is no longer directly connected to the viewer. To
 * compensate, the wrapper explicitly applies registered decorators during
 * {@link #update(ViewerCell)} for all elements.
 */
public class SourceFolderDecorator {

	private static final String WO_OVERLAY_PATH = "icons/ovr16/wo_co.png";

	/**
	 * Installs source folder label overrides on the given tree viewer.
	 * Must be called after the viewer's label provider is set up.
	 */
	public static void install(TreeViewer viewer) {
		CellLabelProvider existingProvider = viewer.getLabelProvider(0);

		if (existingProvider instanceof DelegatingStyledCellLabelProvider delegating) {
			viewer.setLabelProvider(new SourceFolderLabelProvider(delegating));
		}
	}

	/**
	 * A label provider that wraps the existing {@link DelegatingStyledCellLabelProvider},
	 * delegating all calls to it, but overriding the rendered text and icon for
	 * pulled-up source folders. Also explicitly applies platform decorators
	 * so that .wo component icons and other decorations continue to work.
	 */
	private static class SourceFolderLabelProvider extends DelegatingStyledCellLabelProvider implements ILabelProvider {

		private final DelegatingStyledCellLabelProvider _delegate;
		private ImageDescriptor _woOverlay;
		private final Map<Image, Image> _badgeCache = new HashMap<>();

		SourceFolderLabelProvider(DelegatingStyledCellLabelProvider delegate) {
			super(delegate.getStyledStringProvider());
			_delegate = delegate;
		}

		@Override
		public void update(ViewerCell cell) {
			// Let the original provider do its full work (base labels)
			_delegate.update(cell);

			Object element = cell.getElement();

			// Apply platform decorators (WOComponentDecorator, ProjectDecorator, etc.)
			// Since we replaced the DecoratingStyledCellLabelProvider, decorations
			// from plugin.xml won't fire automatically — we apply them explicitly.
			applyDecorations(cell, element);

			// Override text and icon for pulled-up source folders
			if (element instanceof IFolder folder) {
				if (NGPackageExplorerContentProvider.isSourceFolder(folder)) {
					String fullPath = folder.getProjectRelativePath().toString();
					cell.setText(fullPath);
					cell.setStyleRanges(new StyleRange[0]);

					Image baseImage = cell.getImage();
					if (baseImage != null) {
						cell.setImage(badgedImage(baseImage));
					}
				}
			}
		}

		/**
		 * Applies platform-level decorations (from org.eclipse.ui.decorators
		 * extension point) to the cell's image and text.
		 */
		private void applyDecorations(ViewerCell cell, Object element) {
			try {
				ILabelDecorator decorator = PlatformUI.getWorkbench()
						.getDecoratorManager().getLabelDecorator();

				Image image = cell.getImage();
				if (image != null) {
					Image decorated = decorator.decorateImage(image, element);
					if (decorated != null) {
						cell.setImage(decorated);
					}
				}

				String text = cell.getText();
				if (text != null) {
					String decorated = decorator.decorateText(text, element);
					if (decorated != null) {
						cell.setText(decorated);
					}
				}
			} catch (Exception e) {
				// If decoration fails, keep the undecorated label
			}
		}

		@Override
		public Image getImage(Object element) {
			if (_delegate instanceof ILabelProvider lp) {
				Image base = lp.getImage(element);
				if (element instanceof IFolder folder && NGPackageExplorerContentProvider.isSourceFolder(folder)) {
					return base != null ? badgedImage(base) : null;
				}
				return base;
			}
			return null;
		}

		@Override
		public String getText(Object element) {
			if (element instanceof IFolder folder) {
				if (NGPackageExplorerContentProvider.isSourceFolder(folder)) {
					return folder.getProjectRelativePath().toString();
				}
			}
			if (_delegate instanceof ILabelProvider lp) {
				return lp.getText(element);
			}
			return element == null ? "" : element.toString();
		}

		/**
		 * Returns the base image with the "wo" overlay badge composited at
		 * bottom-left. Results are cached to avoid creating new images on every update.
		 */
		private Image badgedImage(Image baseImage) {
			Image cached = _badgeCache.get(baseImage);
			if (cached != null && !cached.isDisposed()) {
				return cached;
			}

			ImageDescriptor overlay = getWOOverlay();
			if (overlay == null) {
				return baseImage;
			}

			Point size = new Point(baseImage.getBounds().width, baseImage.getBounds().height);
			ImageDescriptor[] overlays = new ImageDescriptor[5];
			overlays[IDecoration.BOTTOM_LEFT] = overlay;
			Image composited = new DecorationOverlayIcon(baseImage, overlays, size).createImage(false);

			if (composited != null) {
				_badgeCache.put(baseImage, composited);
				return composited;
			}
			return baseImage;
		}

		private ImageDescriptor getWOOverlay() {
			if (_woOverlay == null) {
				Bundle bundle = FrameworkUtil.getBundle(SourceFolderDecorator.class);
				if (bundle != null) {
					URL url = FileLocator.find(bundle, new Path(WO_OVERLAY_PATH), null);
					if (url != null) {
						_woOverlay = ImageDescriptor.createFromURL(url);
					}
				}
			}
			return _woOverlay;
		}

		@Override
		public void dispose() {
			for (Image img : _badgeCache.values()) {
				if (img != null && !img.isDisposed()) {
					img.dispose();
				}
			}
			_badgeCache.clear();
			super.dispose();
		}
	}
}
