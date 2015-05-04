package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyShell;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
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

    /**
     * Allows plugins to expose pseudovariables to Groovy scripts.
     * Non-null return values are not cached from call to call, so if the variable should be stateful,
     * you would need to save its value somehow, such as in {@link CpsScript#getBinding}.
     * @param script the calling script
     * @param property the name of a property passed to {@link CpsScript#getProperty}
     * @return a value for the property, or null
     * @throws Exception if there is any problem, it will be thrown up to the script
     */
    public @CheckForNull Object getProperty(@Nonnull CpsScript script, @Nonnull String property) throws Exception {
        return null;
    }

    public static ExtensionList<GroovyShellDecorator> all() {
        return ExtensionList.lookup(GroovyShellDecorator.class);
    }
}
