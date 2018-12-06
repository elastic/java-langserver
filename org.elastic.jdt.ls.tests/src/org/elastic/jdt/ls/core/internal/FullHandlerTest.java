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
package org.elastic.jdt.ls.core.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.TextDocumentIdentifier;
// import org.elastic.jdt.ls.core.internal.Full;
// import org.elastic.jdt.ls.core.internal.FullHandler;
// import org.elastic.jdt.ls.core.internal.FullParams;
// import org.elastic.jdt.ls.core.internal.WorkspaceHelper;
import org.elastic.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;

/**
 * @author poytr1
 *
 */
public class FullHandlerTest extends AbstractProjectsManagerBasedTest {

	//	private static String FULL_TEMPLATE =
	//			"{\n" +
	//					"    \"id\": \"1\",\n" +
	//					"    \"method\": \"textDocument/full\",\n" +
	//					"    \"params\": {\n" +
	//					"        \"textDocument\": {\n" +
	//					"            \"uri\": \"${file}\"\n" +
	//					"        },\n" +
	//					"		 \"reference\": false\n" +
	//					"    },\n" +
	//					"    \"jsonrpc\": \"2.0\"\n" +
	//					"}";

	private FullHandler handler;

	private IProject project;

	private IPackageFragmentRoot sourceFolder;

	private PreferenceManager preferenceManager;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new FullHandler(preferenceManager);
	}

	@Test
	public void testFullWithoutRef() throws Exception {
		//given
		//fulls on the System.out
		FullParams fullParams = getFullParams("src/java/Foo.java", false);

		//when
		Full full = handler.full(fullParams, monitor);

		//then
		assertNotNull(full);
		assertNotSame(0, full.getSymbols().size());
		assertSame(0, full.getReferences().size());
	}

	@Test
	public void testFullWithRef() throws Exception {
		//given
		//fulls on the System.out
		FullParams fullParams = getFullParams("src/java/Foo.java", true);

		//when
		Full full = handler.full(fullParams, monitor);

		//then
		assertNotNull(full);
		assertNotSame(0, full.getSymbols().size());
		assertNotSame(0, full.getReferences().size());
	}

	FullParams getFullParams(String file, boolean isRef) {
		URI uri = project.getFile(file).getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(fileURI);
		return new FullParams(textDocument, isRef);
	}

	//
	//	String createFullRequest(String file) {
	//		URI uri = project.getFile(file).getRawLocationURI();
	//		return createFullRequest(uri);
	//	}
	//
	//	String createFullRequest(URI file) {
	//		String fileURI = ResourceUtils.fixURI(file);
	//		return FULL_TEMPLATE.replace("${file}", fileURI);
	//	}

}
