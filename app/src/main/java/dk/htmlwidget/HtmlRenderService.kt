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
        handler.post { render() }
        return START_NOT_STICKY
    }

    private fun render() {

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
        } catch (_: Exception) {
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

        /*
         * Første størrelse er kun arbejdsområdet.
         * Den endelige størrelse findes fra HTML'en.
         */
        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                1000,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                1000,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            1000,
            1000
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Vent på at priserne er hentet
                     * og alle 24 kolonner er oprettet.
                     */
                    handler.postDelayed({

                        preparePage(
                            web,
                            manager,
                            ids,
                            widgetWidth,
                            widgetHeight
                        )

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

    private fun preparePage(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        widgetWidth: Int,
        widgetHeight: Int
    ) {

        web.evaluateJavascript(
            """
            (function() {

                /*
                 * SPECIAL CASE:
                 * Hvis siden har #grid og .wrap,
                 * bruger vi selve tabellens fulde bredde.
                 */
                var grid =
                    document.querySelector('#grid');

                var wrap =
                    document.querySelector('.wrap');

                if (grid && wrap) {

                    var gridWidth =
                        Math.ceil(
                            Math.max(
                                grid.scrollWidth,
                                grid.offsetWidth
                            )
                        );

                    /*
                     * Fjern den vandrette scroll-container.
                     * Nu bliver ALLE 24 timer en del af
                     * selve siden.
                     */
                    wrap.style.maxWidth = 'none';
                    wrap.style.width =
                        gridWidth + 'px';

                    wrap.style.overflow = 'visible';

                    document.body.style.width =
                        (gridWidth + 24) + 'px';

                    document.documentElement.style.width =
                        (gridWidth + 24) + 'px';
                }

                /*
                 * GENEREL FALLBACK:
                 * Virker også med andre HTML-filer.
                 */
                var width =
                    Math.ceil(
                        Math.max(
                            document.body.scrollWidth,
                            document.documentElement.scrollWidth,
                            grid ? grid.scrollWidth : 0
                        )
                    );

                var height =
                    Math.ceil(
                        Math.max(
                            document.body.scrollHeight,
                            document.documentElement.scrollHeight
                        )
                    );

                return width + "," + height;

            })();
            """.trimIndent()
        ) { result ->

            val parts =
                result
                    .replace("\"", "")
                    .trim()
                    .split(",")

            val width =
                parts
                    .getOrNull(0)
                    ?.toIntOrNull()
                    ?.coerceIn(200, 5000)
                    ?: 1000

            val height =
                parts
                    .getOrNull(1)
                    ?.toIntOrNull()
                    ?.coerceIn(100, 2000)
                    ?: 1000

            /*
             * NU ændrer vi WebView'en til den fundne
             * fulde størrelse.
             */
            web.measure(
                View.MeasureSpec.makeMeasureSpec(
                    width,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    height,
                    View.MeasureSpec.EXACTLY
                )
            )

            web.layout(
                0,
                0,
                width,
                height
            )

            web.invalidate()

            handler.postDelayed({

                makeBitmap(
                    web,
                    manager,
                    ids,
                    width,
                    height,
                    widgetWidth,
                    widgetHeight
                )

            }, 1200)
        }
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
         * Hele siden skal være synlig.
         * Samme skalering bruges i begge retninger.
         */
        val scale =
            min(
                widgetWidth.toFloat() /
                    contentWidth.toFloat(),

                widgetHeight.toFloat() /
                    contentHeight.toFloat()
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
