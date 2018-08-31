package org.elastic.jdt.ls.core.internal;

import java.util.List;

import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;


public class DetailSymbolInformation {
	@NonNull
	private SymbolInformation symbolInformation;

	// optional
	private Either<List<Either<String, MarkedString>>, MarkupContent> contents;
	
	// optional 
	private Range range;

	public DetailSymbolInformation(@NonNull final SymbolInformation symbolInformation) {
		this.setSymbolInformation(symbolInformation);
	}

	public DetailSymbolInformation(@NonNull final SymbolInformation symbolInformation, final MarkupContent contents) {
		this.setContents(contents);
		this.setSymbolInformation(symbolInformation);
	}

	public DetailSymbolInformation(@NonNull final SymbolInformation symbolInformation, final List<Either<String, MarkedString>> contents) {
		this.setContents(contents);
		this.setSymbolInformation(symbolInformation);
	}
	
	public DetailSymbolInformation(@NonNull final SymbolInformation symbolInformation, final List<Either<String, MarkedString>> contents, final Range range) {
		this.setContents(contents);
		this.setSymbolInformation(symbolInformation);
		this.setRange(range);
	}

	public void setSymbolInformation(@NonNull final SymbolInformation symbolInformation) {
		this.symbolInformation = symbolInformation;
	}

	@NonNull
	public SymbolInformation getSymbolInformation() {
		return this.symbolInformation;
	}

	public void setContents(final MarkupContent contents) {
		this.contents = Either.forRight(contents);
	}

	public void setContents(final List<Either<String, MarkedString>> contents) {
		this.contents = Either.forLeft(contents);
	}

	public Either<List<Either<String, MarkedString>>, MarkupContent> getContents() {
		return this.contents;
	}
	
	public void setRange(Range range) {
		this.range = range;
	}
	
	public Range getRange() {
		return this.range;
	}

}
