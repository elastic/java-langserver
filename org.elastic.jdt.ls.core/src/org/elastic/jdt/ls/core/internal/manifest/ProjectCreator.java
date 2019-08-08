package org.elastic.jdt.ls.core.internal.manifest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
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
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.elastic.jdt.ls.core.internal.ElasticJavaLanguageServerPlugin;
import org.elastic.jdt.ls.core.internal.manifest.model.Dependency;
import org.elastic.jdt.ls.core.internal.manifest.model.ProjectInfo;
import org.elastic.jdt.ls.core.internal.manifest.model.Repo;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public class ProjectCreator {
	
	private java.nio.file.Path currentDir;

	public IJavaProject createJavaProjectFromProjectInfo(java.nio.file.Path dir, String rootProjectName, ProjectInfo project, IProgressMonitor monitor) {
		try {
			IJavaProject javaProject;
			if (project.getPath().equals(":")) {
				currentDir = dir;
				javaProject = createJavaProject(rootProjectName, monitor);
			} else {
				currentDir = dir.resolve(project.getPath().substring(1).replaceAll(":", "/"));
				javaProject = createJavaProject(rootProjectName.concat(project.getPath().replaceAll(":", ".")), monitor);
			}

			List<String> sourceDirs = project.getSrcDirs();
			List<String> testSourceDirs = project.getTestSrcDirs();
			
			if (sourceDirs != null) {
				createSourceDirectories(sourceDirs, javaProject, monitor);
			}
			
			if (testSourceDirs != null) {
				createSourceDirectories(testSourceDirs, javaProject, monitor);
			}
			// add rt.jar
			if (sourceDirs != null && testSourceDirs != null) {
				addVariableEntry(javaProject, new Path(JavaRuntime.JRELIB_VARIABLE), new Path(JavaRuntime.JRESRC_VARIABLE), new Path(JavaRuntime.JRESRCROOT_VARIABLE), monitor);
			}
			
			// add android SDK if an Android project
			if (project.isAndroid()) {
				setAndroidHome(project.getAndroidSdkVersion(), javaProject, monitor);
			}
			
			if (project.getDependencies() != null) {
				List<ImmutablePair<String, String>> dependencies = retrieveAllDeps(project.getDependencies(), project.getRepos());
				setClasspath(dependencies, javaProject, monitor);
			}

			javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			return javaProject;
		}
		catch (CoreException | InterruptedException ce) {
			ElasticJavaLanguageServerPlugin.logException("Failed to create the java project depending on info " + project.toString(), ce);
			return null;
		}
	}

	private void setClasspath(List<ImmutablePair<String, String>> dependencies, IJavaProject javaProject, IProgressMonitor monitor) throws CoreException {
		if (dependencies.size() == 0) {
			return;
		}
		for (int i = 0; i < dependencies.size(); i++) {
			try {
				String cp = dependencies.get(i).getLeft();
				File classpathEntry = new File(cp);
				if (classpathEntry.isFile()) {
					if (dependencies.get(i).getRight() != null) {
						addLibrary(javaProject, new Path(classpathEntry.getAbsolutePath()), new Path(new File(dependencies.get(i).getRight()).getAbsolutePath()), monitor);
					} else {
						addLibrary(javaProject, new Path(classpathEntry.getAbsolutePath()), null, monitor);
					}
				} else {
					addContainer(javaProject, new Path(classpathEntry.getAbsolutePath()), monitor);
				}
			} catch (JavaModelException e) {
				ElasticJavaLanguageServerPlugin.logException("Failed to add lib " + dependencies.get(i).toString(), e);
			}
		}
	}
	
	
	private void setAndroidHome(String version,  IJavaProject javaProject, IProgressMonitor monitor) throws JavaModelException {
		if (version == null) {
			return;
		}
		String androidHome = System.getenv("ANDROID_HOME");
		if (androidHome != null) {
			addVariableEntry(javaProject, new Path(androidHome + "/platforms/" + version + "/android.jar"), new Path(androidHome + "/sources/" + version), null, monitor);
		} else {
			ElasticJavaLanguageServerPlugin.logError("ANDROID_HOME is undefined");
		}
	}
	
	private List<ImmutablePair<String, String>> retrieveAllDeps(List<Dependency> deps, List<Repo> repos) throws InterruptedException {
		List<ImmutablePair<String, String>> allDepsPaths = Collections.synchronizedList(new ArrayList<>());
		
		class DownloadDepTask implements Runnable {
			private Dependency dep;
			private RepositorySystem system;
			private RepositorySystemSession session;
			private ArtifactRequest artifactRequest;
		
			DownloadDepTask(Dependency dep) {
				this.dep = dep;
				this.system = ArtifactResolver.newRepositorySystem();
				this.session = ArtifactResolver.newRepositorySystemSession(system);

				this.artifactRequest = new ArtifactRequest();
				this.artifactRequest.setRepositories(ArtifactResolver.newRepositories(system, session, repos));
			}
			
			@Override
		    	public void run() {
    				File artifactFile = null;
            			if (dep.getPath() != null) {
					// local libs
					allDepsPaths.add(ImmutablePair.of(dep.getPath(), null));
				} else {
					try {
						Artifact artifact = new DefaultArtifact(String.format("%s:%s:%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion()));
						String artifactPath = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
						artifactFile = Paths.get(ArtifactResolver.MAVEN_LOCAL, artifactPath).toFile();
						if (!artifactFile.exists()) {
							artifactRequest.setArtifact(artifact);
							ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
							artifactFile = artifactResult.getArtifact().getFile();
						}
						if (FilenameUtils.getExtension(artifactFile.getName()) == "aar") {
						// extract all *.aar to jar
						explodeAarJarFiles(artifactFile, String.format("%s-%s-%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion())).forEach(allDepsPaths::add);
					} else {
						Artifact sourceArtifact = new DefaultArtifact(String.format("%s:%s:jar:sources:%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion()));
						String sourceArtifactPath = session.getLocalRepositoryManager().getPathForLocalArtifact(sourceArtifact);
						File sourceArtifactFile = Paths.get(ArtifactResolver.MAVEN_LOCAL, sourceArtifactPath).toFile();
						if (!sourceArtifactFile.exists()) {
							artifactRequest.setArtifact(sourceArtifact);
							ArtifactResult sourceArtifactResult = system.resolveArtifact(session, artifactRequest);
							sourceArtifactFile = sourceArtifactResult.getArtifact().getFile();
						}
						allDepsPaths.add(ImmutablePair.of(artifactFile.getPath(), sourceArtifactFile.getPath()));
					}
					} catch (ArtifactResolutionException e) {
						if (artifactFile != null) {
							if (FilenameUtils.getExtension(artifactFile.getName()) == "aar") {
								// extract all *.aar to jar
								explodeAarJarFiles(artifactFile, String.format("%s-%s-%s", dep.getGroupId(), dep.getArtifactId(), dep.getVersion())).forEach(allDepsPaths::add);
								} else {
									allDepsPaths.add(ImmutablePair.of(artifactFile.getPath(), null));
								}
							} else {
								ElasticJavaLanguageServerPlugin.logException("Failed to resolve dependency: " + dep.toString(), e);
							}
						}
					}
				}
        		}
        
		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);
		for (Dependency dependency: deps) {
			pool.submit(new DownloadDepTask(dependency));
		}
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
	
		return allDepsPaths;
	}
	
	
	// @see com.greensopinion.gradle.android.eclipse.GenerateLibraryDependenciesAction#explodeAarJarFiles
	private Stream<ImmutablePair<String, String>> explodeAarJarFiles(File aarFile, String jarId) {
		File targetFolder = new File(new File(new File(currentDir.toAbsolutePath().toString(), "build"), "exploded-aars"), jarId);
		if (!targetFolder.exists()) {
			if (!targetFolder.mkdirs()) {
				ElasticJavaLanguageServerPlugin.logError("Cannot create folder: " + targetFolder.getAbsolutePath());
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
				ElasticJavaLanguageServerPlugin.logException("Cannot explode aar: " + aarFile.getAbsolutePath(), e);
			}
		}
		List<File> files = listFilesTraversingFolders(targetFolder);
		return files.stream().filter(f -> f.getName().endsWith(".jar")).map(f -> ImmutablePair.of(f.getPath(), null)); 
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
			if (currentDir.resolve(srcDir).toFile().exists()) {
				File srcDirectory = new File(srcDir);
				String srcDirectoryName = srcDirectory.getName();
				IPath sourcePath = javaProject.getProject().getFolder(srcDir).getFullPath();
				ProjectUtils.addSourcePath(sourcePath, new IPath[0], javaProject);
			}
		}
	}

	private IJavaProject createJavaProject(String projectName, IProgressMonitor monitor) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IWorkspace workspace = ResourcesPlugin.getWorkspace();

		IProject project = root.getProject(projectName);
		IProjectDescription description = workspace.newProjectDescription(project.getName());
		
		description.setLocationURI(currentDir.toUri());
		
		if (!project.exists()) {
			project.create(description, monitor);
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
	private void addLibrary(IJavaProject jproject, IPath path, IPath sourcePath, IProgressMonitor monitor) throws JavaModelException {
		IClasspathEntry cpe = JavaCore.newLibraryEntry(path, sourcePath, null);
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
