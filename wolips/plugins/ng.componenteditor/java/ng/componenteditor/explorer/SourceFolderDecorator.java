package ng.componenteditor.explorer;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Installs source-folder-aware label overrides on the Parsley Explorer's
 * tree viewer <em>without</em> replacing its label provider.
 * <p>
 * The JDT Package Explorer uses a {@code DecoratingStyledCellLabelProvider}
 * which connects to Eclipse's {@code DecorationScheduler}. That scheduler
 * feeds asynchronous decorator updates (EGit branch labels, problem markers,
 * etc.) into the viewer. Replacing the label provider — as we did previously
 * — severs that connection and loses git/team decorations.
 * <p>
 * Instead, this class wraps the <em>inner</em> {@link IStyledLabelProvider}
 * that the {@code DecoratingStyledCellLabelProvider} delegates to, and
 * re-injects the wrapper using reflection. The outer decorator chain stays
 * intact, so all platform decorators (EGit, Team, problems, etc.) continue
 * to work.
 * <p>
 * <b>Text override:</b> For pulled-up source folders, replaces the folder
 * name with the full project-relative path (e.g. {@code src/main/components})
 * in a uniform style — no grey qualifier prefix.
 * <p>
 * <b>Icon override:</b> For pulled-up source folders, composites a small
 * overlay badge at bottom-left — "wo" for WO-style folders, "ng" for
 * ng-style folders — similar to how JDT marks Java source folders.
 */
public class SourceFolderDecorator {

	private static final String WO_OVERLAY_PATH = "icons/ovr16/wo_co.png";
	private static final String NG_OVERLAY_PATH = "icons/ovr16/ng_co.png";

	/**
	 * Installs source folder label overrides on the given tree viewer.
	 * Must be called after the viewer's label provider is set up.
	 */
	public static void install(TreeViewer viewer) {
		Object existingProvider = viewer.getLabelProvider();

		if (existingProvider instanceof DelegatingStyledCellLabelProvider decorating) {
			IStyledLabelProvider inner = decorating.getStyledStringProvider();
			if (inner != null && !(inner instanceof SourceFolderStyledLabelProvider)) {
				// Wrap the inner provider with our override
				SourceFolderStyledLabelProvider wrapper = new SourceFolderStyledLabelProvider(inner);

				// Re-inject the wrapper into the DecoratingStyledCellLabelProvider
				// via reflection. The field is "styledLabelProvider" in the superclass
				// DelegatingStyledCellLabelProvider.
				try {
					java.lang.reflect.Field field = DelegatingStyledCellLabelProvider.class
							.getDeclaredField("styledLabelProvider");
					field.setAccessible(true);
					field.set(decorating, wrapper);
				} catch (Exception e) {
					// Reflection failed — fall back to the old approach as a last resort.
					// This means git decorations won't work, but at least the view loads.
					viewer.setLabelProvider(new FallbackSourceFolderLabelProvider(decorating));
				}
			}
		}
	}

	/**
	 * Wraps an {@link IStyledLabelProvider}, overriding text and images for
	 * pulled-up source folders while delegating everything else.
	 */
	private static class SourceFolderStyledLabelProvider implements IStyledLabelProvider {

		private final IStyledLabelProvider _delegate;
		private ImageDescriptor _woOverlay;
		private ImageDescriptor _ngOverlay;

		/** Cache keyed by (base image, overlay path) to handle both badges. */
		private final Map<String, Image> _badgeCache = new HashMap<>();

