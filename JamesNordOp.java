package com.cloudbees.sidewinder.impl.steps;

import com.cloudbees.sidewinder.graph.FlowNode;
import com.cloudbees.sidewinder.graph.actions.LabelAction;
import com.cloudbees.sidewinder.steps.Step;
import com.cloudbees.sidewinder.steps.StepContext;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.ExceptionCatchingThreadFactory;

import java.io.File;
import java.io.IOException;
import java.lang.InterruptedException;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class JamesNordOp extends Step {

    public static final String FLAG = "CURRENT_SEGMENT";
    private String name;

    @Override
    public boolean start(StepContext context) throws IOException, InterruptedException {
        JobActionImpl actions = context.getContext(Run.class).getParent().getAction(JobActionImpl.class);

        Segment s = actions.getSegment(name);

        String prev = (String) context.getGlobalVariable(FLAG);
        actions.getSegment(prev).notifyDone(context);

        s.set(context);

        return false;
    }

    public static class JobActionImpl {
        private transient final Job job;
        List<Segment> segments;


        public Segment getSegment(String name) {
            throw new UnsupportedOperationException();
        }

        public void save() {
            new XmlFile(new File(job.getRootDir(),"jamesNord.xml")).write(this);
        }
    }

    // this would be a job-scoped object
    // when this rehydrates, we need a way to find stale StepContext and remove it from #running
    // TODO: segment to allow arbitrary integer as the cap of the degree of concurrency
    public static class Segment {
        String name;

        StepContext v;

        StepContext/*?*/ running;

        void set(StepContext w) {
            if (w<v) {
                kill(w);
                return;
            }  else {
                kill(v);
                v = w;

                // this will tell the previous segment that it is done
                notifyAll();

                if (running==null)
                    goNow();
            }
        }

        void kill(StepContext w) {
            w.getExecution().finish(Result.SUCCESS);
        }

        StepContext get() {
            StepContext w = v;
            v = null;
            running = w;
            return w;
        }

        public void notifyDone(StepContext v) {
            running = null;
            if (v!=null)
                goNow();
        }

        private void goNow() {
            v.reportSuccess(null);
            v.setGlobalVariable(FLAG, name);
            v.getContext(FlowNode.class).addAction(new LabelAction(name));
            v = null;
        }
    }
}
