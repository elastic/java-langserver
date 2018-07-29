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

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * @author poytr1
 *
 */
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
