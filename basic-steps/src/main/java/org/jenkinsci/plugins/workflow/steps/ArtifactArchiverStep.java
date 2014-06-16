package org.jenkinsci.plugins.workflow.steps;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Artifact archiving.
 *
 * Could be a throw-away until {@link BuildStep} interop.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiverStep extends AbstractSynchronousStepImpl<Void> {

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient EnvVars envVars;

    @StepContextParameter
    private transient Run build;

    @StepContextParameter
    private transient Launcher launcher;

    @DataBoundSetter
    String includes;

    @DataBoundSetter
    String excludes;

    @DataBoundSetter
    boolean fingerprint = true;

    @DataBoundConstructor
    public ArtifactArchiverStep(String value) {
        this.includes = value;
    }

    @Override
    protected Void run(StepContext context) throws Exception {
        String includes = envVars.expand(this.includes);
        Map<String,String> files = ws.act(new ListFiles(includes, excludes));
        build.pickArtifactManager().archive(ws, launcher, fakeBuildListener(), files);
        return null;
    }

    private BuildListener fakeBuildListener() {
        return (BuildListener)Proxy.newProxyInstance(BuildListener.class.getClassLoader(),new Class[]{BuildListener.class},new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return method.invoke(listener,args);
            }
        });
    }

    private static final class ListFiles implements FilePath.FileCallable<Map<String,String>> {
        private static final long serialVersionUID = 1;
        private final String includes, excludes;
        ListFiles(String includes, String excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }
        @Override public Map<String,String> invoke(File basedir, VirtualChannel channel) throws IOException, InterruptedException {
            Map<String,String> r = new HashMap<String,String>();
            for (String f : Util.createFileSet(basedir, includes, excludes).getDirectoryScanner().getIncludedFiles()) {
                f = f.replace(File.separatorChar, '/');
                r.put(f, f);
            }
            return r;
        }
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Override
        public String getFunctionName() {
            return "archive";
        }

        @Override
        public String getDisplayName() {
            return "Archive Artifacts";
        }
    }
}
