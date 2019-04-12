package org.elastic.jdt.ls.core.internal.manifest;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Collection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.BasicFileDetector;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import org.elastic.jdt.ls.core.internal.manifest.model.Config;
import org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin;


public class GradleManifestImporter extends AbstractProjectImporter {
	
	public static final String GRADLE_MANIFEST_FILE = "manifest.json";
	
	public static final String IMPORTING_GRADLE_MANIFEST_PROJECTS = "Importing Gradle manifest project(s)";
	
	private Collection<Path> directories;
	
	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (rootFolder == null) {
			return false;
		}
		PreferenceManager preferencesManager = ElasticJavaLanguageServerPlugin.getPreferencesManager();
		if (directories == null) {
			BasicFileDetector gradleManifestDetector = new BasicFileDetector(rootFolder.toPath(), GRADLE_MANIFEST_FILE).includeNested(false);
			directories = gradleManifestDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}
	
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (!applies(monitor)) {
			return;
		}
		SubMonitor subMonitor = SubMonitor.convert(monitor, directories.size() + 1);
		subMonitor.setTaskName(IMPORTING_GRADLE_MANIFEST_PROJECTS);
		ElasticJavaLanguageServerPlugin.logInfo(IMPORTING_GRADLE_MANIFEST_PROJECTS);
		subMonitor.worked(1);
		ProjectCreator pc = new ProjectCreator();
		for (Path projectDir: directories) {
			Config config = this.deserializedConfig(projectDir.toString() + "/" + GRADLE_MANIFEST_FILE);
			String rootProjectName = config.getProjectInfos().stream().filter(info -> info.getPath().equals(":")).findFirst().get().getName();
			config.getProjectInfos().forEach(info -> {			
				pc.createJavaProjectFromProjectInfo(
					projectDir,
					rootProjectName,
					info,
					subMonitor.newChild(1));
				});
		}
		subMonitor.done();		
	}

	
	private Config deserializedConfig(String file) {
		 BufferedReader bufferedReader;
		try {
			bufferedReader = new BufferedReader(new FileReader(file));
			Gson gson = new GsonBuilder().create();
			return gson.fromJson(bufferedReader, Config.class);
		} catch (FileNotFoundException e) {
			ElasticJavaLanguageServerPlugin.logException("Cannot parse manifest config file: " + file, e);
		}
		return null;
	}


	@Override
	public void reset() {
		directories = null;
	}

}
