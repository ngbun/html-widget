
package dk.htmlwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import kotlin.math.roundToInt

class WebsiteRenderActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val address = getSharedPreferences(
            "settings",
            MODE_PRIVATE
        ).getString("url", "")?.trim() ?: ""

        if (
            !address.startsWith("https://") &&
            !address.startsWith("http://")
        ) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)

        val ids = manager.getAppWidgetIds(
            ComponentName(
                this,
                HtmlWidgetProvider::class.java
            )
        )

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
        }

        /*
         * VIGTIGT:
         * WebView bliver nu sat ind i et rigtigt
         * Activity-vindue.
         */
        setContentView(web)

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                web.postDelayed({

                    renderWidget(
                        manager,
                        ids,
                        width,
                        height
                    )

                }, 5000)
            }
        }

        web.loadUrl(address)
    }

    private fun renderWidget(
        manager: AppWidgetManager,
        ids: IntArray,
        width: Int,
        height: Int
    ) {

        /*
         * Vi tegner den synlige WebView
         * ned i widgettens størrelse.
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

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

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

        web.destroy()
        finish()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            try {
                web.stopLoading()
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}
