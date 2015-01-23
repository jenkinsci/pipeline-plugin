/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.cps;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.httpclient.NameValuePair;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsFlowDefinitionTest2 {

    @Rule public JenkinsRule jenkins = new JenkinsRule();

    @Test public void generateSnippet() throws Exception {
        JenkinsRule.WebClient wc = jenkins.createWebClient();
        WebRequestSettings wrs = new WebRequestSettings(new URL(jenkins.getURL(), jenkins.jenkins.getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class).getDescriptorUrl() + "/generateSnippet"), HttpMethod.POST);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("json", "{'stapler-class':'org.jenkinsci.plugins.workflow.steps.EchoStep', 'message':'hello world'}"));
        // WebClient.addCrumb *replaces* rather than *adds*:
        params.add(new NameValuePair(jenkins.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(), jenkins.jenkins.getCrumbIssuer().getCrumb(null)));
        wrs.setRequestParameters(params);
        WebResponse response = wc.getPage(wrs).getWebResponse();
        assertEquals("text/plain", response.getContentType());
        assertEquals("echo 'hello world'\n", response.getContentAsString());
    }

}
