package org.eclipse.jdt.ls.core.internal;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;


public class FullParams {
	@NonNull
	private TextDocumentIdentifier textDocument;

	public FullParams(TextDocumentIdentifier textDocument) {
		this.setTextDocumentIdentifier(textDocument);
	}

	public void setTextDocumentIdentifier(TextDocumentIdentifier textDocument) {
		this.textDocument = textDocument;
	}

	public TextDocumentIdentifier getTextDocumentIdentifier() {
		return this.textDocument;
	}

}
