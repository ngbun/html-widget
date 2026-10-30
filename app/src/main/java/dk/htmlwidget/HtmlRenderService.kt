package dk.htmlwidget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import kotlin.math.min
import kotlin.math.roundToInt

class HtmlRenderService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        handler.post { renderHtml() }
        return START_NOT_STICKY
    }

    private fun renderHtml() {

        val prefs =
            getSharedPreferences("settings", MODE_PRIVATE)

        val localUri =
            prefs.getString("localUri", null)

        if (localUri.isNullOrBlank()) {
            stopSelf()
            return
        }

        val html = try {

            contentResolver
                .openInputStream(Uri.parse(localUri))
                ?.bufferedReader()
                ?.use { it.readText() }

        } catch (e: Exception) {
            null
        }

        if (html.isNullOrBlank()) {
            stopSelf()
            return
        }

        val manager =
            AppWidgetManager.getInstance(this)

        val ids =
            manager.getAppWidgetIds(
                ComponentName(
                    this,
                    HtmlWidgetProvider::class.java
                )
            )

        if (ids.isEmpty()) {
            stopSelf()
            return
        }

        /*
         * Størrelsen på den valgte Android-widget.
         */
        val options =
            manager.getAppWidgetOptions(ids[0])

        val density =
            resources.displayMetrics.density

        val widgetWidth =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    300
                ) * density
            ).roundToInt()

        val widgetHeight =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            ).roundToInt()

        /*
         * HTML'en renderes først stort.
         *
         * Det er vigtigt for den vandrette elpristabel,
         * så hele tabellen kan eksistere før den
         * skaleres ned.
         */
        val htmlWidth = 1600
        val htmlHeight = 900

        val web = WebView(this)

        web.setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true

            useWideViewPort = true
            loadWithOverviewMode = false
        }

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    handler.postDelayed({

                        web.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                htmlWidth,
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                htmlHeight,
                                View.MeasureSpec.EXACTLY
                            )
                        )

                        web.layout(
                            0,
                            0,
                            htmlWidth,
                            htmlHeight
                        )

                        web.invalidate()

                        handler.postDelayed({

                            val fullBitmap =
                                Bitmap.createBitmap(
                                    htmlWidth,
                                    htmlHeight,
                                    Bitmap.Config.ARGB_8888
                                )

                            val canvas =
                                Canvas(fullBitmap)

                            web.draw(canvas)

                            /*
                             * Beregn hvor meget HELE HTML'en
                             * skal skaleres ned.
                             *
                             * Samme faktor bruges på bredde
                             * og højde, så intet strækkes.
                             */
                            val scaleX =
                                widgetWidth.toFloat() /
                                    htmlWidth.toFloat()

                            val scaleY =
                                widgetHeight.toFloat() /
                                    htmlHeight.toFloat()

                            val scale =
                                min(scaleX, scaleY)

                            val finalWidth =
                                (htmlWidth * scale)
                                    .roundToInt()
                                    .coerceAtLeast(1)

                            val finalHeight =
                                (htmlHeight * scale)
                                    .roundToInt()
                                    .coerceAtLeast(1)

                            val fittedBitmap =
                                Bitmap.createScaledBitmap(
                                    fullBitmap,
                                    finalWidth,
                                    finalHeight,
                                    true
                                )

                            showBitmap(
                                manager,
                                ids,
                                fittedBitmap
                            )

                            if (
                                fittedBitmap !== fullBitmap
                            ) {
                                fullBitmap.recycle()
                            }

                            web.destroy()
                            stopSelf()

                        }, 2000)

                    }, 2000)
                }
            }

        web.loadDataWithBaseURL(
            localUri,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showBitmap(
        manager: AppWidgetManager,
        ids: IntArray,
        bitmap: Bitmap
    ) {

        ids.forEach { id ->

            val views =
                RemoteViews(
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
