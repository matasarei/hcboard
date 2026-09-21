package net.matasar.keyboard.macro

/**
 * Edits on a macro's block stack, addressed by path: `[2]` is the third block, `[2, 0]` the first
 * block inside it (a repeat). A container path names a list: `[]` the macro's own, `[2]` the
 * repeat at `[2]`. A path that leads nowhere leaves the stack as it was.
 */
object MacroEdits {

    fun blockAt(blocks: List<Block>, path: List<Int>): Block? {
        if (path.isEmpty()) return null
        val block = blocks.getOrNull(path.first()) ?: return null
        if (path.size == 1) return block
        return (block as? Block.Repeat)?.let { blockAt(it.blocks, path.drop(1)) }
    }

    /** Adds [block] to the list at [container], at the end or at [index]. */
    fun insert(blocks: List<Block>, container: List<Int>, block: Block, index: Int? = null): List<Block> =
        updateList(blocks, container) { list ->
            val at = (index ?: list.size).coerceIn(0, list.size)
            list.toMutableList().apply { add(at, block) }
        }

    fun remove(blocks: List<Block>, path: List<Int>): List<Block> {
        if (path.isEmpty()) return blocks
        return updateList(blocks, path.dropLast(1)) { list ->
            val i = path.last()
            if (i in list.indices) list.toMutableList().apply { removeAt(i) } else list
        }
    }

    fun replace(blocks: List<Block>, path: List<Int>, block: Block): List<Block> {
        if (path.isEmpty()) return blocks
        return updateList(blocks, path.dropLast(1)) { list ->
            val i = path.last()
            if (i in list.indices) list.toMutableList().apply { set(i, block) } else list
        }
    }

    /** Moves the block at [path] by [delta] places within its own list; past either end nothing moves. */
    fun move(blocks: List<Block>, path: List<Int>, delta: Int): List<Block> {
        if (path.isEmpty()) return blocks
        return updateList(blocks, path.dropLast(1)) { list ->
            val from = path.last()
            val to = from + delta
            if (from !in list.indices || to !in list.indices) list
            else list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    private fun updateList(blocks: List<Block>, container: List<Int>, change: (List<Block>) -> List<Block>): List<Block> {
        if (container.isEmpty()) return change(blocks)
        val i = container.first()
        val repeat = blocks.getOrNull(i) as? Block.Repeat ?: return blocks
        val inner = updateList(repeat.blocks, container.drop(1), change)
        if (inner === repeat.blocks) return blocks
        return blocks.toMutableList().apply { set(i, repeat.copy(blocks = inner)) }
    }
}
