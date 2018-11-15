package org.elastic.jdt.ls.core.internal;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.handlers.BundleUtils;
import org.eclipse.jdt.ls.core.internal.handlers.SignatureHelpHandler;
import org.eclipse.jdt.ls.core.internal.handlers.WorkspaceDiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.WorkspaceExecuteCommandHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;

/**
 * Handler for the VS Code extension initialization
 */
final public class SynchronizedInitHandler {

    public static final String JAVA_LS_INITIALIZATION_JOBS = "java-ls-initialization-jobs";
    private static final String BUNDLES_KEY = "bundles";
    public static final String SETTINGS_KEY = "settings";

    private ProjectsManager projectsManager;
    private PreferenceManager preferenceManager;
    private static WorkspaceDiagnosticsHandler workspaceDiagnosticsHandler;

    public SynchronizedInitHandler(ProjectsManager manager, PreferenceManager preferenceManager) {
        this.projectsManager = manager;
        this.preferenceManager = preferenceManager;
    }

    @SuppressWarnings("unchecked")
    InitializeResult initialize(InitializeParams param, IProgressMonitor monitor) {
        logInfo("Initializing Java Language Server " + JavaLanguageServerPlugin.getVersion());
        Map<?, ?> initializationOptions = this.getInitializationOptions(param);
        Map<String, Object> extendedClientCapabilities = getInitializationOption(initializationOptions, "extendedClientCapabilities", Map.class);
        if (param.getCapabilities() == null) {
            preferenceManager.updateClientPrefences(new ClientCapabilities(), extendedClientCapabilities);
        } else {
            preferenceManager.updateClientPrefences(param.getCapabilities(), extendedClientCapabilities);
        }


        Collection<IPath> rootPaths = new ArrayList<>();
        Collection<String> workspaceFolders = getInitializationOption(initializationOptions, "workspaceFolders", Collection.class);
        if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
            for (String uri : workspaceFolders) {
                IPath filePath = ResourceUtils.filePathFromURI(uri);
                if (filePath != null) {
                    rootPaths.add(filePath);
                }
            }
        } else {
            String rootPath = param.getRootUri();
            if (rootPath == null) {
                rootPath = param.getRootPath();
                if (rootPath != null) {
                    logInfo("In LSP 3.0, InitializeParams.rootPath is deprecated in favour of InitializeParams.rootUri!");
                }
            }
            if (rootPath != null) {
                IPath filePath = ResourceUtils.filePathFromURI(rootPath);
                if (filePath != null) {
                    rootPaths.add(filePath);
                }
            }
        }
        if (rootPaths.isEmpty()) {
            IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation();
            logInfo("No workspace folders or root uri was defined. Falling back on " + workspaceLocation);
            rootPaths.add(workspaceLocation);
        }
        if (initializationOptions.get(SETTINGS_KEY) instanceof Map) {
            Object settings = initializationOptions.get(SETTINGS_KEY);
            @SuppressWarnings("unchecked")
            Preferences prefs = Preferences.createFrom((Map<String, Object>) settings);
            preferenceManager.update(prefs);
        }
        preferenceManager.getPreferences().setRootPaths(rootPaths);
        triggerInitialization(rootPaths, monitor);
        Integer processId = param.getProcessId();
        if (processId != null) {
            JavaLanguageServerPlugin.getLanguageServer().setParentProcessId(processId.longValue());
        }
        try {
            Collection<String> bundleList = getInitializationOption(initializationOptions, BUNDLES_KEY, Collection.class);
            BundleUtils.loadBundles(bundleList);
        } catch (CoreException e) {
            // The additional plug-ins should not affect the main language server loading.
            JavaLanguageServerPlugin.logException("Failed to load extension bundles ", e);
        }
        InitializeResult result = new InitializeResult();
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setCompletionProvider(new CompletionOptions(Boolean.TRUE, Arrays.asList(".", "@", "#")));
        if (!preferenceManager.getClientPreferences().isFormattingDynamicRegistrationSupported()) {
            capabilities.setDocumentFormattingProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isRangeFormattingDynamicRegistrationSupported()) {
            capabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isOnTypeFormattingDynamicRegistrationSupported()) {
            capabilities.setDocumentOnTypeFormattingProvider(new DocumentOnTypeFormattingOptions(";", Arrays.asList("\n", "}")));
        }
        if (!preferenceManager.getClientPreferences().isCodeLensDynamicRegistrationSupported()) {
            capabilities.setCodeLensProvider(new CodeLensOptions(true));
        }
        if (!preferenceManager.getClientPreferences().isSignatureHelpDynamicRegistrationSupported()) {
            capabilities.setSignatureHelpProvider(SignatureHelpHandler.createOptions());
        }
        if (!preferenceManager.getClientPreferences().isRenameDynamicRegistrationSupported()) {
            capabilities.setRenameProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isCodeActionDynamicRegistered()) {
            capabilities.setCodeActionProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isExecuteCommandDynamicRegistrationSupported()) {
            Set<String> commands = WorkspaceExecuteCommandHandler.getCommands();
            capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(new ArrayList<>(commands)));
        }
        if (!preferenceManager.getClientPreferences().isWorkspaceSymbolDynamicRegistered()) {
            capabilities.setWorkspaceSymbolProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isDocumentSymbolDynamicRegistered()) {
            capabilities.setDocumentSymbolProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isDefinitionDynamicRegistered()) {
            capabilities.setDefinitionProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isTypeDefinitionDynamicRegistered()) {
            capabilities.setTypeDefinitionProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isHoverDynamicRegistered()) {
            capabilities.setHoverProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isReferencesDynamicRegistered()) {
            capabilities.setReferencesProvider(Boolean.TRUE);
        }
        if (!preferenceManager.getClientPreferences().isDocumentHighlightDynamicRegistered()) {
            capabilities.setDocumentHighlightProvider(Boolean.TRUE);
        }
        TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
        textDocumentSyncOptions.setOpenClose(Boolean.TRUE);
        textDocumentSyncOptions.setSave(new SaveOptions(Boolean.TRUE));
        textDocumentSyncOptions.setChange(TextDocumentSyncKind.Incremental);
        if (preferenceManager.getClientPreferences().isWillSaveRegistered()) {
            textDocumentSyncOptions.setWillSave(Boolean.TRUE);
        }

        if (preferenceManager.getClientPreferences().isWillSaveWaitUntilRegistered()) {
            textDocumentSyncOptions.setWillSaveWaitUntil(Boolean.TRUE);
        }
        capabilities.setTextDocumentSync(textDocumentSyncOptions);

        WorkspaceServerCapabilities wsCapabilities = new WorkspaceServerCapabilities();
        WorkspaceFoldersOptions wsFoldersOptions = new WorkspaceFoldersOptions();
        wsFoldersOptions.setSupported(Boolean.TRUE);
        wsFoldersOptions.setChangeNotifications(Boolean.TRUE);
        wsCapabilities.setWorkspaceFolders(wsFoldersOptions);
        capabilities.setWorkspace(wsCapabilities);

        result.setCapabilities(capabilities);
        return result;
    }

    private void triggerInitialization(Collection<IPath> roots, IProgressMonitor monitor) {
        long start = System.currentTimeMillis();
        SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
        try {
            projectsManager.setAutoBuilding(false);
            projectsManager.initializeProjects(roots, subMonitor);
            JavaLanguageServerPlugin.logInfo("Workspace initialized in " + (System.currentTimeMillis() - start) + "ms");
        } catch (OperationCanceledException e) {
        } catch (Exception e) {
            JavaLanguageServerPlugin.logException("Initialization failed ", e);
        }
    }

    private Map<?, ?> getInitializationOptions(InitializeParams params) {
        Map<?, ?> initOptions = JSONUtility.toModel(params.getInitializationOptions(), Map.class);
        return initOptions == null ? Collections.emptyMap() : initOptions;
    }

    private <T> T getInitializationOption(Map<?, ?> initializationOptions, String key, Class<T> clazz) {
        if (initializationOptions != null) {
            Object bundleObject = initializationOptions.get(key);
            if (clazz.isInstance(bundleObject)) {
                return clazz.cast(bundleObject);
            }
        }
        return null;
    }
}
