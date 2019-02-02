package org.elastic.jdt.ls.core.internal;


import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.internal.compiler.util.SimpleSet;
import org.eclipse.jdt.internal.core.search.matching.MethodPattern;

public class HackReferenceMethodPattern extends MethodPattern {

protected IJavaElement enclosingElement;
protected SimpleSet knownMethods;

public HackReferenceMethodPattern(IJavaElement enclosingElement) {
	super(null, null, null, null, null, null, null, null, IJavaSearchConstants.REFERENCES, R_EXACT_MATCH | R_CASE_SENSITIVE | R_ERASURE_MATCH);

	this.enclosingElement = enclosingElement;
	this.knownMethods = new SimpleSet();
	this.mustResolve = true;
}
}
