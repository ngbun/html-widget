package dk.htmlwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {

        val manager =
            AppWidgetManager.getInstance(applicationContext)

        val ids = manager.getAppWidgetIds(
            ComponentName(
                applicationContext,
                HtmlWidgetProvider::class.java
            )
        )

        if (ids.isNotEmpty()) {
            HtmlWidgetProvider.refresh(
                applicationContext,
                ids
            )
        }

        return Result.success()
    }
}
