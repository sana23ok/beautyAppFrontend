package com.example.beautyappfrontend.ui.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.beautyappfrontend.R
import com.example.beautyappfrontend.domain.model.MasterScheduleData
import kotlin.math.max
import kotlin.math.min

/**
 * Mon–Sun × 8–19. Default: all cells not working. Drag to select working slots (Excel-style).
 */
class ScheduleGridEditView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val hourCount = MasterScheduleData.HOUR_END_INCLUSIVE - MasterScheduleData.HOUR_START + 1

    private val selected = Array(MasterScheduleData.DAY_COUNT) { BooleanArray(hourCount) }
    private lateinit var baseline: Array<BooleanArray>

    private var anchorDay = 0
    private var anchorHourIdx = 0
    private var dragSelect = false
    private var dragging = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var pad = 0f
    private var labelColW = 0f
    private var headerH = 0f
    private var rowH = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var dayCellW = FloatArray(MasterScheduleData.DAY_COUNT)

    private val colorHeaderText = ContextCompat.getColor(context, R.color.white)
    private val colorLabelText = ContextCompat.getColor(context, R.color.text_primary)
    private val colorSelected = ContextCompat.getColor(context, R.color.schedule_slot_free)
    private val colorSelectedAlt = ContextCompat.getColor(context, R.color.schedule_slot_free_alt)
    private val colorUnselected = ContextCompat.getColor(context, R.color.schedule_hatch_base)
    private val colorStroke = ContextCompat.getColor(context, R.color.schedule_grid_stroke)

    init {
        setWillNotDraw(false)
    }

    fun setWeek(days: List<List<Int>>) {
        for (d in 0 until MasterScheduleData.DAY_COUNT) {
            val hours = days.getOrNull(d).orEmpty()
            for (hi in 0 until hourCount) {
                val hour = MasterScheduleData.HOUR_START + hi
                selected[d][hi] = hour in hours
            }
        }
        invalidate()
    }

    fun getWeek(): List<List<Int>> {
        return (0 until MasterScheduleData.DAY_COUNT).map { d ->
            (0 until hourCount)
                .filter { selected[d][it] }
                .map { MasterScheduleData.HOUR_START + it }
        }
    }

    private fun cloneSelected(): Array<BooleanArray> =
        Array(MasterScheduleData.DAY_COUNT) { d -> selected[d].clone() }

    private fun restoreBaseline() {
        for (d in 0 until MasterScheduleData.DAY_COUNT) {
            selected[d] = baseline[d].clone()
        }
    }

    private fun applyRect(d0: Int, h0: Int, d1: Int, h1: Int) {
        restoreBaseline()
        val dMin = min(d0, d1)
        val dMax = max(d0, d1)
        val hMin = min(h0, h1)
        val hMax = max(h0, h1)
        for (d in dMin..dMax) {
            for (h in hMin..hMax) {
                if (d in 0 until MasterScheduleData.DAY_COUNT && h in 0 until hourCount) {
                    selected[d][h] = dragSelect
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dm = resources.displayMetrics.density
        pad = 4f * dm
        labelColW = 44f * dm
        headerH = 40f * dm
        rowH = 40f * dm
        val w = MeasureSpec.getSize(widthMeasureSpec).toFloat().coerceAtLeast(200f * dm)
        val daysTotal = w - pad * 2 - labelColW
        val base = daysTotal / MasterScheduleData.DAY_COUNT
        val extra = (daysTotal - base * MasterScheduleData.DAY_COUNT).toInt()
        for (i in 0 until MasterScheduleData.DAY_COUNT) {
            dayCellW[i] = base + if (i < extra) 1f else 0f
        }
        val h = pad * 2 + headerH + hourCount * rowH
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gridLeft = pad
        gridTop = pad
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dm = resources.displayMetrics.density
        paint.color = ContextCompat.getColor(context, R.color.peach_dark)
        paint.alpha = 220
        rect.set(gridLeft, gridTop, gridLeft + labelColW, gridTop + headerH)
        canvas.drawRect(rect, paint)
        paint.alpha = 255
        paint.color = colorHeaderText
        paint.textSize = 10f * dm
        paint.textAlign = Paint.Align.CENTER
        val hcx = gridLeft + labelColW / 2f
        val hcy = gridTop + headerH / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(context.getString(R.string.schedule_column_hours), hcx, hcy, paint)

        var x = gridLeft + labelColW
        paint.color = ContextCompat.getColor(context, R.color.peach_dark)
        paint.alpha = 220
        for (d in 0 until MasterScheduleData.DAY_COUNT) {
            val cw = dayCellW[d]
            rect.set(x, gridTop, x + cw, gridTop + headerH)
            canvas.drawRect(rect, paint)
            paint.color = colorHeaderText
            paint.alpha = 255
            paint.textSize = 10f * dm
            val cx = x + cw / 2f
            val cy = gridTop + headerH / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(dayLabels[d], cx, cy, paint)
            paint.color = ContextCompat.getColor(context, R.color.peach_dark)
            paint.alpha = 220
            x += cw
        }
        paint.alpha = 255

        for (hi in 0 until hourCount) {
            val y = gridTop + headerH + hi * rowH
            paint.color = ContextCompat.getColor(context, R.color.schedule_day_label_bg)
            rect.set(gridLeft, y, gridLeft + labelColW, y + rowH)
            canvas.drawRect(rect, paint)
            paint.color = colorLabelText
            paint.textSize = 11f * dm
            paint.textAlign = Paint.Align.CENTER
            val hour = MasterScheduleData.HOUR_START + hi
            val lcy = y + rowH / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(hour.toString(), gridLeft + labelColW / 2f, lcy, paint)

            x = gridLeft + labelColW
            for (d in 0 until MasterScheduleData.DAY_COUNT) {
                val cw = dayCellW[d]
                val fill = if (selected[d][hi]) {
                    if (d % 2 == 0) colorSelected else colorSelectedAlt
                } else {
                    colorUnselected
                }
                paint.style = Paint.Style.FILL
                paint.color = fill
                rect.set(x, y, x + cw, y + rowH)
                canvas.drawRect(rect, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * dm
                paint.color = colorStroke
                canvas.drawRect(rect, paint)
                x += cw
            }
        }
        paint.style = Paint.Style.FILL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val cell = xyToCellClamped(event.x, event.y) ?: return true
                baseline = cloneSelected()
                anchorDay = cell.first
                anchorHourIdx = cell.second
                dragSelect = !selected[anchorDay][anchorHourIdx]
                dragging = true
                applyRect(anchorDay, anchorHourIdx, anchorDay, anchorHourIdx)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                val cell = xyToCellClamped(event.x, event.y) ?: return true
                applyRect(anchorDay, anchorHourIdx, cell.first, cell.second)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun xyToCellClamped(x: Float, y: Float): Pair<Int, Int>? {
        if (x < gridLeft + labelColW || y < gridTop + headerH) return null
        val relY = y - (gridTop + headerH)
        val hi = (relY / rowH).toInt().coerceIn(0, hourCount - 1)
        var relX = x - (gridLeft + labelColW)
        if (relX < 0) return null
        val totalW = dayCellW.sum()
        relX = relX.coerceAtMost(totalW - 1e-3f)
        var acc = 0f
        for (d in 0 until MasterScheduleData.DAY_COUNT) {
            val w = dayCellW[d]
            if (relX < acc + w) return d to hi
            acc += w
        }
        return (MasterScheduleData.DAY_COUNT - 1) to hi
    }
}
