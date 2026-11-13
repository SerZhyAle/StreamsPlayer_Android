package com.sza.streamsplayer.ui.streams.helpers

import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.streamsplayer.R
import com.sza.streamsplayer.databinding.DialogBroadcastPasswordBinding
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The password question of a protected broadcast (contract LIVE-BROADCAST item M): asked once when a descriptor
 * is imported, and again with the "protected" notice when the broadcast refuses the pair this device holds.
 * The field is cleared whenever the dialog goes, so a typed password never outlives the question.
 */
class StreamBroadcastPasswordPromptManager(private val activity: AppCompatActivity) {

    suspend fun ask(refused: Boolean = false): String? = suspendCancellableCoroutine { continuation ->
        show(refused, onCancel = { if (continuation.isActive) continuation.resume(null) }) {
            if (continuation.isActive) continuation.resume(it)
        }
        continuation.invokeOnCancellation { activity.runOnUiThread { close() } }
    }

    fun close() = dismiss()


    private var dialog: AlertDialog? = null
    private var binding: DialogBroadcastPasswordBinding? = null

    /** [refused] adds the "protected by a password" notice: playback was refused, not an import asked. */
    fun show(refused: Boolean, onCancel: () -> Unit = {}, onSubmit: (String) -> Unit) {
        dismiss()
        var submitted = false
        val accept: (String) -> Unit = { submitted = true; onSubmit(it) }
        val view = DialogBroadcastPasswordBinding.inflate(LayoutInflater.from(activity))
        view.textBroadcastPasswordNotice.isVisible = refused
        view.textBroadcastPasswordNotice.setText(R.string.broadcast_password_wrong)
        view.etBroadcastPassword.doAfterTextChanged { view.tilBroadcastPassword.error = null }
        view.etBroadcastPassword.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit(accept) }
        }
        val shown = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.broadcast_password_title)
            .setView(view.root)
            .setPositiveButton(R.string.broadcast_password_connect, null)
            .setNegativeButton(R.string.action_cancel, null)
            // The dismiss message is posted, so a replaced dialog's listener must not forget its successor.
            .setOnDismissListener {
                view.etBroadcastPassword.text?.clear()
                if (binding === view) forget()
                if (!submitted) onCancel()
            }
            .show()
        // Set after show(): a builder listener would dismiss the dialog before an empty field is refused.
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit(accept) }
        shown.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        view.etBroadcastPassword.requestFocus()
        dialog = shown
        binding = view
    }

    private fun submit(onSubmit: (String) -> Unit) {
        val view = binding ?: return
        val password = view.etBroadcastPassword.text?.toString().orEmpty()
        if (password.isEmpty()) {
            view.tilBroadcastPassword.error = activity.getString(R.string.broadcast_password_empty)
            return
        }
        onSubmit(password)
        dismiss()
    }

    private fun dismiss() {
        dialog?.dismiss()
        forget()
    }

    private fun forget() {
        binding?.etBroadcastPassword?.text?.clear()
        dialog = null
        binding = null
    }
}
