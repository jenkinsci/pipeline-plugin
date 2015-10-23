package org.jenkinsci.plugins.workflow.support.steps.build;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.util.StaplerReferer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class BuildTriggerStep extends AbstractStepImpl {

    private final String job;
    private List<ParameterValue> parameters;
    private boolean wait = true;
    private boolean propagate = true;
    private Integer quietPeriod;

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

    public Integer getQuietPeriod() {
        return quietPeriod;
    }

    @DataBoundSetter public void setQuietPeriod(Integer quietPeriod) {
        this.quietPeriod = quietPeriod;
    }

    public boolean isPropagate() {
        return propagate;
    }

    @DataBoundSetter public void setPropagate(boolean propagate) {
        this.propagate = propagate;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(BuildTriggerStepExecution.class);
        }

        @Override public Step newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            BuildTriggerStep step = (BuildTriggerStep) super.newInstance(req, formData);
            // Cf. ParametersDefinitionProperty._doBuild:
            Object parameter = formData.get("parameter");
            JSONArray params = parameter != null ? JSONArray.fromObject(parameter) : null;
            if (params != null) {
                Jenkins jenkins = Jenkins.getInstance();
                Job<?,?> context = StaplerReferer.findItemFromRequest(Job.class);
                Job<?,?> job = jenkins != null ? jenkins.getItem(step.getJob(), context, Job.class) : null;
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
            return "Build a job";
        }

        public AutoCompletionCandidates doAutoCompleteJob(@AncestorInPath ItemGroup<?> context, @QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(ParameterizedJobMixIn.ParameterizedJob.class, value, context);
        }

        @Restricted(DoNotUse.class) // for use from config.jelly
        public String getContext() {
            Job<?,?> job = StaplerReferer.findItemFromRequest(Job.class);
            return job != null ? job.getFullName() : null;
        }

        public FormValidation doCheckPropagate(@QueryParameter boolean value, @QueryParameter boolean wait) {
            if (!value && !wait) {
                return FormValidation.errorWithMarkup("It makes no sense to specify <code>propagate</code> if <code>wait</code> is disabled.");
            }
            return FormValidation.ok();
        }

    }
}
