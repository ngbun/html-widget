package dk.htmlwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SetupActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("settings", MODE_PRIVATE)
    }

    private var localUri: Uri? = null
    private lateinit var url: EditText
    private lateinit var adBlock: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "HTML Widget"
            textSize = 26f
        })

        layout.addView(TextView(this).apply {
            text = "Indsæt adressen på en hjemmeside eller vælg en HTML-fil fra telefonen."
            textSize = 16f
        })

        url = EditText(this).apply {
            hint = "https://eksempel.dk"
            setText(prefs.getString("url", ""))
            singleLine = true
        }

        layout.addView(url)

        layout.addView(Button(this).apply {
            text = "Vælg HTML-fil"

            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/html"
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }

                startActivityForResult(intent, 42)
            }
        })

        adBlock = CheckBox(this).apply {
            text = "Bloker reklamer og tracking"
            isChecked = prefs.getBoolean("adblock", true)
        }

        layout.addView(adBlock)

        layout.addView(TextView(this).apply {
            text = "Widgetten opdateres automatisk cirka hvert 15. minut."
        })

        layout.addView(Button(this).apply {
            text = "Gem og opdater widget"

            setOnClickListener {

                prefs.edit()
                    .putString("url", url.text.toString().trim())
                    .putString(
                        "localUri",
                        localUri?.toString()
                            ?: prefs.getString("localUri", null)
                    )
                    .putBoolean("adblock", adBlock.isChecked)
                    .apply()

                val request =
                    PeriodicWorkRequestBuilder<RefreshWorker>(
                        15,
                        TimeUnit.MINUTES
                    ).build()

                WorkManager
                    .getInstance(this@SetupActivity)
                    .enqueueUniquePeriodicWork(
                        "html-widget-refresh",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )

                val manager =
                    AppWidgetManager.getInstance(this@SetupActivity)

                val ids = manager.getAppWidgetIds(
                    ComponentName(
                        this@SetupActivity,
                        HtmlWidgetProvider::class.java
                    )
                )

                HtmlWidgetProvider.refresh(
                    this@SetupActivity,
                    ids
                )

                Toast.makeText(
                    this@SetupActivity,
                    "Gemt – HTML Widget er opdateret",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        setContentView(
            ScrollView(this).apply {
                addView(layout)
            }
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == 42 && resultCode == RESULT_OK) {

            data?.data?.let { uri ->

                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                localUri = uri
                url.setText("")

                Toast.makeText(
                    this,
                    "HTML-fil valgt",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