		SourceFolderStyledLabelProvider(IStyledLabelProvider delegate) {
			_delegate = delegate;
		}

		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof IFolder folder) {
				if (NGPackageExplorerContentProvider.isSourceFolder(folder)) {
					return new StyledString(folder.getProjectRelativePath().toString());
				}
			}
			return _delegate.getStyledText(element);
		}

		@Override
		public Image getImage(Object element) {
			Image base = _delegate.getImage(element);
			if (element instanceof IFolder folder) {
				if (NGPackageExplorerContentProvider.isSourceFolder(folder)) {
					boolean isNg = NGPackageExplorerContentProvider.isNgSourceFolder(folder);
					return base != null ? badgedImage(base, isNg) : null;
				}
			}
			return base;
		}

		@Override
		public void addListener(ILabelProviderListener listener) {
			_delegate.addListener(listener);
		}

		@Override
		public void removeListener(ILabelProviderListener listener) {
			_delegate.removeListener(listener);
		}

		@Override
		public boolean isLabelProperty(Object element, String property) {
			return _delegate.isLabelProperty(element, property);
		}

		@Override
		public void dispose() {
			for (Image img : _badgeCache.values()) {
				if (img != null && !img.isDisposed()) {
					img.dispose();
				}
			}
			_badgeCache.clear();
			_delegate.dispose();
		}

		private Image badgedImage(Image baseImage, boolean isNg) {
			// Cache key combines the base image identity with the overlay type
			String cacheKey = System.identityHashCode(baseImage) + (isNg ? ":ng" : ":wo");
			Image cached = _badgeCache.get(cacheKey);
			if (cached != null && !cached.isDisposed()) {
				return cached;
			}

			ImageDescriptor overlay = isNg ? getNgOverlay() : getWoOverlay();
			if (overlay == null) {
				return baseImage;
			}

			Point size = new Point(baseImage.getBounds().width, baseImage.getBounds().height);
			ImageDescriptor[] overlays = new ImageDescriptor[5];
			overlays[IDecoration.BOTTOM_LEFT] = overlay;
			Image composited = new DecorationOverlayIcon(baseImage, overlays, size).createImage(false);

			if (composited != null) {
				_badgeCache.put(cacheKey, composited);
				return composited;
			}
			return baseImage;
		}

		private ImageDescriptor getWoOverlay() {
			if (_woOverlay == null) {
				_woOverlay = loadOverlay(WO_OVERLAY_PATH);
			}
			return _woOverlay;
		}

		private ImageDescriptor getNgOverlay() {
			if (_ngOverlay == null) {
				_ngOverlay = loadOverlay(NG_OVERLAY_PATH);
			}
			return _ngOverlay;
		}

		private static ImageDescriptor loadOverlay(String path) {
			Bundle bundle = FrameworkUtil.getBundle(SourceFolderDecorator.class);
			if (bundle != null) {
				URL url = FileLocator.find(bundle, new Path(path), null);
				if (url != null) {
					return ImageDescriptor.createFromURL(url);
				}
			}
			return null;
		}
	}

	/**
	 * Fallback label provider used only if reflection fails. This is the old
	 * approach that replaces the label provider entirely — it works for source
	 * folder labels but loses async decorator updates (e.g. EGit).
	 */
	private static class FallbackSourceFolderLabelProvider
			extends DelegatingStyledCellLabelProvider
			implements org.eclipse.jface.viewers.ILabelProvider {

		private final DelegatingStyledCellLabelProvider _delegate;

		FallbackSourceFolderLabelProvider(DelegatingStyledCellLabelProvider delegate) {
			super(delegate.getStyledStringProvider());
			_delegate = delegate;
		}

		@Override
		public void update(org.eclipse.jface.viewers.ViewerCell cell) {
			_delegate.update(cell);

			Object element = cell.getElement();
			try {
				org.eclipse.jface.viewers.ILabelDecorator decorator =
						org.eclipse.ui.PlatformUI.getWorkbench()
								.getDecoratorManager().getLabelDecorator();
				Image image = cell.getImage();
				if (image != null) {
					Image decorated = decorator.decorateImage(image, element);
					if (decorated != null) cell.setImage(decorated);
				}
				String text = cell.getText();
				if (text != null) {
					String decorated = decorator.decorateText(text, element);
					if (decorated != null) cell.setText(decorated);
				}
			} catch (Exception e) { }

			if (element instanceof IFolder folder) {
				if (NGPackageExplorerContentProvider.isSourceFolder(folder)) {
					cell.setText(folder.getProjectRelativePath().toString());
					cell.setStyleRanges(new org.eclipse.swt.custom.StyleRange[0]);
				}
			}
		}

		@Override
		public Image getImage(Object element) {
			if (_delegate instanceof org.eclipse.jface.viewers.ILabelProvider lp) {
				return lp.getImage(element);
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
			if (_delegate instanceof org.eclipse.jface.viewers.ILabelProvider lp) {
				return lp.getText(element);
			}
			return element == null ? "" : element.toString();
		}
	}
}
