package org.eclipse.jdt.ls.core.internal;

import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

@SuppressWarnings("all")
public class Full {

	private List<DetailSymbolInformation> symbols;
	private List<Reference> references;

	public Full(List<DetailSymbolInformation> symbols, List<Reference> references) {
		this.setSymbols(symbols);
		this.setReferences(references);
	}

	public void setReferences(List<Reference> references) {
		this.references = references;
	}

	public List<Reference> getReferences() {
		return this.references;
	}

	public void setSymbols(List<DetailSymbolInformation> symbols) {
		this.symbols = symbols;
	}

	public List<DetailSymbolInformation> getSymbols() {
		return this.symbols;
	}

}