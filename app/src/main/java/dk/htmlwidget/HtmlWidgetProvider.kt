package dk.htmlwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

class HtmlWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(
            context,
            appWidgetManager,
            appWidgetId,
            newOptions
        )

        refresh(
            context,
            intArrayOf(appWidgetId)
        )
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        HtmlRenderService.start(context)
    }

    companion object {

        fun refresh(
            context: Context,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return

            val manager =
                AppWidgetManager.getInstance(context)

            val prefs = context.getSharedPreferences(
                "settings",
                Context.MODE_PRIVATE
            )

            val website =
                prefs.getString("url", null)

            ids.forEach { id ->

                val views = RemoteViews(
                    context.packageName,
                    R.layout.widget_layout
                )

                /*
                 * Hvis der er valgt en hjemmeside,
                 * åbner et tryk på widgetten den
                 * fungerende WebsiteRenderActivity.
                 */
                if (!website.isNullOrBlank()) {

                    val intent = Intent(
                        context,
                        WebsiteRenderActivity::class.java
                    ).apply {
                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    val pendingIntent =
                        PendingIntent.getActivity(
                            context,
                            id,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                        )

                    views.setOnClickPendingIntent(
                        R.id.widget_image,
                        pendingIntent
                    )
                }

                manager.updateAppWidget(
                    id,
                    views
                )
            }

            /*
             * Lokal HTML fortsætter med den
             * eksisterende renderer.
             */
            if (website.isNullOrBlank()) {
                HtmlRenderService.start(context)
            }
        }
    }
}
