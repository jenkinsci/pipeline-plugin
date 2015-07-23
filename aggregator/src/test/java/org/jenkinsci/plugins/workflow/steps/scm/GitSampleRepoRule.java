/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.triggers.SCMTrigger;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Manages a sample Git repository.
 */
public class GitSampleRepoRule extends ExternalResource {

    private final TemporaryFolder tmp;
    private File sampleRepo;

    public GitSampleRepoRule() {
        this.tmp = new TemporaryFolder();
    }

    @Override protected void before() throws Throwable {
        tmp.create();
        sampleRepo = tmp.newFolder();
        git("init");
        write("file", "");
        git("add", "file");
        git("commit", "--message=init");
    }

    @Override protected void after() {
        tmp.delete();
    }

    public void write(String rel, String text) throws IOException {
        FileUtils.write(new File(sampleRepo, rel), text);
    }

    public void git(String... cmds) throws Exception {
        List<String> args = new ArrayList<String>();
        args.add("git");
        args.addAll(Arrays.asList(cmds));
        SubversionStepTest.run(sampleRepo, args.toArray(new String[args.size()]));
    }

    @Override public String toString() {
        return sampleRepo.getAbsolutePath();
    }

    public void notifyCommit(JenkinsRule r) throws Exception {
        synchronousPolling(r);
        System.out.println(r.createWebClient().goTo("git/notifyCommit?url=" + URLEncoder.encode(toString(), "UTF-8"), "text/plain").getWebResponse().getContentAsString());
        r.waitUntilNoActivity();
    }
    /** Otherwise {@link JenkinsRule#waitUntilNoActivity()} is ineffective when we have just pinged a commit notification endpoint. */
    private void synchronousPolling(JenkinsRule r) {
        r.jenkins.getDescriptorByType(SCMTrigger.DescriptorImpl.class).synchronousPolling = true;
    }


}
