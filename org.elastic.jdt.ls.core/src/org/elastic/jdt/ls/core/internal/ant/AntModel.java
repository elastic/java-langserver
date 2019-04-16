package org.elastic.jdt.ls.core.internal.ant;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Main;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskAdapter;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelperRepository;
import org.apache.tools.ant.Target;
import org.eclipse.ant.internal.core.AntClassLoader;
import org.eclipse.ant.internal.core.AntCoreUtil;
import org.eclipse.ant.internal.core.AntSecurityManager;
import org.eclipse.ant.internal.core.IAntCoreConstants;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.ant.core.AntCorePreferences;
import org.eclipse.ant.core.AntSecurityException;
import org.eclipse.ant.core.Property;
import org.eclipse.ant.core.Type;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ISynchronizable;
import org.elastic.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.xml.sax.Attributes;

public class AntModel implements IAntModel {
	
	private static ClassLoader fgClassLoader;
	private static int fgInstanceCount = 0;
	private static Object loaderLock = new Object();
	
	private IDocument fDocument;
	private LocationProvider fLocationProvider;
	
	private AntProjectNode fProjectNode;
	private AntTargetNode fCurrentTargetNode;
	private AntElementNode fLastNode;
	private AntElementNode fNodeBeingResolved;
	private int fNodeBeingResolvedIndex = -1;

	/**
	 * Stack of still open elements.
	 * <P>
	 * On top of the stack is the innermost element.
	 */
	private Stack<AntElementNode> fStillOpenElements = new Stack<>();

	private Map<Task, AntTaskNode> fTaskToNode = new HashMap<>();

	private List<AntTaskNode> fTaskNodes = new ArrayList<>();
	
	private final Object fDirtyLock = new Object();
	private boolean fIsDirty = true;
	
	private boolean fHasLexicalInfo = true;
	private boolean fHasPositionInfo = true;
	private boolean fHasTaskInfo = true;
	
	private ClassLoader fLocalClassLoader = null;
	
	private boolean fShouldReconcile = true;
	
	private Map<String, String> fProperties = null;
	private List<String> fPropertyFiles = null;
	
	private Map<String, String> fDefinersToText;
	private Map<String, String> fPreviousDefinersToText;
	private Map<String, List<String>> fDefinerNodeIdentifierToDefinedTasks;
	private Map<String, String> fCurrentNodeIdentifiers;
	private HashMap<String, String> fNamespacePrefixMappings;
	private List<AntElementNode> fNonStructuralNodes = new ArrayList<>(1);
	
	public AntModel(IDocument document, LocationProvider locationProvider, boolean resolveLexicalInfo, boolean resolvePositionInfo, boolean resolveTaskInfo) {
		fDocument = document;
		fLocationProvider = locationProvider;
		fHasLexicalInfo = resolveLexicalInfo;
		fHasPositionInfo = resolvePositionInfo;
		fHasTaskInfo = resolveTaskInfo;
	}
	
