package tk.eclipse.plugin.htmleditor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.aonir.fuzzyxml.FuzzyXMLElement;
import jp.aonir.fuzzyxml.XPath;
import jp.aonir.fuzzyxml.internal.FuzzyXMLUtil;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.graphics.RGB;

/**
 * This provides utility methods.
 * 
 * @author Naoki Takezoe
 */
public class HTMLUtil {
	
	/**
	 * Escape HTML special characters.
	 * 
	 * @param str the raw string
	 * @return the escaped string
	 */
	public static String escapeHTML(String str){
		return FuzzyXMLUtil.escape(str, true);
	}
	
	/**
	 * Replaces comments of CSS with whitespaces.
	 * 
	 * @param source CSS source code
	 * @return processed source code
	 */
	public static String cssComment2space(String source){
		int index = 0;
		int last  = 0;
		StringBuffer sb = new StringBuffer();
		while((index = source.indexOf("/*",last))!=-1){
			int end = source.indexOf("*/",index);
			if(end!=-1){
				sb.append(source.substring(last,index));
				int length = end - index + 2;
				for(int i=0;i<length;i++){
					sb.append(" ");
				}
			} else {
				break;
			}
			last = end + 2;
		}
		if(last != source.length()-1){
			sb.append(source.substring(last));
		}
		return sb.toString();
	}
	
	/**
	 * Replace comments of HTML/JSP/XML with whitespaces.
	 * 
	 * <ul>
	 *   <li>replace &lt;!-- ... --&gt; to the whitespaces</li>
	 *   <li>replace &lt;%-- ... --%&gt; to the whitespaces</li>
	 * </ul>
	 * 
	 * @param source source code of the HTML/JSP/XML
	 * @param contentsOnly
	 * <ul>
	 *   <li>true - &lt;!-- --&gt; and &lt;%-- --%&gt; are not replaced.<li>
	 *   <li>false - &lt;!-- --&gt; and &lt;%-- --%&gt; are also replaced.<li>
	 * </ul>
	 * @return processed source code
	 */
	public static String comment2space(String source,boolean contentsOnly){
		source = jspComment2space(source,contentsOnly);
		source = FuzzyXMLUtil.comment2space(source,contentsOnly);
		return source;
	}
	
	/**
	 * Replace comments of the JSP with whitespaces.
	 * 
	 * @param source source code of the JSP
	 * @param contentsOnly 
	 * <ul>
	 *   <li>true - &lt;% %&gt; are not replaced.</li>
	 *   <li>false - &lt;% %&gt; are also replaced.</li>
	 * </ul>
	 * @return processed source code
	 */
	public static String jspComment2space(String source,boolean contentsOnly){
		int index = 0;
		int last  = 0;
		StringBuffer sb = new StringBuffer();
		while((index = source.indexOf("<%--",last))!=-1){
			int end = source.indexOf("--%>",index);
			if(end!=-1){
				sb.append(source.substring(last,index));
				int length = end - index + 4;
				if(contentsOnly){
					sb.append("<%--");
					length = length - 8;
				}
				for(int i=0;i<length;i++){
					sb.append(" ");
				}
				if(contentsOnly){
					sb.append("--%>");
				}
			} else {
				break;
			}
			last = end + 4;
		}
		if(last != source.length()-1){
			sb.append(source.substring(last));
		}
		return sb.toString();
	}
	
	/**
	 * Replace scriptlet in the JSP to whitespaces.
	 * 
	 * @param source source code of the JSP
	 * @param contentsOnly
	 * <ul>
	 *   <li>true - &lt;% %&gt; are not replaced.
	 *   <li>false - &lt;% %&gt; are also replaced.
	 * </ul>
	 * @return processed source code
	 */
	public static String scriptlet2space(String source,boolean contentsOnly){
		int index = 0;
		int last  = 0;
		StringBuffer sb = new StringBuffer();
		while((index = source.indexOf("<%",last))!=-1){
			int end = source.indexOf("%>",index);
			if(end!=-1){
				sb.append(source.substring(last,index));
				int length = end - index + 2;
				if(contentsOnly){
					sb.append("<%");
					length = length - 4;
				}
				for(int i=0;i<length;i++){
					sb.append(" ");
				}
				if(contentsOnly){
					sb.append("%>");
				}
			} else {
				break;
			}
			last = end + 2;
		}
		if(last != source.length()-1){
			sb.append(source.substring(last));
		}
		return sb.toString();
	}

	/**
	 * Returns stream contents as a byte array.
	 */
	public static byte[] readStream(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int len = 0;
		byte[] buf = new byte[1024 * 8];
		while((len = in.read(buf))!=-1){
			out.write(buf,0,len);
		}
		byte[] result = out.toByteArray();
		in.close();
		out.close();
		return result;
	}
	
	/**
	 * Escape XML string.
	 * <p>
	 * If value is null, this method returns an empty string.
	 * </p>
	 * @param value string
	 * @return escaped string
	 */
	public static String escapeXML(String value){
		return FuzzyXMLUtil.escape(value, false);
	}
	
