package org.elastic.jdt.ls.core.internal;

import static org.elastic.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.CancellableProgressMonitor;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.elastic.jdt.ls.core.internal.EDefinitionHandler;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public class ExtendedJDTLanguageServer extends JDTLanguageServer {

	private static final int FORCED_EXIT_CODE = 1;

	private ProjectsManager pm;

	private PreferenceManager preferenceManager;

	public ExtendedJDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		super(projects, preferenceManager);
		this.pm = projects;
		this.preferenceManager = preferenceManager;
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		logInfo(">> java/didChangeWorkspaceFolders");
		SynchronizedWorkspaceFolderChangeHandler handler = new SynchronizedWorkspaceFolderChangeHandler(pm);
		handler.update(params);
	}

	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		logInfo(">> document/hover");
		ExtendedHoverHandler handler = new ExtendedHoverHandler(this.preferenceManager);
		return computeAsync((monitor) -> handler.extendedHover(position, monitor));
	}
	
	@JsonRequest(value = "textDocument/full", useSegment = false)
	public CompletableFuture<Full> full(FullParams fullParams) {
		logInfo(">> document/full");
		FullHandler handler;
		handler = new FullHandler(this.preferenceManager);
		return computeAsync((monitor) -> handler.full(fullParams, monitor));
	}

	@JsonRequest(value = "textDocument/edefinition", useSegment = false)
	public CompletableFuture<SymbolLocator> eDefinition(TextDocumentPositionParams position) {
		logInfo(">> document/edefinition");
		EDefinitionHandler handler = new EDefinitionHandler(this.preferenceManager);
		return computeAsync((monitor) -> handler.eDefinition(position, monitor));
	}

	@Override
	public void exit() {
		logInfo(">> exit");
		JavaLanguageServerPlugin.getLanguageServer().exit();
		Executors.newSingleThreadScheduledExecutor().schedule(() -> {
			logInfo("Forcing exit after 1 min.");
			System.exit(FORCED_EXIT_CODE);
		}, 1, TimeUnit.MINUTES);
	}

	private <R> CompletableFuture<R> computeAsync(Function<IProgressMonitor, R> code) {
		return CompletableFutures.computeAsync(cc -> code.apply(toMonitor(cc)));
	}
	private IProgressMonitor toMonitor(CancelChecker checker) {
		return new CancellableProgressMonitor(checker);
	}

	private void waitForLifecycleJobs(IProgressMonitor monitor) {
		try {
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		} catch (OperationCanceledException ignorable) {
			// No need to pollute logs when query is cancelled
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}
}
