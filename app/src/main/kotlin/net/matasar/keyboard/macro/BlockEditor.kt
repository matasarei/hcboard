package net.matasar.keyboard.macro

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import net.matasar.keyboard.R
import net.matasar.keyboard.input.label
import net.matasar.keyboard.layout.ModifierKey
import kotlin.math.roundToInt

/**
 * The kinds of block the palette offers, each with its colour, like the blocks of a children's
 * programming app: text blue, keys orange, random green, repeat purple, wait and paste teal.
 */
enum class BlockKind(val title: Int, val color: Color, val make: () -> Block) {
    TEXT(R.string.block_text, Color(0xFF3B82F6), { Block.TypeText("") }),
    KEY(R.string.block_key, Color(0xFFF59E0B), { Block.PressKey("Enter") }),
    RANDOM(R.string.block_random, Color(0xFF22A06B), { Block.RandomKeys() }),
    REPEAT(R.string.block_repeat, Color(0xFF8B5CF6), { Block.Repeat(2) }),
    WAIT(R.string.block_wait, Color(0xFF14B8A6), { Block.Wait(500) }),
    PASTE(R.string.block_paste, Color(0xFF0EA5A4), { Block.PasteClipboard }),
}

val Block.kind: BlockKind
    get() = when (this) {
        is Block.TypeText -> BlockKind.TEXT
        is Block.PressKey -> BlockKind.KEY
        is Block.RandomKeys -> BlockKind.RANDOM
        is Block.Repeat -> BlockKind.REPEAT
        is Block.Wait -> BlockKind.WAIT
        Block.PasteClipboard -> BlockKind.PASTE
    }

/** The palette pinned under the script: one chip per kind, each appends its block. */
@Composable
fun BlockPalette(onAdd: (Block) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (kind in BlockKind.entries) {
            AssistChip(
                onClick = { onAdd(kind.make()) },
                label = { Text("+ " + stringResource(kind.title), fontWeight = FontWeight.Medium) },
                colors = AssistChipDefaults.assistChipColors(containerColor = kind.color, labelColor = Color.White),
                border = null,
            )
        }
    }
}

/**
 * The blocks of the list at [container] (`[]` the macro's own, `[i]` inside the repeat at `i`),
 * each edited in place. Every change goes back as the macro's whole new block list.
 */
@Composable
fun BlockStack(root: List<Block>, container: List<Int>, onChange: (List<Block>) -> Unit) {
    val blocks = if (container.isEmpty()) root else (MacroEdits.blockAt(root, container) as? Block.Repeat)?.blocks ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { i, block ->
            val path = container + i
            BlockCard(
                block = block,
                first = i == 0,
                last = i == blocks.lastIndex,
                onMove = { delta -> onChange(MacroEdits.move(root, path, delta)) },
                onRemove = { onChange(MacroEdits.remove(root, path)) },
                onReplace = { onChange(MacroEdits.replace(root, path, it)) },
            ) {
                if (block is Block.Repeat) {
                    BlockStack(root, path, onChange)
                    AddInside { onChange(MacroEdits.insert(root, path, it)) }
                }
            }
        }
    }
}

