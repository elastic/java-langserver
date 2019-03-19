package org.elastic.jdt.ls.core.internal;

import java.util.List;
import java.lang.Exception;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.jdt.ls.core.internal.handlers.NavigateToDefinitionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.elastic.jdt.ls.core.internal.hover.JavaElementLabels;

@SuppressWarnings("restriction")
public class EDefinitionHandler extends NavigateToDefinitionHandler {

	private PreferenceManager preferenceManager;

	public EDefinitionHandler(PreferenceManager preferenceManager) {
		super(preferenceManager);
		this.preferenceManager = preferenceManager;
	}

	public SymbolLocator eDefinition(TextDocumentPositionParams position, IProgressMonitor monitor) {
		try {
			List<? extends Location> locations = this.definition(position, monitor);
			if (locations != null && locations.size() > 0) {
				return new SymbolLocator(locations.get(0));
			}
		} catch (Exception e) {
			// ignore
		}
		ITypeRoot unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());
		try {
			IJavaElement element = JDTUtils.findElementAtSelection(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), this.preferenceManager, monitor);
			if (element == null) {
				return null;
			}
			String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
			return new SymbolLocator(QnameHelper.getSimplifiedQname(qname), DocumentSymbolHandler.mapKind(element));
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem computing edefinition for" +  unit.getElementName(), e);
		}
		return null;
	}

}
