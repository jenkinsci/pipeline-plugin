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

package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * A special revision state that can accommodate multiple SCMs, using {@link SCM#getKey}.
 * Should be attached to a flow run when running checkouts.
 */
final class MultiSCMRevisionState extends SCMRevisionState {

	private final Map<String,SCMRevisionState> revisionStates;

	MultiSCMRevisionState() {
		revisionStates = new HashMap<String, SCMRevisionState>();
	}

	public void add(@Nonnull SCM scm, @Nonnull SCMRevisionState scmState) {
        String key = scm.getKey();
        SCMRevisionState old = revisionStates.put(key, scmState);
        if (old != null) {
            Logger.getLogger(MultiSCMRevisionState.class.getName()).log(Level.WARNING, "overriding old revision state {0} from {1}", new Object[] {old, key});
        }
	}

	public SCMRevisionState get(@Nonnull SCM scm) {
		return revisionStates.get(scm.getKey());
	}

    @Override public String toString() {
        return "MultiSCMRevisionState" + revisionStates;
    }

}
