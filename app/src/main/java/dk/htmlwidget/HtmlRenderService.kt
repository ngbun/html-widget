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
         * Stor arbejdsflade.
         * Dette er IKKE HTML'ens faste størrelse.
         * Den bruges kun til at give brede sider plads
         * mens vi måler deres rigtige indhold.
         */
        val workWidth = 5000
        val workHeight = 2000

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

        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                workWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                workHeight,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            workWidth,
            workHeight
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Vent på priser og JavaScript.
                     */
                    handler.postDelayed({

                        /*
                         * Find den yderste kant af ALLE
                         * synlige elementer.
                         *
                         * Vi ændrer ikke viewporten bagefter.
                         */
                        web.evaluateJavascript(
                            """
                            (function() {

                                var elements =
                                    document.querySelectorAll('*');

                                var maxRight = 0;
                                var maxBottom = 0;

                                for (
                                    var i = 0;
                                    i < elements.length;
                                    i++
                                ) {

                                    var el = elements[i];

                                    var style =
                                        window.getComputedStyle(el);

                                    if (
                                        style.display === 'none' ||
                                        style.visibility === 'hidden'
                                    ) {
                                        continue;
                                    }

                                    var r =
                                        el.getBoundingClientRect();

                                    if (
                                        r.width > 0 &&
                                        r.height > 0
                                    ) {
                                        maxRight =
                                            Math.max(
                                                maxRight,
                                                r.right
                                            );

                                        maxBottom =
                                            Math.max(
                                                maxBottom,
                                                r.bottom
                                            );
                                    }
                                }

                                maxRight =
                                    Math.max(
                                        maxRight,
                                        document.body.scrollWidth,
                                        document.documentElement.scrollWidth
                                    );

                                maxBottom =
                                    Math.max(
                                        maxBottom,
                                        document.body.scrollHeight,
                                        document.documentElement.scrollHeight
                                    );

                                return (
                                    Math.ceil(maxRight) +
                                    "," +
                                    Math.ceil(maxBottom)
                                );
                            })();
                            """.trimIndent()
                        ) { result ->

                            val clean =
                                result
                                    .replace("\"", "")
                                    .trim()

                            val parts =
                                clean.split(",")

                            var contentWidth =
                                parts
                                    .getOrNull(0)
                                    ?.toIntOrNull()
                                    ?: workWidth

                            var contentHeight =
                                parts
                                    .getOrNull(1)
                                    ?.toIntOrNull()
                                    ?: workHeight

                            /*
                             * Lille sikkerhedsmargin.
                             */
                            contentWidth += 20
                            contentHeight += 20

                            contentWidth =
                                contentWidth.coerceIn(
                                    1,
                                    workWidth
                                )

                            contentHeight =
                                contentHeight.coerceIn(
                                    1,
                                    workHeight
                                )

                            makeBitmap(
                                web,
                                manager,
                                ids,
                                contentWidth,
                                contentHeight,
                                widgetWidth,
                                widgetHeight
                            )
                        }

                    }, 4000)
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

    private fun makeBitmap(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        contentWidth: Int,
        contentHeight: Int,
        widgetWidth: Int,
        widgetHeight: Int
    ) {

        /*
         * WebView'en ændres IKKE i størrelse her.
         *
         * Vi tegner kun det område, som JavaScript
         * har fundet indeholder HTML'en.
         */
        val fullBitmap =
            Bitmap.createBitmap(
                contentWidth,
                contentHeight,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(fullBitmap)

        web.draw(canvas)

        /*
         * Hele HTML-indholdet skal være indenfor
         * både widgettens bredde OG højde.
         */
        val scaleX =
            widgetWidth.toFloat() /
                contentWidth.toFloat()

        val scaleY =
            widgetHeight.toFloat() /
                contentHeight.toFloat()

        /*
         * Vi forstørrer ikke små HTML-sider over
         * deres oprindelige størrelse.
         */
        val scale =
            min(
                1f,
                min(scaleX, scaleY)
            )

        val finalWidth =
            (contentWidth * scale)
                .roundToInt()
                .coerceAtLeast(1)

        val finalHeight =
            (contentHeight * scale)
                .roundToInt()
                .coerceAtLeast(1)

        val fittedBitmap =
            Bitmap.createScaledBitmap(
                fullBitmap,
                finalWidth,
                finalHeight,
                true
            )

        ids.forEach { id ->

            val views =
                RemoteViews(
                    packageName,
                    R.layout.widget_layout
                )

            views.setImageViewBitmap(
                R.id.widget_image,
                fittedBitmap
            )

            manager.updateAppWidget(
                id,
                views
            )
        }

        if (fittedBitmap !== fullBitmap) {
            fullBitmap.recycle()
        }

        web.destroy()
        stopSelf()
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
