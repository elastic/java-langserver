package org.elastic.jdt.ls.core.internal;
import java.util.List;


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