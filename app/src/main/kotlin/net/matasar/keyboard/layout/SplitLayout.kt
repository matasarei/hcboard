package net.matasar.keyboard.layout

/** The 60% board cut into a left and a right half, for a hinge, a sideways phone or two thumbs. */
data class SplitLayer(val left: Layer, val right: Layer)

/**
 * How many keys of each of the four upper rows the left hand takes: the row's first key and five
 * more, and one more on the digits. With Esc 1.5 keys wide the 6 sits right over the T|Y line, so
 * either half would do; on the left, as on Microsoft's, Kinesis's and UHK's split boards, the
 * right half is no longer widened by its digits alone, and every key grows.
 */
private val LEFT_KEYS = listOf(7, 6, 6, 6)

/**
 * The split board for [language]: the balanced 60% board cut after 6, T, G and B (or the
 * language's letters on those slots), every key at its full-board width. The outer edges are
 * flush with the screen and the inner edges keep the rows' stagger, so the letters sit as far from
 * the edge as on the whole board. The bottom row is built for two thumbs, a Space on each half;
 * each Space takes what brings its row to the widest row of its half.
 */
fun splitLayer(language: Language, withGlobe: Boolean): SplitLayer {
    val whole = sixtyPercentLayer(language, withGlobe)
    val upper = whole.rows.take(4)
    val leftUpper = upper.mapIndexed { index, row -> row.keys.take(LEFT_KEYS[index]) }
    val rightUpper = upper.mapIndexed { index, row -> row.keys.drop(LEFT_KEYS[index]) }
    val leftUnits = leftUpper.maxOf { it.units() }
    val rightUnits = rightUpper.maxOf { it.units() }

    val leftMods = listOf(
        Key("Ctrl", KeyAction.Modifier(ModifierKey.CTRL), 1.25f, KeyStyle.MODIFIER),
        Key("Meta", KeyAction.Modifier(ModifierKey.META), 1.25f, KeyStyle.MODIFIER),
        Key("Alt", KeyAction.Modifier(ModifierKey.ALT), 1.25f, KeyStyle.MODIFIER),
    )
    val rightMods = buildList {
        add(Key("Alt", KeyAction.Modifier(ModifierKey.ALT), 1.25f, KeyStyle.MODIFIER))
        if (withGlobe) add(Key("globe", KeyAction.SwitchLanguage, 1.25f, KeyStyle.FUNCTION, KeyIcon.GLOBE))
        add(Key("Fn", KeyAction.Modifier(ModifierKey.FN), 1.25f, KeyStyle.MODIFIER))
        add(Key("Ctrl", KeyAction.Modifier(ModifierKey.CTRL), 1.25f, KeyStyle.MODIFIER))
    }
    // The left Space has no name: the language is on the right one, and two keys with the same
    // label would share an id.
    val leftBottom = leftMods + Key("", KeyAction.Space, leftUnits - leftMods.units(), KeyStyle.SPACE)
    val rightBottom = listOf(Key(language.nativeName, KeyAction.Space, rightUnits - rightMods.units(), KeyStyle.SPACE)) + rightMods

    // Rows shorter than their half are padded on the inner side, so each half is flush with its
    // outer edge: the left half's rows end early, the right half's start late.
    val left = (leftUpper + listOf(leftBottom)).map { Row(it, trailingUnits = leftUnits - it.units()) }
    val right = (rightUpper + listOf(rightBottom)).map { Row(it, leadingUnits = rightUnits - it.units()) }
    return SplitLayer(
        left = Layer(LayerId.LETTERS, left, units = leftUnits),
        right = Layer(LayerId.LETTERS, right, units = rightUnits),
    )
}

private fun List<Key>.units(): Float = sumOf { it.width.toDouble() }.toFloat()
