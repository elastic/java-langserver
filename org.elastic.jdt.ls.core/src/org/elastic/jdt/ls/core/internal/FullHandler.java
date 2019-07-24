package org.elastic.jdt.ls.core.internal;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.elastic.jdt.ls.core.internal.hover.JavaElementLabels;


@SuppressWarnings("restriction")
public class FullHandler {

	private final PreferenceManager preferenceManager;

	public FullHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Full full(FullParams fullParams, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(fullParams.getTextDocumentIdentifier().getUri());
		TextDocumentIdentifier textDocument = fullParams.getTextDocumentIdentifier();
		return this.getFull(unit, fullParams.isReference(), textDocument, monitor);
	}

	private Full getFull(ITypeRoot unit, boolean isReference, TextDocumentIdentifier textDocument, IProgressMonitor monitor) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<DetailSymbolInformation> symbols = new ArrayList<>(elements.length);
			ArrayList<Reference> references = new ArrayList<>();
			collectSymbols(unit, elements, symbols, monitor);
			if (isReference) {
				collectReferences(references, textDocument, monitor);
			}
			return new Full(symbols, references);
		} catch (JavaModelException e) {
			ElasticJavaLanguageServerPlugin.logException("Problem getting outline for" +  unit.getElementName(), e);
			if (e.getMessage().indexOf("exist") != -1) {
				throw new RuntimeException("temporarily unavailable");
			}
		}
		return null;
	}

	private void collectSymbols(ITypeRoot unit, IJavaElement[] elements, ArrayList<DetailSymbolInformation> symbols, IProgressMonitor monitor) throws JavaModelException {
		for (IJavaElement element : elements) {
			try {
				if (monitor.isCanceled()) {
					return;
				}
				if (element instanceof IParent) {
					collectSymbols(unit, filter(((IParent) element).getChildren()), symbols, monitor);
				}
				Location rawLocation = JDTUtils.toLocation(element);
				int type = element.getElementType();
				if (type != IJavaElement.TYPE && type != IJavaElement.FIELD && type != IJavaElement.METHOD) {
					continue;
				}
				if (rawLocation != null) {
					SymbolInformation si = new SymbolInformation();
					String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
					String name = JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT);
					si.setName(name == null ? element.getElementName() : name);
					// List<Either<String, MarkedString>> res = new LinkedList<>();
					// MarkedString signature = HoverInfoProvider.computeSignature(element);
					// res.add(Either.forRight(signature));
					// MarkedString javadoc = HoverInfoProvider.computeJavadoc(element);
					// if (javadoc != null && javadoc.getValue() != null) {
					// 	res.add(Either.forLeft(javadoc.getValue()));
					// }
					si.setName(name == null ? element.getElementName() : name);
					si.setKind(DocumentSymbolHandler.mapKind(element));
					if (element.getParent() != null) {
						si.setContainerName(element.getParent().getElementName());
					}
					rawLocation.setUri(ResourceUtils.toClientUri(rawLocation.getUri()));
					si.setLocation(rawLocation);
					DetailSymbolInformation detailSymbolInfo = new DetailSymbolInformation(si, QnameHelper.getSimplifiedQname(qname));
					if (!symbols.contains(detailSymbolInfo)) {
						symbols.add(detailSymbolInfo);
					}
				}
			} catch (Exception e) {
				// Ignore Exception when indexing
				ElasticJavaLanguageServerPlugin.logException("Problem when do indexing for" +  unit.getElementName(), e);
			}
		}
	}

	private void collectReferences(ArrayList<Reference> references, TextDocumentIdentifier textDocument, IProgressMonitor monitor) {
		ICompilationUnit compilationUnit = JDTUtils.resolveCompilationUnit(textDocument.getUri());
		ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		parser.setEnvironment(null, null, null, true);
		parser.setBindingsRecovery(true);
		CompilationUnit ast = (CompilationUnit) parser.createAST(null);
		ast.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(QualifiedName node) {
				IBinding bind = node.resolveBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return false;
			}
			
			@Override
			public boolean visit(SuperFieldAccess node) {
				IBinding bind = node.resolveFieldBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(FieldAccess node) {
				IBinding bind = node.resolveFieldBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(ConstructorInvocation node) {
				IBinding bind = node.resolveConstructorBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return true;
			}
			
			
			@Override
			public boolean visit(SuperConstructorInvocation node) {
				IBinding bind = node.resolveConstructorBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(SuperMethodInvocation node) {
				IBinding bind = node.resolveMethodBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				IBinding bind = node.resolveMethodBinding();
				if (bind == null) {
					return false;
				} else {
					try {
						this.addReferenceOfNode(bind.getJavaElement(), toRange(node.getStartPosition(), node.getLength()));
					} catch (Exception e) {
						return false;
					}
				}
				return true;
			}

			private void addReferenceOfNode(IJavaElement element, Range range) {
				try {
					Reference reference;
					Location rawLocation = JDTUtils.toLocation(textDocument.getUri());
					rawLocation.setRange(range);
					if (element != null) {
						Location location = JDTUtils.toLocation(element);
						if (element instanceof IMember && ((IMember) element).getClassFile() != null) {
							location = JDTUtils.toLocation(((IMember) element).getClassFile());
						}
						if (location != null) {
							reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(location));
						} else {
							String qname = JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
							reference = new Reference(ReferenceCategory.UNCATEGORIZED, rawLocation, new SymbolLocator(QnameHelper.getSimplifiedQname(qname), DocumentSymbolHandler.mapKind(element)));
						}
						references.add(reference);
					}
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					ElasticJavaLanguageServerPlugin.logException("Find references failure ", e);
				}
			}

			private Range toRange(int start, int length) {
				Range range = JDTUtils.newRange();
				this.setPosition(range.getStart(), start);
				this.setPosition(range.getEnd(), start + length);
				return range;
			}

			private void setPosition(Position position, int offset) {
				int line = ast.getLineNumber(offset) - 1;
				int column = ast.getColumnNumber(offset);
				position.setLine(line);
				position.setCharacter(column);
			}
		});	
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

}
