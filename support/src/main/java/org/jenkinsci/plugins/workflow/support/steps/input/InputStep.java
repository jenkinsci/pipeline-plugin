package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.Extension;
import hudson.Util;
import hudson.model.ParameterDefinition;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * {@link Step} that pauses for human input.
 *
 * @author Kohsuke Kawaguchi
 */
public class InputStep extends AbstractStepImpl implements Serializable {
    private final String message;

    /**
     * Optional ID that uniquely identifies this input from all others.
     */
    private String id;

    /**
     * Optional user/group name who can approve this.
     */
    private String submitter;


    /**
     * Either a single {@link ParameterDefinition} or a list of them.
     */
    private List<ParameterDefinition> parameters = Collections.emptyList();

    /**
     * Caption of the OK button.
     */
    private String ok;

    @DataBoundConstructor
    public InputStep(String message) {
        if (message==null)
            message = "Workflow has paused and needs your input before proceeding";
        this.message = message;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = capitalize(Util.fixEmpty(id));
    }

    public String getId() {
        if (id==null)
            id = capitalize(Util.getDigestOf(message));
        return id;
    }

    public String getSubmitter() {
        return submitter;
    }

    @DataBoundSetter public void setSubmitter(String submitter) {
        this.submitter = Util.fixEmptyAndTrim(submitter);
    }

    private String capitalize(String id) {
        if (id==null)
            return null;
        if (id.length()==0)
            throw new IllegalArgumentException();
        // a-z as the first char is reserved for InputAction
        char ch = id.charAt(0);
        if ('a'<=ch && ch<='z')
            id = ((char)(ch-'a'+'A')) + id.substring(1);
        return id;
    }

    /**
     * Caption of the OK button.
     */
    public String getOk() {
        return ok!=null ? ok : "Proceed";
    }

    @DataBoundSetter public void setOk(String ok) {
        this.ok = Util.fixEmptyAndTrim(ok);
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    @DataBoundSetter public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }

    public String getMessage() {
        return message;
    }

    @Deprecated
    public boolean canSubmit() {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this input.
     */
    @Deprecated
    public boolean canSettle(Authentication a) {
        if (submitter==null || a.getName().equals(submitter))
            return true;
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (ga.getAuthority().equals(submitter))
                return true;
        }
        return false;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(InputStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "input";
        }

        @Override
        public String getDisplayName() {
            return "Wait for interactive input";
        }
    }
}
