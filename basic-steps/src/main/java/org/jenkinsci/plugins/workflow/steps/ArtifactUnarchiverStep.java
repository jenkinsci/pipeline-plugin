package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.trilead.ssh2.util.IOUtils.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactUnarchiverStep extends AbstractSynchronousStepImpl<List<FilePath>> {
    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient Run build;

    /**
     * Files to copy over.
     */
    private Map<String, String> files;

    @DataBoundSetter
    private Run from;

    @DataBoundConstructor
    public ArtifactUnarchiverStep(Map<String,String> mapping) {
        this.files = mapping;
    }

    @Override
    protected List<FilePath> run(StepContext context) throws Exception {
        // where to copy artifacts from?
        Run r = from;
        if (r==null)    r=build;

        ArtifactManager am = r.getArtifactManager();

        List<FilePath> files = new ArrayList<FilePath>();

        for (Entry<String, String> e : this.files.entrySet()) {
            FilePath dst = new FilePath(ws,e.getValue());

            String[] all = am.root().list(e.getKey());
            if (all.length==1 && all[0].equals(e.getKey())) {
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
            closeQuietly(in);
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

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Override
        public String getFunctionName() {
            return "unarchive";
        }

        @Override
        public String getDisplayName() {
            return "Copy archived artifacts into the workspace";
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) {
            Object v = arguments.get("value");
            if (v!=null) {
                // sole string invocation like : unarchive('target/x.war')
                return new ArtifactUnarchiverStep(Collections.singletonMap(v.toString(), "."));
            }

            // multi-argument invocations like: unarchive(mapping:['x.txt':'y.txt'], from:anotherBuild)
            return super.newInstance(arguments);
        }
    }

}
