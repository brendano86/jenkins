
package jenkins.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Extension
@Restricted(NoExternalUse.class)
public class StopBuildsCommand extends CLICommand {

    @Argument(usage = "Name of the job(s) to stop", required = true, multiValued = true)
    private List<String> jobNames;

    private boolean isAnyBuildStopped;

    @Override
    public String getShortDescription() {
        return "Stop all running builds for job(s)";
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();
        final HashSet<String> names = new HashSet<>(jobNames);

        final List<Job> jobsToStop = new ArrayList<>();
        for (final String jobName : names) {
            Item item = jenkins.getItemByFullName(jobName);
            if (item instanceof Job) {
                jobsToStop.add((Job) item);
            } else {
                throw new IllegalArgumentException("Job not found: '" + jobName + "'");
            }
        }

        for (final Job job : jobsToStop) {
            stopJobBuilds(job);
        }

        if (!isAnyBuildStopped) {
            stdout.println("No builds stopped");
        }

        return 0;
    }

    private void stopJobBuilds(final Job job) {
        final Run lastBuild = job.getLastBuild();
        final String jobName = job.getFullDisplayName();
        if (lastBuild != null && lastBuild.isBuilding()) {
            stopBuild(lastBuild, jobName);
            checkAndStopPreviousBuilds(lastBuild, jobName);
        }
    }

    private void stopBuild(final Run build,
                           final String jobName) {
        final String buildName = build.getDisplayName();
        Executor executor = build.getExecutor();
        if (executor != null) {
            try {
                executor.doStop();
                isAnyBuildStopped = true;
                stdout.println(String.format("Build '%s' stopped for job '%s'", buildName, jobName));
            } catch (final Exception e) {
                stdout.print(String.format("Exception occurred while trying to stop build '%s' for job '%s'. ", buildName, jobName));
                stdout.println(String.format("Exception class: %s, message: %s", e.getClass().getSimpleName(), e.getMessage()));
            }
        } else {
            stdout.println(String.format("Build '%s' in job '%s' not stopped", buildName, jobName));
        }
    }

    private void checkAndStopPreviousBuilds(final Run lastBuild,
                                            final String jobName) {
        Run build = lastBuild.getPreviousBuildInProgress();
        while (build != null) {
            stopBuild(build, jobName);
            build = build.getPreviousBuildInProgress();
        }
    }

}
