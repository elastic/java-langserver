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
package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.Full;
import org.eclipse.jdt.ls.core.internal.FullParams;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * @author poytr1
 *
 */
public class FullHandler {

	private final PreferenceManager preferenceManager;

	public FullHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Full full(FullParams fullParams, IProgressMonitor monitor) {
		String uri = fullParams.getTextDocumentIdentifier().getUri();
		return new Full(null, null);
	}

}
