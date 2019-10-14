package org.elastic.jdt.ls.core.internal;

import static org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin.logInfo;
import static org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin.logException;
import static org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin.getBundleContext;

import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.lang3.reflect.MethodUtils;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.CancellableProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.handlers.InitHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.elastic.jdt.ls.core.internal.EDefinitionHandler;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

import org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin;

public class ElasticJDTLanguageServer extends JDTLanguageServer {

	private static final int FORCED_EXIT_CODE = 1;

	private ProjectsManager pm;

	private PreferenceManager preferenceManager;

	public ElasticJDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		super(JavaLanguageServerPlugin.getProjectsManager(), JavaLanguageServerPlugin.getPreferencesManager());
		this.pm = JavaLanguageServerPlugin.getProjectsManager();
		this.preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		logInfo("Elastic Java Language Server version: " + ElasticJavaLanguageServerPlugin.getVersion());

		// if we enable Gradle build system support and security is enabled, we need update Gradle-specific permissions
		Map<?, ?> initializationOptions = this.getInitializationOptions(params);
		if (isImportGradleEnabled(initializationOptions)) {
			SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				SecurityPermissionHelper.updateGradlePermissions(getBundleContext());
			}
		}

		CompletableFuture<InitializeResult> result = super.initialize(params);
		BuildPathHelper pathHelper = new BuildPathHelper(ResourceUtils.canonicalFilePathFromURI(params.getRootUri()), super.getClientConnection());
		pathHelper.IncludeAllJavaFiles();
		// change this method to a synchronized way
		try {
			Job.getJobManager().join(InitHandler.JAVA_LS_INITIALIZATION_JOBS, null);
		} catch (OperationCanceledException | InterruptedException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return result;
	}

	@Override
	public void initialized(InitializedParams params) {
		logInfo(">> initialized");
		if (preferenceManager.getClientPreferences().isCompletionDynamicRegistered()) {
			registerCapability(Preferences.COMPLETION_ID, Preferences.COMPLETION, CompletionHandler.DEFAULT_COMPLETION_OPTIONS);
		}
		if (preferenceManager.getClientPreferences().isWorkspaceSymbolDynamicRegistered()) {
			registerCapability(Preferences.WORKSPACE_SYMBOL_ID, Preferences.WORKSPACE_SYMBOL);
		}
		if (preferenceManager.getClientPreferences().isDocumentSymbolDynamicRegistered()) {
			registerCapability(Preferences.DOCUMENT_SYMBOL_ID, Preferences.DOCUMENT_SYMBOL);
		}
		if (preferenceManager.getClientPreferences().isDefinitionDynamicRegistered()) {
			registerCapability(Preferences.DEFINITION_ID, Preferences.DEFINITION);
		}
		if (preferenceManager.getClientPreferences().isTypeDefinitionDynamicRegistered()) {
			registerCapability(Preferences.TYPEDEFINITION_ID, Preferences.TYPEDEFINITION);
		}
		if (preferenceManager.getClientPreferences().isHoverDynamicRegistered()) {
			registerCapability(Preferences.HOVER_ID, Preferences.HOVER);
		}
		if (preferenceManager.getClientPreferences().isReferencesDynamicRegistered()) {
			registerCapability(Preferences.REFERENCES_ID, Preferences.REFERENCES);
		}
		if (preferenceManager.getClientPreferences().isDocumentHighlightDynamicRegistered()) {
			registerCapability(Preferences.DOCUMENT_HIGHLIGHT_ID, Preferences.DOCUMENT_HIGHLIGHT);
		}
		if (preferenceManager.getClientPreferences().isFoldgingRangeDynamicRegistered()) {
			registerCapability(Preferences.FOLDINGRANGE_ID, Preferences.FOLDINGRANGE);
		}
		if (preferenceManager.getClientPreferences().isWorkspaceFoldersSupported()) {
			registerCapability(Preferences.WORKSPACE_CHANGE_FOLDERS_ID, Preferences.WORKSPACE_CHANGE_FOLDERS);
		}
		if (preferenceManager.getClientPreferences().isImplementationDynamicRegistered()) {
			registerCapability(Preferences.IMPLEMENTATION_ID, Preferences.IMPLEMENTATION);
		}
		try {
			MethodUtils.invokeMethod((Object)JavaLanguageServerPlugin.getInstance().getProtocol(), "syncCapabilitiesToSettings");
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			logException(e.getMessage(), e);
		}
		Job initializeWorkspace = new Job("Initialize workspace") {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				try {
					JobHelpers.waitForBuildJobs(60 * 60 * 1000); // 1 hour
					logInfo(">> build jobs finished");
				} catch (OperationCanceledException e) {
					logException(e.getMessage(), e);
					return Status.CANCEL_STATUS;
				}
				return Status.OK_STATUS;
			}
		};
		initializeWorkspace.setPriority(Job.BUILD);
		initializeWorkspace.setSystem(true);
		initializeWorkspace.schedule();
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		logInfo(">> java/didChangeWorkspaceFolders");
		SynchronizedWorkspaceFolderChangeHandler handler = new SynchronizedWorkspaceFolderChangeHandler(pm);
		handler.update(params);
		BuildPathHelper pathHelper = new BuildPathHelper(ResourceUtils.canonicalFilePathFromURI(params.getEvent().getAdded().get(0).getUri()), super.getClientConnection());
		pathHelper.IncludeAllJavaFiles();
		try {
			Job.getJobManager().join(InitHandler.JAVA_LS_INITIALIZATION_JOBS, null);
		} catch (OperationCanceledException | InterruptedException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}
	
	@Override
	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
		setActiveElementInASTProvider(JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri()));
		boolean hierarchicalDocumentSymbolSupported = preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported();
		DocumentSymbolHandler handler = new DocumentSymbolHandler(hierarchicalDocumentSymbolSupported);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
                	return handler.documentSymbol(params, monitor);
		});
	}
	
	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		logInfo(">> document/hover");
		ExtendedHoverHandler handler = new ExtendedHoverHandler(this.preferenceManager);
		setActiveElementInASTProvider(JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri()));
		return computeAsync((monitor) -> handler.extendedHover(position, monitor));
	}
	
	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		logInfo(">> document/references");
		ExtendedReferencesHandler handler = new ExtendedReferencesHandler(this.preferenceManager);
		setActiveElementInASTProvider(JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri()));
		return computeAsync((monitor) -> handler.findReferences(params, monitor));
	}
	
	@JsonRequest(value = "textDocument/full", useSegment = false)
	public CompletableFuture<Full> full(FullParams fullParams) {
		logInfo(">> document/full");
		FullHandler handler;
		handler = new FullHandler(this.preferenceManager);
		setActiveElementInASTProvider(JDTUtils.resolveCompilationUnit(fullParams.getTextDocumentIdentifier().getUri()));
		return computeAsync((monitor) -> handler.full(fullParams, monitor));
	}

	@JsonRequest(value = "textDocument/edefinition", useSegment = false)
	public CompletableFuture<SymbolLocator> eDefinition(TextDocumentPositionParams position) {
		logInfo(">> document/edefinition");
		EDefinitionHandler handler = new EDefinitionHandler(this.preferenceManager);
		setActiveElementInASTProvider(JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri()));
		return computeAsync((monitor) -> handler.eDefinition(position, monitor));
	}

	@Override
	public void exit() {
		logInfo(">> exit");
		org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin.getLanguageServer().exit();
		Executors.newSingleThreadScheduledExecutor().schedule(() -> {
			logInfo("Forcing exit after 1 min.");
			System.exit(FORCED_EXIT_CODE);
		}, 1, TimeUnit.MINUTES);
	}

	private Map<?, ?> getInitializationOptions(InitializeParams params) {
		Map<?, ?> initOptions = JSONUtility.toModel(params.getInitializationOptions(), Map.class);
		return initOptions == null ? Collections.emptyMap() : initOptions;
	}
	
	private boolean isImportGradleEnabled(Map<?, ?> initOptions) {
		if (initOptions.get(InitHandler.SETTINGS_KEY) instanceof Map) {
			Object settings = initOptions.get(InitHandler.SETTINGS_KEY);
			return MapFlattener.getBoolean((Map<String, Object>) settings, Preferences.IMPORT_GRADLE_ENABLED);
		}
		return false;
	}

	private <R> CompletableFuture<R> computeAsync(Function<IProgressMonitor, R> code) {
		return CompletableFutures.computeAsync(cc -> {
			return AccessController.doPrivileged(new PrivilegedAction<R>() {
				public R run() {
					return code.apply(toMonitor(cc));
				}
			});
		});
	}
	private IProgressMonitor toMonitor(CancelChecker checker) {
		return new CancellableProgressMonitor(checker);
	}
	
	private void setActiveElementInASTProvider(ICompilationUnit unit) {
		if (unit != null) {
			CoreASTProvider.getInstance().setActiveJavaElement(unit);
		}
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
