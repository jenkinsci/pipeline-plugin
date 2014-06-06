package org.jenkinsci.plugins.workflow.steps.pause;

import hudson.Extension;
import hudson.Util;
import hudson.model.Failure;
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
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Step} that pauses for human input.
 *
 * @author Kohsuke Kawaguchi
 */
public class PauseStep extends AbstractStepImpl implements ModelObject {
    private final String message;

    /**
     * Optional ID that uniquely identifies this pause from all others.
     */
    @DataBoundSetter
    private String id;

    /**
     * Optional user/group name who can approve this.
     */
    @DataBoundSetter
    private String approveBy;


    /**
     * Either a single {@link ParameterDefinition} or a map of them.
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
     * Result of the pause.
     */
    private Outcome outcome;

    @DataBoundConstructor
    public PauseStep(String message) {
        if (message==null)
            message = "Workflow has paused and needs your input before proceeding";
        this.message = message;
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

    public Map<String,ParameterDefinition> getParameters() {
        if (params instanceof ParameterDefinition)
            return Collections.singletonMap("value",(ParameterDefinition)params);
        if (params instanceof Map)
            return (Map)params;
        if (params==null)
            return Collections.emptyMap();
        throw new IllegalStateException("Unexpected parameters: "+params);
    }

    public Run getRun() {
        return run;
    }

    public String getMessage() {
        return message;
    }

    public String getId() {
        if (id==null)
            id = Util.getDigestOf(message);
        return id;
    }

    /**
     * If this pause step has been decided one way or the other.
     */
    public boolean isSettled() {
        return outcome!=null;
    }

    @Override
    public boolean doStart(StepContext context) throws Exception {
        this.context = context;

        // record this pause
        getPauseAction().add(this);

        return false;
    }

    /**
     * Gets the {@link PauseAction} that this step should be attached to.
     */
    private PauseAction getPauseAction() {
        PauseAction a = run.getAction(PauseAction.class);
        if (a==null)
            run.addAction(a=new PauseAction());
        return a;
    }

    public HttpResponse doProceed(StaplerRequest request, @QueryParameter boolean redirect) throws IOException, ServletException {
        preSettlementCheck();

        if (request.getParameter("proceed")!=null) {
            Object v = parseValue(request);
            outcome = new Outcome(v, null);
            context.onSuccess(v);
        } else {
            RejectionException e = new RejectionException(User.current());
            outcome = new Outcome(null,e);
            context.onFailure(e);
        }

        // TODO: record this decision to FlowNode

        return postSettlement(redirect);
    }

    /**
     * Parse the submitted {@link ParameterValue}s
     */
    private Object parseValue(StaplerRequest request) throws ServletException {
        Map<String, Object> mapResult = new HashMap<String, Object>();
        Map<String, ParameterDefinition> defs = getParameters();

        JSONArray a = request.getSubmittedForm().optJSONArray("parameter");
        if (a!=null) {
            for (Object o : a) {
                JSONObject jo = (JSONObject) o;
                String name = jo.getString("name");

                ParameterDefinition d = defs.get(name);
                if (d == null)
                    throw new IllegalArgumentException("No such parameter definition: " + name);

                ParameterValue v = d.createValue(request, jo);
                // TODO: we want v.getValueObject() kind of method
                mapResult.put(name, v);
            }
        }

        // TODO: perhaps we should return a different object to allow the workflow to look up
        // who approved it, etc?
        switch (defs.size()) {
        case 0:
            return null;    // no value if there's no parameter
        case 1:
            return mapResult.values().iterator().next();
        default:
            return mapResult;
        }
    }

    /**
     * Common check to run enforce we approve/deny the outcome of the settlement.
     */
    private void preSettlementCheck() {
        if (isSettled())
            throw new Failure("This pause step has been already decided");
        // TODO: permission check
        if (approveBy!=null && !canSettle()) {
            throw new Failure("You need to be "+approveBy+" to approve this");
        }
    }

    private HttpResponse postSettlement(boolean redirect) throws IOException {
        getPauseAction().remove(this);
        run.save();
        return redirect ? HttpResponses.forwardToPreviousPage() : HttpResponses.ok();
    }

    private boolean canSettle() {
        Authentication a = Jenkins.getAuthentication();
        return canSettle(a);
    }

    /**
     * Checks if the given user can settle this pause.
     */
    public boolean canSettle(Authentication a) {
        if (a.getName().equals(approveBy))
            return true;
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (ga.getAuthority().equals(approveBy))
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
            return "Human Input";
        }
    }
}
