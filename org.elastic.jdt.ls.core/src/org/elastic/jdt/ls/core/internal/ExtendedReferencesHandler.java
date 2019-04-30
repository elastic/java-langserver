package org.elastic.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;

public class ExtendedReferencesHandler {
	
	private PreferenceManager preferenceManager;
	private final int MAX_REFERENCES = 1000;

	public ExtendedReferencesHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}
	
	private IJavaSearchScope createSearchScope(ITypeRoot unit) throws JavaModelException {
		IJavaProject[] projects = new IJavaProject[]{unit.getJavaProject()};
		
		int scope = IJavaSearchScope.SOURCES;
		if (preferenceManager.isClientSupportsClassFileContent()) {
			scope |= IJavaSearchScope.APPLICATION_LIBRARIES;
		}
		return SearchEngine.createJavaSearchScope(projects, scope);
	}

	public List<Location> findReferences(ReferenceParams param, IProgressMonitor monitor) {
		
		ITypeRoot unit = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri());

		final List<Location> locations = new ArrayList<>();
		try {
			IJavaElement elementToSearch = JDTUtils.findElementAtSelection(JDTUtils.resolveTypeRoot(param.getTextDocument().getUri()), param.getPosition().getLine(), param.getPosition().getCharacter(), this.preferenceManager, monitor);

			if (elementToSearch == null) {
				return locations;
			}

			boolean includeClassFiles = preferenceManager.isClientSupportsClassFileContent();
			SearchEngine engine = new SearchEngine();
			SearchPattern pattern = SearchPattern.createPattern(elementToSearch, IJavaSearchConstants.REFERENCES);

			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, createSearchScope(unit), new SearchRequestor() {
				
				private int currentSymbolNum = 0;

				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					if (currentSymbolNum >= MAX_REFERENCES) {
						return;
					}
					Object o = match.getElement();
					if (o instanceof IJavaElement) {
						IJavaElement element = (IJavaElement) o;
						ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						Location location = null;
						if (compilationUnit != null) {
							location = JDTUtils.toLocation(compilationUnit, match.getOffset(), match.getLength());
						} else if (includeClassFiles) {
							IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
							if (cf != null && cf.getSourceRange() != null) {
								location = JDTUtils.toLocation(cf, match.getOffset(), match.getLength());
							}
						}
						if (location != null) {
							locations.add(location);
							currentSymbolNum += 1;
						}
					}
				}
			}, monitor);

		} catch (CoreException e) {
			ElasticJavaLanguageServerPlugin.logException("Find references failure ", e);
		}
		return locations;
	}

}
