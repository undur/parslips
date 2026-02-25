/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2002 - 2004 The ObjectStyle Group
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne"
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */

package org.objectstyle.wolips.preferences;

import java.io.IOException;

import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * @author uli
 */
public class Preferences {

	public static final String PREF_WOLIPS_PROPERTIES_FILE = "ng.componenteditor.wolipsPropertiesFile";

	public static final String trueString = "true";

	public static final String falseString = "false";

	// set this string to a preferences key and call set defaults to set the
	// default value for this preferences key
	private static String SET_DEFAULTS_STRING = null;

	public static void save() {
		IPreferenceStore store = getPreferenceStore();
		if (store instanceof IPersistentPreferenceStore) {
			IPersistentPreferenceStore pstore = (IPersistentPreferenceStore) store;
			if (pstore.needsSaving()) {
				try {
					pstore.save();
				} catch (IOException up) {
					// hmm, what should we do?
				}
			}
		}
	}

	public static synchronized void setDefaults() {
		IPreferenceStore store = getPreferenceStore();
		if (Preferences.SET_DEFAULTS_STRING == null || Preferences.SET_DEFAULTS_STRING.equals(Preferences.PREF_WOLIPS_PROPERTIES_FILE)) {
			store.setDefault(Preferences.PREF_WOLIPS_PROPERTIES_FILE, "wolips.properties");
		}
		Preferences.SET_DEFAULTS_STRING = null;
	}

	public static synchronized String getString(String key) {
		IPreferenceStore store = getPreferenceStore();
		String returnValue = store.getString(key);
		if (returnValue.equals(IPreferenceStore.STRING_DEFAULT_DEFAULT)) {
			Preferences.setDefaults();
			Preferences.SET_DEFAULTS_STRING = key;
			returnValue = store.getString(key);
		}
		return returnValue;
	}

	public static void setString(String key, String value) {
		IPreferenceStore store = getPreferenceStore();
		store.setValue(key, value);
	}

	public static IPreferenceStore getPreferenceStore() {
		return PreferencesPlugin.getDefault().getPreferenceStore();
	}
}
