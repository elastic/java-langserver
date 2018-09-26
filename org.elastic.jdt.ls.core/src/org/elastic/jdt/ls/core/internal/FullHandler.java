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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.elastic.jdt.ls.core.internal.hover.JavaElementLabels;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

@SuppressWarnings("restriction")
public class FullHandler extends DocumentSymbolHandler {

	private final PreferenceManager preferenceManager;

	public FullHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Full full(FullParams fullParams, IProgressMonitor monitor) {
		TextDocumentIdentifier textDocument = fullParams.getTextDocumentIdentifier();
		String uri = textDocument.getUri();
		ITypeRoot unit = JDTUtils.resolveTypeRoot(uri);
		List<? extends SymbolInformation> symbols = this.documentSymbol(new DocumentSymbolParams(textDocument), monitor);
		List<DetailSymbolInformation> detailInfos = new ArrayList<>();
		for (SymbolInformation symbol : symbols) {
			int line = this.getSymbolLine(symbol);
			int column = this.getSymbolColumn(symbol);
			try {
				IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column, this.preferenceManager, monitor);
				Hover hover = this.getHover(symbol, line, column, textDocument, monitor);
				DetailSymbolInformation detailInfo = createDetailSymbol(symbol, element, hover, monitor);
				detailInfos.add(detailInfo);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
						Reference reference;
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
							if (location != null) {
								reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(location));
							} else {
								String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
								reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(QnameHelper.getSimplifiedQname(qname), mapKind(element)));
							}
							// check if the reference already existed
							if (!allReferences.contains(reference)) {
								allReferences.add(reference);
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

	private DetailSymbolInformation createDetailSymbol(SymbolInformation symbol, IJavaElement element, Hover hover, IProgressMonitor monitor) {
		String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
		DetailSymbolInformation detailSymbolInfo = new DetailSymbolInformation(symbol, QnameHelper.getSimplifiedQname(qname), hover.getContents().getLeft(), hover.getRange());
		return detailSymbolInfo;
	}

	private Hover getHover(SymbolInformation symbol, int line, int column, TextDocumentIdentifier textDocument, IProgressMonitor monitor) {
		ExtendedHoverHandler hoverHandler = new ExtendedHoverHandler(this.preferenceManager);
		TextDocumentPositionParams position = new TextDocumentPositionParams(textDocument, new Position(line, column));
		return hoverHandler.extendedHover(position, monitor);
	}

	private int getSymbolLine(SymbolInformation symbol) {
		return symbol.getLocation().getRange().getStart().getLine();
	}

	private int getSymbolColumn(SymbolInformation symbol) {
		return symbol.getLocation().getRange().getStart().getCharacter();
	}

}
