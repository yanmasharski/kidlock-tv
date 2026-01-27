package uk.telegramgames.kidlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("KidLock", "BootReceiver - received ACTION_BOOT_COMPLETED")
            val dataRepository = DataRepository.getInstance(context)

            dataRepository.ensureDailyResetIfNeeded()

            if (dataRepository.isAutostartEnabled()) {
                val usageStatsHelper = UsageStatsHelper(context)
                val remainingMinutes = usageStatsHelper.getRemainingTimeMinutes(
                    dataRepository.getDailyTimeLimitMinutes(),
                    dataRepository.getAddedTimeMinutes()
                )

                if (remainingMinutes > 0) {
                    Log.d("KidLock", "BootReceiver - autostart enabled and remaining time exists ($remainingMinutes min), starting MainActivity")
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(launchIntent)
                } else {
                    Log.d("KidLock", "BootReceiver - autostart enabled but no remaining time, not starting MainActivity")
                }
            } else {
                Log.d("KidLock", "BootReceiver - autostart disabled")
            }
        }
    }
}

