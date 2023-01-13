/*
 * JTextPaneKeyTypedOverride.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.anvil.text

import avail.anvil.shortcuts.Key
import avail.anvil.shortcuts.KeyCode
import avail.anvil.shortcuts.ModifierKey
import java.awt.event.KeyEvent
import javax.swing.JTextPane

/**
 * An abstract [KeyTypedOverride] that is targeted for use in a [JTextPane].
 *
 * @author Richard Arriaga
 *
 * @property key
 *   The [Key] that, when matched to a [KeyEvent], should have the a [action]
 *   run.
 * @property action
 *   The action associated with this [JTextPaneKeyTypedOverride].
 */
internal abstract class JTextPaneKeyTypedOverride constructor (
	key: Key,
	val action: (KeyEvent, JTextPane) -> Unit
): KeyTypedOverride(key)

/**
 * A [KeyTypedAdapter] that is targeted for use in a [JTextPane].
 *
 * ***Note:*** *Multiple [KeyTypedOverride]s with the same [Key] are permitted*
 * *to be added to [keyTypedOverrides]. This allows for multiple actions to be*
 * *performed on the same [Key] press.*
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [KeyTypedAdapter].
 *
 * @param keyTypedOverrides
 *   The [JTextPaneKeyTypedOverride]s to add to this [JTextPaneKeyTypedAdapter].
 */
internal open class JTextPaneKeyTypedAdapter constructor(
	vararg keyTypedOverrides: JTextPaneKeyTypedOverride
): KeyTypedAdapter<JTextPaneKeyTypedOverride>(*keyTypedOverrides)
{
	override fun keyTyped(e: KeyEvent)
	{
		keyTypedOverrides.forEach {
			Key.from(e)?.let { key ->
				if (key == it.key)
				{
					it.action(e, e.component as JTextPane)
					if (e.isConsumed) return
				}
			}
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                        Surround Text With Wrapper.                         //
////////////////////////////////////////////////////////////////////////////////

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in double quotes
 * ("⁁").
 *
 * @author Richard Arriaga
 */
internal object WrapInDoubleQuotes : JTextPaneKeyTypedOverride(
	KeyCode.VK_QUOTE.with(ModifierKey.SHIFT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "\"$this\"" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in single quotes
 * ('⁁').
 *
 * @author Richard Arriaga
 */
internal object WrapInSingleQuotes : JTextPaneKeyTypedOverride(
	KeyCode.VK_QUOTE.with(),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "'$this'" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in double smart
 * quotes (“⁁”).
 *
 * @author Richard Arriaga
 */
internal object WrapInDoubleSmartQuotes : JTextPaneKeyTypedOverride(
	KeyCode.VK_OPEN_BRACKET.with(ModifierKey.ALT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "“$this“" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in single smart
 * quotes (‘⁁’).
 *
 * @author Richard Arriaga
 */
internal object WrapInSingleSmartQuotes : JTextPaneKeyTypedOverride(
	KeyCode.VK_CLOSE_BRACKET.with(ModifierKey.ALT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "‘$this’" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in guillemets
 * («⁁»).
 *
 * @author Richard Arriaga
 */
internal object WrapInGuillemets : JTextPaneKeyTypedOverride(
	KeyCode.VK_BACK_SLASH.with(ModifierKey.ALT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "«$this»" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in backticks (`⁁`).
 *
 * @author Richard Arriaga
 */
internal object WrapInBackticks : JTextPaneKeyTypedOverride(
	KeyCode.VK_BACK_QUOTE.with(),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "`$this`" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in parenthesis
 * ((⁁)).
 *
 * @author Richard Arriaga
 */
internal object WrapInParenthesis : JTextPaneKeyTypedOverride(
	KeyCode.VK_9.with(ModifierKey.SHIFT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "($this)" }
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in angle brackets
 * (<⁁>).
 *
 * @author Richard Arriaga
 */
internal object WrapInAngleBrackets : JTextPaneKeyTypedOverride(
	KeyCode.VK_COMMA.with(ModifierKey.SHIFT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transform { "<$this>" }
			e.consume()
		}
	})

/**
 * Wrap the target text in a block structure, surrounding it with the provide
 * prefix and suffix text and tabbing the target content in one level.
 *
 * @param text
 *   The text to wrap in a block.
 * @param prefix
 *   The block's opening string to be placed on its own line above the original
 *   text at the original text's tab level.
 * @param suffix
 *   The block's closing string to be placed on its own line below the original
 *   text at the original text's tab level.
 * @return
 *   The text wrapped in a block.
 */
private fun wrapInBlock(text: String, prefix: String, suffix: String): String =
	buildString {
		val tabPosition = minTabCount(text)
		val prefixTabs = "\t".repeat(tabPosition)
		append(prefixTabs)
		append(prefix)
		append("\n\t")
		append(text.replace("\n", "\n\t"))
		append("\n")
		append(prefixTabs)
		append(suffix)
	}

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in guillemets
 * («⁁»).
 *
 * @author Richard Arriaga
 */
internal object WrapInBrackets : JTextPaneKeyTypedOverride(
	KeyCode.VK_OPEN_BRACKET.with(),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transformLines {
				wrapInBlock(this, "[", "]")
			}
			e.consume()
		}
	})

/**
 * The [JTextPaneKeyTypedOverride] to wrap the text selection in guillemets
 * («⁁»).
 *
 * @author Richard Arriaga
 */
internal object WrapInBraces : JTextPaneKeyTypedOverride(
	KeyCode.VK_OPEN_BRACKET.with(ModifierKey.SHIFT),
	{ e, pane ->
		if (pane.selectedText.isNotEmpty())
		{
			pane.transformLines {
				wrapInBlock(this, "{", "}")
			}
			e.consume()
		}
	})
