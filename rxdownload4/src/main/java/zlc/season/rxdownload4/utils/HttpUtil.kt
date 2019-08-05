package zlc.season.rxdownload4.utils

import okhttp3.internal.http.HttpHeaders
import retrofit2.Response
import zlc.season.rxdownload4.*
import zlc.season.rxdownload4.downloader.Downloader
import zlc.season.rxdownload4.downloader.NormalDownloader
import zlc.season.rxdownload4.downloader.RangeDownloader
import java.io.File
import java.util.regex.Pattern

fun Response<*>.url(): String {
    return raw().request().url().toString()
}

fun Response<*>.contentLength(): Long {
    return HttpHeaders.contentLength(headers())
}

fun Response<*>.isChunked(): Boolean {
    return header("Transfer-Encoding") == "chunked"
}

fun Response<*>.isSupportRange(): Boolean {
    if (!isSuccessful) {
        return false
    }

    if (code() == 206
            || header("Content-Range").isNotEmpty()
            || header("Accept-Ranges") == "bytes") {
        return true
    }

    return false
}

fun Response<*>.map(): Downloader {
    return if (isSupportRange()) {
        RangeDownloader()
    } else {
        NormalDownloader()
    }
}

fun Response<*>.file(): File {
    val fileName = fileName()
    return File(DEFAULT_SAVE_PATH, fileName)
}

fun Response<*>.fileName(): String {
    val url = this.raw().request().url().toString()

    var fileName = contentDisposition()
    if (fileName.isEmpty()) {
        fileName = getFileNameFromUrl(url)
    }

    val dotIndex = fileName.indexOf('.')

    if (dotIndex > 0 && dotIndex < fileName.lastIndex) {
        return fileName
    } else {
        throw IllegalStateException("Invalid filename: $fileName")
    }
}

fun Response<*>.sliceCount(): Long {
    val totalSize = contentLength()
    val remainder = totalSize % DEFAULT_RANGE_SIZE
    val result = totalSize / DEFAULT_RANGE_SIZE

    return if (remainder == 0L) {
        result
    } else {
        result + 1
    }
}

private fun Response<*>.contentDisposition(): String {
    val contentDisposition = header("Content-Disposition").toLowerCase()

    if (contentDisposition.isEmpty()) {
        return ""
    }

    val matcher = Pattern.compile(".*filename=(.*)").matcher(contentDisposition)
    if (!matcher.find()) {
        return ""
    }

    var result = matcher.group(1)
    if (result.startsWith("\"")) {
        result = result.substring(1)
    }
    if (result.endsWith("\"")) {
        result = result.substring(0, result.length - 1)
    }

    result = result.replace("/", "_", false)

    return result
}

private fun getFileNameFromUrl(url: String): String {
    var temp = url
    if (temp.isNotEmpty()) {
        val fragment = temp.lastIndexOf('#')
        if (fragment > 0) {
            temp = temp.substring(0, fragment)
        }

        val query = temp.lastIndexOf('?')
        if (query > 0) {
            temp = temp.substring(0, query)
        }

        val filenamePos = temp.lastIndexOf('/')
        val filename = if (0 <= filenamePos) temp.substring(filenamePos + 1) else temp

        if (filename.isNotEmpty() && Pattern.matches("[a-zA-Z_0-9.\\-()%]+", filename)) {
            return filename
        }
    }

    return ""
}

private fun Response<*>.header(key: String): String {
    val header = headers().get(key)
    return header ?: ""
}