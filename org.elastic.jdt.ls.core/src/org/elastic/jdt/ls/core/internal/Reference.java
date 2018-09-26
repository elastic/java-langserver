package org.elastic.jdt.ls.core.internal;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;


public class Reference {
	@NonNull
	private ReferenceCategory category;
	@NonNull
	private Location location;
	@NonNull
	private SymbolInformation referencedSymbol;

	private SymbolLocator target;

	public Reference(@NonNull final ReferenceCategory category, @NonNull final Location location, @NonNull final SymbolInformation referencedSymbol) {
		this.setCategory(category);
		this.setLocation(location);
		this.setReferenceSymbol(referencedSymbol);
	}

	public Reference(@NonNull final ReferenceCategory category, @NonNull final Location location, SymbolLocator target) {
		this.setCategory(category);
		this.setLocation(location);
		this.setTarget(target);
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

	public void setTarget(SymbolLocator target) {
		this.target = target;
	}

	public SymbolLocator getTarget() {
		return this.target;
	}
}