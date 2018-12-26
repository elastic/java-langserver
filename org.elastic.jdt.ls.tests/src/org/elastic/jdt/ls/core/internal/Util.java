package org.elastic.jdt.ls.core.internal;

public final class Util {

	private Util() {
		// private constructor - only static methods are expected
	}

	public static String convertToIndependentLineDelimiter(String source) {
		if (source.indexOf('\n') == -1 && source.indexOf('\r') == -1) {
			return source;
		}
		StringBuilder buffer = new StringBuilder();
		for (int i = 0, length = source.length(); i < length; i++) {
			char car = source.charAt(i);
			if (car == '\r') {
				buffer.append('\n');
				if (i < length-1 && source.charAt(i+1) == '\n') {
					i++; // skip \n after \r
				}
			} else {
				buffer.append(car);
			}
		}
		return buffer.toString();
	}

}
