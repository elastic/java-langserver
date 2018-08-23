package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.elastic.jdt.ls.core.internal.hover.JavaElementLabels;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class FullHandler extends DocumentSymbolHandler {

	private final PreferenceManager preferenceManager;

	public FullHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Full full(FullParams fullParams, IProgressMonitor monitor) {
		TextDocumentIdentifier textDocument = fullParams.getTextDocumentIdentifier();
		ITypeRoot unit = JDTUtils.resolveTypeRoot(textDocument.getUri());
		List<? extends SymbolInformation> symbols = this.documentSymbol(new DocumentSymbolParams(textDocument), monitor);
		List<DetailSymbolInformation> detailInfos = new ArrayList<>();
		for (SymbolInformation symbol : symbols) {
			DetailSymbolInformation detailInfo = createDetailSymbol(unit, symbol, monitor);
			detailInfos.add(detailInfo);
		}
		List<Reference> allReferences = getAllReferences(monitor, textDocument);

		return new Full(detailInfos, allReferences);
	}

	private List<Reference> getAllReferences(IProgressMonitor monitor, TextDocumentIdentifier textDocument) {
		final List<Reference> allReferences = new ArrayList<>();
		ICompilationUnit compilationUnit = JDTUtils.resolveCompilationUnit(textDocument.getUri());
		ITypeRoot unit = JDTUtils.resolveTypeRoot(textDocument.getUri());
		File file = ResourceUtils.toFile(JDTUtils.toURI(textDocument.getUri()));
		ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		try {
			String contents = Files.toString(file, Charsets.UTF_8);
			parser.setSource(contents.toCharArray());
			CompilationUnit cu = (CompilationUnit) parser.createAST(null);
			PreferenceManager pm = this.preferenceManager;
			cu.accept(new ASTVisitor() {

				@Override
				public boolean visit(SimpleName node) {
					return this.addReferenceOfNode(node.getStartPosition(), node.getLength());
				}

				@Override
				public boolean visit(SimpleType node) {
					return this.addReferenceOfNode(node.getStartPosition(), node.getLength());
				}

				private boolean addReferenceOfNode(int start, int length) {
					try {
						Location rawLocation = JDTUtils.toLocation(compilationUnit, start, length);
						Position position = rawLocation.getRange().getStart();
						IJavaElement element = JDTUtils.findElementAtSelection(unit, position.getLine(), position.getCharacter(), pm, monitor);
						if (element != null) {
							ICompilationUnit cu = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
							IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
							Location location = null;
							if (cu != null || (cf != null && cf.getSourceRange() != null)) {
								location = JDTUtils.toLocation(element);
							}
							if (element instanceof IMember && ((IMember) element).getClassFile() != null) {
								location = JDTUtils.toLocation(((IMember) element).getClassFile());
							}
							SymbolInformation si = new SymbolInformation();
							String name = JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT);
							si.setName(name == null ? element.getElementName() : name);
							si.setKind(mapKind(element));
							if (element.getParent() != null) {
								si.setContainerName(element.getParent().getElementName());
							}
							if (location != null) {
								location.setUri(ResourceUtils.toClientUri(location.getUri()));
								si.setLocation(location);
								Reference reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, si);
								if (!allReferences.contains(reference)) {
									allReferences.add(reference);
								}
							}
						}
					} catch (JavaModelException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return true;
				}
			});
		} catch (IOException e) {
			//ignore
		}
		return allReferences;
	}

	private DetailSymbolInformation createDetailSymbol(ITypeRoot unit, SymbolInformation symbol, IProgressMonitor monitor) {
		int line = symbol.getLocation().getRange().getStart().getLine();
		int column = symbol.getLocation().getRange().getStart().getCharacter();
		HoverInfoProvider provider = new HoverInfoProvider(unit, this.preferenceManager);
		List<Either<String, MarkedString>> contents = provider.computeHover(line, column, monitor);
		DetailSymbolInformation detailSymbolInfo = new DetailSymbolInformation(symbol, contents);
		return detailSymbolInfo;
	}

}
