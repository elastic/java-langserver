package org.elastic.jdt.ls.core.internal.ant;

import java.io.File;
import java.net.URL;

import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.IDocument;
import org.elastic.jdt.ls.core.internal.JavaLanguageServerPlugin;

public final class AntUtil {
	
	public static IAntModel getAntModel(String buildFilePath, boolean needsLexicalResolution, boolean needsPositionResolution, boolean needsTaskResolution) {
		IAntModel model = getAntModel(getBuildFile(buildFilePath), null, needsLexicalResolution, needsPositionResolution, needsTaskResolution);
		return model;
	}
	
	/**
	 * Return a buildfile from the specified location. If there isn't one return null.
	 */
	private static File getBuildFile(String path) {
		File buildFile = new File(path);
		if (!buildFile.isFile() || !buildFile.exists()) {
			return null;
		}

		return buildFile;
	}

	private static IAntModel getAntModel(final File buildFile, URL[] urls, boolean needsLexical, boolean needsPosition, boolean needsTask) {
		if (buildFile == null || !buildFile.exists()) {
			return null;
		}
		IDocument doc = getDocument(buildFile);
		if (doc == null) {
			return null;
		}
		final IFile file = getFileForLocation(buildFile.getAbsolutePath(), null);
		LocationProvider provider = new LocationProvider() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.ant.internal.ui.model.LocationProvider#getFile()
			 */
			@Override
			public IFile getFile() {
				return file;
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.ant.internal.ui.model.LocationProvider#getLocation()
			 */
			@Override
			public IPath getLocation() {
				if (file == null) {
					return new Path(buildFile.getAbsolutePath());
				}
				return file.getLocation();
			}
		};
		IAntModel model = new AntModel(doc, provider, needsLexical, needsPosition, needsTask);

		if (urls != null) {
			model.setClassLoader(AntCorePlugin.getPlugin().getNewClassLoader(urls));
		}
		return model;
	}
	
	private static IDocument getDocument(File buildFile) {
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		IPath location = new Path(buildFile.getAbsolutePath());
		boolean connected = false;
		try {
			ITextFileBuffer buffer = manager.getTextFileBuffer(location, LocationKind.NORMALIZE);
			if (buffer == null) {
				// no existing file buffer..create one
				manager.connect(location, LocationKind.NORMALIZE, new NullProgressMonitor());
				connected = true;
				buffer = manager.getTextFileBuffer(location, LocationKind.NORMALIZE);
				if (buffer == null) {
					return null;
				}
			}

			return buffer.getDocument();
		}
		catch (CoreException ce) {
			JavaLanguageServerPlugin.logException("core error", ce);
			return null;
		}
		finally {
			if (connected) {
				try {
					manager.disconnect(location, LocationKind.NORMALIZE, new NullProgressMonitor());
				}
				catch (CoreException e) {
					JavaLanguageServerPlugin.logException("core error", e);
				}
			}
		}
	}
	
	/**
	 * Returns the workspace file associated with the given path in the local file system, or <code>null</code> if none. If the path happens to be a
	 * relative path, then the path is interpreted as relative to the specified parent file.
	 * 
	 * Attempts to handle linked files; the first found linked file with the correct path is returned.
	 * 
	 * @param path
	 * @param buildFileParent
	 * @return file or <code>null</code>
	 * @see org.eclipse.core.resources.IWorkspaceRoot#findFilesForLocation(IPath)
	 */
	public static IFile getFileForLocation(String path, File buildFileParent) {
		return AntLaunchingUtil.getFileForLocation(path, buildFileParent);
	}
}
