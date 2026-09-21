package net.matasar.keyboard.macro

/** Seals a secret text for the disk and opens it again; [open] gives null for what it cannot open. */
interface SecretBox {
    fun seal(plain: String): String
    fun open(sealed: String): String?
}

/** [blocks] as they are stored: every secret text sealed, repeats included; other blocks untouched. */
fun List<Block>.sealSecrets(box: SecretBox): List<Block> = map { block ->
    when {
        block is Block.TypeText && block.secret -> {
            val sealed = if (block.text.isEmpty() && block.keptSealed != null) block.keptSealed else box.seal(block.text)
            Block.TypeText(sealed, secret = true)
        }
        block is Block.Repeat -> block.copy(blocks = block.blocks.sealSecrets(box))
        else -> block
    }
}

/** Stored [blocks] with every secret opened; one this phone cannot open is empty and keeps its sealed text. */
fun List<Block>.openSecrets(box: SecretBox): List<Block> = map { block ->
    when {
        block is Block.TypeText && block.secret -> box.open(block.text)?.let { Block.TypeText(it, secret = true) }
            ?: Block.TypeText("", secret = true, keptSealed = block.text)
        block is Block.Repeat -> block.copy(blocks = block.blocks.openSecrets(box))
        else -> block
    }
}

fun Macro.sealSecrets(box: SecretBox): Macro = copy(blocks = blocks.sealSecrets(box))

fun Macro.openSecrets(box: SecretBox): Macro = copy(blocks = blocks.openSecrets(box))

/** Whether [this] types something that must not be read back or shown: random keys or a secret text. */
fun Macro.typesSecrets(): Boolean = blocks.anySecrets()

private fun List<Block>.anySecrets(): Boolean = any {
    it is Block.RandomKeys || (it is Block.TypeText && it.secret) || (it is Block.Repeat && it.blocks.anySecrets())
}
