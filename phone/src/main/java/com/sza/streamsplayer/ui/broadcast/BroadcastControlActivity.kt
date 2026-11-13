package com.sza.streamsplayer.ui.broadcast

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sza.streamsplayer.core.util.LocaleHelper
import com.sza.streamsplayer.databinding.ActivityBroadcastControlBinding
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.broadcast.helpers.BroadcastControlManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BroadcastControlActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var controlManager: BroadcastControlManager

    private lateinit var binding: ActivityBroadcastControlBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBroadcastControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        controlManager.setup(this, binding)
        binding.btnHelp.setOnClickListener { HelpOpenManager.open(this, HelpSurfaceIds.BROADCAST) }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnHelp, HelpTooltipManager.tooltipText(this))
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (HelpKeys.handle(this, HelpSurfaceIds.BROADCAST, event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Keep screen on while interacting on wake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        controlManager.onDetach()
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, BroadcastControlActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}
