package net.matasar.keyboard.macro

/** A reversible stand-in for the Keystore: "sealed:" and the text backwards; anything else will not open. */
class FakeSecretBox : SecretBox {
    override fun seal(plain: String): String = PREFIX + plain.reversed()
    override fun open(sealed: String): String? = if (sealed.startsWith(PREFIX)) sealed.removePrefix(PREFIX).reversed() else null

    private companion object {
        const val PREFIX = "sealed:"
    }
}
