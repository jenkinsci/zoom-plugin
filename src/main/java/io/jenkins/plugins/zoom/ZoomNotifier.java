package io.jenkins.plugins.zoom;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.Permission;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.io.IOException;

@Data
@Slf4j
public class ZoomNotifier extends Notifier implements SimpleBuildStep {

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
            ZoomNotifyClient.notify(this.webhookUrl, this.authToken, messageBuilder.prebuild());
        }
        return super.prebuild(build, listener);
    }


    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        log.info("Perform: {}", run.getFullDisplayName());
        taskListener.getLogger().println("---------------------- Perform ----------------------");
        if(notifyPerform(run)){
            MessageBuilder messageBuilder = new MessageBuilder(this, run, taskListener);
            ZoomNotifyClient.notify(this.webhookUrl, this.authToken, messageBuilder.build());
        } else {
          taskListener.getLogger().println("------------------- Unknown Result ------------------");
        }
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
        try {
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
        } catch (final IllegalArgumentException e) {
          return false;
        }
    }

    @Symbol("zoomNotifier")
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

        @POST
        public FormValidation doTestConnection(@QueryParameter("webhookUrl") final String webhookUrl,
                                               @QueryParameter("authToken") final String authToken){
            Jenkins.get().checkPermission(Permission.CONFIGURE);
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
