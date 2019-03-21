package io.jenkins.plugins.zoom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import io.jenkins.plugins.zoom.model.BuildReport;
import lombok.extern.slf4j.Slf4j;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class MessageBuilder {

    private static final Pattern aTag = Pattern.compile("(?i)<a([^>]+)>(.+?)</a>|(\\{)");
    private static final Pattern href = Pattern.compile("\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))");


    private ZoomNotifier notifier;
    private AbstractBuild build;
    private BuildListener listener;
    private BuildReport report;

    public MessageBuilder(ZoomNotifier notifier, AbstractBuild build, BuildListener listener) {
        this.notifier = notifier;
        this.build = build;
        this.listener = listener;
        this.report = new BuildReport();
    }

    public String prebuild(boolean includeCommitInfo){
        appendFullDisplayName();
        appendDisplayName();
        appendOpenLink();
        appendCause();
        if(includeCommitInfo){
            appendChanges();
        }
        appendStatus(ZoomNotifier.START_STATUS_MESSAGE);
        try {
            return new ObjectMapper().writeValueAsString(report);
        } catch (JsonProcessingException e) {
            log.error("Error build json process", e);
            e.printStackTrace();
        }
        return null;
    }

    public String build(String status, boolean includeTestSummary, boolean includeFailedTests){
        appendFullDisplayName();
        appendDisplayName();
        appendOpenLink();
        appendCause();
        appendDuration();
        appendStatus(status);
        if(includeTestSummary){
            appendTestSummary();
        }
        if(includeFailedTests){
            appendFailedTests();
        }
        try {
            return new ObjectMapper().writeValueAsString(report);
        } catch (JsonProcessingException e) {
            log.error("Error build json process", e);
            e.printStackTrace();
        }
        return null;
    }

    private void appendFullDisplayName(){
        report.setName(this.escape(build.getProject().getFullDisplayName()));
    }

    private void appendDisplayName(){
        report.setNumber(this.escape(build.getDisplayName()));
    }

    private void appendOpenLink(){
        String url = DisplayURLProvider.get().getRunURL(build);
        report.setFullUrl(this.escape(url));
    }

    private void appendCause(){
        CauseAction causeAction = build.getAction(CauseAction.class);
        if (causeAction != null){
            report.setCause(this.escape(causeAction.getCauses().get(0).getShortDescription()));
        }
    }

    private void appendDuration(){
        report.setDuration(build.getDuration());
    }

    private void appendChanges(){
        ChangeLogSet changeSet = build.getChangeSet();
        if (!build.hasChangeSetComputed() || changeSet == null || changeSet.getItems().length == 0){
            listener.getLogger().println("No commit changes");
            log.info("No commit changes");
            return;
        }
        for (Object o : changeSet.getItems()){
            ChangeLogSet.Entry entry = (ChangeLogSet.Entry) o;
            report.addChange(entry);
        }
    }

    private void appendTestSummary(){
        AbstractTestResultAction<?> action = this.build
                .getAction(AbstractTestResultAction.class);
        if (action != null) {
            report.setTotalTest(action.getTotalCount());
            report.setFailTest(action.getFailCount());
            report.setSkipTest(action.getSkipCount());
        } else {
            listener.getLogger().println("No test action");
            log.info("No test action");
        }
    }

    private void appendFailedTests(){
        AbstractTestResultAction<?> action = this.build
                .getAction(AbstractTestResultAction.class);
        if (action != null && action.getFailCount() > 0) {
            for(TestResult result : action.getFailedTests()){
                report.getTestSummary().addFailedTestResults(result);
            }
        } else {
            listener.getLogger().println("No failed tests");
            log.info("No failed tests");
        }
    }

    private void appendStatus(String status){
        report.setStatus(this.escape(status));
    }


    private String[] extractReplaceLinks(Matcher aTag, StringBuffer sb) {
        int size = 0;
        List<String> links = new ArrayList<>();
        while (aTag.find()) {
            Matcher url = href.matcher(aTag.group(1));
            if (url.find()) {
                String escapeThis = aTag.group(3);
                if (escapeThis != null) {
                    aTag.appendReplacement(sb,String.format("{%s}", size++));
                    links.add("{");
                } else {
                    aTag.appendReplacement(sb,String.format("{%s}", size++));
                    links.add(String.format("<%s|%s>", url.group(1).replaceAll("\"", ""), aTag.group(2)));
                }
            }
        }
        aTag.appendTail(sb);
        return links.toArray(new String[size]);
    }

    private String escapeCharacters(String string) {
        string = string.replace("&", "&amp;");
        string = string.replace("<", "&lt;");
        string = string.replace(">", "&gt;");
        return string;
    }

    public String escape(String string) {
        StringBuffer pattern = new StringBuffer();
        String[] links = extractReplaceLinks(aTag.matcher(string), pattern);
        return MessageFormat.format(escapeCharacters(pattern.toString()), links);
    }
}
