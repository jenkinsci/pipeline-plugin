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

import groovy.lang.GroovyObject;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.Iterators.FlattenIterator;
import jenkins.model.RunAction2;

import javax.annotation.Nonnull;
import java.util.Iterator;

/**
 * Defines a provider of a global variable offered to flows.
 * Within a given flow execution, the variable is assumed to be a singleton.
 * It is created on demand if the script refers to it by name.
 *
 * <p>
 * Should have a view named {@code help} offering usage.
 * 
 * @see GlobalVariableSet
 */
public abstract class GlobalVariable implements ExtensionPoint {

    /**
     * Defines the name of the variable.
     * @return a Java identifier
     */
    public abstract @Nonnull String getName();

    /**
     * Gets or creates the singleton value of the variable.
     * If the object is stateful, and the state should not be managed externally (such as with a {@link RunAction2}),
     * then the implementation is responsible for saving it in the {@link CpsScript#getBinding}.
     * @param script the script we are running
     * @return a POJO or {@link GroovyObject}
     * @throws Exception if there was any problem creating it (will be thrown up to the script)
     * @see CpsScript#getProperty
     */
    public abstract @Nonnull Object getValue(@Nonnull CpsScript script) throws Exception;

    /**
     * Returns all the registered {@link GlobalVariable}s.
     */
    public static final Iterable<GlobalVariable> ALL = new Iterable<GlobalVariable>() {
        @Override
        public Iterator<GlobalVariable> iterator() {
            return new FlattenIterator<GlobalVariable,GlobalVariableSet>(ExtensionList.lookup(GlobalVariableSet.class).iterator()) {
                @Override
                protected Iterator<GlobalVariable> expand(GlobalVariableSet vs) {
                    return vs.iterator();
                }
            };
        }
    };
}
