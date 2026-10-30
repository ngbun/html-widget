package dk.htmlwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.widget.RemoteViews

class HtmlWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context, appWidgetIds)
    }

    companion object {
        fun refresh(context: Context, ids: IntArray) {

            ids.forEach { id ->
                val views = RemoteViews(
                    context.packageName,
                    R.layout.widget_layout
                )

                views.setImageViewResource(
                    R.id.widget_image,
                    android.R.drawable.ic_menu_view
                )

                AppWidgetManager
                    .getInstance(context)
                    .updateAppWidget(id, views)
            }

            if (ids.isNotEmpty()) {
                HtmlRenderService.start(context)
            }
        }
    }
}
