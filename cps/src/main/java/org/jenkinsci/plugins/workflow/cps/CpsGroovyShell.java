package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.NonCPS;
import com.cloudbees.groovy.cps.SandboxCpsTransformer;
import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.io.IOException;

/**
 * {@link GroovyShell} with additional tweaks necessary to run {@link CpsScript}
 *
 * @author Kohsuke Kawaguchi
 */
class CpsGroovyShell extends GroovyShell {
    private final CpsFlowExecution execution;

    CpsGroovyShell(CpsFlowExecution execution) {
        super(makeClassLoader(),new Binding(),makeConfig(execution));
        this.execution = execution;

        for (GroovyShellDecorator d : GroovyShellDecorator.all()) {
            d.configureShell(execution,this);
        }
    }

    private static ClassLoader makeClassLoader() {
        Jenkins j = Jenkins.getInstance();
        return j!=null ? j.getPluginManager().uberClassLoader : CpsGroovyShell.class.getClassLoader();
    }

    private static CompilerConfiguration makeConfig(CpsFlowExecution execution) {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");

        for (GroovyShellDecorator d : GroovyShellDecorator.all()) {
            d.customizeImports(execution,ic);
        }

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(ic);
        cc.addCompilationCustomizers(execution.isSandbox() ? new SandboxCpsTransformer() : new CpsTransformer());
        cc.setScriptBaseClass(CpsScript.class.getName());

        for (GroovyShellDecorator d : GroovyShellDecorator.all()) {
            d.configureCompiler(execution,cc);
        }

        return cc;
    }

    public void prepareScript(Script script) {
        if (script instanceof CpsScript) {
            CpsScript cs = (CpsScript) script;
            cs.execution = execution;
            try {
                cs.initialize();
            } catch (IOException e) {
                // TODO: write a library to let me throw this
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * When loading additional scripts, we need to remember its source text so that
     * when we come back from persistent we can load them again.
     */
    @Override
    public Script parse(GroovyCodeSource codeSource) throws CompilationFailedException {
        Script s = super.parse(codeSource);
        execution.loadedScripts.put(s.getClass().getName(), codeSource.getScriptText());
        prepareScript(s);
        return s;
    }

    /**
     * Used internally to reload the script back when coming back from the persisted state
     * (therefore we don't want to record this.)
     */
    /*package*/ Script reparse(String className, String text) throws CompilationFailedException {
        return super.parse(new GroovyCodeSource(text,className,DEFAULT_CODE_BASE));
    }

    /**
     * Every script we parse get caught into {@code execution.loadedScripts}, so the size
     * yields a unique enough ID.
     */
    @Override
    protected synchronized String generateScriptName() {
        return "Script" + (execution.loadedScripts.size()+1) + ".groovy";
    }
}
