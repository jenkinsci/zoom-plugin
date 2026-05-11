/*
 * The MIT License
 *
 * Copyright 2026 Mark Waite.
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
package io.jenkins.plugins.zoom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class MessageBuilderTest {
    private static JenkinsRule r;
    private static ZoomNotifier notifier;
    private static Run run;
    private MessageBuilder messageBuilder;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) throws Exception {
        r = rule;
        FreeStyleProject job = r.createFreeStyleProject();
        run = r.buildAndAssertSuccess(job);
        notifier = new ZoomNotifier();
    }

    @BeforeEach
    void createMessageBuilder() {
        messageBuilder = new MessageBuilder(notifier, run, TaskListener.NULL);
    }

    public MessageBuilderTest() {}

    @Test
    public void testBuildPipeMsg() throws Exception {
        String message = "My message " + UUID.randomUUID();
        assertThat(messageBuilder.buildPipeMsg(message), containsString(message));
    }

    @Test
    public void testPrebuild() throws Exception {
        assertThat(messageBuilder.prebuild(), containsString("Legacy code started this job"));
    }

    @Test
    public void testPrebuildWithIncludeCommitInfo() throws Exception {
        ZoomNotifier notifierIncludeCommitInfo = new ZoomNotifier();
        notifierIncludeCommitInfo.setIncludeCommitInfo(true);
        messageBuilder = new MessageBuilder(notifierIncludeCommitInfo, run, TaskListener.NULL);
        assertThat(messageBuilder.prebuild(), containsString("Legacy code started this job"));
    }

    @Test
    public void testBuild() throws Exception {
        assertThat(messageBuilder.build(), containsString("No cause information is available"));
        assertThat(messageBuilder.build(), containsString("Success"));
    }

    @Test
    public void testBuildFailed() throws Exception {
        FreeStyleProject failingJob = r.createFreeStyleProject();
        failingJob
                .getBuildersList()
                .add(TestBuilder.of((build, launcher, listener) -> build.setResult(Result.FAILURE)));
        Run failingRun = r.buildAndAssertStatus(Result.FAILURE, failingJob);
        messageBuilder = new MessageBuilder(notifier, failingRun, TaskListener.NULL);
        assertThat(messageBuilder.build(), containsString("broken since this build"));
        assertThat(messageBuilder.build(), containsString("Failure"));
    }

    @Test
    public void testBuildIncludeFailedTests() throws Exception {
        ZoomNotifier notifierIncludeFailedTests = new ZoomNotifier();
        notifierIncludeFailedTests.setIncludeFailedTests(true);
        messageBuilder = new MessageBuilder(notifierIncludeFailedTests, run, TaskListener.NULL);
        assertThat(messageBuilder.build(), containsString("No cause information is available"));
    }

    @Test
    public void testBuildIncludeTestSummary() throws Exception {
        ZoomNotifier notifierIncludeTestSummary = new ZoomNotifier();
        notifierIncludeTestSummary.setIncludeTestSummary(true);
        messageBuilder = new MessageBuilder(notifierIncludeTestSummary, run, TaskListener.NULL);
        assertThat(messageBuilder.build(), containsString("No cause information is available"));
    }

    @Test
    public void testEscape() throws Exception {
        assertThat(messageBuilder.escape("a < b"), is("a &lt; b"));
        assertThat(messageBuilder.escape("b > a"), is("b &gt; a"));
        assertThat(messageBuilder.escape("c & d"), is("c &amp; d"));
    }
}
