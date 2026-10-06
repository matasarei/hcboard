package net.matasar.keyboard.layout

/** The 60% board cut into a left and a right half, for a hinge, a sideways phone or two thumbs. */
data class SplitLayer(val left: Layer, val right: Layer)

/**
 * How many keys of each of the four upper rows the left hand takes: the row's first key and five
 * more, and one more on the digits. With Esc 1.5 keys wide the 6 sits right over the T|Y line, so
 * either half would do; on the left, as on Microsoft's, Kinesis's and UHK's split boards, the
 * right half is no longer widened by its digits alone, so the board is narrower and the keys
 * can grow (unless a hinge, not the width, sets their size). On the Shift row the six are
 * `Shift \| z x c v`: the ISO key puts the B slot under the right hand, 14 letters to 12.
 */
private val LEFT_KEYS = listOf(7, 6, 6, 6)

/** The split letters page for [language]. */
fun splitLayer(language: Language, withGlobe: Boolean): SplitLayer = splitLayer(sixtyPercentLayer(language, withGlobe))

/**
 * A page of the 60% board cut in two: after 6, T, G and V (or what the page has on those slots),
 * every key at its full-board width. The outer edges are
 * flush with the screen and the inner edges keep the rows' stagger, so the letters sit as far from
 * the edge as on the whole board. The bottom row is built for two thumbs, a Space on each half;
 * each Space takes what brings its row to the widest row of its half.
 */
fun splitLayer(whole: Layer): SplitLayer {
    val upper = whole.rows.take(4)
    val leftUpper = upper.mapIndexed { index, row -> row.keys.take(LEFT_KEYS[index]) }
    val rightUpper = upper.mapIndexed { index, row -> row.keys.drop(LEFT_KEYS[index]) }
    val leftUnits = leftUpper.maxOf { it.units() }
    val rightUnits = rightUpper.maxOf { it.units() }

    // The bottom row is the whole board's cut at its space bar: a space for each thumb, each as
    // wide as brings its row to the widest row of its half. The left space has no name: the
    // language is on the right one, and two keys with the same label would share an id.
    val bottom = whole.rows[4].keys
    val space = bottom.indexOfFirst { it.action == KeyAction.Space }
    val leftMods = bottom.take(space)
    val rightMods = bottom.drop(space + 1)
    val leftBottom = leftMods + Key("", KeyAction.Space, leftUnits - leftMods.units(), KeyStyle.SPACE)
    val rightBottom = listOf(bottom[space].copy(width = rightUnits - rightMods.units())) + rightMods

    // Rows shorter than their half are padded on the inner side, so each half is flush with its
    // outer edge: the left half's rows end early, the right half's start late.
    val left = (leftUpper + listOf(leftBottom)).map { Row(it, trailingUnits = leftUnits - it.units()) }
    val right = (rightUpper + listOf(rightBottom)).map { Row(it, leadingUnits = rightUnits - it.units()) }
    return SplitLayer(
        left = Layer(whole.id, left, units = leftUnits),
        right = Layer(whole.id, right, units = rightUnits),
    )
}
