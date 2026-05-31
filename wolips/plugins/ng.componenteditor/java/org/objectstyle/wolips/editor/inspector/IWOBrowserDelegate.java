package org.objectstyle.wolips.editor.inspector;

import org.eclipse.swt.graphics.Point;

public interface IWOBrowserDelegate {
	public void browserColumnAdded(WOBrowserColumn column);
	
	public void browserColumnRemoved(WOBrowserColumn column);
	
	public void bindingDragging(WOBrowserColumn column, Point dragPoint);
	
	public void bindingDragCanceled(WOBrowserColumn column);
	
	public boolean bindingDropped(WOBrowserColumn column, Point dropPoint, BindingsDragHandler dragHandler);
}