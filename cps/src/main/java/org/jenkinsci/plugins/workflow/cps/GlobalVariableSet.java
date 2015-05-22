package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Iterator;

/**
 * Extension point that defines a collection of global variables.
 * 
 * @since 1.7
 * @author Kohsuke Kawaguchi
 * @see GlobalVariable#ALL
 */
public abstract class GlobalVariableSet implements ExtensionPoint, Iterable<GlobalVariable> {

    /**
     * Allow {@link GlobalVariable}s to be defined with {@link Extension}, and make them discoverable
     * via {@link GlobalVariableSet}. This simplifies the registration of single global variable.
     */
    @Restricted(NoExternalUse.class)
    public static class GlobalVariableProvider extends GlobalVariableSet {
        @Override
        public Iterator<GlobalVariable> iterator() {
            return Jenkins.getInstance().getExtensionList(GlobalVariable.class).iterator();
        }
    }
}
