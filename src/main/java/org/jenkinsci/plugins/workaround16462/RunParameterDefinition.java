/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package org.jenkinsci.plugins.workaround16462;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemVisitor;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.SimpleParameterDefinition;
import javax.annotation.CheckForNull;
import org.kohsuke.stapler.QueryParameter;

public class RunParameterDefinition extends SimpleParameterDefinition {

    private final String projectName;
    private final String runId;

    @DataBoundConstructor
    public RunParameterDefinition(String name, String projectName, String description) {
        super(name, description);
        this.projectName = projectName;
        this.runId = null;
    }

    private RunParameterDefinition(String name, String projectName, String runId, String description) {
        super(name, description);
        this.projectName = projectName;
        this.runId = runId;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof RunParameterValue) {
            RunParameterValue value = (RunParameterValue) defaultValue;
            return new RunParameterDefinition(getName(), value.getRunId(), getDescription());
        } else {
            return this;
        }
    }

    @Exported
    public String getProjectName() {
        return projectName;
    }

    public Job getProject() {
        return Jenkins.getInstance().getItemByFullName(projectName, Job.class);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Run Parameter @ JENKINS-16462";
        }

        @Override
        public ParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(RunParameterDefinition.class, formData);
        }
        
        public AutoCompletionCandidates doAutoCompleteProjectName(@QueryParameter String value) {
            return ofJobNames(Job.class, value, null, Jenkins.getInstance());
        }

    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        if (runId != null) {
            return createValue(runId);
        }

        Run<?,?> lastBuild = getProject().getLastBuild();
        if (lastBuild != null) {
        	return createValue(getExternalizableId(lastBuild));
        } else {
        	return null;
        }
    }

    public String getExternalizableId(Run r) {
        return r.getParent().getFullName() + "#" + r.getNumber();
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        RunParameterValue value = req.bindJSON(RunParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    public RunParameterValue createValue(String value) {
        return new RunParameterValue(getName(), value, getDescription());
    }

    private static <T extends Item> AutoCompletionCandidates ofJobNames(final Class<T> type, final String value, @CheckForNull Item self, ItemGroup container) {
        if (self==container)
            container = self.getParent();

        final AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        class Visitor extends ItemVisitor {
            String prefix;

            Visitor(String prefix) {
                this.prefix = prefix;
            }

            @Override
            public void onItem(Item i) {
                String n = contextualNameOf(i);
                if ((n.startsWith(value) || value.startsWith(n))
                    // 'foobar' is a valid candidate if the current value is 'foo'.
                    // Also, we need to visit 'foo' if the current value is 'foo/bar'
                 && (value.length()>n.length() || !n.substring(value.length()).contains("/"))
                    // but 'foobar/zot' isn't if the current value is 'foo'
                    // we'll first show 'foobar' and then wait for the user to type '/' to show the rest
                 && i.hasPermission(Item.READ)
                    // and read permission required
                ) {
                    if (type.isInstance(i) && n.startsWith(value))
                        candidates.add(n);

                    // recurse
                    String oldPrefix = prefix;
                    prefix = n;
                    super.onItem(i);
                    prefix = oldPrefix;
                }
            }

            private String contextualNameOf(Item i) {
                if (prefix.endsWith("/") || prefix.length()==0)
                    return prefix+i.getName();
                else
                    return prefix+'/'+i.getName();
            }
        }

        if (container==null || container==Jenkins.getInstance()) {
            new Visitor("").onItemGroup(Jenkins.getInstance());
        } else {
            new Visitor("").onItemGroup(container);
            if (value.startsWith("/"))
                new Visitor("/").onItemGroup(Jenkins.getInstance());

            for ( String p="../"; value.startsWith(p); p+="../") {
                container = ((Item)container).getParent();
                new Visitor(p).onItemGroup(container);
            }
        }

        return candidates;
    }
}
