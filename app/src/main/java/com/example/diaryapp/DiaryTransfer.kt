package com.example.diaryapp

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BACKUP_VERSION = 1
private const val BACKUP_METADATA = "backup.json"
private const val MAX_BACKUP_METADATA_BYTES = 8 * 1024 * 1024
private const val MAX_BACKUP_PHOTO_BYTES = 16L * 1024 * 1024
private const val MAX_BACKUP_TOTAL_BYTES = 1024L * 1024 * 1024
private const val MAX_BACKUP_ENTRIES = 20_000

data class RestoredDiaryData(
    val profile: Profile,
    val entries: List<DiaryEntry>,
    val theme: String
)

fun profileToJson(profile: Profile): JSONObject {
    val details = JSONObject()
    profile.topicDetails.forEach { (topic, values) ->
        details.put(topic, JSONArray(values))
    }
    return JSONObject()
        .put("name", profile.name)
        .put("gender", profile.gender)
        .put("age", profile.age)
        .put("notifyTimes", JSONArray(profile.notifyTimes))
        .put("topics", JSONArray(profile.topics))
        .put("topicDetails", details)
        .put("topicPromptDismissedDay", profile.topicPromptDismissedDay)
}

fun profileFromJson(json: JSONObject): Profile {
    val topics = json.optJSONArray("topics") ?: JSONArray()
    val times = json.optJSONArray("notifyTimes") ?: JSONArray()
    val detailsJson = json.optJSONObject("topicDetails") ?: JSONObject()
    val details = buildMap {
        val keys = detailsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val values = detailsJson.optJSONArray(key) ?: JSONArray()
            put(key, List(values.length()) { index -> values.optString(index) }.filter { it.isNotBlank() })
        }
    }
    return Profile(
        name = json.optString("name"),
        gender = json.optString("gender"),
        age = json.optString("age"),
        notifyTimes = (if (json.has("notifyTimes")) {
            List(times.length()) { times.optString(it) }
        } else {
            listOf(json.optString("notifyTime", "22:00"))
        }).map(::normalizeReminderText).distinct(),
        topics = List(topics.length()) { topics.optString(it) }.filter { it.isNotBlank() },
        topicDetails = details,
        topicPromptDismissedDay = json.optInt("topicPromptDismissedDay", 0)
    )
}

fun entryToJson(entry: DiaryEntry): JSONObject = JSONObject()
    .put("id", entry.id)
    .put("date", entry.date)
    .put("time", entry.time)
    .put("text", entry.text)
    .put("kind", entry.kind)
    .put("photoUri", entry.photoUri ?: "")

fun entryFromJson(json: JSONObject, index: Int = 0): DiaryEntry {
    val date = json.optString("date")
    val time = json.optString("time")
    val text = json.optString("text")
    val stableSource = "$date|$time|$text|$index"
    val id = json.optString("id").ifBlank {
        UUID.nameUUIDFromBytes(stableSource.toByteArray(StandardCharsets.UTF_8)).toString()
    }
    return DiaryEntry(
        date = date,
        time = time,
        text = text,
        kind = json.optString("kind", "normal"),
        photoUri = json.optString("photoUri").takeIf { it.isNotBlank() },
        id = id
    )
}

fun backupFileName(today: LocalDate = LocalDate.now()): String =
    "haru-piece-backup-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.zip"

