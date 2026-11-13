package com.sza.streamsplayer.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sza.streamsplayer.R
import com.sza.streamsplayer.core.notification.NotificationIcons
import com.sza.streamsplayer.core.notification.NotificationIds
import com.sza.streamsplayer.domain.delivery.DeliverableCapabilityRepository
import com.sza.streamsplayer.domain.delivery.DeliverableSet
import com.sza.streamsplayer.domain.delivery.DeliverableSetDownloader
import com.sza.streamsplayer.domain.delivery.DownloadProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException

/**
 * Background worker to run deliverable set downloads in application scope via WorkManager (S0397).
 * Updates progress notifications in the system tray and exposes work progress.
 *
 * It runs as an ordinary job, not as a foreground service: a tile pack fits the job's window, and a
 * job the system stops is rescheduled by WorkManager. So the progress notification is a plain one this
 * worker takes down itself.
 */
@HiltWorker
class DeliverableDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloader: DeliverableSetDownloader,
    private val repository: DeliverableCapabilityRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val set = deliverableSetNamed(inputData.getString(KEY_DELIVERABLE_SET)) ?: return Result.failure()

        // A forced run replaces a stale payload that is still on disk; only an unforced one may stop here.
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (isAlreadyDone(force, repository.isInstalled(set))) {
            return Result.success()
        }

        ensureNotificationChannel()
        var result: Result = Result.failure()
        try {
            downloader.download(set).collect { progress ->
                val progressData = when (progress) {
                    DownloadProgress.Queued -> {
                        updateNotification(set, context.getString(R.string.delivery_state_downloading), 0)
                        workDataOf("status" to "queued")
                    }
                    is DownloadProgress.Running -> {
                        updateNotification(
                            set,
                            context.getString(R.string.delivery_state_downloading),
                            progress.percent
                        )
                        workDataOf(
                            "status" to "running",
                            "percent" to progress.percent,
                            "bytesDownloaded" to progress.bytesDownloaded,
                            "totalBytes" to progress.totalBytes
                        )
                    }
                    DownloadProgress.Verifying -> {
                        updateNotification(set, context.getString(R.string.delivery_state_verifying), -1)
                        workDataOf("status" to "verifying")
                    }
                    DownloadProgress.Installed -> {
                        showSuccessNotification(set)
                        workDataOf("status" to "installed")
                    }
                    is DownloadProgress.Failed -> {
                        showFailureNotification(set)
                        workDataOf(
                            "status" to "failed",
                            "reason" to progress.reason
                        )
                    }
                }
                setProgress(progressData)

                if (progress is DownloadProgress.Installed) {
                    result = Result.success()
                } else if (progress is DownloadProgress.Failed) {
                    result = Result.failure(workDataOf("reason" to progress.reason))
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "DeliverableDownloadWorker failed for $set")
            showFailureNotification(set)
            result = Result.failure(workDataOf("reason" to (e.message ?: "Unknown error")))
        } catch (e: CancellationException) {
            // The system stopped the job: WorkManager reschedules it, so this is not a failure to report.
            throw e
        } catch (e: IllegalStateException) {
            // OkHttp and the storage layer raise this for a call or file in the wrong state.
            Timber.e(e, "DeliverableDownloadWorker failed for $set")
            showFailureNotification(set)
            result = Result.failure(workDataOf("reason" to (e.message ?: "Unknown error")))
        } finally {
            notificationManager.cancel(notificationId(set))
        }

        return result
    }

    private fun updateNotification(set: DeliverableSet, contentText: String, percent: Int) {
        notificationManager.notify(notificationId(set), buildNotification(set, contentText, percent))
    }

    private fun buildNotification(set: DeliverableSet, contentText: String, percent: Int): Notification {
        val featureName = context.getString(featureNameRes(set))
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_running, featureName))
            .setContentText(contentText)
            .setOnlyAlertOnce(true)

        if (percent >= 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(100, 0, true) // indeterminate
        }

        return builder.build()
    }

    private fun showSuccessNotification(set: DeliverableSet) {
        val featureName = context.getString(featureNameRes(set))
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_installed, featureName))
            .setContentText(context.getString(R.string.delivery_state_done))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(resultNotificationId(set), notification)
    }

    private fun showFailureNotification(set: DeliverableSet) {
        val featureName = context.getString(featureNameRes(set))
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.download_runner_notif_title_failed, featureName))
            .setContentText(context.getString(R.string.delivery_state_error))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(resultNotificationId(set), notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.download_runner_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun featureNameRes(set: DeliverableSet): Int = when (set) {
        DeliverableSet.CHANNEL_PREVIEW_ATLAS -> R.string.streams_atlas_prompt_title
        DeliverableSet.STREAM_LOGO_ATLAS -> R.string.streams_logo_prompt_title
    }

    private fun notificationId(set: DeliverableSet): Int =
        NotificationIds.slotIn(NotificationIds.DELIVERABLE_DOWNLOAD_PROGRESS, set.ordinal)

    private fun resultNotificationId(set: DeliverableSet): Int =
        NotificationIds.slotIn(NotificationIds.DELIVERABLE_DOWNLOAD_RESULTS, set.ordinal)

    companion object {
        const val KEY_DELIVERABLE_SET = "deliverable_set"
        const val KEY_FORCE = "deliverable_force"
        const val NOTIFICATION_CHANNEL_ID = "deliverable_download_channel"

        internal fun deliverableSetNamed(name: String?): DeliverableSet? =
            DeliverableSet.values().firstOrNull { it.name == name }

        /** True when there is nothing to fetch: a payload is present and nobody asked to replace it. */
        internal fun isAlreadyDone(force: Boolean, installed: Boolean): Boolean = installed && !force
    }
}
