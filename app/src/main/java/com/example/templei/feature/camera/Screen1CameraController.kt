package com.example.templei.feature.camera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.templei.R
import java.io.File

/**
 * Coordinates Screen 1 camera preview, capture actions, and saved media selection.
 */
class Screen1CameraController(
    private val activity: ComponentActivity,
    private val repository: Screen1MediaRepository,
    private val views: Views
) {
    data class Views(
        val renderContainer: FrameLayout,
        val renderPlaceholder: TextView,
        val startFeedButton: Button,
        val stopFeedButton: Button,
        val capturePhotoButton: Button,
        val recordVideoButton: Button,
        val mediaSpinner: Spinner,
        val selectionPreviewImage: ImageView,
        val selectionLabel: TextView,
        val statusText: TextView
    )

    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private val mediaAdapter = ArrayAdapter<String>(
        activity,
        android.R.layout.simple_spinner_item,
        mutableListOf(activity.getString(R.string.screen1_media_prompt))
    ).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var mediaEntries: List<Screen1MediaEntry> = emptyList()
    private var selectedEntry: Screen1MediaEntry? = null
    private var feedState: Screen1CameraFeedState = Screen1CameraFeedState.Stopped
    private var captureState: Screen1CameraCaptureState = Screen1CameraCaptureState.Idle

    fun bind() {
        previewView = PreviewView(activity).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        views.renderContainer.addView(
            previewView,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        views.mediaSpinner.adapter = mediaAdapter
        refreshMediaLibrary()
        bindSelectedEntry(null)
        renderStatus(activity.getString(R.string.screen1_status_idle))
        render()
    }

    fun startFeed() {
        if (feedState == Screen1CameraFeedState.Live || feedState == Screen1CameraFeedState.Starting) {
            renderStatus(activity.getString(R.string.screen1_status_feed_already_live))
            return
        }

        feedState = Screen1CameraFeedState.Starting
        render()

        val providerFuture = ProcessCameraProvider.getInstance(activity)
        providerFuture.addListener(
            {
                try {
                    cameraProvider = providerFuture.get()
                    bindCameraUseCases()
                    feedState = Screen1CameraFeedState.Live
                    renderStatus(activity.getString(R.string.screen1_status_feed_live))
                } catch (exception: Exception) {
                    feedState = Screen1CameraFeedState.Error
                    renderStatus(
                        activity.getString(
                            R.string.screen1_status_feed_error,
                            exception.localizedMessage ?: "unknown error"
                        )
                    )
                }
                render()
            },
            mainExecutor
        )
    }

    fun stopFeed() {
        stopRecordingIfNeeded()
        cameraProvider?.unbindAll()
        imageCapture = null
        videoCapture = null
        feedState = Screen1CameraFeedState.Stopped
        renderStatus(activity.getString(R.string.screen1_status_feed_stopped))
        render()
    }

    fun capturePhoto() {
        if (feedState != Screen1CameraFeedState.Live) {
            renderStatus(activity.getString(R.string.screen1_status_feed_required))
            return
        }

        if (!repository.hasConfiguredDestination(Screen1MediaType.Photo)) {
            renderStatus(activity.getString(R.string.screen1_status_photo_folder_required))
            return
        }

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            renderStatus(activity.getString(R.string.screen1_status_capture_unavailable))
            return
        }

        val outputFile = repository.createPendingMediaFile(Screen1MediaType.Photo)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        renderStatus(activity.getString(R.string.screen1_status_saving_photo))
        captureUseCase.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runCatching {
                        repository.saveCapturedMedia(outputFile, Screen1MediaType.Photo)
                    }.onSuccess { savedEntry ->
                        refreshMediaLibrary(selectUri = savedEntry.uri)
                        renderStatus(activity.getString(R.string.screen1_status_photo_saved, outputFile.name))
                    }.onFailure { error ->
                        renderStatus(
                            activity.getString(
                                R.string.screen1_status_photo_failed,
                                error.localizedMessage ?: "unknown error"
                            )
                        )
                    }
                    outputFile.delete()
                }

                override fun onError(exception: ImageCaptureException) {
                    renderStatus(
                        activity.getString(
                            R.string.screen1_status_photo_failed,
                            exception.localizedMessage ?: "unknown error"
                        )
                    )
                }
            }
        )
    }

    fun toggleRecording(withAudio: Boolean) {
        if (captureState == Screen1CameraCaptureState.Recording) {
            stopRecordingIfNeeded()
            renderStatus(activity.getString(R.string.screen1_status_recording_stopping))
            render()
            return
        }

        if (feedState != Screen1CameraFeedState.Live) {
            renderStatus(activity.getString(R.string.screen1_status_feed_required))
            return
        }

        val videoUseCase = videoCapture
        if (videoUseCase == null) {
            renderStatus(activity.getString(R.string.screen1_status_capture_unavailable))
            return
        }

        if (!repository.hasConfiguredDestination(Screen1MediaType.Video)) {
            renderStatus(activity.getString(R.string.screen1_status_video_folder_required))
            return
        }

        val outputFile = repository.createPendingMediaFile(Screen1MediaType.Video)
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pendingRecording = videoUseCase.output.prepareRecording(activity, outputOptions)
        if (withAudio) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    captureState = Screen1CameraCaptureState.Recording
                    renderStatus(activity.getString(R.string.screen1_status_recording_live))
                    render()
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    captureState = Screen1CameraCaptureState.Idle
                    if (event.hasError()) {
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                        renderStatus(
                            activity.getString(
                                R.string.screen1_status_recording_failed,
                                event.cause?.localizedMessage ?: event.error.toString()
                            )
                        )
                    } else {
                        runCatching {
                            repository.saveCapturedMedia(outputFile, Screen1MediaType.Video)
                        }.onSuccess { savedEntry ->
                            refreshMediaLibrary(selectUri = savedEntry.uri)
                            renderStatus(activity.getString(R.string.screen1_status_recording_saved, outputFile.name))
                        }.onFailure { error ->
                            renderStatus(
                                activity.getString(
                                    R.string.screen1_status_recording_failed,
                                    error.localizedMessage ?: "unknown error"
                                )
                            )
                        }
                        outputFile.delete()
                    }
                    render()
                }
            }
        }

        render()
    }

    fun handleMediaSelection(position: Int) {
        if (position <= 0 || position > mediaEntries.size) {
            bindSelectedEntry(null)
            return
        }
        bindSelectedEntry(mediaEntries[position - 1])
    }

    fun refreshMediaLibrary(selectUri: android.net.Uri? = null) {
        mediaEntries = repository.listMediaEntries()
        mediaAdapter.clear()
        mediaAdapter.add(activity.getString(R.string.screen1_media_prompt))
        mediaAdapter.addAll(mediaEntries.map(Screen1MediaEntry::displayName))
        mediaAdapter.notifyDataSetChanged()

        val selectedIndex = when {
            selectUri != null -> mediaEntries.indexOfFirst { it.uri == selectUri }
            selectedEntry != null -> mediaEntries.indexOfFirst { it.stableId == selectedEntry?.stableId }
            else -> -1
        }

        if (selectedIndex >= 0) {
            views.mediaSpinner.setSelection(selectedIndex + 1)
            bindSelectedEntry(mediaEntries[selectedIndex])
        } else {
            views.mediaSpinner.setSelection(0)
            bindSelectedEntry(null)
        }
    }

    fun release() {
        stopFeed()
    }

    private fun bindCameraUseCases() {
        val previewSurface = previewView ?: return
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewSurface.surfaceProvider
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        provider.unbindAll()
        provider.bindToLifecycle(
            activity,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
            videoCapture
        )
    }

    private fun bindSelectedEntry(entry: Screen1MediaEntry?) {
        selectedEntry = entry
        if (entry == null) {
            views.selectionPreviewImage.visibility = View.GONE
            views.selectionLabel.text = activity.getString(R.string.screen1_preview_empty)
            return
        }

        val bitmap = when (entry.type) {
            Screen1MediaType.Photo -> decodePhotoBitmap(entry)
            Screen1MediaType.Video -> decodeVideoThumbnail(entry)
        }
        if (bitmap == null) {
            views.selectionPreviewImage.visibility = View.GONE
            views.selectionLabel.text = activity.getString(R.string.screen1_preview_failed)
            return
        }

        views.selectionPreviewImage.setImageBitmap(bitmap)
        views.selectionPreviewImage.visibility = View.VISIBLE
        views.selectionLabel.text = activity.getString(R.string.screen1_preview_selected, entry.displayName)
    }

    private fun decodePhotoBitmap(entry: Screen1MediaEntry): Bitmap? {
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.contentResolver, entry.uri))
        }.getOrNull()
    }

    private fun decodeVideoThumbnail(entry: Screen1MediaEntry): Bitmap? {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(activity, entry.uri)
                retriever.frameAtTime
            }
        }.getOrNull()
    }

    private fun stopRecordingIfNeeded() {
        val recording = activeRecording ?: return
        activeRecording = null
        captureState = Screen1CameraCaptureState.Idle
        recording.stop()
    }

    private fun render() {
        views.renderPlaceholder.visibility = if (feedState == Screen1CameraFeedState.Live) {
            View.GONE
        } else {
            View.VISIBLE
        }
        views.renderPlaceholder.text = when (feedState) {
            Screen1CameraFeedState.Stopped -> activity.getString(R.string.screen1_placeholder_stopped)
            Screen1CameraFeedState.Starting -> activity.getString(R.string.screen1_placeholder_starting)
            Screen1CameraFeedState.Live -> ""
            Screen1CameraFeedState.Error -> activity.getString(R.string.screen1_placeholder_error)
        }
        views.startFeedButton.isEnabled = feedState != Screen1CameraFeedState.Starting &&
            feedState != Screen1CameraFeedState.Live
        views.stopFeedButton.isEnabled = feedState == Screen1CameraFeedState.Live ||
            captureState == Screen1CameraCaptureState.Recording
        views.capturePhotoButton.isEnabled = feedState == Screen1CameraFeedState.Live &&
            captureState == Screen1CameraCaptureState.Idle
        views.recordVideoButton.isEnabled = feedState == Screen1CameraFeedState.Live ||
            captureState == Screen1CameraCaptureState.Recording
        views.recordVideoButton.text = activity.getString(
            if (captureState == Screen1CameraCaptureState.Recording) {
                R.string.screen1_stop_recording
            } else {
                R.string.screen1_start_recording
            }
        )
    }

    private fun renderStatus(message: String) {
        views.statusText.text = message
    }
}
