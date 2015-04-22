package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyShell;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import javax.annotation.CheckForNull;
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
     *
     * @param context
     *      null if {@link GroovyShell} is created just to test the parsing of the script.
     */
    public void customizeImports(@CheckForNull CpsFlowExecution context, ImportCustomizer ic) {}

    /**
     * Called with {@link CompilerConfiguration} to provide opportunity to tweak the runtime environment further.
     *
     * @param context
     *      null if {@link GroovyShell} is created just to test the parsing of the script.
     */
    public void configureCompiler(@CheckForNull CpsFlowExecution context, CompilerConfiguration cc) {}

    /**
     * Called with a configured {@link GroovyShell} to further tweak its behaviours.
     * @param context null if {@link GroovyShell} is created just to test the parsing of the script.
     */
    public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {}

    public static ExtensionList<GroovyShellDecorator> all() {
        return ExtensionList.lookup(GroovyShellDecorator.class);
    }
}
