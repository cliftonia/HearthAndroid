package app.hearth.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What runs one sync for the background job: the application, which holds the coordinator. */
interface BackgroundSyncHost {
    /**
     * Waits until the app has read its saved data, then syncs once if sync is on, and returns when that is done. It
     * never syncs against stores that were not read.
     */
    suspend fun backgroundSync()
}

/**
 * Keeps the phone's periodic job that syncs while the app is not open, so that a scheduled task's answer arrives (and
 * can be announced) without the app being opened. It needs a network, and runs about every fifteen minutes, the
 * least the phone allows; the phone decides exactly when.
 */
object BackgroundSync {
    /** The job's number with the phone's scheduler. It stays put, since a scheduled job outlives an update of the app. */
    private const val JOB_ID = 4_210

    private val PERIOD_MILLIS = TimeUnit.MINUTES.toMillis(15)

    /** Schedules the job while sync is on and cancels it when sync is off. Scheduling it twice changes nothing. */
    fun update(context: Context, isSyncOn: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!isSyncOn) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, BackgroundSyncService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD_MILLIS)
            // Kept across a restart of the phone, which is why the app asks to hear that the phone has started.
            .setPersisted(true)
            .build()
        // Background sync is a convenience: if the phone refuses the job, the app carries on without it.
        val result = runCatching { scheduler.schedule(job) }
        result.exceptionOrNull()?.let { Log.w(TAG, "The background sync could not be scheduled: ${it.javaClass.simpleName}") }
        if (result.getOrNull() != JobScheduler.RESULT_SUCCESS) Log.w(TAG, "The background sync could not be scheduled")
    }

    internal const val TAG = "BackgroundSync"
}

/** The phone's job that runs one sync in the background. Starting the app's process reads its data first. */
class BackgroundSyncService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val host = application as? BackgroundSyncHost ?: return false
        running = scope.launch {
            try {
                host.backgroundSync()
                jobFinished(params, false)
            } catch (failure: CancellationException) {
                // Stopped by the phone, which has already let the job go.
                throw failure
            } catch (failure: Exception) {
                Log.w(BackgroundSync.TAG, "The background sync failed: ${failure.javaClass.simpleName}")
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        // The next period comes round anyway.
        return false
    }
}
