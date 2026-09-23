package io.github.zhora87.bezmen.ocr.paddle

/**
 * Turns the DB detector's probability map into axis-aligned text boxes: threshold, connected
 * components, mean-score filter, then "unclip" (expand) by area / perimeter * ratio, because the
 * network predicts shrunk text regions. Price tags are read upright, so axis-aligned boxes are
 * enough and no contour geometry is needed.
 */
class DbPostProcessor(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val boxThreshold: Float = DEFAULT_BOX_THRESHOLD,
    private val unclipRatio: Float = DEFAULT_UNCLIP_RATIO,
    private val minSide: Int = DEFAULT_MIN_SIDE,
) {
    /** Box in probability-map pixel coordinates, right/bottom exclusive. */
    class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int, val score: Float) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    fun boxes(probabilities: FloatArray, width: Int, height: Int): List<TextBox> {
        val labels = IntArray(width * height)
        val out = mutableListOf<TextBox>()
        var nextLabel = 1
        for (start in probabilities.indices) {
            if (labels[start] != 0 || probabilities[start] <= threshold) continue
            val component = flood(probabilities, labels, width, height, start, nextLabel++)
            component.toBox(width, height)?.let { out += it }
        }
        return out.sortedWith(compareBy({ it.top }, { it.left }))
    }

    private class Component {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        var count = 0
        var sum = 0f
    }

    private fun flood(prob: FloatArray, labels: IntArray, width: Int, height: Int, start: Int, label: Int): Component {
        val component = Component()
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        labels[start] = label
        while (stack.isNotEmpty()) {
            val index = stack.removeLast()
            val x = index % width
            val y = index / width
            component.add(x, y, prob[index])
            if (x > 0) visit(prob, labels, index - 1, label, stack)
            if (x < width - 1) visit(prob, labels, index + 1, label, stack)
            if (y > 0) visit(prob, labels, index - width, label, stack)
            if (y < height - 1) visit(prob, labels, index + width, label, stack)
        }
        return component
    }

    private fun visit(prob: FloatArray, labels: IntArray, index: Int, label: Int, stack: ArrayDeque<Int>) {
        if (labels[index] == 0 && prob[index] > threshold) {
            labels[index] = label
            stack.addLast(index)
        }
    }

    private fun Component.add(x: Int, y: Int, p: Float) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        count++
        sum += p
    }

    private fun Component.toBox(width: Int, height: Int): TextBox? {
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        if (minOf(w, h) < minSide) return null
        val score = sum / count
        if (score < boxThreshold) return null
        val area = w.toFloat() * h
        val perimeter = 2f * (w + h)
        val distance = area * unclipRatio / perimeter
        val left = (minX - distance).toInt().coerceAtLeast(0)
        val top = (minY - distance).toInt().coerceAtLeast(0)
        val right = (maxX + 1 + distance).toInt().coerceAtMost(width)
        val bottom = (maxY + 1 + distance).toInt().coerceAtMost(height)
        return TextBox(left, top, right, bottom, score)
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.3f
        const val DEFAULT_BOX_THRESHOLD = 0.5f
        const val DEFAULT_UNCLIP_RATIO = 1.6f
        const val DEFAULT_MIN_SIDE = 3
    }
}
