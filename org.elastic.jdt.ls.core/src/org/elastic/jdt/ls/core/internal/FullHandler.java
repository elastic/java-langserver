package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.*;
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
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.elastic.jdt.ls.core.internal.hover.JavaElementLabels;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

@SuppressWarnings("restriction")
public class FullHandler {

	private final PreferenceManager preferenceManager;

	public FullHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Full full(FullParams fullParams, IProgressMonitor monitor) {
//		TextDocumentIdentifier textDocument = fullParams.getTextDocumentIdentifier();
//		String uri = textDocument.getUri();
//		ITypeRoot unit = JDTUtils.resolveTypeRoot(uri);
//        long start = System.currentTimeMillis();
//		List<? extends SymbolInformation> symbols = this.documentSymbol(new DocumentSymbolParams(textDocument), monitor);
//        long end = System.currentTimeMillis();
//        JavaLanguageServerPlugin.logInfo("Get documentSymbol time: " + (end-start) + "ms");
//		List<DetailSymbolInformation> detailInfos = new ArrayList<>();
//        start = System.currentTimeMillis();
//		for (SymbolInformation symbol : symbols) {
//			int line = this.getSymbolLine(symbol);
//			int column = this.getSymbolColumn(symbol);
//			try {
//				IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column, this.preferenceManager, monitor);
//				Hover hover = this.getHover(line, column, textDocument, monitor);
//				DetailSymbolInformation detailInfo = createDetailSymbol(symbol, element, hover);
//				detailInfos.add(detailInfo);
//			} catch (JavaModelException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//        end = System.currentTimeMillis();
//        JavaLanguageServerPlugin.logInfo("Get Qname time: " + (end-start) + "ms");
//        start = System.currentTimeMillis();
//		List<Reference> allReferences = getAllReferences(monitor, textDocument);
//		end = System.currentTimeMillis();
//        JavaLanguageServerPlugin.logInfo("Get references time: " + (end-start) + "ms");
		ITypeRoot unit = JDTUtils.resolveTypeRoot(fullParams.getTextDocumentIdentifier().getUri());

//		SymbolInformation[] elements = this.getOutline(unit, monitor);
		return this.getFull(unit, monitor);
	}


	private Full getFull(ITypeRoot unit, IProgressMonitor monitor) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<DetailSymbolInformation> symbols = new ArrayList<>(elements.length);
			ArrayList<Reference> references = new ArrayList<>(elements.length);
			collectSymbolsAndReferences(unit, elements, symbols, references, monitor);
			return new Full(symbols, references);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting outline for" +  unit.getElementName(), e);
		}
		return null;
	}


	private void collectSymbolsAndReferences(ITypeRoot unit, IJavaElement[] elements, ArrayList<DetailSymbolInformation> symbols, ArrayList<Reference> references, IProgressMonitor monitor) throws JavaModelException {
		for(IJavaElement element : elements ){
			if (monitor.isCanceled()) {
				return;
			}
			if(element instanceof IParent){
				collectSymbolsAndReferences(unit, filter(((IParent) element).getChildren()), symbols, references, monitor);
			}
			Location rawLocation = JDTUtils.toLocation(element);
			ICompilationUnit cu = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
			IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
			Location location = null;
			if (cu != null || (cf != null && cf.getSourceRange() != null)) {
				location = JDTUtils.toLocation(element);
			}
			if (element instanceof IMember && ((IMember) element).getClassFile() != null) {
				location = JDTUtils.toLocation(((IMember) element).getClassFile());
			}
			Reference reference;
			if (location != null) {
				reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(location));
			} else {
				String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
				reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(QnameHelper.getSimplifiedQname(qname), DocumentSymbolHandler.mapKind(element)));
			}
			// check if the reference already existed
			if (!references.contains(reference)) {
				references.add(reference);
			}
			int type = element.getElementType();
			if (type != IJavaElement.TYPE && type != IJavaElement.FIELD && type != IJavaElement.METHOD) {
				continue;
			}
			try {
				if (rawLocation != null) {
					SymbolInformation si = new SymbolInformation();
					String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
					String name = JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT);
					si.setName(name == null ? element.getElementName() : name);
					List<Either<String, MarkedString>> res = new LinkedList<>();
					MarkedString signature = HoverInfoProvider.computeSignature(element);
					res.add(Either.forRight(signature));
					MarkedString javadoc = HoverInfoProvider.computeJavadoc(element);
					if (javadoc != null && javadoc.getValue() != null) {
						res.add(Either.forLeft(javadoc.getValue()));
					}
					si.setName(name == null ? element.getElementName() : name);
					si.setKind(DocumentSymbolHandler.mapKind(element));
					if (element.getParent() != null) {
						si.setContainerName(element.getParent().getElementName());
					}
					rawLocation.setUri(ResourceUtils.toClientUri(rawLocation.getUri()));
					si.setLocation(rawLocation);
					DetailSymbolInformation detailSymbolInfo = new DetailSymbolInformation(si, QnameHelper.getSimplifiedQname(qname), res);
					if (!symbols.contains(detailSymbolInfo)) {
						symbols.add(detailSymbolInfo);
					}
				}
			} catch (CoreException e) {
				// ignore
			}
		}
//		List<Reference> allReferences = java.util.Collections.emptyList();
//		if (fullParams.isReference()) {
//			allReferences = getAllReferences(monitor, textDocument);
//		}
	}

	private IJavaElement[] filter(IJavaElement[] elements) {
		return Stream.of(elements)
				.filter(e -> (!isInitializer(e) && !isSyntheticElement(e)))
				.toArray(IJavaElement[]::new);
	}

	private boolean isInitializer(IJavaElement element) {
		if (element.getElementType() == IJavaElement.METHOD) {
			String name = element.getElementName();
			if ((name != null && name.indexOf('<') >= 0)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSyntheticElement(IJavaElement element) {
		if (!(element instanceof IMember)) {
			return false;
		}
		IMember member= (IMember)element;
		if (!(member.isBinary())) {
			return false;
		}
		try {
			return Flags.isSynthetic(member.getFlags());
		} catch (JavaModelException e) {
			return false;
		}
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
								reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(QnameHelper.getSimplifiedQname(qname), DocumentSymbolHandler.mapKind(element)));
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

	private DetailSymbolInformation createDetailSymbol(SymbolInformation symbol, IJavaElement element, Hover hover) {
		String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
		DetailSymbolInformation detailSymbolInfo = new DetailSymbolInformation(symbol, QnameHelper.getSimplifiedQname(qname), hover.getContents().getLeft(), hover.getRange());
		return detailSymbolInfo;
	}

	private Hover getHover(int line, int column, TextDocumentIdentifier textDocument, IProgressMonitor monitor) {
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
