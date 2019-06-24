package org.jenkinsci.plugins.blockqueuedjob.condition;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import hudson.model.queue.CauseOfBlockage;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Copyright © 2016-2019 Dell Inc. or its subsidiaries.
 * All Rights Reserved.
 */
public class JobAndParamBlockQueueCondition extends BlockQueueCondition {

    @CheckForNull
    private final String jobName;

    @CheckForNull
    private final List<StringParameterValue> blockingParams;

    /**
     * Constructor
     *
     * @param jobName        The name of the job to block on if running.
     * @param blockingParams The params to check when looking to ignore the block.
     */
    @DataBoundConstructor
    public JobAndParamBlockQueueCondition(String jobName, List<StringParameterValue> blockingParams) {
        this.jobName = jobName;
        this.blockingParams = (blockingParams == null) ? Collections.<StringParameterValue>emptyList() : blockingParams;
    }

    public String getJobName() {
        return this.jobName;
    }

    public List<StringParameterValue> getBlockingParams() {
        return this.blockingParams;
    }

    @Override
    public CauseOfBlockage isBlocked(Queue.Item item) {

        CauseOfBlockage blocked = null;
        Jenkins instance = Jenkins.getActiveInstance();

        final Item targetJob = instance.getItemByFullName(jobName);

        if (targetJob instanceof Job<?, ?>) {
            final Job<?, ?> job = (Job<?, ?>) targetJob;

            if (job.isBuilding()) {

                if (CollectionUtils.isEmpty(blockingParams)) {
                    blocked = new CauseOfBlockage() {
                        @Override
                        public String getShortDescription() {
                            return String.format("Job %s is currently building.", job.getFullName());
                        }
                    };

                } else {

                    ParametersAction params = item.getAction(ParametersAction.class);
                    List<ParameterValue> paramsList = (params != null) ? params.getParameters() : Collections.<ParameterValue>emptyList();

                    if (!paramsList.isEmpty() && paramsList.containsAll(blockingParams)) {

                        blocked = new CauseOfBlockage() {
                            @Override
                            public String getShortDescription() {
                                return String.format("Job %s is currently building and params are matched", job.getFullName());
                            }
                        };

                    }
                }

            }

        }

        return blocked;
    }

    @Extension
    public static class DescriptorImpl extends BlockQueueConditionDescriptor {

        public AutoCompletionCandidates doAutoCompleteJobName(@QueryParameter String value,
                                                              @AncestorInPath Item self,
                                                              @AncestorInPath ItemGroup container) {
            return AutoCompletionCandidates.ofJobNames(Job.class, value, self, container);
        }

        public FormValidation doCheckJobName(@QueryParameter String jobName) {
            FormValidation formValidation;

            if (StringUtils.isBlank(jobName)) {
                formValidation = FormValidation.error("Job must be specified");
            } else if (Jenkins.getActiveInstance().getItemByFullName(jobName) == null) {
                formValidation = FormValidation.error(String.format("Job: '%s' not found", jobName));
            } else {
                formValidation = FormValidation.ok();
            }

            return formValidation;
        }

        @Override
        public BlockQueueCondition newInstance(StaplerRequest req, JSONObject jsonObject) throws FormException {

            JobAndParamBlockQueueCondition condition = null;
            List<StringParameterValue> blockingParams = new ArrayList<>();

            final String jobName = jsonObject.getString("jobName");
            if (StringUtils.isNotBlank(jobName)) {

                final JSONObject defineBlockingParams = jsonObject.getJSONObject("defineBlockingParams");
                if (defineBlockingParams != null && !defineBlockingParams.isNullObject()) {

                    final JSONArray blockingParamsArrayObj = getBlockingParamsArray(defineBlockingParams);
                    for (int idx = 0; idx < blockingParamsArrayObj.size(); idx++) {
                        checkAndAddBlockingParam(blockingParamsArrayObj.getJSONObject(idx), blockingParams);
                    }
                }

                condition = new JobAndParamBlockQueueCondition(jobName, blockingParams);
            }

            return condition;
        }

        private JSONArray getBlockingParamsArray(final JSONObject defineBlockingParams) {

            JSONArray result = new JSONArray();

            final JSONObject blockingParamsObj = defineBlockingParams.optJSONObject("blockingParams");
            if (blockingParamsObj == null) {
                final JSONArray blockingParamsArrayObj = defineBlockingParams.optJSONArray("blockingParams");
                if (blockingParamsArrayObj != null) {
                    result = blockingParamsArrayObj;
                }
            } else {
                result.add(blockingParamsObj);
            }

            return result;
        }

        private void checkAndAddBlockingParam(final JSONObject blockingParamsObj, final List<StringParameterValue> blockingParams) {

            final String name = blockingParamsObj.getString("name");
            final String value = blockingParamsObj.getString("value");

            if (StringUtils.isNotBlank(name) && StringUtils.isNotEmpty(value)) {
                blockingParams.add(new StringParameterValue(name, value));
            }

        }

        @Override
        public String getDisplayName() {
            return "Block when a specific Job is running";
        }
    }

}
