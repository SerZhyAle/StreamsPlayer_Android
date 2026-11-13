package com.sza.streamsplayer.ui.exchange

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.streamsplayer.R
import com.sza.streamsplayer.databinding.DialogExchangePasswordBinding
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import kotlinx.coroutines.launch

object ExchangePasswordDialog {
    fun show(activity: AppCompatActivity, model: ExchangeViewModel, hostPort: String) {
        val fields = DialogExchangePasswordBinding.inflate(activity.layoutInflater)
        fields.tvPasswordDestination.text = hostPort
        // S0027: a dialog title with the help action in its end corner (CONTEXT-HELP rule 2).
        val titleView = activity.layoutInflater.inflate(R.layout.view_dialog_title_help, null)
        titleView.findViewById<TextView>(R.id.tvDialogTitle).setText(R.string.exchange_change_password)
        val helpButton = titleView.findViewById<View>(R.id.btnDialogHelp)
        TooltipCompat.setTooltipText(helpButton, HelpTooltipManager.tooltipText(activity))
        helpButton.setOnClickListener { HelpOpenManager.open(activity, HelpSurfaceIds.EXCHANGE_PASSWORD) }
        val dialog = AlertDialog.Builder(activity)
            .setCustomTitle(titleView)
            .setView(fields.root)
            .setPositiveButton(R.string.exchange_change_password, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        HelpKeys.install(dialog, HelpSurfaceIds.EXCHANGE_PASSWORD)
        fun clear() {
            fields.etOldPassword.text?.clear()
            fields.etNewPassword.text?.clear()
            fields.etConfirmPassword.text?.clear()
        }
        fun submit() {
            val old = fields.etOldPassword.text?.toString().orEmpty().toCharArray()
            val new = fields.etNewPassword.text?.toString().orEmpty().toCharArray()
            val confirmation = fields.etConfirmPassword.text?.toString().orEmpty().toCharArray()
            clear()
            model.onChangePassword(old, new, confirmation)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            fields.etConfirmPassword.setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                    submit()
                    true
                } else false
            }
        }
        val observer = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.passwordChange.collect { state ->
                    fields.passwordError.isVisible = state.messageRes != null
                    state.messageRes?.let { fields.passwordError.setText(it) }
                    fields.etOldPassword.isEnabled = !state.busy
                    fields.etNewPassword.isEnabled = !state.busy
                    fields.etConfirmPassword.isEnabled = !state.busy
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !state.busy
                }
            }
        }
        dialog.setOnDismissListener { clear(); observer.cancel() }
        dialog.show()
        fields.etOldPassword.requestFocus()
    }
}