suspend fun exportDiaryBackup(
    context: Context,
    destination: Uri,
    profile: Profile,
    entries: List<DiaryEntry>,
    theme: String
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        require(entries.size <= MAX_BACKUP_ENTRIES) { "기록이 너무 많아요." }
        val photoAssets = entries.mapNotNull { entry ->
            entry.photoUri?.takeIf { it.isNotBlank() }?.let { uri ->
                val readable = openPhotoInputStream(context, uri)?.use { true } == true
                require(readable) { "백업할 사진을 읽을 수 없어요." }
                entry.id to ("photos/${entry.id}.jpg" to uri)
            }
        }.toMap()

        val entryRows = JSONArray()
        entries.forEach { entry ->
            val assetName = photoAssets[entry.id]?.first
            entryRows.put(entryToJson(entry).apply {
                remove("photoUri")
                put("photoAsset", assetName ?: "")
            })
        }
        val metadata = JSONObject()
            .put("format", "haru-piece")
            .put("version", BACKUP_VERSION)
            .put("createdAt", LocalDateTime.now().toString())
            .put("theme", theme)
            .put("profile", profileToJson(profile))
            .put("entries", entryRows)
        val metadataBytes = metadata.toString().toByteArray(StandardCharsets.UTF_8)
        require(metadataBytes.size <= MAX_BACKUP_METADATA_BYTES) { "백업 정보가 너무 커요." }

        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("백업 파일을 열 수 없어요.")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_METADATA))
            zip.write(metadataBytes)
            zip.closeEntry()

            photoAssets.values.forEach { (name, uriText) ->
                openPhotoInputStream(context, uriText)?.use { input ->
                    zip.putNextEntry(ZipEntry(name))
                    input.copyLimitedTo(zip, MAX_BACKUP_PHOTO_BYTES)
                    zip.closeEntry()
                }
            }
        }
    }
}
suspend fun importDiaryBackup(context: Context, source: Uri): Result<RestoredDiaryData> =
    withContext(Dispatchers.IO) {
        runCatching {
            val restoreDir = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                var metadataText: String? = null
                var fileCount = 0
                var totalBytes = 0L
                val stagedPhotos = mutableMapOf<String, File>()
                val input = context.contentResolver.openInputStream(source)
                    ?: error("백업 파일을 열 수 없어요.")

                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        fileCount += 1
                        require(fileCount <= MAX_BACKUP_ENTRIES) { "백업 파일 항목이 너무 많아요." }
                        require(isSafeZipEntry(entry.name)) { "안전하지 않은 백업 파일이에요." }

                        when {
                            entry.isDirectory -> Unit
                            entry.name == BACKUP_METADATA -> {
                                val bytes = zip.readBytesLimited(MAX_BACKUP_METADATA_BYTES.toLong())
                                totalBytes += bytes.size
                                metadataText = String(bytes, StandardCharsets.UTF_8)
                            }
                            entry.name.startsWith("photos/") -> {
                                val fileName = entry.name.substringAfter("photos/")
                                require(fileName.isNotBlank() && !fileName.contains('/')) {
                                    "사진 파일 경로가 올바르지 않아요."
                                }
                                val target = File(restoreDir, fileName)
                                FileOutputStream(target).use { output ->
                                    totalBytes += zip.copyLimitedTo(output, MAX_BACKUP_PHOTO_BYTES)
                                }
                                stagedPhotos[entry.name] = target
                            }
                        }
                        require(totalBytes <= MAX_BACKUP_TOTAL_BYTES) { "백업 파일이 너무 커요." }
                        zip.closeEntry()
                    }
                }

                val metadata = JSONObject(metadataText ?: error("하루조각 백업 정보가 없어요."))
                require(metadata.optString("format") == "haru-piece") { "하루조각 백업 파일이 아니에요." }
                require(metadata.optInt("version", 0) in 1..BACKUP_VERSION) { "지원하지 않는 백업 버전이에요." }

                val profile = profileFromJson(metadata.getJSONObject("profile"))
                val rows = metadata.optJSONArray("entries") ?: JSONArray()
                require(rows.length() <= MAX_BACKUP_ENTRIES) { "기록이 너무 많아요." }
                val photosDir = File(context.filesDir, "entry_photos").apply { mkdirs() }
                val restoredEntries = List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    val base = entryFromJson(row, index)
                    val assetName = row.optString("photoAsset")
                    val staged = stagedPhotos[assetName]
                    if (staged == null) {
                        base.copy(photoUri = null)
                    } else {
                        val restoredPhoto = File(photosDir, "${UUID.randomUUID()}.jpg")
                        staged.copyTo(restoredPhoto, overwrite = false)
                        base.copy(photoUri = restoredPhoto.absolutePath)
                    }
                }
                RestoredDiaryData(
                    profile = profile,
                    entries = restoredEntries,
                    theme = metadata.optString("theme", "기본").takeIf {
                        it in listOf("기본", "밤", "종이")
                    } ?: "기본"
                )
            } finally {
                restoreDir.deleteRecursively()
            }
        }
    }