	/**
	 * Wrapps XPath#getValue() of FuzzyXML.
	 * This method provides following additional features.
	 * 
	 * <ul>
	 *   <li>returns null when exceptions are caused.</li>
	 *   <li>trims a return value.</li>
	 * </ul>
	 * 
	 * @param element the base element
	 * @param xpath XPath
	 * @return the selected value or null
	 */
	public static String getXPathValue(FuzzyXMLElement element,String xpath){
		try {
			String value = (String)XPath.getValue(element,xpath);
			return value.trim();
		} catch(Exception ex){
			return null;
		}
	}
	
	
	/**
	 * Sorts informations of code completion in alphabetical order.
	 * 
	 * @param prop the list of ICompletionProposal
	 */
	public static void sortCompilationProposal(List<ICompletionProposal> prop){
		Collections.sort(prop,new Comparator<ICompletionProposal>(){
			public int compare(ICompletionProposal o1, ICompletionProposal o2){
				return o1.getDisplayString().compareTo(o2.getDisplayString());
			}
		});
	}
	
	/**
	 * Returns a project encoding.
	 * 
	 * @param project project
	 * @return encoding
	 */
	public static String getProjectCharset(IProject project){
		try {
			String charset = project.getDefaultCharset();
			if(charset.equals("MS932")){
//				charset = "Shift_JIS";
				charset = "Windows-31J";
			}
			return charset;
		} catch(Exception ex){
			HTMLPlugin.logException(ex);
		}
		return null;
	}
	
	/**
	 * Adds marker to the specified line.
	 * 
	 * @param resource the target resource
	 * @param type the error type that defined by IMaker
	 * @param line the line number
	 * @param message the error message
	 */
	public static void addMarker(IResource resource, int type, int line, String message){
		try {
			IMarker marker = resource.createMarker(IMarker.PROBLEM);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(IMarker.SEVERITY, Integer.valueOf(type));
			map.put(IMarker.MESSAGE, message);
			map.put(IMarker.LINE_NUMBER,Integer.valueOf(line));
			marker.setAttributes(map);
		} catch(CoreException ex){
			HTMLPlugin.logException(ex);
		}
	}
	
	/**
	 * Adds task marker to the specified range.
	 * 
	 * @param resource the target resource
	 * @param priority the priority that defined by IMaker
	 * @param line the line number
	 * @param offset the offset
	 * @param length the length
	 * @param message the error message
	 */
	public static void addTaskMarker(IResource resource,int priority, int line, String message){
		try {
			IMarker marker = resource.createMarker(IMarker.TASK);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(IMarker.PRIORITY, Integer.valueOf(priority));
			map.put(IMarker.MESSAGE, message);
			map.put(IMarker.LINE_NUMBER,Integer.valueOf(line));
			marker.setAttributes(map);
		} catch(CoreException ex){
			HTMLPlugin.logException(ex);
		}
	}
	
	/**
	 * Adds marker to the specified range.
	 * 
	 * @param resource the target resource
	 * @param type the error type that defined by IMaker
	 * @param line the line number
	 * @param offset the offset
	 * @param length the length
	 * @param message the error message
	 */
	public static void addMarker(IResource resource,int type, int line, int offset,int length,String message){
		try {
			IMarker marker = resource.createMarker(IMarker.PROBLEM);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(IMarker.SEVERITY, Integer.valueOf(type));
			map.put(IMarker.MESSAGE, message);
			map.put(IMarker.CHAR_START,Integer.valueOf(offset));
			map.put(IMarker.CHAR_END,Integer.valueOf(offset + length));
			map.put(IMarker.LINE_NUMBER,Integer.valueOf(line));
			marker.setAttributes(map);
		} catch(CoreException ex){
			HTMLPlugin.logException(ex);
		}
	}
	
	/**
	 * Converts {@link RGB} to the hex string.
	 * 
	 * @param color the RGB object
	 * @return the hex string
	 */
	public static String toHex(RGB color){
		StringBuffer sb = new StringBuffer();
		sb.append("#").
			append(toHex(color.red)).
			append(toHex(color.green)).
			append(toHex(color.blue));
		return sb.toString();
	}
	
	private static String toHex(int value){
		String hex = Integer.toHexString(value);
		if(hex.length()==1){
			hex = "0" + hex;
		}
		return hex;
	}
	
	private static HashMap<IJavaProject, ICompilationUnit> unitMap = new HashMap<IJavaProject, ICompilationUnit>();
	
	/**
	 * Creates the <code>ICompilationUnit</code> to use temporary.
	 * 
	 * @param project the java project
	 * @return the temporary <code>ICompilationUnit</code>
	 * @throws JavaModelException
	 * @since 2.0.3
	 */
	public synchronized static ICompilationUnit getTemporaryCompilationUnit(
			IJavaProject project) throws JavaModelException {
		
		if(unitMap.get(project) != null){
			return unitMap.get(project);
		}
		
		IPackageFragment root = project.getPackageFragments()[0];
		ICompilationUnit unit = root.getCompilationUnit("_xxx.java").getWorkingCopy(
				new NullProgressMonitor());
		
		unitMap.put(project, unit);
		
		return unit;
	}
	
	
	/**
	 * Set contents of the compilation unit to the translated jsp text.
	 *
	 * @param unit the ICompilationUnit on which to set the buffer contents
	 * @param value Java source code
	 * @since 2.0.3
	 */	
	public static void setContentsToCU(ICompilationUnit unit, String value){
		if (unit == null)
			return;

		synchronized (unit) {
			IBuffer buffer;
			try {

				buffer = unit.getBuffer();
			}
			catch (JavaModelException e) {
				HTMLPlugin.logException(e);
				buffer = null;
			}

			if (buffer != null)
				buffer.setContents(value);
		}
	}
	
}
