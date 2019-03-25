package io.jenkins.plugins.zoom.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import hudson.scm.ChangeLogSet;
import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Data
public class BuildReport {
    private String name;
    private String number;
    @JsonProperty("full_url")
    private String fullUrl;
    private long duration;
    private String status;
    private String summary;
    private String cause;
    private Set<Change> changes = new HashSet<>();
    @JsonProperty("test_summary")
    private TestSummary testSummary = new TestSummary();

    public void addChange(ChangeLogSet.Entry entry){
        Change c = new Change();
        c.setAuthor(entry.getAuthor().getDisplayName());
        c.setCommitId(entry.getCommitId());
        c.setMessage(entry.getMsgEscaped());
        for(ChangeLogSet.AffectedFile file: entry.getAffectedFiles()){
            c.addAffectedFile(file);
        }
        changes.add(c);
    }

    public void setTotalTest(int count){
        this.testSummary.setTotal(count);
    }

    public void setFailTest(int count){
        this.testSummary.setFail(count);
    }

    public void setSkipTest(int count){
        this.testSummary.setSkip(count);
    }

    @Data
    public static class Change {
        private String author;
        private String message;
        private String commitId;
        private Set<AffectedFile> files = new HashSet<>();

        public void addAffectedFile(ChangeLogSet.AffectedFile file){
            AffectedFile f = new AffectedFile();
            f.setName(file.getPath());
            files.add(f);
        }
    }

    @Data
    public static class AffectedFile {
        private String name;
    }

    @Data
    public static class TestSummary {
        private int total;
        private int fail;
        private int skip;
        private Set<TestResult> failedResults = new HashSet<>();

        public void addFailedTestResults(hudson.tasks.test.TestResult result){
            TestResult r = new TestResult();
            r.setName(getTestClassAndMethod(result));
            r.setDuration(String.valueOf(result.getDuration()));
            failedResults.add(r);
        }
    }

    @Data
    public static class TestResult {
        private String name;
        private String duration;
    }

    private static String getTestClassAndMethod(hudson.tasks.test.TestResult result) {
        String fullDisplayName = result.getFullDisplayName();

        if (StringUtils.countMatches(fullDisplayName, ".") > 1) {
            int methodDotIndex = fullDisplayName.lastIndexOf('.');
            int testClassDotIndex = fullDisplayName.substring(0, methodDotIndex).lastIndexOf('.');

            return fullDisplayName.substring(testClassDotIndex + 1);

        } else {
            return fullDisplayName;
        }
    }
}
