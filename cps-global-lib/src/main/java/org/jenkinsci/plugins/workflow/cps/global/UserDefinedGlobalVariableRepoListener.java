/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.workflow.cps.global;

import hudson.Extension;
import javax.inject.Inject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link WorkflowLibRepositoryListener} for {@link UserDefinedGlobalVariable}s.
 */
@Extension
@Restricted({NoExternalUse.class})
public class UserDefinedGlobalVariableRepoListener extends WorkflowLibRepositoryListener {
    @Inject
    UserDefinedGlobalVariableList globalVariableList;

    /**
     * Called when the {@link WorkflowLibRepository} receives a pack, rebuilding the list of
     * {@link UserDefinedGlobalVariable}s.
     */
    @Override
    public void repositoryUpdated() {
        globalVariableList.rebuild();
    }
}
