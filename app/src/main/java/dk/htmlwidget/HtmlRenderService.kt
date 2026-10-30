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

        val address =
            prefs.getString("url", "")?.trim() ?: ""

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
            )
                .roundToInt()
                .coerceAtLeast(1)

        val widgetHeight =
            (
                options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    180
                ) * density
            )
                .roundToInt()
                .coerceAtLeast(1)

        /*
         * LOKAL HTML
         *
         * Behold den fungerende metode.
         */
        if (!localUri.isNullOrBlank()) {

            val html = try {

                contentResolver
                    .openInputStream(
                        Uri.parse(localUri)
                    )
                    ?.bufferedReader()
                    ?.use { it.readText() }

            } catch (_: Exception) {
                null
            }

            if (!html.isNullOrBlank()) {

                renderLocalHtml(
                    html,
                    localUri,
                    manager,
                    ids,
                    widgetWidth,
                    widgetHeight
                )

                return
            }
        }

        /*
         * HJEMMESIDE
         */
        if (
            address.startsWith("https://") ||
            address.startsWith("http://")
        ) {

            renderWebsite(
                address,
                manager,
                ids,
                widgetWidth,
                widgetHeight
            )

            return
        }

        stopSelf()
    }


    /*
     * ==========================================
     * LOKAL HTML
     * ==========================================
     */

    private fun renderLocalHtml(
        html: String,
        localUri: String,
        manager: AppWidgetManager,
        ids: IntArray,
        width: Int,
        height: Int
    ) {

        val web = createWebView()

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

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    handler.postDelayed({

                        /*
                         * Din elpris-HTML bruger resize
                         * til fitGrid().
                         */
                        web.evaluateJavascript(
                            """
                            window.dispatchEvent(
                                new Event('resize')
                            );
                            """.trimIndent()
                        ) {

                            handler.postDelayed({

                                drawDirect(
                                    web,
                                    manager,
                                    ids,
                                    width,
                                    height
                                )

                            }, 1200)
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


    /*
     * ==========================================
     * HJEMMESIDE
     * ==========================================
     */

    private fun renderWebsite(
        address: String,
        manager: AppWidgetManager,
        ids: IntArray,
        widgetWidth: Int,
        widgetHeight: Int
    ) {

        val web = createWebView()

        /*
         * Hjemmesiden får en større virtuel skærm.
         *
         * Vi bruger widgettens proportioner,
         * men cirka dobbelt størrelse.
         */
        val pageWidth =
            (widgetWidth * 2)
                .coerceIn(1000, 1800)

        val ratio =
            widgetHeight.toFloat() /
                widgetWidth.toFloat()

        val pageHeight =
            (pageWidth * ratio)
                .roundToInt()
                .coerceAtLeast(600)

        web.settings.apply {

            /*
             * Få hjemmesider til at opføre sig mere
             * som på tablet/desktop.
             */
            useWideViewPort = true
            loadWithOverviewMode = true

            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Tablet) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Safari/537.36"
        }

        web.measure(
            View.MeasureSpec.makeMeasureSpec(
                pageWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                pageHeight,
                View.MeasureSpec.EXACTLY
            )
        )

        web.layout(
            0,
            0,
            pageWidth,
            pageHeight
        )

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    /*
                     * Hjemmesider bruger ofte længere
                     * tid på JS, billeder og layout.
                     */
                    handler.postDelayed({

                        drawWebsite(
                            web,
                            manager,
                            ids,
                            pageWidth,
                            pageHeight,
                            widgetWidth,
                            widgetHeight
                        )

                    }, 5000)
                }
            }

        web.loadUrl(address)
    }


    /*
     * ==========================================
     * FÆLLES WEBVIEW
     * ==========================================
     */

    private fun createWebView(): WebView {

        val web = WebView(this)

        /*
         * Software-rendering skal beholdes.
         */
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

        return web
    }


    /*
     * ==========================================
     * HTML -> BITMAP
     * ==========================================
     */

    private fun drawDirect(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        width: Int,
        height: Int
    ) {

        web.invalidate()

        handler.postDelayed({

            val bitmap =
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

            web.draw(
                Canvas(bitmap)
            )

            updateWidget(
                manager,
                ids,
                bitmap
            )

            web.destroy()
            stopSelf()

        }, 300)
    }


    /*
     * ==========================================
     * HJEMMESIDE -> BITMAP -> SKALER NED
     * ==========================================
     */

    private fun drawWebsite(
        web: WebView,
        manager: AppWidgetManager,
        ids: IntArray,
        pageWidth: Int,
        pageHeight: Int,
        widgetWidth: Int,
        widgetHeight: Int
    ) {

        web.invalidate()

        handler.postDelayed({

            val largeBitmap =
                Bitmap.createBitmap(
                    pageWidth,
                    pageHeight,
                    Bitmap.Config.ARGB_8888
                )

            web.draw(
                Canvas(largeBitmap)
            )

            /*
             * Hele den virtuelle browserflade
             * skaleres til widgetten.
             */
            val bitmap =
                Bitmap.createScaledBitmap(
                    largeBitmap,
                    widgetWidth,
                    widgetHeight,
                    true
                )

            updateWidget(
                manager,
                ids,
                bitmap
            )

            if (bitmap !== largeBitmap) {
                largeBitmap.recycle()
            }

            web.destroy()
            stopSelf()

        }, 300)
    }


    /*
     * ==========================================
     * OPDATER WIDGET
     * ==========================================
     */

    private fun updateWidget(
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
