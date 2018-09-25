package org.elastic.jdt.ls.core.internal;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;

public class SymbolLocator {

	private String qname;

	private SymbolKind symbolKind;

	// In repo file path for the symbol, TODO we may not need this because if qname could serve its purpose
	private String path;

	// if location is provided for external source, then use this
	private Location location;


	public SymbolLocator(String qname, SymbolKind symbolKind) {
		this.setQname(qname);
		this.setSymbolKind(symbolKind);
	}

	public SymbolLocator(String qname, SymbolKind symbolKind, String path) {
		this.setQname(qname);
		this.setSymbolKind(symbolKind);
		this.setPath(path);
	}

	public SymbolLocator(Location location) {
		this.setLocation(location);
	}

	public SymbolLocator(String path, Location location) {
		this.setPath(path);
		this.setLocation(location);
	}

	public SymbolLocator(String qname, SymbolKind symbolKind, String path, Location location) {
		this.setQname(qname);
		this.setSymbolKind(symbolKind);
		this.setPath(path);
		this.setLocation(location);
	}


	public void setQname(String qname) {
		this.qname = qname;
	}

	public String getQname() {
		return this.qname;
	}


	public void setSymbolKind(SymbolKind symbolKind) {
		this.symbolKind = symbolKind;
	}

	public SymbolKind getSymbolKind() {
		return this.symbolKind;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public Location getLocation() {
		return this.location;
	}

}
