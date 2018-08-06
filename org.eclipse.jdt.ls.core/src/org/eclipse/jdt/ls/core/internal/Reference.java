package org.eclipse.jdt.ls.core.internal;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Pure;


public class Reference {
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