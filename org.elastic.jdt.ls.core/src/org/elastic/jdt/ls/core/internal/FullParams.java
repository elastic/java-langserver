package org.elastic.jdt.ls.core.internal;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;


public class FullParams {
	@NonNull
	private TextDocumentIdentifier textDocument;
	
	private boolean reference;

	public FullParams(TextDocumentIdentifier textDocument) {
		this.setTextDocumentIdentifier(textDocument);
	}

	public void setTextDocumentIdentifier(TextDocumentIdentifier textDocument) {
		this.textDocument = textDocument;
	}

	public TextDocumentIdentifier getTextDocumentIdentifier() {
		return this.textDocument;
	}

	public boolean isReference() {
		return reference;
	}

	public void setReference(boolean reference) {
		this.reference = reference;
	}

}
