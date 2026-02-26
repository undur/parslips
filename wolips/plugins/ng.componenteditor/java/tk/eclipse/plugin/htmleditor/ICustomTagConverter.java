package tk.eclipse.plugin.htmleditor;

import java.util.Map;

import jp.aonir.fuzzyxml.FuzzyXMLNode;

/**
 * An interface to convert taglibs for HTML preview.
 *
 * @author Naoki Takezoe
 */
public interface ICustomTagConverter {

	public String process(Map attrs,FuzzyXMLNode[] children);

}
