package dk.htmlwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.RemoteViews
import kotlin.math.roundToInt

class WebsiteRenderActivity : Activity() {

    private lateinit var web: WebView
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
    AppWidgetManager.EXTRA_APPWIDGET_ID,
    AppWidgetManager.INVALID_APPWIDGET_ID
)

if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
    finish()
    return
}

        val address = getSharedPreferences(
            "settings",
            MODE_PRIVATE
        ).getString(
    "url_$appWidgetId",
    ""
)?.trim() ?: ""

        if (
            !address.startsWith("https://") &&
            !address.startsWith("http://")
        ) {
            finish()
            return
        }
val manager = AppWidgetManager.getInstance(this)
        val ids = intArrayOf(appWidgetId)

        if (ids.isEmpty()) {
            finish()
            return
        }

        val options =
            manager.getAppWidgetOptions(ids[0])

        val density =
            resources.displayMetrics.density

        val width = (
            options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                300
            ) * density
        ).roundToInt().coerceAtLeast(1)

        val height = (
            options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                180
            ) * density
        ).roundToInt().coerceAtLeast(1)

        web = WebView(this)

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

            /*
             * Hjælper WebView med at holde siden
             * klar til rendering.
             */
            offscreenPreRaster = true
        }

        /*
         * WebView skal være synlig og attached
         * til Activity-vinduet.
         */
        setContentView(web)

        web.webViewClient =
            object : WebViewClient() { override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
): WebResourceResponse? {

    val adBlock = getSharedPreferences(
        "settings",
        MODE_PRIVATE
    ).getBoolean("adblock", false)

    if (!adBlock) {
        return super.shouldInterceptRequest(
            view,
            request
        )
    }

    val host =
        request?.url?.host
            ?.lowercase()
            ?: return super.shouldInterceptRequest(
                view,
                request
            )

    val blocked = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com"
    )

    if (blocked.any {
            host == it ||
            host.endsWith(".$it")
        }) {

        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            null
        )
    }

    return super.shouldInterceptRequest(
        view,
        request
    )
            }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    if (finished) return

                    /*
                     * DR kan fortsætte med at bygge
                     * siden efter onPageFinished.
                     */
                    web.postDelayed({

                        if (finished) return@postDelayed

                        web.postVisualStateCallback(
                            1L,
                            object :
                                WebView.VisualStateCallback() {

                                override fun onComplete(
                                    requestId: Long
                                ) {
                                    if (finished) return

                                    /*
                                     * Vent én ekstra frame,
                                     * mens WebView stadig er
                                     * synlig på skærmen.
                                     */
                                    web.postDelayed({

                                        captureWidget(
                                            manager,
                                            ids,
                                            width,
                                            height
                                        )

                                    }, 500)
                                }
                            }
                        )

                    }, 4000)
                }
            }

        web.loadUrl(address)
    }

    private fun captureWidget(
        manager: AppWidgetManager,
        ids: IntArray,
        width: Int,
        height: Int
    ) {
        if (finished) return

        /*
         * VIGTIGT:
         * Vi ændrer ikke WebViewens layout her.
         * Den har allerede været synlig og
         * renderet i Activity-vinduet.
         */

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        /*
         * Skalér den synlige WebView ned/op
         * til widgettens faktiske størrelse.
         */
        val scaleX =
            width.toFloat() /
                web.width.coerceAtLeast(1).toFloat()

        val scaleY =
            height.toFloat() /
                web.height.coerceAtLeast(1).toFloat()

        canvas.scale(
            scaleX,
            scaleY
        )

        web.draw(canvas)

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

        finished = true

        web.stopLoading()
        web.destroy()

        finish()
    }
}
