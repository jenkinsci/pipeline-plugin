/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.persistence;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import hudson.Extension;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.pickles.PickleFactory;

/**
 * Makes Java iterators effectively serializable.
 */
@Extension public class IteratorHack extends PickleFactory {

    private static final Logger LOGGER = Logger.getLogger(IteratorHack.class.getName());
    private static final Set<String> ACCEPTED_TYPES = ImmutableSet.of("java.util.ArrayList$Itr");

    @Override public Pickle writeReplace(Object object) {
        if (ACCEPTED_TYPES.contains(object.getClass().getName())) {
            return new Replacement(object);
        } else {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class Replacement extends Pickle {

        private final Class type;
        private final Map<Class,Map<String,Object>> fields;

        Replacement(Object o) {
            type = o.getClass();
            fields = new HashMap<>();
            ReflectionProvider rp = Jenkins.XSTREAM2.getReflectionProvider();
            rp.visitSerializableFields(o, new ReflectionProvider.Visitor() {
                @Override public void visit(String name, Class type, Class definedIn, Object value) {
                    if (name.equals("expectedModCount")) {
                        LOGGER.log(Level.FINER, "ignoring expectedModCount={0}", value);
                        return;
                    }
                    Map<String,Object> fieldsByClass = fields.get(definedIn);
                    if (fieldsByClass == null) {
                        fieldsByClass = new HashMap<>();
                        fields.put(definedIn, fieldsByClass);
                    }
                    fieldsByClass.put(name, value);
                }
            });
            LOGGER.log(Level.FINE, "replacing {0} with {1}", new Object[] {o, fields});
        }

        @Override public ListenableFuture<?> rehydrate() {
            ReflectionProvider rp = Jenkins.XSTREAM2.getReflectionProvider();
            Object o = rp.newInstance(type);
            for (Map.Entry<Class,Map<String,Object>> entry : fields.entrySet()) {
                Class definedIn = entry.getKey();
                for (Map.Entry<String,Object> entry2 : entry.getValue().entrySet()) {
                    String fieldName = entry2.getKey();
                    Object value = entry2.getValue();
                    rp.writeField(o, fieldName, value, definedIn);
                    if (fieldName.equals("this$0")) {
                        try {
                            Field f = AbstractList.class.getDeclaredField("modCount"); // TODO if not handling only ArrayList.Itr, search in superclasses
                            f.setAccessible(true);
                            int modCount = f.getInt(value);
                            LOGGER.log(Level.FINER, "found a modCount={0}", modCount);
                            rp.writeField(o, "expectedModCount", modCount, type); // TODO search in superclasses
                        } catch (Exception x) {
                            return Futures.immediateFailedFuture(x);
                        }
                    }
                }
            }
            LOGGER.log(Level.FINE, "reconstructed {0}", o);
            return Futures.immediateFuture(o);
        }

    }

}
