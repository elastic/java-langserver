package org.elastic.jdt.ls.core.internal;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.handlers.NavigateToDefinitionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class ExtendedNavigateToDefinitionHandler extends NavigateToDefinitionHandler {

	public ExtendedNavigateToDefinitionHandler(PreferenceManager preferenceManager) {
		super(preferenceManager);
		// TODO Auto-generated constructor stub
	}

	public List<? extends Location> extendedDefinition(TextDocumentPositionParams position, IProgressMonitor monitor) {
		List<? extends Location> locations = this.definition(position, monitor);
		ITypeRoot unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());
		Location location = null;
		try {
			if (locations == null) {
				CompilationUnit ast = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
				int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), position.getPosition().getLine(), position.getPosition().getCharacter());
				if (ast == null || offset < 0) {
					return null;
				}
				NodeFinder finder = new NodeFinder(ast, offset, 0);
				ASTNode coveringNode = finder.getCoveringNode();
				if (coveringNode instanceof SimpleName) {
					IBinding resolvedBinding = ((SimpleName) coveringNode).resolveBinding();
					if (resolvedBinding instanceof ITypeBinding) {
						String qualifedName = ((ITypeBinding) resolvedBinding).getQualifiedName();
						String uri = String.format("symbol://%s", qualifedName);
						location = new Location(uri, JDTUtils.newRange());
					}
				}
			}
			else {
				return locations;
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem computing definition for" + unit.getElementName(), e);
		}
		return location == null ? null : Arrays.asList(location);
	}
}
