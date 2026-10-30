package dk.htmlwidget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.IBinder
import android.widget.RemoteViews

class HtmlRenderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val width = 1000
        val height = 500

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.rgb(25, 25, 25))

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 70f
            isAntiAlias = true
        }

        canvas.drawText(
            "HTML WIDGET VIRKER!",
            60f,
            180f,
            paint
        )

        paint.textSize = 42f

        canvas.drawText(
            "Test af startskærms-widget",
            60f,
            270f,
            paint
        )

        val manager =
            AppWidgetManager.getInstance(this)

        val ids = manager.getAppWidgetIds(
            ComponentName(
                this,
                HtmlWidgetProvider::class.java
            )
        )

        ids.forEach { id ->

            val views = RemoteViews(
                packageName,
                R.layout.widget_layout
            )

            views.setImageViewBitmap(
                R.id.widget_image,
                bitmap
            )

            manager.updateAppWidget(
                id,
                views
            )
        }

        stopSelf()

        return START_NOT_STICKY
    }

    companion object {

        fun start(context: Context) {

            context.startService(
                Intent(
                    context,
                    HtmlRenderService::class.java
                )
            )
        }
    }
}
