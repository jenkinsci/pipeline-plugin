/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow.support.steps.input;

import hudson.Extension;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.HyperlinkNote;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Hyperlink which sends a POST request to the specified URL.
 */
public final class POSTHyperlinkNote extends HyperlinkNote {

    private static final Logger LOGGER = Logger.getLogger(POSTHyperlinkNote.class.getName());
    
    public static String encodeTo(String url, String text) {
        try {
            return new POSTHyperlinkNote(url, text.length()).encode() + text;
        } catch (IOException e) {
            // impossible, but don't make this a fatal problem
            LOGGER.log(Level.WARNING, "Failed to serialize " + POSTHyperlinkNote.class, e);
            return text;
        }
    }

    private final String url;

    public POSTHyperlinkNote(String url, int length) {
        super("#", length);
        if (url.startsWith("/")) {
            StaplerRequest req = Stapler.getCurrentRequest();
            // When req is not null?
            if (req != null) {
                url = req.getContextPath() + url;
            } else {
                Jenkins j = Jenkins.getInstance();
                if (j != null) {
                    String rootUrl = j.getRootUrl();
                    if (rootUrl != null) {
                        url = rootUrl + url.substring(1);
                    } else {
                        // hope that / works, i.e., that there is no context path
                        // TODO: Does not works when there is a content path, p.e. http://localhost:8080/jenkins
                        // This message log should be an error.
                        LOGGER.warning("You need to define the root URL of Jenkins");
                    }
                }
            }
        }
        this.url = url;
    }

    @Override protected String extraAttributes() {
        // TODO perhaps add hoverNotification
        // TODO do we need to add a crumb if security is enabled?
        return " onclick=\"new Ajax.Request('" + url + "'); false\"";
    }

    // TODO why does there need to be a descriptor at all?
    @Extension public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        @Override public String getDisplayName() {
            return "POST Hyperlinks";
        }
    }

}
