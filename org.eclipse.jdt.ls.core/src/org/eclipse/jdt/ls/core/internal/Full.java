/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * @author poytr1
 *
 */
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

class DetailSymbolInformation {
	@NonNull
	private SymbolInformation symbolInformation;

	// optional
	private Either<List<Either<String, MarkedString>>, MarkupContent> contents;

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

}

enum ReferenceCategory {
	UNCATEGORIZED, READ, WRITE, INHERIT, IMPLEMENT
}

class Reference {
	@NonNull
	private ReferenceCategory category;
	@NonNull
	private Location location;
	@NonNull
	private SymbolInformation referencedSymbol;

	public Reference(@NonNull final ReferenceCategory category, @NonNull final Location location, @NonNull final SymbolInformation referencedSymbol) {
		this.setCategory(category);
		this.setLocation(location);
		this.setReferenceSymbol(referencedSymbol);
	}

	public void setCategory(@NonNull final ReferenceCategory category) {
		this.category = category;
	}

	@NonNull
	public ReferenceCategory getCategory() {
		return this.category;
	}

	public void setLocation(@NonNull final Location location) {
		this.location = location;
	}

	@NonNull
	public Location getLocation() {
		return this.location;
	}

	public void setReferenceSymbol(@NonNull final SymbolInformation referencedSymbol) {
		this.referencedSymbol = referencedSymbol;
	}

	@NonNull
	public SymbolInformation getReferencedSymbol() {
		return this.referencedSymbol;
	}

}