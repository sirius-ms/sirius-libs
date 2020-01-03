package de.unijena.bioinf;

import de.unijena.bioinf.ChemistryBase.jobs.SiriusJobs;
import de.unijena.bioinf.jjobs.BasicJJob;
import de.unijena.bioinf.jjobs.WaiterJJob;
import de.unijena.bioinf.ms.rest.model.JobId;
import de.unijena.bioinf.ms.rest.model.JobTable;
import de.unijena.bioinf.ms.rest.model.JobUpdate;
import de.unijena.bioinf.utils.NetUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

final class WebJobWatcher {
    private static final int INIT_WAIT_TIME = 1000;

    private final Map<JobId, WebJJob<?, ?, ?>> waitingJobs = new ConcurrentHashMap<>();
    private final WebAPI api;
    private WebJobWatcherJJob job = null;

    //this is for efficient job update even with a large number of jobs on large multi core machines
    public WebJobWatcher(WebAPI api) {
        this.api = api;
    }

    public <J extends WebJJob<?, ?, ?>> J watchJob(@NotNull final J jobToWatch) {
        checkWatcherJob();

        synchronized (waitingJobs) {
            waitingJobs.put(jobToWatch.jobId, jobToWatch);
            waitingJobs.notifyAll();
        }

        return jobToWatch;
    }

    private synchronized void checkWatcherJob() {
        if (job == null || job.isFinished())
            job = SiriusJobs.getGlobalJobManager().submitJob(new WebJobWatcherJJob());
    }

    final class WebJobWatcherJJob extends BasicJJob<Boolean> {

        public WebJobWatcherJJob() {
            super(JobType.WEBSERVICE);
        }

        @Override
        protected Boolean compute() throws Exception {
            long waitTime = INIT_WAIT_TIME;
            while (true) {
                checkForInterruption();

                synchronized (waitingJobs) {
                    if (waitingJobs.isEmpty())
                        waitingJobs.wait();
                }

                final Set<JobId> orphanJobs = new HashSet<>(waitingJobs.keySet());
                EnumMap<JobTable, List<JobUpdate<?>>> updates = NetUtils.tryAndWait(
                        () -> api.updateJobStates(waitingJobs.keySet().stream().map(id -> id.jobTable).collect(Collectors.toSet())),
                        this::checkForInterruption
                );
                List<JobUpdate<?>> toRemove = null;


                if (updates != null && !updates.isEmpty()) {
                    //update, find orphans and notify finished jobs
                    toRemove = updates.values().stream().flatMap(Collection::stream).filter(up -> {
                        try {
                            checkForInterruption();

                            final WebJJob<?, ?, ?> job;
                            orphanJobs.remove(up.getGlobalId());
                            job = waitingJobs.get(up.getGlobalId());

                            if (job == null) {
                                LOG().warn("Job \"" + up.getGlobalId().toString() + "\" was found on the server but is unknown locally. Trying to match it again later!");
                                return false;
                            }

                            return job.update(up).isFinished();
                        } catch (Exception e) {
                            LOG().warn("Could not update Job", e);
                            return false;
                        }
                    }).collect(Collectors.toList());

                    checkForInterruption();

                    // crash and notify orphan jobs
                    orphanJobs.stream().map(waitingJobs::get).forEach(j -> {
                        if (!j.isFinished()) {
                            j.crash(new Exception("Job not found on Server. It might have been deleted due to an Error."));
                        } else
                            LOG().warn("Already finished job is missing on Server. This indicates some Synchronization problem");
                    });

                    checkForInterruption();

                    orphanJobs.addAll(toRemove.stream().map(JobUpdate::getGlobalId).collect(Collectors.toSet()));

                    checkForInterruption();

                    if (!orphanJobs.isEmpty()) {
                        orphanJobs.forEach(waitingJobs::remove);
                        // not it sync because it may take some time and is not needed since jobwatcher is single threaded
                        NetUtils.tryAndWait(() -> api.deleteJobs(orphanJobs), this::checkForInterruption);
                    }
                } else {
                    LOG().warn("Cannot fetch jobUpdates from CSI:FingerID Server. Trying again in " + waitTime + "ms.");
                }

                checkForInterruption();
                // if nothing was finished increase waiting time
                // else set back to normal for fast reaction times
                if (toRemove == null || toRemove.isEmpty()) {
                    waitTime = (long) Math.min(waitTime * NetUtils.WAIT_TIME_MULTIPLIER, 30000);
                    LOG().info("No CSI:FingerID prediction jobs finished. Try again in " + waitTime / 1000d + "s");
                } else {
                    waitTime = INIT_WAIT_TIME;
                }

                NetUtils.sleep(this::checkForInterruption, waitTime);
            }
        }

        @Override
        protected synchronized void cleanup() {
            super.cleanup();

            LOG().info("Canceling WebWaiterJobs...");
            synchronized (waitingJobs) {
                try {
                    waitingJobs.values().forEach(WaiterJJob::cancel); //this jobs are not submitted to the job manager and need no be canceled manually
                    LOG().info("Try to delete leftover jobs on web server...");
                    NetUtils.tryAndWait(() -> api.deleteJobs(waitingJobs.keySet()), () -> {}, 4000);
                    LOG().info("...Job deletion Done!");
                } catch (InterruptedException | TimeoutException e) {
                    LOG().warn("Failed to delete remote jobs from server!", e);
                } finally {
                    waitingJobs.clear();
                }
            }
        }
    }
}
