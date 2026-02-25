/* ====================================================================
 *
 * The ObjectStyle Group Software License, Version 1.0
 *
 * Copyright (c) 2005 The ObjectStyle Group,
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
package org.objectstyle.wolips.core.resources.internal.pattern;

import java.io.File;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.List;

import org.objectstyle.wolips.core.resources.pattern.IPattern;

public class Pattern implements IPattern {

	private String pattern;

	public Pattern(String pattern) {
		super();
		this.pattern = pattern;
	}

	public String getPattern() {
		return this.pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	/**
	 * match the given <code>text</code> with the pattern
	 *
	 * @param string
	 *
	 * @return true if matched eitherwise false
	 */
	public boolean matches(String string) {
		return matchPath(this.pattern.replace("/", File.separator),
				string.replace("/", File.separator));
	}

	/**
	 * Ant-style path matching. Supports *, ** and ? wildcards.
	 * Replaces the previous dependency on org.apache.tools.ant SelectorUtils.
	 */
	private static boolean matchPath(String pattern, String path) {
		String sep = File.separator;
		String[] patDirs = tokenizePath(pattern, sep);
		String[] strDirs = tokenizePath(path, sep);

		int patIdxStart = 0;
		int patIdxEnd = patDirs.length - 1;
		int strIdxStart = 0;
		int strIdxEnd = strDirs.length - 1;

		// Match from the beginning
		while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
			String patDir = patDirs[patIdxStart];
			if ("**".equals(patDir)) {
				break;
			}
			if (!matchSegment(patDir, strDirs[strIdxStart])) {
				return false;
			}
			patIdxStart++;
			strIdxStart++;
		}

		if (strIdxStart > strIdxEnd) {
			// String is exhausted
			for (int i = patIdxStart; i <= patIdxEnd; i++) {
				if (!"**".equals(patDirs[i])) {
					return false;
				}
			}
			return true;
		}

		if (patIdxStart > patIdxEnd) {
			// Pattern is exhausted but string is not
			return false;
		}

		// Match from the end
		while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
			String patDir = patDirs[patIdxEnd];
			if ("**".equals(patDir)) {
				break;
			}
			if (!matchSegment(patDir, strDirs[strIdxEnd])) {
				return false;
			}
			patIdxEnd--;
			strIdxEnd--;
		}

		if (strIdxStart > strIdxEnd) {
			for (int i = patIdxStart; i <= patIdxEnd; i++) {
				if (!"**".equals(patDirs[i])) {
					return false;
				}
			}
			return true;
		}

		// Handle ** in the middle
		while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
			int patIdxTmp = -1;
			for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
				if ("**".equals(patDirs[i])) {
					patIdxTmp = i;
					break;
				}
			}
			if (patIdxTmp == patIdxStart + 1) {
				// ** next to **, skip
				patIdxStart++;
				continue;
			}
			int patLength = patIdxTmp - patIdxStart - 1;
			int strLength = strIdxEnd - strIdxStart + 1;
			int foundIdx = -1;
			for (int i = 0; i <= strLength - patLength; i++) {
				boolean match = true;
				for (int j = 0; j < patLength; j++) {
					if (!matchSegment(patDirs[patIdxStart + j + 1], strDirs[strIdxStart + i + j])) {
						match = false;
						break;
					}
				}
				if (match) {
					foundIdx = strIdxStart + i;
					break;
				}
			}
			if (foundIdx == -1) {
				return false;
			}
			patIdxStart = patIdxTmp;
			strIdxStart = foundIdx + patLength;
		}

		for (int i = patIdxStart; i <= patIdxEnd; i++) {
			if (!"**".equals(patDirs[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Matches a single path segment against a pattern segment (supports * and ?).
	 */
	private static boolean matchSegment(String pattern, String str) {
		char[] patArr = pattern.toCharArray();
		char[] strArr = str.toCharArray();
		int patIdxStart = 0;
		int patIdxEnd = patArr.length - 1;
		int strIdxStart = 0;
		int strIdxEnd = strArr.length - 1;

		boolean containsStar = false;
		for (char ch : patArr) {
			if (ch == '*') {
				containsStar = true;
				break;
			}
		}

		if (!containsStar) {
			if (patIdxEnd != strIdxEnd) {
				return false;
			}
			for (int i = 0; i <= patIdxEnd; i++) {
				char ch = patArr[i];
				if (ch != '?' && ch != strArr[i]) {
					return false;
				}
			}
			return true;
		}

		if (patIdxEnd == 0) {
			return true; // pattern is just "*"
		}

		// Process characters before first star
		while ((patArr[patIdxStart] != '*') && strIdxStart <= strIdxEnd) {
			char ch = patArr[patIdxStart];
			if (ch != '?' && ch != strArr[strIdxStart]) {
				return false;
			}
			patIdxStart++;
			strIdxStart++;
		}

		if (strIdxStart > strIdxEnd) {
			for (int i = patIdxStart; i <= patIdxEnd; i++) {
				if (patArr[i] != '*') {
					return false;
				}
			}
			return true;
		}

		// Process characters after last star
		while ((patArr[patIdxEnd] != '*') && strIdxStart <= strIdxEnd) {
			char ch = patArr[patIdxEnd];
			if (ch != '?' && ch != strArr[strIdxEnd]) {
				return false;
			}
			patIdxEnd--;
			strIdxEnd--;
		}

		if (strIdxStart > strIdxEnd) {
			for (int i = patIdxStart; i <= patIdxEnd; i++) {
				if (patArr[i] != '*') {
					return false;
				}
			}
			return true;
		}

		// Process between stars
		while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
			int patIdxTmp = -1;
			for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
				if (patArr[i] == '*') {
					patIdxTmp = i;
					break;
				}
			}
			if (patIdxTmp == patIdxStart + 1) {
				patIdxStart++;
				continue;
			}
			int patLength = patIdxTmp - patIdxStart - 1;
			int strLength = strIdxEnd - strIdxStart + 1;
			int foundIdx = -1;
			for (int i = 0; i <= strLength - patLength; i++) {
				boolean match = true;
				for (int j = 0; j < patLength; j++) {
					char ch = patArr[patIdxStart + j + 1];
					if (ch != '?' && ch != strArr[strIdxStart + i + j]) {
						match = false;
						break;
					}
				}
				if (match) {
					foundIdx = strIdxStart + i;
					break;
				}
			}
			if (foundIdx == -1) {
				return false;
			}
			patIdxStart = patIdxTmp;
			strIdxStart = foundIdx + patLength;
		}

		for (int i = patIdxStart; i <= patIdxEnd; i++) {
			if (patArr[i] != '*') {
				return false;
			}
		}
		return true;
	}

	private static String[] tokenizePath(String path, String separator) {
		List<String> tokens = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(path, separator);
		while (st.hasMoreTokens()) {
			tokens.add(st.nextToken());
		}
		return tokens.toArray(new String[0]);
	}

}
