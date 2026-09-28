package io.kopitiam.app.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Android Sharesheet helpers (the mirror of iOS ShareSheet). */
object Share {

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun mimeFor(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".docx", true) ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        name.endsWith(".xlsx", true) ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }

    fun share(context: Context, files: List<File>, title: String = "Save & Share") {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.map { uriFor(context, it) })
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeFor(files[0].name)
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
