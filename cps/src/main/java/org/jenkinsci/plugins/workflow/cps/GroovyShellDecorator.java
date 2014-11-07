package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyShell;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

/**
 * Hook to customize the behaviour of {@link GroovyShell}
 * used to run workflow scripts.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class GroovyShellDecorator implements ExtensionPoint {

    /**
     * Called with {@link ImportCustomizer} to auto-import more packages, etc.
     */
    public void customizeImports(CpsFlowExecution context, ImportCustomizer ic) {}

    /**
     * Called with {@link CompilerConfiguration} to provide opportunity to tweak the runtime environment further.
     */
    public void configureCompiler(CpsFlowExecution context, CompilerConfiguration cc) {}

    /**
     * Called with a configured {@link GroovyShell} to further tweak its behaviours.
     */
    public void configureShell(CpsFlowExecution context, GroovyShell shell) {}

    public static ExtensionList<GroovyShellDecorator> all() {
        return ExtensionList.lookup(GroovyShellDecorator.class);
    }
}
