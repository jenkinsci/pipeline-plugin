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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Step} that pauses for human input.
 *
 * @author Kohsuke Kawaguchi
 */
public class InputStep extends AbstractStepImpl implements ModelObject {
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

    private StepContext context;

    /**
     * Pause gets added here.
     */
    @StepContextParameter
    /*package*/ transient Run run;

    /**
     * Result of the input.
     */
    private Outcome outcome;

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

    @Override
    public String getDisplayName() {
        if (message.length()<32)    return message;
        return message.substring(0,32)+"...";
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

    public Run getRun() {
        return run;
    }

    public String getMessage() {
        return message;
    }

    /**
     * If this input step has been decided one way or the other.
     */
    public boolean isSettled() {
        return outcome!=null;
    }

    @Override
    public boolean doStart(StepContext context) throws Exception {
        this.context = context;

        // record this input
        getPauseAction().add(this);

        return false;
    }

    /**
     * Gets the {@link InputAction} that this step should be attached to.
     */
    private InputAction getPauseAction() {
        InputAction a = run.getAction(InputAction.class);
        if (a==null)
            run.addAction(a=new InputAction());
        return a;
    }

    /**
     * Called from the form via browser to submit/abort this input step.
     */
    public HttpResponse doSubmit(StaplerRequest request) throws IOException, ServletException, InterruptedException {
        if (request.getParameter("proceed")!=null) {
            doProceed(request);
        } else {
            doAbort();
        }

        // go back to the Run page
        return HttpResponses.redirectTo("../../");
    }

    /**
     * REST endpoint to submit the input.
     */
    public HttpResponse doProceed(StaplerRequest request) throws IOException, ServletException, InterruptedException {
        preSubmissionCheck();

        Object v = parseValue(request);
        outcome = new Outcome(v, null);
        context.onSuccess(v);

        // TODO: record this decision to FlowNode

        return HttpResponses.ok();
    }

    /**
     * REST endpoint to abort the workflow.
     */
    public HttpResponse doAbort() throws IOException, ServletException {
        preSubmissionCheck();

        RejectionException e = new RejectionException(User.current());
        outcome = new Outcome(null,e);
        context.onFailure(e);

        // TODO: record this decision to FlowNode

        return HttpResponses.ok();
    }

    /**
     * Parse the submitted {@link ParameterValue}s
     */
    private Object parseValue(StaplerRequest request) throws ServletException, IOException, InterruptedException {
        Map<String, Object> mapResult = new HashMap<String, Object>();
        List<ParameterDefinition> defs = getParameters();

        Object params = request.getSubmittedForm().get("parameter");
        if (params!=null) {
            for (Object o : JSONArray.fromObject(params)) {
                JSONObject jo = (JSONObject) o;
                String name = jo.getString("name");

                ParameterDefinition d=null;
                for (ParameterDefinition def : defs) {
                    if (def.getName().equals(name))
                        d = def;
                }
                if (d == null)
                    throw new IllegalArgumentException("No such parameter definition: " + name);

                ParameterValue v = d.createValue(request, jo);
                mapResult.put(name, convert(name, v));
            }
        }

        // TODO: perhaps we should return a different object to allow the workflow to look up
        // who approved it, etc?
        switch (mapResult.size()) {
        case 0:
            return null;    // no value if there's no parameter
        case 1:
            return mapResult.values().iterator().next();
        default:
            return mapResult;
        }
    }

    private Object convert(String name, ParameterValue v) throws IOException, InterruptedException {
        if (v instanceof FileParameterValue) {
            FileParameterValue fv = (FileParameterValue) v;
            FilePath fp = new FilePath(run.getRootDir()).child(name);
            fp.copyFrom(fv.getFile());
            return fp;
        }

        // TODO: post
        try {
            Method m = v.getClass().getMethod("getValue");
            return m.invoke(v);
        } catch (NoSuchMethodException e) {
            // fall through
        } catch (IllegalAccessException e) {
            // fall through
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to convert value: "+v,e);
        }

        try {
            Field f = v.getClass().getField("value");
            return f.get(v);
        } catch (IllegalAccessException e) {
            // fall through
        } catch (NoSuchFieldException e) {
            // fall through
        }

        // not sure what to do
        return null;
    }

    /**
     * Check if the current user can submit the input.
     */
    private void preSubmissionCheck() {
        if (isSettled())
            throw new Failure("This input has been already given");
        // TODO: permission check
        if (submitter !=null && !canSubmit()) {
            throw new Failure("You need to be "+ submitter +" to submit this");
        }
    }

    private void postSettlement(boolean redirect) throws IOException {
        getPauseAction().remove(this);
        run.save();
    }

    private boolean canSubmit() {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this input.
     */
    public boolean canSettle(Authentication a) {
        if (a.getName().equals(submitter))
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
