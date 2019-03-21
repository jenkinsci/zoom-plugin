package io.jenkins.plugins.zoom;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;

@Data
@Slf4j
public class ZoomNotifier extends Notifier{

    @DataBoundSetter
    private String webhookUrl;
    @DataBoundSetter
    private String authToken;
    @DataBoundSetter
    private boolean notifyStart;
    @DataBoundSetter
    private boolean notifySuccess;
    @DataBoundSetter
    private boolean notifyAborted;
    @DataBoundSetter
    private boolean notifyNotBuilt;
    @DataBoundSetter
    private boolean notifyUnstable;
    @DataBoundSetter
    private boolean notifyFailure;
    @DataBoundSetter
    private boolean notifyRegression;
    @DataBoundSetter
    private boolean notifyBackToNormal;
    @DataBoundSetter
    private boolean notifyRepeatedFailure;
    @DataBoundSetter
    private boolean includeCommitInfo;
    @DataBoundSetter
    private boolean includeTestSummary;
    @DataBoundSetter
    private boolean includeFailedTests;
    public static final String START_STATUS_MESSAGE = "Start",
            BACK_TO_NORMAL_STATUS_MESSAGE = "Back to normal",
            STILL_FAILING_STATUS_MESSAGE = "Still Failing",
            SUCCESS_STATUS_MESSAGE = "Success",
            FAILURE_STATUS_MESSAGE = "Failure",
            ABORTED_STATUS_MESSAGE = "Aborted",
            NOT_BUILT_STATUS_MESSAGE = "Not built",
            UNSTABLE_STATUS_MESSAGE = "Unstable",
            REGRESSION_STATUS_MESSAGE = "Regression",
            UNKNOWN_STATUS_MESSAGE = "Unknown";

    @DataBoundConstructor
    public ZoomNotifier() {

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener){
        log.info("Prebuild: " + build.getProject().getFullDisplayName());
        listener.getLogger().println("---------------------- Prebuild ----------------------");
        if(notifyStart){
            MessageBuilder messageBuilder = new MessageBuilder(this, build, listener);
            ZoomNotifyClient.notify(this.webhookUrl, this.authToken, messageBuilder.prebuild(isIncludeCommitInfo()));
        }
        return super.prebuild(build, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        log.info("Perform: " + build.getProject().getFullDisplayName());
        listener.getLogger().println("---------------------- Perform ----------------------");
        String status = getStatusMessage(build);
        if(notifyPerform(status)){
            MessageBuilder messageBuilder = new MessageBuilder(this, build, listener);
            ZoomNotifyClient.notify(this.webhookUrl, this.authToken, messageBuilder.build(status, isIncludeTestSummary(), isIncludeFailedTests()));
        }
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    private boolean notifyPerform(String status){
        if ((ABORTED_STATUS_MESSAGE.equals(status) && this.isNotifyAborted())
                ||(NOT_BUILT_STATUS_MESSAGE.equals(status) && this.isNotifyNotBuilt())
                ||(UNSTABLE_STATUS_MESSAGE.equals(status) && this.isNotifyUnstable())
                ||(STILL_FAILING_STATUS_MESSAGE.equals(status) && this.isNotifyRepeatedFailure())
                ||(FAILURE_STATUS_MESSAGE.equals(status) && this.isNotifyFailure())
                ||(SUCCESS_STATUS_MESSAGE.equals(status) && this.isNotifySuccess())
                ||(BACK_TO_NORMAL_STATUS_MESSAGE.equals(status))){
            return true;
        }
        return false;
    }

    public String getStatusMessage(AbstractBuild build){
        Result result = build.getResult();
        if (null != result){
            if (result == Result.ABORTED) {
                return ABORTED_STATUS_MESSAGE;
            }
            if (result == Result.NOT_BUILT) {
                return NOT_BUILT_STATUS_MESSAGE;
            }
            if (result == Result.UNSTABLE) {
                return UNSTABLE_STATUS_MESSAGE;
            }
            AbstractBuild lastBuild = build.getProject().getLastBuild();
            if (null != lastBuild) {
                Result previousResult;
                Run previousBuild = lastBuild.getPreviousBuild();
                Run previousSuccessfulBuild = build.getPreviousSuccessfulBuild();
                boolean buildHasSucceededBefore = previousSuccessfulBuild != null;

                /*
                 * If the last build was aborted, go back to find the last non-aborted build.
                 * This is so that aborted builds do not affect build transitions.
                 * I.e. if build 1 was failure, build 2 was aborted and build 3 was a success the transition
                 * should be failure -> success (and therefore back to normal) not aborted -> success.
                 */
                Run lastNonAbortedBuild = previousBuild;
                while (lastNonAbortedBuild != null && lastNonAbortedBuild.getResult() == Result.ABORTED) {
                    lastNonAbortedBuild = lastNonAbortedBuild.getPreviousBuild();
                }


                /* If all previous builds have been aborted, then use
                 * SUCCESS as a default status so an aborted message is sent
                 */
                if (lastNonAbortedBuild == null) {
                    previousResult = Result.SUCCESS;
                } else {
                    previousResult = lastNonAbortedBuild.getResult();
                }

                if (result == Result.SUCCESS
                        && (previousResult == Result.FAILURE || previousResult == Result.UNSTABLE)
                        && buildHasSucceededBefore && this.isNotifyBackToNormal()) {
                    return BACK_TO_NORMAL_STATUS_MESSAGE;
                }
                if (result == Result.FAILURE && previousResult == Result.FAILURE) {
                    return STILL_FAILING_STATUS_MESSAGE;
                }
            }
            if (result == Result.SUCCESS) {
                return SUCCESS_STATUS_MESSAGE;
            }
            if (result == Result.FAILURE) {
                return FAILURE_STATUS_MESSAGE;
            }
        }
        return UNKNOWN_STATUS_MESSAGE;
    }


    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher>{

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return FreeStyleProject.class.isAssignableFrom(aClass);
        }

        @Override
        public String getDisplayName() {
            return "Zoom Build Notifier";
        }

        public FormValidation doTestConnection(@QueryParameter("webhookUrl") final String webhookUrl,
                                               @QueryParameter("authToken") final String authToken){
            if(ZoomNotifyClient.notify(webhookUrl, authToken, null)){
                return FormValidation.ok("Connection is ok");
            }
            return FormValidation.error("Connect failed");
        }

//        public FormValidation doCheckWebhookUrl(@QueryParameter String value) {
//            if (Util.fixEmptyAndTrim(value) == null) {
//                return FormValidation.error("URL cannot be empty");
//            }
//            return FormValidation.ok();
//        }
//
//        public FormValidation doCheckAuthToken(@QueryParameter String value) {
//            if (Util.fixEmptyAndTrim(value) == null) {
//                return FormValidation.error("Token cannot be empty");
//            }
//            return FormValidation.ok();
//        }

    }
}
