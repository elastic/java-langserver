package org.elastic.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;


public class SynchronizedWorkspaceFolderChangeHandler {

    private ProjectsManager projectManager;

    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    SynchronizedWorkspaceFolderChangeHandler(ProjectsManager projectManager) {
        this.projectManager = projectManager;
    }

    public void update(DidChangeWorkspaceFoldersParams params) {
        final Collection<IPath> addedRootPaths = new ArrayList<>();
        final Collection<IPath> removedRootPaths = new ArrayList<>();
        for (WorkspaceFolder folder : params.getEvent().getAdded()) {
            IPath rootPath = ResourceUtils.filePathFromURI(folder.getUri());
            if (rootPath != null) {
                addedRootPaths.add(rootPath);
            }
        }
        for (WorkspaceFolder folder : params.getEvent().getRemoved()) {
            IPath rootPath = ResourceUtils.filePathFromURI(folder.getUri());
            if (rootPath != null) {
                removedRootPaths.add(rootPath);
            }
        }
        Job job = projectManager.updateWorkspaceFolders(addedRootPaths, removedRootPaths);
        job.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                countDownLatch.countDown();
            }
        });
        // main thread wait until update job done
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}