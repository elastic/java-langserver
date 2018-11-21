package org.elastic.jdt.ls.core.internal;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;
import static org.eclipse.jdt.ls.core.internal.handlers.InitHandler.JAVA_LS_INITIALIZATION_JOBS;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.ls.core.internal.CancellableProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.elastic.jdt.ls.core.internal.EDefinitionHandler;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public class ExtendedJDTLanguageServer extends JDTLanguageServer {

	private ProjectsManager pm;

	private PreferenceManager preferenceManager;

	private final CountDownLatch countDownLatch = new CountDownLatch(1);

	public ExtendedJDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		super(projects, preferenceManager);
		this.pm = projects;
		this.preferenceManager = preferenceManager;
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		logInfo(">> initialize");
		CompletableFuture<InitializeResult> result = super.initialize(params);
		Job[] initialJobs = Job.getJobManager().find(JAVA_LS_INITIALIZATION_JOBS);
		// should exist one initial job
		if (initialJobs.length == 1) {
			initialJobs[0].addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent event) {
					countDownLatch.countDown();
				}
			});
			JavaLanguageServerPlugin.logInfo("initial job begins at " + System.currentTimeMillis() + "ms");
			// if there's an initial job, main thread will wait until initial job done
			try {
				countDownLatch.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			JavaLanguageServerPlugin.logInfo("initial job ends at " + System.currentTimeMillis() + "ms");
		}
		return result;
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

	@Override
	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		logInfo(">> document/definition");
		ExtendedNavigateToDefinitionHandler handler = new ExtendedNavigateToDefinitionHandler(this.preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return handler.extendedDefinition(position, monitor);
		});
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
