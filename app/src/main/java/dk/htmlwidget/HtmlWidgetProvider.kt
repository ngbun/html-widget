package dk.htmlwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
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

        // Widgetten er blevet større eller mindre.
        // Lav HTML-visningen igen i den nye størrelse.
        refresh(context, intArrayOf(appWidgetId))
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

            /*
             * Vis en midlertidig visning mens HTML-siden
             * bliver genereret.
             */
            val manager = AppWidgetManager.getInstance(context)

            ids.forEach { id ->
                val views = RemoteViews(
                    context.packageName,
                    R.layout.widget_layout
                )

                views.setImageViewResource(
                    R.id.widget_image,
                    android.R.drawable.ic_menu_view
                )

                manager.updateAppWidget(id, views)
            }

            // HtmlRenderService finder selv widgetternes
            // aktuelle størrelse og renderer dem derefter.
            HtmlRenderService.start(context)
        }
    }
}
