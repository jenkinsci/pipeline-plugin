package org.jenkinsci.plugins.workflow.cps.global;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

import javax.inject.Inject;
import java.io.File;
import java.net.MalformedURLException;

/**
 * Adds the global shared library space into {@link GroovyClassLoader} classpath.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Inject
    WorkflowLibRepository repo;

    @Override
    public void configureShell(CpsFlowExecution context, GroovyShell shell) {
        try {
            shell.getClassLoader().addURL(new File(repo.workspace,"src").toURL());
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }
}
