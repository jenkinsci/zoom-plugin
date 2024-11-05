package io.jenkins.plugins.zoom;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.Permission;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

@Slf4j
public class ZoomNotifier extends Notifier {

    private String webhookUrl;
    private Secret authToken;
    private boolean jenkinsProxyUsed;
    private boolean notifyStart;
    private boolean notifySuccess;
    private boolean notifyAborted;
    private boolean notifyNotBuilt;
    private boolean notifyUnstable;
    private boolean notifyFailure;
    private boolean notifyRegression;
    private boolean notifyBackToNormal;
    private boolean notifyRepeatedFailure;
    private boolean includeCommitInfo;
    private boolean includeTestSummary;
    private boolean includeFailedTests;

    @DataBoundConstructor
    public ZoomNotifier() {

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener){
        log.info("Prebuild: {}", build.getProject().getFullDisplayName());
        listener.getLogger().println("---------------------- Prebuild ----------------------");
        if(notifyStart){
            MessageBuilder messageBuilder = new MessageBuilder(this, build, listener);
            ZoomNotifyClient.notify(this.webhookUrl, this.authToken, this.jenkinsProxyUsed, messageBuilder.prebuild());
        }
        return super.prebuild(build, listener);
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        log.info("Perform: {}", build.getProject().getFullDisplayName());
        listener.getLogger().println("---------------------- Perform ----------------------");
        if(notifyPerform(build)){
            MessageBuilder messageBuilder = new MessageBuilder(this, build, listener);
            ZoomNotifyClient.notify(this.webhookUrl, this.authToken, this.jenkinsProxyUsed, messageBuilder.build());
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

    private boolean notifyPerform(Run run){
        if(run.isBuilding()){
            return false;
        }
        ResultTrend trend = ResultTrend.getResultTrend(run);
        switch(trend) {
            case ABORTED:
                return this.isNotifyAborted();
            case NOT_BUILT:
                return this.isNotifyNotBuilt();
            case FAILURE:
                return this.isNotifyFailure();
            case STILL_FAILING:
                return this.isNotifyRepeatedFailure();
            case NOW_UNSTABLE:
                return this.isNotifyUnstable();
            case STILL_UNSTABLE:
                return this.isNotifyUnstable();
            case UNSTABLE:
                return this.isNotifyUnstable();
            case SUCCESS:
                return this.isNotifySuccess();
            case FIXED:
                return this.isNotifySuccess()||this.isNotifyBackToNormal();
            default:
                return false;
        }
    }

    @Symbol("zoomNotifier")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher>{

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Zoom Build Notifier";
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("webhookUrl") final String webhookUrl,
                                               @QueryParameter("authToken") final String authToken,
                                               @QueryParameter("jenkinsProxyUsed") final boolean jenkinsProxyUsed){
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            if(ZoomNotifyClient.notify(webhookUrl, authToken, jenkinsProxyUsed, null)){
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


    public String getWebhookUrl() {
        return webhookUrl;
    }

    public Secret getAuthToken() {
        return authToken;
    }

    public boolean isJenkinsProxyUsed() {
        return jenkinsProxyUsed;
    }

    public boolean isNotifyStart() {
        return notifyStart;
    }

    public boolean isNotifySuccess() {
        return notifySuccess;
    }

    public boolean isNotifyAborted() {
        return notifyAborted;
    }

    public boolean isNotifyNotBuilt() {
        return notifyNotBuilt;
    }

    public boolean isNotifyUnstable() {
        return notifyUnstable;
    }

    public boolean isNotifyFailure() {
        return notifyFailure;
    }

    public boolean isNotifyRegression() {
        return notifyRegression;
    }

    public boolean isNotifyBackToNormal() {
        return notifyBackToNormal;
    }

    public boolean isNotifyRepeatedFailure() {
        return notifyRepeatedFailure;
    }

    public boolean isIncludeCommitInfo() {
        return includeCommitInfo;
    }

    public boolean isIncludeTestSummary() {
        return includeTestSummary;
    }

    public boolean isIncludeFailedTests() {
        return includeFailedTests;
    }


    @DataBoundSetter
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @DataBoundSetter
    public void setAuthToken(Secret authToken) {
        this.authToken = authToken;
    }

    @DataBoundSetter
    public void setJenkinsProxyUsed(boolean jenkinsProxyUsed) {
        this.jenkinsProxyUsed = jenkinsProxyUsed;
    }

    @DataBoundSetter
    public void setNotifyStart(boolean notifyStart) {
        this.notifyStart = notifyStart;
    }

    @DataBoundSetter
    public void setNotifySuccess(boolean notifySuccess) {
        this.notifySuccess = notifySuccess;
    }

    @DataBoundSetter
    public void setNotifyAborted(boolean notifyAborted) {
        this.notifyAborted = notifyAborted;
    }

    @DataBoundSetter
    public void setNotifyNotBuilt(boolean notifyNotBuilt) {
        this.notifyNotBuilt = notifyNotBuilt;
    }

    @DataBoundSetter
    public void setNotifyUnstable(boolean notifyUnstable) {
        this.notifyUnstable = notifyUnstable;
    }

    @DataBoundSetter
    public void setNotifyFailure(boolean notifyFailure) {
        this.notifyFailure = notifyFailure;
    }

    @DataBoundSetter
    public void setNotifyRegression(boolean notifyRegression) {
        this.notifyRegression = notifyRegression;
    }

    @DataBoundSetter
    public void setNotifyBackToNormal(boolean notifyBackToNormal) {
        this.notifyBackToNormal = notifyBackToNormal;
    }

    @DataBoundSetter
    public void setNotifyRepeatedFailure(boolean notifyRepeatedFailure) {
        this.notifyRepeatedFailure = notifyRepeatedFailure;
    }

    @DataBoundSetter
    public void setIncludeCommitInfo(boolean includeCommitInfo) {
        this.includeCommitInfo = includeCommitInfo;
    }

    @DataBoundSetter
    public void setIncludeTestSummary(boolean includeTestSummary) {
        this.includeTestSummary = includeTestSummary;
    }

    @DataBoundSetter
    public void setIncludeFailedTests(boolean includeFailedTests) {
        this.includeFailedTests = includeFailedTests;
    }
}
