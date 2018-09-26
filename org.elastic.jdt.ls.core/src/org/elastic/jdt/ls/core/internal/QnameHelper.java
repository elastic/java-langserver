package org.elastic.jdt.ls.core.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QnameHelper {

	public static String getSimplifiedQname(String qname) {
		String regex = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*";
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(qname);
		String simplifiedQname = null;
		while (matcher.find()) {
			simplifiedQname = matcher.group(0);
		}
		return simplifiedQname;
	}
}
