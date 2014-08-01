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
import hudson.AbortException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ArtifactUnarchiverStep extends AbstractStepImpl {
    /**
     * Files to copy over.
     */
    /*package*/ Map<String, String> files;

    @DataBoundSetter
    /*package*/ Run from;

    @DataBoundConstructor
    public ArtifactUnarchiverStep(Map<String,String> mapping) {
        this.files = mapping;
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
        public Step newInstance(Map<String, Object> arguments) throws Exception {
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
