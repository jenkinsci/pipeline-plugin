package org.jenkinsci.plugins.workflow.cps.global;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.Jenkins;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps {@link UserDefinedGlobalVariable}s in {@link ExtensionList} up-to-date
 * from {@code $JENKINS_HOME/workflow-libs/src/*.groovy}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class UserDefinedGlobalVariableList {
    private @Inject WorkflowLibRepository repo;

    private final ExtensionList<GlobalVariable> globalVariables = Jenkins.getActiveInstance().getExtensionList(GlobalVariable.class);

    /**
     * Rebuilds the list of {@link UserDefinedGlobalVariable}s and update {@link ExtensionList} accordingly.
     */
    public void rebuild() {
        File[] children = new File(repo.workspace,UserDefinedGlobalVariable.PREFIX).listFiles();
        if (children==null) children = new File[0];

        Set<UserDefinedGlobalVariable> gone = new HashSet<UserDefinedGlobalVariable>(
                Util.filter(globalVariables, UserDefinedGlobalVariable.class));


        for (File child : children) {
            if (!child.getName().endsWith(".groovy") || child.isDirectory())
                continue;

            UserDefinedGlobalVariable uv = new UserDefinedGlobalVariable(repo,FilenameUtils.getBaseName(child.getName()));
            if (!gone.remove(uv)) {// if this is a new global variable, we need to add it
                globalVariables.add(uv);
                LOGGER.fine("Registered user-defined global variable "+uv.getName()+" for "+child);
            }
        }

        // remove all the goners
        for (UserDefinedGlobalVariable uv : gone) {
            globalVariables.remove(uv);
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            for (UserDefinedGlobalVariable uv : gone) {
                LOGGER.fine("Unregistered user-defined global variable " + uv.getName());
            }
        }
    }

    /**
     * Generates the initial list.
     */
    @Initializer(fatal=false,after= InitMilestone.EXTENSIONS_AUGMENTED,before=InitMilestone.JOB_LOADED)
    public static void init() {
        Jenkins.getActiveInstance().getExtensionList(UserDefinedGlobalVariableList.class).get(UserDefinedGlobalVariableList.class).rebuild();
    }

    private static final Logger LOGGER = Logger.getLogger(UserDefinedGlobalVariableList.class.getName());
}
