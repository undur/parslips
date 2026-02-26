package tk.eclipse.plugin.htmleditor;

import java.util.Map;

import jp.aonir.fuzzyxml.FuzzyXMLElement;

/**
 * An interface to validate custom tags.
 *
 * @author Naoki Takezoe
 */
public interface ICustomTagValidator {

	public void validate(Map attrs,FuzzyXMLElement element);

}
