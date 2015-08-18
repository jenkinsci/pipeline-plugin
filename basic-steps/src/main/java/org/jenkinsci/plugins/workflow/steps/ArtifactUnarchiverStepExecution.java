package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactUnarchiverStepExecution extends AbstractSynchronousNonBlockingStepExecution<List<FilePath>> {
    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient Run build;

    @Inject
    private transient ArtifactUnarchiverStep step;

    @Override
    protected List<FilePath> run() throws Exception {
        // where to copy artifacts from?
        Run r = build; // TODO consider an option to override this (but in what format?)

        ArtifactManager am = r.getArtifactManager();

        List<FilePath> files = new ArrayList<FilePath>();

        if (step.mapping == null)
            throw new AbortException("'mapping' has not been defined for this 'unarchive' step");

        for (Entry<String, String> e : step.mapping.entrySet()) {
            FilePath dst = new FilePath(ws,e.getValue());
            String src = e.getKey();
            String[] all = am.root().list(src);
            if (all.length == 0) {
                throw new AbortException("no artifacts to unarchive in " + src);
            } else if (all.length == 1 && all[0].equals(src)) {
                // the source is a file
                if (dst.isDirectory())
                    dst = dst.child(getFileName(all[0]));

                files.add(copy(am.root().child(all[0]), dst));
            } else {
                // copy into a directory
                for (String path : all) {
                    files.add(copy(am.root().child(path), dst.child(path)));
                }
            }
        }

        return files;
    }

    private FilePath copy(VirtualFile src, FilePath dst) throws IOException, InterruptedException {
        InputStream in = src.open();
        try {
            dst.copyFrom(in);
        } finally {
            in.close();
        }
        return dst;
    }

    /**
     * Grabs the file name portion out of a path name.
     */
    private String getFileName(String s) {
        int idx = s.lastIndexOf('/');
        if (idx>=0) s=s.substring(idx+1);
        idx = s.lastIndexOf('\\');
        if (idx>=0) s=s.substring(idx+1);
        return s;
    }

    private static final long serialVersionUID = 1L;

}
