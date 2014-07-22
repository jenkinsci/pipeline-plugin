package org.jenkinsci.plugins.workflow.steps.input;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Failure;
import hudson.model.FileParameterValue;
import hudson.model.ModelObject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.User;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
    @DataBoundSetter
    private String submitter;


    /**
     * Either a single {@link ParameterDefinition} or a list of them.
     */
    @DataBoundSetter
    private Object params;

    /**
     * Caption of the OK button.
     */
    @DataBoundSetter
    private String ok;

    @DataBoundConstructor
    public InputStep(String message) {
        if (message==null)
            message = "Workflow has paused and needs your input before proceeding";
        this.message = message;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = capitalize(id);
    }

    public String getId() {
        if (id==null)
            id = capitalize(Util.getDigestOf(message));
        return id;
    }

    public String getSubmitter() {
        return submitter;
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

    public List<ParameterDefinition> getParameters() {
        if (params instanceof ParameterDefinition)
            return Collections.singletonList((ParameterDefinition)params);
        if (params instanceof ParameterDefinition[])
            return Arrays.asList((ParameterDefinition[]) params);
        if (params instanceof List)
            return (List)params;
        if (params==null)
            return Collections.emptyList();
        throw new IllegalStateException("Unexpected parameters: "+params);
    }

    public String getMessage() {
        return message;
    }

    public boolean canSubmit() {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this input.
     */
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
        @Override
        public String getFunctionName() {
            return "input";
        }

        @Override
        public String getDisplayName() {
            return "Input";
        }
    }
}
