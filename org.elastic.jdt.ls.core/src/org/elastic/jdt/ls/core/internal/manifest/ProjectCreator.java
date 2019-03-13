package org.elastic.jdt.ls.core.internal.manifest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

import org.elastic.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.elastic.jdt.ls.core.internal.manifest.model.Dependency;
import org.elastic.jdt.ls.core.internal.manifest.model.ProjectInfo;
import org.elastic.jdt.ls.core.internal.manifest.model.Repo;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public class ProjectCreator {

	public IJavaProject createJavaProjectFromProjectInfo(String projectName, ProjectInfo project, IProgressMonitor monitor) {
		try {
			IJavaProject javaProject = createJavaProject(projectName, monitor);

			List<String> sourceDirs = project.getSrcDirs();
			createSourceDirectories(sourceDirs, javaProject, monitor);

			// add rt.jar
			addVariableEntry(javaProject, new Path(JavaRuntime.JRELIB_VARIABLE), new Path(JavaRuntime.JRESRC_VARIABLE), new Path(JavaRuntime.JRESRCROOT_VARIABLE), monitor);
			
			// add android SDK if an Android project
			if (project.isAndroid()) {
				setAndroidHome(project.getAndroidSdkVersion(), javaProject, monitor);
			}
		
			List<String> dependencies = retrieveAllDeps(project.getDependencies(), project.getRepos());
			setClasspath(dependencies, javaProject, monitor);

			javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
			return javaProject;
		}
		catch (CoreException | ArtifactResolutionException ce) {
			JavaLanguageServerPlugin.logException("Failed to create the java project depending on info" + project.toString(), ce);
			return null;
		}
	}

	private void setClasspath(List<String> dependencies, IJavaProject javaProject, IProgressMonitor monitor) throws CoreException {
		if (dependencies.size() == 0) {
			return;
		}
		for (int i = 0; i < dependencies.size(); i++) {
			String cp = dependencies.get(i);
			File classpathEntry = new File(cp);
			if (classpathEntry.isFile()) {
				addLibrary(javaProject, new Path(classpathEntry.getAbsolutePath()), monitor);
			} else {
				addContainer(javaProject, new Path(classpathEntry.getAbsolutePath()), monitor);
			}
		}
	}
	
	
	private void setAndroidHome(String version,  IJavaProject javaProject, IProgressMonitor monitor) throws JavaModelException {
		if (version == null) {
			return;
		}
		JavaLanguageServerPlugin.logInfo("Adding Android SDK classpath entry");
		addVariableEntry(javaProject, new Path("ANDROID_HOME/platforms/" + version + "/android.jar"), new Path("ANDROID_HOME/sources/" + version), null, monitor);
	}
	
	private List<String> retrieveAllDeps(List<Dependency> deps, List<Repo> repos) throws ArtifactResolutionException {
		ArrayList<String> allDepsPaths = new ArrayList();
		
		RepositorySystem system = ArtifactResolver.newRepositorySystem();
        RepositorySystemSession session = ArtifactResolver.newRepositorySystemSession(system);
        
        ArrayList<ArtifactRequest> artifactRequests = new ArrayList();
        for (Dependency dep: deps) {
        	if (dep.getPath() != null) {
        		// local libs
        		allDepsPaths.add(dep.getPath());
        	} else {
        		Artifact artifact = new DefaultArtifact(String.format("%s:%s:%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion()));
    			ArtifactRequest artifactRequest = new ArtifactRequest();
    			artifactRequest.setArtifact(artifact);
    			artifactRequest.setRepositories(ArtifactResolver.newRepositories(system, session, repos));
    			artifactRequests.add(artifactRequest);
        	}
        }
        List<ArtifactResult> artifactResults = system.resolveArtifacts(session, artifactRequests);
        
        artifactResults.forEach(r -> {
        	Artifact artifact = r.getArtifact();
        	if (FilenameUtils.getExtension(artifact.getFile().getName()) == "aar") {
        		// extract all *.aar to jar
        		explodeAarJarFiles(artifact.getFile(), String.format("%s-%s-%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion())).forEach(allDepsPaths::add);
        	} else {
        		allDepsPaths.add(artifact.getFile().getPath());
        	}
        });
     
        return allDepsPaths;
	}
	
	// @see com.greensopinion.gradle.android.eclipse.GenerateLibraryDependenciesAction#explodeAarJarFiles
	private Stream<String> explodeAarJarFiles(File aarFile, String jarId) {
		File targetFolder = new File(new File(new File(ResourcesPlugin.getWorkspace().getRoot().getFullPath().toString(), "build"), "exploded-aars"), jarId);
		if (!targetFolder.exists()) {
			if (!targetFolder.mkdirs()) {
				JavaLanguageServerPlugin.logError("Cannot create folder: " + targetFolder.getAbsolutePath());
			}
			try (ZipFile zipFile = new ZipFile(aarFile)) {
				zipFile.stream().forEach(f -> {
					if (f.getName().endsWith(".jar")) {
						String targetName = jarId + ".jar";
						File targetFile = new File(targetFolder, targetName);
						ensureParentFolderExists(targetFile);
						int index = 1;
						while (targetFile.exists()) {
							targetFile = new File(targetFolder, String.format("%s_%s", ++index, targetName));
						}
						copy(zipFile, targetFile, f);
					}
				});
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException("Cannot explode aar: " + aarFile.getAbsolutePath(), e);
			}
		}
		List<File> files = listFilesTraversingFolders(targetFolder);
		return files.stream().filter(f -> f.getName().endsWith(".jar")).map(f -> f.getPath()); 
	}
	
	private List<File> listFilesTraversingFolders(File folder) {
		List<File> files = new ArrayList<>();
		File[] children = folder.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isFile()) {
					files.add(child);
				} else if (child.isDirectory()) {
					files.addAll(listFilesTraversingFolders(child));
				}
			}
		}
		return files;
	}
	
	private void ensureParentFolderExists(File targetFile) {
		File parentFolder = targetFile.getParentFile();
		if (!parentFolder.exists()) {
			if (!parentFolder.mkdirs()) {
				throw new RuntimeException("Cannot create folder: " + parentFolder.getAbsolutePath());
			}
		}
	}
	
	private void copy(ZipFile zipFile, File targetFile, ZipEntry entry) {
		try (InputStream inputStream = zipFile.getInputStream(entry)) {
			Files.copy(inputStream, targetFile.toPath());
		} catch (IOException e) {
			throw new RuntimeException(String.format("Cannot write entry to file: %s: %s", e.getMessage(), targetFile.getAbsolutePath()), e);
		}
	}

	private void createSourceDirectories(List<String> sourceDirs, IJavaProject javaProject, IProgressMonitor monitor) throws CoreException {
		for (int i = 0; i < sourceDirs.size(); i++) {
			String srcDir = sourceDirs.get(i);
			File srcDirectory = new File(srcDir);
			String srcDirectoryName = srcDirectory.getName();
			addSourceContainer(javaProject, srcDirectoryName, srcDir, srcDirectoryName, srcDir, monitor);
		}
	}

	private IJavaProject createJavaProject(String projectName, IProgressMonitor monitor) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		if (!project.exists()) {
			project.create(monitor);
		} else {
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		}

		if (!project.isOpen()) {
			project.open(monitor);
		}

		if (!project.hasNature(JavaCore.NATURE_ID)) {
			addNatureToProject(project, JavaCore.NATURE_ID, monitor);
		}

		IJavaProject jproject = JavaCore.create(project);

		jproject.setRawClasspath(new IClasspathEntry[0], monitor);

		return jproject;
	}

	private void addNatureToProject(IProject proj, String natureId, IProgressMonitor monitor) throws CoreException {
		IProjectDescription description = proj.getDescription();
		String[] prevNatures = description.getNatureIds();
		String[] newNatures = new String[prevNatures.length + 1];
		System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
		newNatures[prevNatures.length] = natureId;
		description.setNatureIds(newNatures);
		proj.setDescription(description, monitor);
	}

	/**
	 * Adds a source container to a IJavaProject.
	 */
	private void addSourceContainer(IJavaProject jproject, String srcName, String srcPath, String outputName, String outputPath, IProgressMonitor monitor) throws CoreException {
		IProject project = jproject.getProject();
		IContainer container = null;
		if (srcName == null || srcName.length() == 0) {
			container = project;
		} else {
			IFolder folder = project.getFolder(srcName);
			if (!folder.exists()) {
				folder.createLink(new Path(srcPath), IResource.ALLOW_MISSING_LOCAL, monitor);
			}
			container = folder;
		}
		IPackageFragmentRoot root = jproject.getPackageFragmentRoot(container);

		IPath output = null;
		if (outputName != null) {
			IFolder outputFolder = project.getFolder(outputName);
			if (!outputFolder.exists()) {
				outputFolder.createLink(new Path(outputPath), IResource.ALLOW_MISSING_LOCAL, monitor);
			}
			output = outputFolder.getFullPath();
		}

		IClasspathEntry cpe = JavaCore.newSourceEntry(root.getPath(), new IPath[0], output);

		addToClasspath(jproject, cpe, monitor);
	}

	private void addToClasspath(IJavaProject jproject, IClasspathEntry cpe, IProgressMonitor monitor) throws JavaModelException {
		IClasspathEntry[] oldEntries = jproject.getRawClasspath();
		for (int i = 0; i < oldEntries.length; i++) {
			if (oldEntries[i].equals(cpe)) {
				return;
			}
		}
		int nEntries = oldEntries.length;
		IClasspathEntry[] newEntries = new IClasspathEntry[nEntries + 1];
		System.arraycopy(oldEntries, 0, newEntries, 0, nEntries);
		newEntries[nEntries] = cpe;
		jproject.setRawClasspath(newEntries, monitor);
	}

	/**
	 * Adds a variable entry with source attachment to a IJavaProject if the path can be resolved.
	 */
	private void addVariableEntry(IJavaProject jproject, IPath path, IPath sourceAttachPath, IPath sourceAttachRoot, IProgressMonitor monitor) throws JavaModelException {
		IClasspathEntry cpe = JavaCore.newVariableEntry(path, sourceAttachPath, sourceAttachRoot);
		addToClasspath(jproject, cpe, monitor);
	}

	/**
	 * Adds a library entry to an IJavaProject.
	 */
	private void addLibrary(IJavaProject jproject, IPath path, IProgressMonitor monitor) throws JavaModelException {
		IClasspathEntry cpe = JavaCore.newLibraryEntry(path, null, null);
		addToClasspath(jproject, cpe, monitor);
	}

	/**
	 * Adds a container entry to an IJavaProject.
	 */
	private void addContainer(IJavaProject jproject, IPath path, IProgressMonitor monitor) throws CoreException {
		IClasspathEntry cpe = JavaCore.newContainerEntry(path);
		addToClasspath(jproject, cpe, monitor);
		IProject project = jproject.getProject();
		IFolder folder = project.getFolder(path.lastSegment());
		if (!folder.exists()) {
			folder.createLink(path, IResource.ALLOW_MISSING_LOCAL, monitor);
		}
	}
}
