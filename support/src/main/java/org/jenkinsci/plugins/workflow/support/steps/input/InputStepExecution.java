package org.jenkinsci.plugins.workflow.support.steps.input;

import com.google.inject.Inject;
import hudson.FilePath;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Failure;
import hudson.model.FileParameterValue;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

/**
 * @author Kohsuke Kawaguchi
 */
public class InputStepExecution extends AbstractStepExecutionImpl implements ModelObject {
    /**
     * Pause gets added here.
     */
    @StepContextParameter
    /*package*/ transient Run run;

    @StepContextParameter private transient TaskListener listener;

    @StepContextParameter private transient FlowNode node;

    /**
     * Result of the input.
     */
    private Outcome outcome;

    @Inject(optional=true) InputStep input;

    @Override
    public boolean start() throws Exception {
        // record this input
        getPauseAction().add(this);

        // This node causes the flow to pause at this point so we mark it as a "Pause Node".
        node.addAction(new PauseAction("Input"));

        String baseUrl = '/' + run.getUrl() + getPauseAction().getUrlName() + '/';
        if (input.getParameters().isEmpty()) {
            String thisUrl = baseUrl + Util.rawEncode(getId()) + '/';
            listener.getLogger().printf("%s%n%s or %s%n", input.getMessage(),
                    POSTHyperlinkNote.encodeTo(thisUrl + "proceedEmpty", input.getOk()),
                    POSTHyperlinkNote.encodeTo(thisUrl + "abort", "Abort"));
        } else {
            // TODO listener.hyperlink(…) does not work; why?
            // TODO would be even cooler to embed the parameter form right in the build log (hiding it after submission)
            listener.getLogger().println(HyperlinkNote.encodeTo(baseUrl, "Input requested"));
        }
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        doAbort();
    }

    public String getId() {
        return input.getId();
    }

    public InputStep getInput() {
        return input;
    }

    public Run getRun() {
        return run;
    }

    /**
     * If this input step has been decided one way or the other.
     */
    public boolean isSettled() {
        return outcome!=null;
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

    @Override
    public String getDisplayName() {
        String message = getInput().getMessage();
        if (message.length()<32)    return message;
        return message.substring(0,32)+"...";
    }


    /**
     * Called from the form via browser to submit/abort this input step.
     */
    @RequirePOST
    public HttpResponse doSubmit(StaplerRequest request) throws IOException, ServletException, InterruptedException {
        if (request.getParameter("proceed")!=null) {
            doProceed(request);
        } else {
            doAbort();
        }

        // go back to the Run console page
        return HttpResponses.redirectTo("../../console");
    }

    /**
     * REST endpoint to submit the input.
     */
    @RequirePOST
    public HttpResponse doProceed(StaplerRequest request) throws IOException, ServletException, InterruptedException {
        preSubmissionCheck();
        Object v = parseValue(request);
        return proceed(v);
    }

    /**
     * Processes the acceptance (approval) request.
     * This method is used by both {@link #doProceedEmpty()} and {@link #doProceed(StaplerRequest)}
     *
     * @param v An object that represents the parameters sent in the request
     * @return A HttpResponse object that represents Status code (200) indicating the request succeeded normally.
     * @throws IOException
     */
    public HttpResponse proceed(Object v) throws IOException {
        User user = User.current();
        if (user != null){
            run.addAction(new ApproverAction(user.getId()));
            listener.getLogger().println("Approved by " + hudson.console.ModelHyperlinkNote.encodeTo(user));
        }

        outcome = new Outcome(v, null);
        getContext().onSuccess(v);

        postSettlement();

        // TODO: record this decision to FlowNode

        return HttpResponses.ok();
    }

    /**
     * Used from the Proceed hyperlink when no parameters are defined.
     */
    @RequirePOST
    public HttpResponse doProceedEmpty() throws IOException {
        preSubmissionCheck();

        return proceed(null);
    }

    /**
     * REST endpoint to abort the workflow.
     */
    @RequirePOST
    public HttpResponse doAbort() throws IOException, ServletException {
        preAbortCheck();

        FlowInterruptedException e = new FlowInterruptedException(Result.ABORTED, new Rejection(User.current()));
        outcome = new Outcome(null,e);
        getContext().onFailure(e);

        postSettlement();

        // TODO: record this decision to FlowNode

        return HttpResponses.ok();
    }

    /**
     * Check if the current user can abort/cancel the run from the input.
     */
    private void preAbortCheck() {
        if (isSettled()) {
            throw new Failure("This input has been already given");
        } if (!canCancel() && !canSubmit()) {
            throw new Failure("You need to be '"+ input.getSubmitter() +"' (or have Job CANCEL permissions) to cancel this.");
        }
    }

    /**
     * Check if the current user can submit the input.
     */
    private void preSubmissionCheck() {
        if (isSettled())
            throw new Failure("This input has been already given");
        if (!canSubmit()) {
            throw new Failure("You need to be "+ input.getSubmitter() +" to submit this");
        }
    }

    private void postSettlement() throws IOException {
        try {
            getPauseAction().remove(this);
            run.save();
        } finally {
            PauseAction.endCurrentPause(node);
        }
    }

    private boolean canCancel() {
        return getRun().getParent().hasPermission(Job.CANCEL);
    }

    private boolean canSubmit() {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this input.
     */
    private boolean canSettle(Authentication a) {
        String submitter = input.getSubmitter();
        if (submitter==null || a.getName().equals(submitter)) {
            return true;
        }
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (ga.getAuthority().equals(submitter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse the submitted {@link ParameterValue}s
     */
    private Object parseValue(StaplerRequest request) throws ServletException, IOException, InterruptedException {
        Map<String, Object> mapResult = new HashMap<String, Object>();
        List<ParameterDefinition> defs = input.getParameters();

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
                if (v == null) {
                    continue;
                }
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
        } else {
            return v.getValue();
        }
    }

    private static final long serialVersionUID = 1L;
}