suspend fun shareDiaryEntry(context: Context, entry: DiaryEntry): Result<Unit> {
    val card = withContext(Dispatchers.IO) {
        runCatching { createShareCard(context, entry) }
    }.getOrElse { return Result.failure(it) }

    return withContext(Dispatchers.Main) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                card
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${entry.date}\n${entry.text}")
                clipData = ClipData.newUri(context.contentResolver, "하루조각", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "오늘의 조각 공유하기"))
        }
    }
}
private fun createShareCard(context: Context, entry: DiaryEntry): File {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(255, 244, 244))

    paint.color = Color.rgb(59, 48, 48)
    paint.textSize = 58f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("하루조각", 90f, 115f, paint)

    paint.color = Color.rgb(214, 109, 112)
    paint.textSize = 32f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(entry.date, 90f, 170f, paint)

    var textTop = 350f
    val photo = decodeEntryPhoto(context, entry.photoUri)
    if (photo != null) {
        val target = RectF(90f, 220f, 990f, 760f)
        drawPhotoFit(canvas, photo, target, paint)
        photo.recycle()
        textTop = 860f
    }

    paint.color = Color.WHITE
    canvas.drawRoundRect(RectF(60f, textTop - 85f, 1020f, 1240f), 34f, 34f, paint)

    paint.color = Color.rgb(214, 109, 112)
    paint.textSize = 30f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("오늘의 조각", 110f, textTop, paint)

    paint.color = Color.rgb(59, 48, 48)
    paint.textSize = if (photo == null) 54f else 46f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    drawWrappedText(canvas, entry.text, 110f, textTop + 92f, 860f, paint, paint.textSize * 1.55f)

    paint.color = Color.rgb(120, 105, 105)
    paint.textSize = 25f
    canvas.drawText("조각들이 모여 하루가 돼요", 110f, 1185f, paint)

    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L }
        ?.forEach(File::delete)
    val file = File(dir, "haru-piece-${entry.id}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

private fun drawPhotoFit(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
    paint.color = Color.WHITE
    canvas.drawRoundRect(target, 28f, 28f, paint)
    val scale = minOf(target.width() / bitmap.width, target.height() / bitmap.height)
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    val destination = RectF(
        target.centerX() - width / 2f,
        target.centerY() - height / 2f,
        target.centerX() + width / 2f,
        target.centerY() + height / 2f
    )
    val clipPath = android.graphics.Path().apply {
        addRoundRect(target, 28f, 28f, android.graphics.Path.Direction.CW)
    }
    canvas.save()
    canvas.clipPath(clipPath)
    canvas.drawBitmap(bitmap, null, destination, paint)
    canvas.restore()
}
private fun drawWrappedText(
    canvas: Canvas,
    text: String,
    x: Float,
    startY: Float,
    maxWidth: Float,
    paint: Paint,
    lineHeight: Float
) {
    var y = startY
    text.lines().forEach { paragraph ->
        if (paragraph.isBlank()) {
            y += lineHeight
            return@forEach
        }
        var line = ""
        paragraph.forEach { char ->
            val candidate = line + char
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                line = char.toString()
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
    }
}

private fun decodeEntryPhoto(context: Context, uriText: String?): Bitmap? {
    if (uriText.isNullOrBlank()) return null
    return runCatching {
        if (uriText.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(uriText))?.use(BitmapFactory::decodeStream)
        } else {
            BitmapFactory.decodeFile(uriText)
        }
    }.getOrNull()
}

private fun openPhotoInputStream(context: Context, uriText: String?): InputStream? {
    if (uriText.isNullOrBlank()) return null
    return runCatching {
        if (uriText.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(uriText))
        } else {
            File(uriText).takeIf(File::isFile)?.inputStream()
        }
    }.getOrNull()
}

fun deleteOwnedEntryPhoto(context: Context, uriText: String?) {
    if (uriText.isNullOrBlank() || uriText.startsWith("content://")) return
    runCatching {
        val photoRoot = File(context.filesDir, "entry_photos").canonicalFile
        val target = File(uriText).canonicalFile
        if (target.parentFile == photoRoot && target.isFile) target.delete()
    }
}
internal fun isSafeZipEntry(name: String): Boolean =
    name.isNotBlank() &&
        !name.startsWith("/") &&
        !name.startsWith("\\") &&
        !name.contains("../") &&
        !name.contains("..\\") &&
        !name.contains(':') &&
        name.length <= 240

private fun InputStream.readBytesLimited(limit: Long): ByteArray {
    val output = ByteArrayOutputStream()
    copyLimitedTo(output, limit)
    return output.toByteArray()
}

internal fun InputStream.copyLimitedTo(output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "백업 항목이 너무 커요." }
        output.write(buffer, 0, count)
    }
    return total
}
