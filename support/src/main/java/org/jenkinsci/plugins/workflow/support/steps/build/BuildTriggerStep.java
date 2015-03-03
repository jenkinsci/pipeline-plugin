package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Vivek Pandey
 */
public class BuildTriggerStep extends AbstractStepImpl {

    private final String job;
    private List<ParameterValue> parameters;
    private boolean wait = true;

    @DataBoundConstructor
    public BuildTriggerStep(String job) {
        this.job = job;
    }

    public String getJob() {
        return job;
    }

    public List<ParameterValue> getParameters() {
        return parameters;
    }

    @DataBoundSetter public void setParameters(List<ParameterValue> parameters) {
        this.parameters = parameters;
    }

    public boolean getWait() {
        return wait;
    }

    @DataBoundSetter public void setWait(boolean wait) {
        this.wait = wait;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(BuildTriggerStepExecution.class);
        }

        @Override public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            BuildTriggerStep step = (BuildTriggerStep) super.newInstance(req, formData);
            // Cf. ParametersDefinitionProperty._doBuild:
            JSONArray params = formData.optJSONArray("parameter");
            if (params != null) {
                Job<?,?> job = Jenkins.getInstance().getItemByFullName(step.getJob(), Job.class);
                if (job != null) {
                    ParametersDefinitionProperty pdp = job.getProperty(ParametersDefinitionProperty.class);
                    if (pdp != null) {
                        List<ParameterValue> values = new ArrayList<ParameterValue>();
                        for (Object o : params) {
                            JSONObject jo = (JSONObject) o;
                            String name = jo.getString("name");
                            ParameterDefinition d = pdp.getParameterDefinition(name);
                            if (d == null) {
                                throw new IllegalArgumentException("No such parameter definition: " + name);
                            }
                            ParameterValue parameterValue = d.createValue(req, jo);
                            if (parameterValue != null) {
                                values.add(parameterValue);
                            } else {
                                throw new IllegalArgumentException("Cannot retrieve the parameter value: " + name);
                            }
                        }
                        step.setParameters(values);
                    }
                }
            }
            return step;
        }

        @Override
        public String getFunctionName() {
            return "build";
        }

        @Override
        public String getDisplayName() {
            return "Build a Job";
        }

        public AutoCompletionCandidates doAutoCompleteJob(@AncestorInPath ItemGroup<?> context, @QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(ParameterizedJobMixIn.ParameterizedJob.class, value, context);
        }

    }
}
