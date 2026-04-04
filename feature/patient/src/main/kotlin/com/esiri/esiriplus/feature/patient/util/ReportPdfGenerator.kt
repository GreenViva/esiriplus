package com.esiri.esiriplus.feature.patient.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.feature.patient.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a PDF matching the ESIRII HEALTH consultation report template.
 * Uses Android's built-in PdfDocument API (no external dependencies).
 */
object ReportPdfGenerator {

    private const val PAGE_WIDTH = 595  // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN.toInt()

    private val TEAL = Color.rgb(42, 157, 143) // #2A9D8F
    private val DARK_TEXT = Color.rgb(31, 41, 55)
    private val LABEL_GREY = Color.rgb(107, 114, 128)
    private val SECTION_BG = Color.rgb(248, 255, 254)
    private val DISCLAIMER_BG = Color.rgb(255, 251, 235)
    private val DISCLAIMER_TEXT = Color.rgb(146, 64, 14)
    private val DIVIDER_GREY = Color.rgb(229, 231, 235)

    fun generate(context: Context, report: PatientReport): File {
        val document = PdfDocument()
        val pages = mutableListOf<PdfDocument.Page>()
        var currentPage = createPage(document, pages.size + 1)
        pages.add(currentPage)
        var canvas = currentPage.canvas
        var y = 0f

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(230, 255, 255, 255)
            textSize = 12f
        }

        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = TEAL
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = LABEL_GREY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = DARK_TEXT
            textSize = 10f
        }

        val smallPaint = Paint().apply {
            isAntiAlias = true
            color = LABEL_GREY
            textSize = 9f
        }

        val bgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // --- Teal Header ---
        bgPaint.color = TEAL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 80f, bgPaint)
        y = 35f
        drawCenteredText(canvas, context.getString(R.string.pdf_esirii_health), titlePaint, y)
        y += 20f
        drawCenteredText(canvas, context.getString(R.string.pdf_telemedicine_report), subtitlePaint, y)
        y = 80f

        // --- Consultation info bar ---
        bgPaint.color = SECTION_BG
        canvas.drawRect(0f, y, PAGE_WIDTH.toFloat(), y + 35f, bgPaint)

        val refPaint = Paint(labelPaint).apply { color = TEAL; textSize = 10f }
        canvas.drawText(context.getString(R.string.pdf_consultation_report), MARGIN, y + 15f, refPaint)
        if (report.verificationCode.isNotBlank()) {
            val refSmall = Paint(smallPaint)
            canvas.drawText(context.getString(R.string.pdf_ref_format, report.verificationCode), MARGIN, y + 28f, refSmall)
        }
        if (report.consultationDate > 0) {
            val datePaint = Paint(bodyPaint).apply { textSize = 10f }
            val dateStr = formatDate(report.consultationDate)
            val dateWidth = datePaint.measureText(dateStr)
            canvas.drawText(dateStr, PAGE_WIDTH - MARGIN - dateWidth, y + 20f, datePaint)
        }
        y += 35f

        // Teal divider
        bgPaint.color = TEAL
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 2f, bgPaint)
        y += 16f

        // Helper to check if we need a new page
        fun ensureSpace(needed: Float): Float {
            var currentY = y
            if (currentY + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(currentPage)
                currentPage = createPage(document, pages.size + 1)
                pages.add(currentPage)
                canvas = currentPage.canvas
                currentY = MARGIN
            }
            return currentY
        }

        // --- Section 1: Patient Information ---
        y = ensureSpace(80f)
        canvas.drawText(context.getString(R.string.pdf_section_patient_info), MARGIN, y, sectionPaint)
        y += 18f

        if (report.patientSessionId.isNotBlank()) {
            y = drawInfoRow(canvas, context.getString(R.string.pdf_patient_id), report.patientSessionId.take(12) + "...", labelPaint, bodyPaint, y)
        }
        if (report.consultationDate > 0) {
            y = drawInfoRow(canvas, context.getString(R.string.pdf_consultation_date), formatDate(report.consultationDate), labelPaint, bodyPaint, y)
        }
        y = drawInfoRow(canvas, context.getString(R.string.pdf_consultation_type), context.getString(R.string.pdf_telemedicine), labelPaint, bodyPaint, y)
        y += 16f

        // --- Section 2: Presenting Symptoms ---
        y = ensureSpace(60f)
        canvas.drawText(context.getString(R.string.pdf_section_symptoms), MARGIN, y, sectionPaint)
        y += 14f
        val symptomsText = report.presentingSymptoms.ifBlank { context.getString(R.string.pdf_no_symptoms) }
        y = drawProseBlock(canvas, symptomsText, bodyPaint, bgPaint, y) { needed -> ensureSpace(needed) }
        y += 16f

        // --- Section 3: Diagnosis and Assessment ---
        y = ensureSpace(80f)
        canvas.drawText(context.getString(R.string.pdf_section_diagnosis), MARGIN, y, sectionPaint)
        y += 18f
        if (report.diagnosedProblem.isNotBlank()) {
            y = ensureSpace(16f)
            y = drawInfoRow(canvas, context.getString(R.string.pdf_primary_diagnosis), report.diagnosedProblem, labelPaint, bodyPaint, y)
        }
        if (report.category.isNotBlank()) {
            y = ensureSpace(16f)
            y = drawInfoRow(canvas, context.getString(R.string.pdf_category), report.category, labelPaint, bodyPaint, y)
        }
        if (report.severity.isNotBlank()) {
            y = ensureSpace(16f)
            y = drawInfoRow(canvas, context.getString(R.string.pdf_severity), report.severity, labelPaint, bodyPaint, y)
        }
        if (report.diagnosisAssessment.isNotBlank()) {
            y += 6f
            y = drawProseBlock(canvas, report.diagnosisAssessment, bodyPaint, bgPaint, y) { needed -> ensureSpace(needed) }
        }
        y += 16f

        // --- Section 4: Treatment Plan ---
        y = ensureSpace(60f)
        canvas.drawText(context.getString(R.string.pdf_section_treatment), MARGIN, y, sectionPaint)
        y += 14f
        val planText = report.treatmentPlan.ifBlank { context.getString(R.string.pdf_no_treatment) }
        y = drawProseBlock(canvas, planText, bodyPaint, bgPaint, y) { needed -> ensureSpace(needed) }
        y += 16f

        // --- Prescribed Medications ---
        if (report.prescribedMedications.isNotBlank()) {
            y = ensureSpace(60f)
            canvas.drawText("PRESCRIBED MEDICATIONS", MARGIN, y, sectionPaint)
            y += 18f
            y = drawProseBlock(canvas, report.prescribedMedications, bodyPaint, bgPaint, y) { needed -> ensureSpace(needed) }
            y += 16f
        }

        // --- Section 5: Follow-up Instructions ---
        y = ensureSpace(60f)
        canvas.drawText(context.getString(R.string.pdf_section_followup), MARGIN, y, sectionPaint)
        y += 18f
        y = drawInfoRow(
            canvas,
            context.getString(R.string.pdf_followup_recommended),
            if (report.followUpRecommended) context.getString(R.string.pdf_yes) else context.getString(R.string.pdf_no),
            labelPaint,
            bodyPaint,
            y,
        )
        if (report.followUpInstructions.isNotBlank()) {
            y += 6f
            y = drawProseBlock(canvas, report.followUpInstructions, bodyPaint, bgPaint, y) { needed -> ensureSpace(needed) }
        }
        if (report.furtherNotes.isNotBlank()) {
            y += 8f
            y = ensureSpace(30f)
            val noteLabel = Paint(labelPaint).apply { color = DARK_TEXT }
            canvas.drawText(context.getString(R.string.pdf_additional_notes), MARGIN, y, noteLabel)
            y += 12f
            y = drawProseBlock(canvas, report.furtherNotes, bodyPaint, bgPaint, y) { needed -> ensureSpace(needed) }
        }
        y += 16f

        // --- Section 6: Disclaimer ---
        y = ensureSpace(80f)
        canvas.drawText(context.getString(R.string.pdf_section_disclaimer), MARGIN, y, sectionPaint)
        y += 14f
        val disclaimerText = context.getString(R.string.pdf_disclaimer_text)
        val disclaimerPaint = Paint(bodyPaint).apply {
            color = DISCLAIMER_TEXT
            textSize = 9f
        }
        bgPaint.color = DISCLAIMER_BG
        y = drawProseBlock(canvas, disclaimerText, disclaimerPaint, bgPaint, y) { needed -> ensureSpace(needed) }
        y += 24f

        // --- Signature ---
        y = ensureSpace(60f)
        bgPaint.color = DIVIDER_GREY
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1f, bgPaint)
        y += 14f
        if (report.doctorName.isNotBlank()) {
            val sigPaint = Paint(bodyPaint).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = DARK_TEXT
            }
            canvas.drawText("Dr. ${report.doctorName}", MARGIN, y, sigPaint)
            y += 14f
            canvas.drawText(context.getString(R.string.pdf_attending_physician), MARGIN, y, smallPaint)
            y += 14f
        }
        val italicPaint = Paint(smallPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText(context.getString(R.string.pdf_electronically_signed), MARGIN, y, italicPaint)
        y += 24f

        // --- Footer ---
        y = ensureSpace(30f)
        bgPaint.color = Color.rgb(243, 244, 246)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f, bgPaint)
        val footerPaint = Paint(smallPaint).apply { textSize = 8f }
        drawCenteredText(canvas, context.getString(R.string.pdf_generated_by), footerPaint, y + 15f)

        document.finishPage(currentPage)

        // Write to cache
        val outputFile = File(context.cacheDir, "consultation_report_${report.reportId.take(8)}.pdf")
        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return outputFile
    }

    private fun createPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(pageInfo)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, paint: Paint, y: Float) {
        val textWidth = paint.measureText(text)
        canvas.drawText(text, (PAGE_WIDTH - textWidth) / 2f, y, paint)
    }

    private fun drawInfoRow(
        canvas: Canvas,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint,
        startY: Float,
    ): Float {
        canvas.drawText("$label:", MARGIN, startY, labelPaint)
        // Wrap value text to fit
        val valueX = MARGIN + 140f
        val maxWidth = PAGE_WIDTH - MARGIN - valueX
        val lines = wrapText(value, valuePaint, maxWidth)
        var y = startY
        lines.forEach { line ->
            canvas.drawText(line, valueX, y, valuePaint)
            y += 14f
        }
        if (lines.isEmpty()) y += 14f
        return y
    }

    private fun drawProseBlock(
        canvas: Canvas,
        text: String,
        textPaint: Paint,
        bgPaint: Paint,
        startY: Float,
        ensureSpace: (Float) -> Float,
    ): Float {
        val padding = 10f
        val maxWidth = CONTENT_WIDTH - 2 * padding
        val lines = wrapText(text, textPaint, maxWidth)
        val lineHeight = 14f
        val blockHeight = lines.size * lineHeight + 2 * padding

        var y = ensureSpace(blockHeight)

        val prevColor = bgPaint.color
        // Only draw bg if it's set to a special color (not default)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + blockHeight, bgPaint)
        bgPaint.color = prevColor

        y += padding + lineHeight - 4f
        lines.forEach { line ->
            canvas.drawText(line, MARGIN + padding, y, textPaint)
            y += lineHeight
        }
        return y + padding - lineHeight + 4f
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        paragraphs.forEach { paragraph ->
            if (paragraph.isBlank()) {
                result.add("")
                return@forEach
            }
            val words = paragraph.split(" ")
            var currentLine = StringBuilder()
            words.forEach { word ->
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine = StringBuilder(testLine)
                } else {
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString())
                    }
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
        }
        return result
    }

    private fun formatDate(millis: Long): String {
        if (millis == 0L) return ""
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}