@Composable
private fun BlockCard(
    block: Block,
    first: Boolean,
    last: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onReplace: (Block) -> Unit,
    inner: @Composable () -> Unit,
) {
    val kind = block.kind
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(kind.color.copy(alpha = 0.14f)),
    ) {
        // The rail down the side is the block's colour, and a repeat's inner blocks sit inside it.
        Box(modifier = Modifier.width(8.dp).fillMaxHeight().background(kind.color))
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(kind.title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(kind.color).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = !first) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.macros_move_up), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onMove(1) }, enabled = !last) {
                    Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.macros_move_down), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRemove) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.macros_remove), modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockFields(block, onReplace)
                inner()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockFields(block: Block, onReplace: (Block) -> Unit) {
    when (block) {
        is Block.TypeText -> {
            // Shown only while the eye is held open on this card; never saved.
            var revealed by remember { mutableStateOf(false) }
            val masked = block.secret && !revealed
            OutlinedTextField(
                value = block.text,
                onValueChange = { onReplace(block.copy(text = it)) },
                label = { Text(stringResource(R.string.block_text_field)) },
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                // A password field to the keyboard too, so nothing suggests from or learns the secret.
                keyboardOptions = if (block.secret) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
                trailingIcon = if (block.secret) {
                    {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                painterResource(if (masked) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                stringResource(if (masked) R.string.block_text_show else R.string.block_text_hide),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    null
                },
                supportingText = if (block.keptSealed != null && block.text.isEmpty()) {
                    { Text(stringResource(R.string.block_text_unreadable)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).toggleable(
                    value = block.secret,
                    role = Role.Checkbox,
                    onValueChange = { onReplace(block.copy(secret = it)); revealed = false },
                ),
            ) {
                Checkbox(checked = block.secret, onCheckedChange = null)
                Text(stringResource(R.string.block_text_secret), modifier = Modifier.padding(end = 8.dp))
            }
        }
        is Block.PressKey -> {
            var picking by remember { mutableStateOf(false) }
            FilledTonalButton(onClick = { picking = true }) { Text(block.keyCombination()) }
            Text(stringResource(R.string.block_key_holding), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (modifier in MACRO_MODIFIERS) {
                    val on = modifier in block.modifiers
                    FilterChip(
                        selected = on,
                        onClick = { onReplace(block.copy(modifiers = if (on) block.modifiers - modifier else block.modifiers + modifier)) },
                        label = { Text(modifier.label) },
                    )
                }
            }
            if (picking) {
                KeyPicker(
                    current = block.key,
                    onPick = { onReplace(block.copy(key = it)); picking = false },
                    onDismiss = { picking = false },
                )
            }
        }
        is Block.RandomKeys -> {
            Text(stringResource(R.string.block_random_length, block.size))
            Slider(
                value = block.size.toFloat(),
                onValueChange = { onReplace(block.copy(length = it.roundToInt())) },
                valueRange = Block.RandomKeys.MIN_LENGTH.toFloat()..Block.RandomKeys.MAX_LENGTH.toFloat(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chosen = listOf(block.letters, block.digits, block.symbols).count { it }
                // The last chosen set stays on: random keys from nothing is not a password.
                SetChip(R.string.block_random_letters, block.letters, chosen) { onReplace(block.copy(letters = it)) }
                SetChip(R.string.block_random_digits, block.digits, chosen) { onReplace(block.copy(digits = it)) }
                SetChip(R.string.block_random_symbols, block.symbols, chosen) { onReplace(block.copy(symbols = it)) }
            }
        }
        is Block.Repeat -> Stepper(
            text = pluralStringResource(R.plurals.block_repeat_times, block.count, block.count),
            onMinus = { onReplace(block.copy(times = (block.count - 1).coerceAtLeast(1))) },
            onPlus = { onReplace(block.copy(times = (block.count + 1).coerceAtMost(Block.Repeat.MAX_TIMES))) },
        )
        is Block.Wait -> Stepper(
            text = stringResource(R.string.block_wait_millis, block.duration.toInt()),
            onMinus = { onReplace(block.copy(millis = (block.duration - WAIT_STEP).coerceAtLeast(0L))) },
            onPlus = { onReplace(block.copy(millis = (block.duration + WAIT_STEP).coerceAtMost(Block.Wait.MAX_MILLIS))) },
        )
        Block.PasteClipboard -> Text(stringResource(R.string.block_paste_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetChip(label: Int, on: Boolean, chosen: Int, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = on,
        onClick = { if (!on || chosen > 1) onChange(!on) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onMinus) { Text("−") }
        Text(text, style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(onClick = onPlus) { Text("+") }
    }
}

/** A repeat's own "+": the same kinds as the palette, added at the end of the repeat. */
@Composable
private fun AddInside(onAdd: (Block) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("+ " + stringResource(R.string.macros_add_inside)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (kind in BlockKind.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(kind.title)) },
                    leadingIcon = { Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(kind.color)) },
                    onClick = { onAdd(kind.make()); open = false },
                )
            }
        }
    }
}

/** Every named key by group, and a field for a single character; a name that resolves to no key is refused. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyPicker(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var character by remember { mutableStateOf(if (MacroKeys.named.none { it.name == current }) current else "") }
    val characterValid = character.length == 1 && MacroKeys.strokeFor(character) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_key_choose)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (group in KeyGroup.entries) {
                    Text(stringResource(group.title), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (key in MacroKeys.named.filter { it.group == group }) {
                            FilterChip(selected = key.name == current, onClick = { onPick(key.name) }, label = { Text(key.name) })
                        }
                    }
                }
                OutlinedTextField(
                    value = character,
                    onValueChange = { character = it.take(1) },
                    label = { Text(stringResource(R.string.block_key_character)) },
                    singleLine = true,
                    isError = character.isNotEmpty() && !characterValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(character) }, enabled = characterValid) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.macros_back)) }
        },
    )
}

private val KeyGroup.title: Int
    get() = when (this) {
        KeyGroup.EDITING -> R.string.key_group_editing
        KeyGroup.MOVEMENT -> R.string.key_group_movement
        KeyGroup.FUNCTION -> R.string.key_group_function
        KeyGroup.MODIFIER -> R.string.key_group_modifier
    }

/** Fn has no meaning in a macro: the named keys already are what Fn would make of them. */
private val MACRO_MODIFIERS = listOf(ModifierKey.CTRL, ModifierKey.ALT, ModifierKey.SHIFT, ModifierKey.META)

private const val WAIT_STEP = 100L
