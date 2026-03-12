package com.esiri.esiriplus.core.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Applies a repeating diagonal watermark to attachments (images and PDFs).
 * The watermark contains "eSIRI+" and the doctor's name for traceability.
 */
object WatermarkUtil {

    private const val WATERMARK_ALPHA = 40 // 0-255, semi-transparent
    private const val TEXT_SIZE_RATIO = 0.018f // smaller text for denser tiling
    private const val SPACING_RATIO = 1.3f // tight horizontal gap
    private const val ROW_SPACING_RATIO = 0.4f // tight vertical gap
    private const val ROTATION_DEGREES = -30f

    // PDF constants
    private const val PDF_FONT_SIZE = 16f
    private const val PDF_OPACITY = 0.10f
    private const val PDF_ROTATION_RAD = -0.5236f // -30 degrees in radians
    private const val PDF_SPACING_X = 180f
    private const val PDF_SPACING_Y = 90f

    /**
     * Applies a watermark to image bytes.
     *
     * @param imageBytes Original image file bytes
     * @param doctorName Name of the doctor on the consultation session
     * @param mimeType MIME type of the image (used to pick output format)
     * @return Watermarked image bytes, or original bytes if watermarking fails
     */
    fun applyImageWatermark(
        imageBytes: ByteArray,
        doctorName: String,
        mimeType: String = "image/jpeg",
    ): ByteArray {
        return try {
            val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return imageBytes

            val watermarked = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(watermarked)

            val diagonal = Math.hypot(
                watermarked.width.toDouble(),
                watermarked.height.toDouble(),
            ).toFloat()
            val textSize = (diagonal * TEXT_SIZE_RATIO).coerceIn(14f, 60f)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = WATERMARK_ALPHA
                this.textSize = textSize
                isFakeBoldText = true
                setShadowLayer(2f, 1f, 1f, Color.argb(WATERMARK_ALPHA, 0, 0, 0))
            }

            val watermarkText = "eSIRI+ | Dr. $doctorName"
            val textWidth = paint.measureText(watermarkText)
            val spacing = textWidth * SPACING_RATIO

            canvas.save()
            canvas.rotate(ROTATION_DEGREES, watermarked.width / 2f, watermarked.height / 2f)

            val startX = -diagonal
            val startY = -diagonal
            val endX = diagonal * 2
            val endY = diagonal * 2

            var y = startY
            while (y < endY) {
                var x = startX
                while (x < endX) {
                    canvas.drawText(watermarkText, x, y, paint)
                    x += spacing
                }
                y += spacing * ROW_SPACING_RATIO
            }

            canvas.restore()

            if (!original.isRecycled) original.recycle()

            val outputStream = ByteArrayOutputStream()
            val format = when {
                mimeType.contains("png") -> Bitmap.CompressFormat.PNG
                mimeType.contains("webp") -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.JPEG
            }
            watermarked.compress(format, 92, outputStream)
            watermarked.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            imageBytes
        }
    }

    /**
     * Applies a repeating diagonal watermark to PDF bytes.
     *
     * @param pdfBytes Original PDF file bytes
     * @param doctorName Name of the doctor on the consultation session
     * @return Watermarked PDF bytes, or original bytes if watermarking fails
     */
    fun applyPdfWatermark(
        context: Context,
        pdfBytes: ByteArray,
        doctorName: String,
    ): ByteArray {
        return try {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context.applicationContext)
            }
            val document = PDDocument.load(ByteArrayInputStream(pdfBytes))

            val watermarkText = "eSIRI+ | Dr. $doctorName"
            val font = PDType1Font.HELVETICA_BOLD

            // Transparency state
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = PDF_OPACITY
                strokingAlphaConstant = PDF_OPACITY
            }

            for (i in 0 until document.numberOfPages) {
                val page: PDPage = document.getPage(i)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                val contentStream = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,  // compress
                    true,  // reset context
                )

                contentStream.setGraphicsStateParameters(graphicsState)
                @Suppress("DEPRECATION")
                contentStream.setNonStrokingColor(128, 128, 128) // gray

                // Draw repeating diagonal watermark across the page
                var y = -pageHeight
                while (y < pageHeight * 2) {
                    var x = -pageWidth
                    while (x < pageWidth * 2) {
                        contentStream.beginText()
                        contentStream.setFont(font, PDF_FONT_SIZE)
                        val matrix = Matrix.getRotateInstance(
                            PDF_ROTATION_RAD.toDouble(),
                            x,
                            y,
                        )
                        contentStream.setTextMatrix(matrix)
                        contentStream.showText(watermarkText)
                        contentStream.endText()
                        x += PDF_SPACING_X
                    }
                    y += PDF_SPACING_Y
                }

                contentStream.close()
            }

            val outputStream = ByteArrayOutputStream()
            document.save(outputStream)
            document.close()

            outputStream.toByteArray()
        } catch (e: Exception) {
            pdfBytes
        }
    }
}
