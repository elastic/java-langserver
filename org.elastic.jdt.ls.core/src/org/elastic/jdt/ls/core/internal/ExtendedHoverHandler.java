package org.elastic.jdt.ls.core.internal;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.HoverHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;


public class ExtendedHoverHandler extends HoverHandler {
	
	public ExtendedHoverHandler(PreferenceManager preferenceManager) {
		super(preferenceManager);
	}
	
	public Hover extendedHover(TextDocumentPositionParams position, IProgressMonitor monitor) {
		Hover hover = removeLinkInHoverContent(hover(position, monitor));
		String uri = position.getTextDocument().getUri();
        ITypeRoot unit = JDTUtils.resolveTypeRoot(uri);
        // use nodefinder to get the covering node
		if (unit != null && !monitor.isCanceled()) {
			try {
				File file = ResourceUtils.toFile(JDTUtils.toURI(uri));
				String content = Files.toString(file, Charsets.UTF_8);
				ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
				char[] source = content.toCharArray();
				parser.setIgnoreMethodBodies(false);
				parser.setSource(source);
				CompilationUnit ast = (CompilationUnit) parser.createAST(null);
				NodeFinder fNodeFinder = new NodeFinder(ast, JsonRpcHelpers.toOffset(unit.getBuffer(), position.getPosition().getLine(), position.getPosition().getCharacter()), 0);
				ASTNode node = fNodeFinder.getCoveringNode();
				hover.setRange(JDTUtils.toRange(unit, node.getStartPosition(), node.getLength()));
			} catch (JavaModelException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
		return hover;
	}

	// disable the markdown link in the hover
	private Hover removeLinkInHoverContent(Hover hover) {
		List<Either<String, MarkedString>> contents = hover.getContents().getLeft();
		if (contents == null) {
			return hover;
		}
		for (int i = 0; i < contents.size(); i++) {
			String originMarkdown = contents.get(i).getLeft();
			if (originMarkdown != null) {
				contents.set(i, Either.forLeft(FromLinkToCode(originMarkdown)));
			}
		}
		hover.setContents(contents);
		return hover;
	}

	private String FromLinkToCode(String origin) {
		String regex = "\\[(.+)\\]\\((.+)\\)";
		return origin.replaceAll(regex, "`$1`");
	}
}