	ProjectHelper getProjectHelper() {
		Iterator<org.apache.tools.ant.ProjectHelper> helpers = ProjectHelperRepository.getInstance().getHelpers();
		while (helpers.hasNext()) {
			org.apache.tools.ant.ProjectHelper helper = helpers.next();
			if (helper instanceof ProjectHelper) {
				return (ProjectHelper) helper;
			}
		}
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.IAntModel#reconcile()
	 */
	@Override
	public void reconcile() {
		synchronized (fDirtyLock) {
			if (!fShouldReconcile || !fIsDirty) {
				return;
			}
			fIsDirty = false;
		}

		synchronized (getLockObject()) {
			if (fLocationProvider == null) {
				// disposed
				return;
			}

			if (fDocument == null) {
				fProjectNode = null;
			} else {
				reset();
				parseDocument(fDocument);
				reconcileTaskAndTypes();
			}
			AntModelCore.getDefault().notifyAntModelListeners(new AntModelChangeEvent(this));
		}
	}
	
	private void reset() {
		fCurrentTargetNode = null;
		fStillOpenElements = new Stack<>();
		fTaskToNode = new HashMap<>();
		fTaskNodes = new ArrayList<>();
		fNodeBeingResolved = null;
		fNodeBeingResolvedIndex = -1;
		fLastNode = null;
		fCurrentNodeIdentifiers = null;
		fNamespacePrefixMappings = null;

		fNonStructuralNodes = new ArrayList<>(1);
		if (fDefinersToText != null) {
			fPreviousDefinersToText = new HashMap<>(fDefinersToText);
			fDefinersToText = null;
		}
	}
	
	public void setClassLoader(URLClassLoader loader) {
		AntDefiningTaskNode.setJavaClassPath(loader.getURLs());
		fLocalClassLoader = loader;
	}
	
	public int getLine(int offset) {
		try {
			return fDocument.getLineOfOffset(offset) + 1;
		}
		catch (BadLocationException be) {
			return -1;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.IAntModel#getProjectNode(boolean)
	 */
	@Override
	public AntProjectNode getProjectNode(boolean doReconcile) {
		if (doReconcile) {
			synchronized (getLockObject()) { // ensure to wait for any current synchronization
				reconcile();
			}
		}
		return fProjectNode;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.IAntModel#getProjectNode()
	 */
	@Override
	public AntProjectNode getProjectNode() {
		return getProjectNode(true);
	}

	
	private Object getLockObject() {
		if (fDocument instanceof ISynchronizable) {
			Object lock = ((ISynchronizable) fDocument).getLockObject();
			if (lock != null) {
				return lock;
			}
		}
		return this;
	}
	
	private void parseDocument(IDocument input) {
		boolean parsed = true;
		if (input.getLength() == 0) {
			fProjectNode = null;
			parsed = false;
			return;
		}
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader parsingClassLoader = getClassLoader(originalClassLoader);
		Thread.currentThread().setContextClassLoader(parsingClassLoader);
		Project project = null;
		try {
			ProjectHelper projectHelper = null;
			String textToParse = input.get();
			if (fProjectNode == null || !fProjectNode.hasChildren()) {
				fProjectNode = null;
				project = new AntModelProject();
				projectHelper = prepareForFullParse(project, parsingClassLoader);
			} else {
				project = fProjectNode.getProject();
				projectHelper = (ProjectHelper) project.getReference("ant.projectHelper"); //$NON-NLS-1$
				projectHelper.setBuildFile(getEditedFile());
				prepareForFullIncremental();
			}
			Map<String, Object> references = project.getReferences();
			references.remove("ant.parsing.context"); //$NON-NLS-1$
			ProjectHelper.setAntModel(this);
			projectHelper.parse(project, textToParse);

		}
		catch (BuildException e) {
			JavaLanguageServerPlugin.logException("Build fails", e);
		}
		finally {
			if (parsed) {
				SecurityManager origSM = System.getSecurityManager();
				processAntHome(true);
				try {
					// set a security manager to disallow system exit and system property setting
					System.setSecurityManager(new AntSecurityManager(origSM, Thread.currentThread(), false));
					resolveBuildfile();
				}
				catch (AntSecurityException e) {
					// do nothing
				}
				finally {
					Thread.currentThread().setContextClassLoader(originalClassLoader);
					getClassLoader(null);
					System.setSecurityManager(origSM);
					project.fireBuildFinished(null); // cleanup (IntrospectionHelper)
				}
			}
		}
	}

	
	private ProjectHelper prepareForFullParse(Project project, ClassLoader parsingClassLoader) {
		initializeProject(project, parsingClassLoader);
		// Ant's parsing facilities always works on a file, therefore we need
		// to determine the actual location of the file. Though the file
		// contents will not be parsed. We parse the passed document string
		File file = getEditedFile();
		String filePath = IAntCoreConstants.EMPTY_STRING;
		if (file != null) {
			filePath = file.getAbsolutePath();
		}
		project.setUserProperty("ant.file", filePath); //$NON-NLS-1$
		project.setUserProperty("ant.version", Main.getAntVersion()); //$NON-NLS-1$

		ProjectHelper projectHelper = getProjectHelper();
		ProjectHelper.setAntModel(this);
		projectHelper.setBuildFile(file);
		project.addReference("ant.projectHelper", projectHelper); //$NON-NLS-1$
		return projectHelper;
	}
	
	private void prepareForFullIncremental() {
		fProjectNode.reset();
		fTaskToNode = new HashMap<>();
		fTaskNodes = new ArrayList<>();
	}
	
	private void initializeProject(Project project, ClassLoader loader) {
		try {
			processAntHome(false);
		}
		catch (AntSecurityException ex) {
			// do nothing - Ant home can not be set from this thread
		}
		project.init();
		setProperties(project);
		setTasks(project, loader);
		setTypes(project, loader);
	}
	
	private void setTasks(Project project, ClassLoader loader) {
		List<org.eclipse.ant.core.Task> tasks = AntCorePlugin.getPlugin().getPreferences().getTasks();
		for (org.eclipse.ant.core.Task task : tasks) {
			AntTypeDefinition def = new AntTypeDefinition();
			def.setName(task.getTaskName());
			def.setClassName(task.getClassName());
			def.setClassLoader(loader);
			def.setAdaptToClass(Task.class);
			def.setAdapterClass(TaskAdapter.class);
			ComponentHelper.getComponentHelper(project).addDataTypeDefinition(def);
		}
	}

	private void setTypes(Project project, ClassLoader loader) {
		List<Type> types = AntCorePlugin.getPlugin().getPreferences().getTypes();
		for (Type type : types) {
			AntTypeDefinition def = new AntTypeDefinition();
			def.setName(type.getTypeName());
			def.setClassName(type.getClassName());
			def.setClassLoader(loader);
			ComponentHelper.getComponentHelper(project).addDataTypeDefinition(def);
		}
	}
	
	private void setProperties(Project project) {
		setBuiltInProperties(project);
		setExtraProperties(project);
		setGlobalProperties(project);
		loadExtraPropertyFiles(project);
		loadPropertyFiles(project);
	}
	
	private void setExtraProperties(Project project) {
		if (fProperties != null) {
			Pattern pattern = Pattern.compile("\\$\\{.*_prompt.*\\}"); //$NON-NLS-1$
			IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
			for (Iterator<String> iter = fProperties.keySet().iterator(); iter.hasNext();) {
				String name = iter.next();
				String value = fProperties.get(name);
				if (!pattern.matcher(value).find()) {
					try {
						value = manager.performStringSubstitution(value);
					}
					catch (CoreException e) {
						// do nothing
					}
				}
				if (value != null) {
					project.setUserProperty(name, value);
				}
			}
		}
	}
	
	private void loadExtraPropertyFiles(Project project) {
		if (fPropertyFiles != null) {
			try {
				List<Properties> allProperties = AntCoreUtil.loadPropertyFiles(fPropertyFiles, project.getUserProperty("basedir"), getEditedFile().getAbsolutePath()); //$NON-NLS-1$
				setPropertiesFromFiles(project, allProperties);
			}
			catch (IOException e1) {
				JavaLanguageServerPlugin.logException("load property file error", e1);
			}
		}
	}
	
	/**
	 * Load all properties from the files
	 */
	private void loadPropertyFiles(Project project) {
		List<String> fileNames = Arrays.asList(AntCorePlugin.getPlugin().getPreferences().getCustomPropertyFiles());
		try {
			List<Properties> allProperties = AntCoreUtil.loadPropertyFiles(fileNames, project.getUserProperty("basedir"), getEditedFile().getAbsolutePath()); //$NON-NLS-1$
			setPropertiesFromFiles(project, allProperties);
		}
		catch (IOException e1) {
			JavaLanguageServerPlugin.logException("load property file error", e1);
		}
	}


	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ant.internal.ui.model.IAntModel#setProperties(java.util.Map)
	 */
	@Override
	public void setProperties(Map<String, String> properties) {
		fProperties = properties;
	}
	
	private void setBuiltInProperties(Project project) {
		// set processAntHome for other properties set as system properties
		project.setUserProperty("ant.file", getEditedFile().getAbsolutePath()); //$NON-NLS-1$
		project.setUserProperty("ant.version", Main.getAntVersion()); //$NON-NLS-1$
	}
	
	private void setGlobalProperties(Project project) {
		List<Property> properties = AntCorePlugin.getPlugin().getPreferences().getProperties();
		if (properties != null) {
			for (Iterator<Property> iter = properties.iterator(); iter.hasNext();) {
				Property property = iter.next();
				String value = property.getValue(true);
				if (value != null) {
					project.setUserProperty(property.getName(), value);
				}
			}
		}
	}
	
	private void resolveBuildfile() {
		Collection<AntTaskNode> nodeCopy = new ArrayList<>(fTaskNodes);
		Iterator<AntTaskNode> iter = nodeCopy.iterator();
		while (iter.hasNext()) {
			AntTaskNode node = iter.next();
			fNodeBeingResolved = node;
			fNodeBeingResolvedIndex = -1;
			if (node.configure(false)) {
				// resolve any new elements that may have been added
				resolveBuildfile();
			}
		}
		fNodeBeingResolved = null;
		fNodeBeingResolvedIndex = -1;
	}

	
	private void setPropertiesFromFiles(Project project, List<Properties> allProperties) {
		for (Properties props : allProperties) {
			Enumeration<?> propertyNames = props.propertyNames();
			while (propertyNames.hasMoreElements()) {
				String name = (String) propertyNames.nextElement();
				// do not override extra local properties with the global settings
				if (project.getUserProperty(name) == null) {
					project.setUserProperty(name, props.getProperty(name));
				}
			}
		}
	}
	
	private void processAntHome(boolean finished) {
		AntCorePreferences prefs = AntCorePlugin.getPlugin().getPreferences();
		String antHome = prefs.getAntHome();
		if (finished || antHome == null) {
			System.getProperties().remove("ant.home"); //$NON-NLS-1$
			System.getProperties().remove("ant.library.dir"); //$NON-NLS-1$
		} else {
			System.setProperty("ant.home", antHome); //$NON-NLS-1$
			File antLibDir = new File(antHome, "lib"); //$NON-NLS-1$
			System.setProperty("ant.library.dir", antLibDir.getAbsolutePath()); //$NON-NLS-1$
		}
	}
	
	private ClassLoader getClassLoader(ClassLoader contextClassLoader) {
		synchronized (loaderLock) {
			if (fLocalClassLoader != null) {
				((AntClassLoader) fLocalClassLoader).setPluginContextClassloader(contextClassLoader);
				return fLocalClassLoader;
			}
			if (fgClassLoader == null) {
				fgClassLoader = AntCorePlugin.getPlugin().getNewClassLoader(true);
			}
			if (fgClassLoader instanceof AntClassLoader) {
				((AntClassLoader) fgClassLoader).setPluginContextClassloader(contextClassLoader);
			}
			return fgClassLoader;
		}
	}
	
	/**
	 * Removes any type definitions that no longer exist in the buildfile
	 */
	private void reconcileTaskAndTypes() {
		if (fCurrentNodeIdentifiers == null || fDefinerNodeIdentifierToDefinedTasks == null) {
			return;
		}
		Iterator<String> iter = fDefinerNodeIdentifierToDefinedTasks.keySet().iterator();
		ComponentHelper helper = ComponentHelper.getComponentHelper(fProjectNode.getProject());
		while (iter.hasNext()) {
			String key = iter.next();
			if (fCurrentNodeIdentifiers.get(key) == null) {
				removeDefinerTasks(key, helper.getAntTypeTable());
			}
		}
	}
	
	protected void removeDefinerTasks(String definerIdentifier, Hashtable<String, AntTypeDefinition> typeTable) {
		if (fDefinerNodeIdentifierToDefinedTasks == null) {
			return;
		}
		List<String> tasks = fDefinerNodeIdentifierToDefinedTasks.get(definerIdentifier);
		if (tasks == null) {
			return;
		}
		Iterator<String> iterator = tasks.iterator();
		while (iterator.hasNext()) {
			typeTable.remove(iterator.next());
		}
	}

	@Override
	public String getEntityName(String path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LocationProvider getLocationProvider() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setPropertyFiles(String[] propertyFiles) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IFile getFile() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getEncoding() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void handleBuildException(BuildException be, AntElementNode node, int severity) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addTarget(Target newTarget, int lineNumber, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addProject(Project project, int lineNumber, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public File getEditedFile() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean canGetTaskInfo() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean canGetLexicalInfo() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean canGetPositionInfo() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void addComment(int lineNumber, int columnNumber, int length) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addDTD(String name, int lineNumber, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addEntity(String name, String currentEntityPath) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addTask(Task newTask, Task parentTask, Attributes attributes, int lineNumber, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setCurrentElementLength(int lineNumber, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getOffset(int lineNumber, int column) throws BadLocationException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void error(Exception e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fatalError(Exception e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void warning(Exception e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void errorFromElement(Exception e, AntElementNode element, int lineNumber, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void errorFromElementText(Exception e, int offset, int columnNumber) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getText(int offset, int length) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDefiningTaskNodeText(AntDefiningTaskNode node) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addPrefixMapping(String prefix, String uri) {
		// TODO Auto-generated method stub
		
	}
}
