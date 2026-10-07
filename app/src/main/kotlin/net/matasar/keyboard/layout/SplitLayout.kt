package net.matasar.keyboard.layout

/** The 60% board cut into a left and a right half, for a hinge, a sideways phone or two thumbs. */
data class SplitLayer(val left: Layer, val right: Layer)

/**
 * How many keys of each of the four upper rows the left hand takes: the row's first key and five
 * more, and one more on the digits: the 6 goes left, as on Microsoft's, Kinesis's and UHK's split
 * boards, so the right half is not widened by its digits alone, the board is narrower and the
 * keys can grow (unless a hinge, not the width, sets their size). On the Shift row the six are
 * `Shift \| z x c v`: the ISO key puts the B slot under the right hand, 14 letters to 12.
 */
private val LEFT_KEYS = listOf(7, 6, 6, 6)

/** The split letters page for [language]. */
fun splitLayer(language: Language, withGlobe: Boolean): SplitLayer = splitLayer(sixtyPercentLayer(language, withGlobe))

/** Where the Shift row is among the four upper rows. */
private const val SHIFT_ROW = 3

/** The `` `~ `` key, slim on the whole board and a full key on a half. */
private val Backtick = KeyAction.Text("`", "~")

/**
 * A page of the 60% board cut in two: after 6, T, G and V (or what the page has on those slots),
 * every key at its full-board width but the outer one of each row, which takes what brings its
 * row to the widest row of its half, so both edges of a half are straight. Only function keys
 * stand there: Esc, Tab, Caps and Shift on the left, Backspace, €± (ABC), Enter and Shift on the
 * right. `` `~ `` is a full key on the right Shift row, from that Shift: on a nine-letter row,
 * which leaves no slack, the Shift narrows to one unit. The bottom row is built for two thumbs, a
 * Space on each half; each Space takes what brings its row to the widest row of its half.
 */
fun splitLayer(whole: Layer): SplitLayer {
    val upper = whole.rows.take(4)
    val leftUpper = upper.mapIndexed { index, row -> Row(row.keys.take(LEFT_KEYS[index])) }
    val rightCut = upper.mapIndexed { index, row -> Row(row.keys.drop(LEFT_KEYS[index])) }
    val leftUnits = leftUpper.maxOf { it.totalUnits }
    val rightUnits = rightCut.maxOf { it.totalUnits }
    // Measured first, so a wider `~ comes out of the Shift row's Shift rather than widening the half.
    val rightUpper = rightCut.mapIndexed { index, row ->
        if (index != SHIFT_ROW) row else row.copy(keys = row.keys.map { if (it.action == Backtick) it.copy(width = 1f) else it })
    }

    // The bottom row is the whole board's cut at its space bar: a space for each thumb, each as
    // wide as brings its row to the widest row of its half. The left space has no name: the
    // language is on the right one, and two keys with the same label would share an id.
    val bottom = whole.rows[4].keys
    val space = bottom.indexOfFirst { it.action == KeyAction.Space }
    val leftMods = bottom.take(space)
    val rightMods = bottom.drop(space + 1)
    val leftBottom = Row(leftMods + Key("", KeyAction.Space, leftUnits - leftMods.units(), KeyStyle.SPACE))
    val rightBottom = Row(listOf(bottom[space].copy(width = rightUnits - rightMods.units())) + rightMods)

    // A row shorter than its half widens its outer key, so the half is flush on both edges.
    val left = (leftUpper + leftBottom).map { it.widenEdgeKey(first = true, by = leftUnits - it.totalUnits) }
    val right = (rightUpper + rightBottom).map { it.widenEdgeKey(first = false, by = rightUnits - it.totalUnits) }
    return SplitLayer(
        left = Layer(whole.id, left, units = leftUnits),
        right = Layer(whole.id, right, units = rightUnits),
    )
}

/** This row with its first (or last) key [by] units wider; refuses to leave that key under one unit. */
private fun Row.widenEdgeKey(first: Boolean, by: Float): Row {
    // Exact: every width on the board is a multiple of a quarter unit, which a Float holds exactly.
    if (by == 0f) return this
    val index = if (first) 0 else keys.lastIndex
    val edge = keys[index]
    require(edge.width + by >= 1f) { "${edge.label} would be ${edge.width + by} units wide" }
    return copy(keys = keys.toMutableList().also { it[index] = edge.copy(width = edge.width + by) })
}
