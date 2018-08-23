package org.elastic.jdt.ls.core.internal;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.CancellableProgressMonitor;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public class ExtendedJDTLanguageServer extends JDTLanguageServer {
	
	private PreferenceManager preferenceManager;
	
	public ExtendedJDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		super(projects, preferenceManager);
		this.preferenceManager = preferenceManager;
	}
	
	@JsonRequest(value = "textDocument/full", useSegment = false)
	public CompletableFuture<Full> full(FullParams fullParams) {
		logInfo(">> document/full");
		FullHandler handler;
		handler = new FullHandler(this.preferenceManager);
		return computeAsync((monitor) -> handler.full(fullParams, monitor));
	}
	
	private <R> CompletableFuture<R> computeAsync(Function<IProgressMonitor, R> code) {
		return CompletableFutures.computeAsync(cc -> code.apply(toMonitor(cc)));
	}
	
	private IProgressMonitor toMonitor(CancelChecker checker) {
		return new CancellableProgressMonitor(checker);
	}
}
