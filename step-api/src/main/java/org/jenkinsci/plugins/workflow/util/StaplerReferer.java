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

package org.jenkinsci.plugins.workflow.util;

import hudson.model.Item;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * JENKINS-19413 workaround.
 * Will be deleted in the future.
 */
public class StaplerReferer {

    private static final Logger LOGGER = Logger.getLogger(StaplerReferer.class.getName());

    private static final Pattern REFERER = Pattern.compile(".+?((/job/[^/]+)+)/configure");

    static @CheckForNull String itemFromReferer(@Nonnull String referer) {
        Matcher m = REFERER.matcher(referer);
        if (m.matches()) {
            return URI.create(m.group(1).replace("/job/", "/")).getPath().substring(1);
        } else {
            return null;
        }
    }

    /**
     * Like {@link StaplerRequest#findAncestorObject} except works also in configuration screens when a lazy-load section is newly added.
     */
    public static @CheckForNull <T extends Item> T findItemFromRequest(Class<T> type) {
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request == null) {
            LOGGER.warning("no current request");
            return null;
        }
        T ancestor = request.findAncestorObject(type);
        if (ancestor != null) {
            LOGGER.log(Level.FINE, "found {0} in {1}", new Object[] {ancestor, request.getRequestURI()});
            return ancestor;
        }
        String referer = request.getReferer();
        if (referer != null) {
            String name = itemFromReferer(referer);
            if (name != null) {
                Jenkins jenkins = Jenkins.getInstance();
                if (jenkins == null) {
                    LOGGER.warning("Jenkins is not running");
                    return null;
                }
                Item item = jenkins.getItemByFullName(name);
                if (type.isInstance(item)) {
                    LOGGER.log(Level.FINE, "found {0} from {1}", new Object[] {item, referer});
                    return type.cast(item);
                } else if (item != null) {
                    LOGGER.log(Level.FINE, "{0} was not a {1}", new Object[] {item, type.getName()});
                } else {
                    LOGGER.log(Level.WARNING, "no such item {0}", name);
                }
            } else {
                LOGGER.log(request.findAncestorObject(Item.class) == null ? Level.WARNING : Level.FINE, "unrecognized Referer: {0} from {1}", new Object[] {referer, request.getRequestURI()});
            }
        } else {
            LOGGER.log(Level.WARNING, "no Referer in {0}", request.getRequestURI());
        }
        return null;
    }

    private StaplerReferer() {}

}
