/**
 * ReplaceTextTemplate.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package com.avail.environment.editor;

import com.avail.environment.editor.fx.FilterDropDownDialog;
import com.avail.environment.editor.fx.FilterTrieComboBox;
import com.avail.environment.editor.utility.PrefixTrie;
import com.avail.environment.editor.utility.PrefixTrie.NodeContent;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@code ReplaceTextTemplate} is a holder of replacement text for a given
 * key word.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ReplaceTextTemplate
{
	/**
	 * The {@link PrefixTrie} that contains the templates.
	 */
	private final @NotNull PrefixTrie<String> prefixTrie = new PrefixTrie<>();

	/**
	 * Populate the {@link ReplaceTextTemplate#prefixTrie} from the input from
	 * the preferences.
	 *
	 * @param input
	 *        The stored preference.
	 */
	public void setPrefixTrie (final @NotNull String input)
	{
		if (!input.isEmpty())
		{
			String pairSpliter = "" + '\0';
			String recordSpliter = "" + 0x1e;
			final String[] substrings = input.split(recordSpliter);
			for (String template : substrings)
			{
				String[] pair = template.split(pairSpliter);
				assert pair.length == 2;
				prefixTrie.addBranch(pair[0], pair[1]);
			}
		}
		else
		{
			final Map<String, String> templateMap = new HashMap<>();
			templateMap.put("top", "⊤");
			templateMap.put("bottom", "⊥");
			templateMap.put("o1", "①");
			templateMap.put("o2", "②");
			templateMap.put("o3", "③");
			templateMap.put("o4", "④");
			templateMap.put("o5", "⑤");
			templateMap.put("o6", "⑥");
			templateMap.put("o7", "⑦");
			templateMap.put("o8", "⑧");
			templateMap.put("o9", "⑨");
			templateMap.put("union", "∪");
			templateMap.put("uparrow", "↑");
			templateMap.put("yields", "⇒");
			templateMap.put("times", "×");
			templateMap.put("divide", "÷");
			templateMap.put("ceiling", "⎡$EXPR$⎤");
			templateMap.put("floor", "⎣$EXPR$⎦");
			templateMap.put("conjunction", "∧");
			templateMap.put("debugprint",
				"Print: format \"$EXPR$=“①”\n\" with $EXPR$;");
			templateMap.put("disjunction", "∨");
			templateMap.put("doublequestion", "⁇");
			templateMap.put("downarrow", "↓");
			templateMap.put("earlydebugprint",
				"Print: \"$EXPR$=\";\nPrint: “$EXPR$”;\nPrint:\"\n\";");
			templateMap.put("elementof", "∈");
			templateMap.put("emptyset", "∅");
			templateMap.put("enumerationtype", "{$VALUES$}ᵀ");
			templateMap.put("intersection", "∩");
			templateMap.put("leftarrow", "←");
			templateMap.put("rightarrow", "→");
			templateMap.put("subset", "⊆");
			templateMap.put("superset", "⊇");
			templateMap.put("leftguillemet","«");
			templateMap.put("rightguillemet","»");
			templateMap.put("doubledagger","‡");
			templateMap.put("lessthanequal", "≤");
			templateMap.put("greaterthanequal", "≥");
			templateMap.put("leftdoublesmartquote", "“");
			templateMap.put("rightdoublesmartquote", "”");
			templateMap.put("leftsinglesmartquote", "‘");
			templateMap.put("rightsinglesmartquote", "’");
			templateMap.put("sum", "∑");
			templateMap.put("product", "∏");
			templateMap.put("negation", "¬");
			templateMap.put("notequals", "≠");
			templateMap.put("inf", "∞");
			templateMap.put("...", "…");
			templateMap.put("section", "§");

			templateMap.forEach((k, v) -> prefixTrie.addBranch(k, v));
		}
	}

	/**
	 * Answer a string representation of this {@link ReplaceTextTemplate} store
	 * that is suitable for being stored and restored via the {@link
	 * ReplaceTextTemplate} constructor.
	 *
	 * @return A string.
	 */
	public String stringToStore ()
	{
		final StringBuilder sb = new StringBuilder("");
		final List<NodeContent<String>> wordTemplates =
			prefixTrie.wordContent();

		int size = wordTemplates.size();
		for (int i = 0; i < size - 1; i++)
		{
			final NodeContent<String> nodeContent = wordTemplates.get(i);
			sb.append(nodeContent.word)
				.append('\0')
				.append(nodeContent.content)
				.append(0x1e);
		}

		final NodeContent<String> finalNodeContent =
			wordTemplates.get(size - 1);
		return sb.append(finalNodeContent.word)
			.append('\0')
			.append(finalNodeContent.content).toString();
	}

	/**
	 * Answer a {@link ChoiceDialog} with the templates.
	 *
	 * @return A {@link ChoiceDialog}.
	 */
	public @NotNull
	FilterDropDownDialog<FilterTrieComboBox<String>, String> dialog ()
	{
		FilterDropDownDialog<FilterTrieComboBox<String>, String> dialog =
			new FilterDropDownDialog<>(
			"",
			prefixTrie.root().wordList(),
			new FilterTrieComboBox<>(
				(typedText, itemToCompare) ->
					itemToCompare.toLowerCase().contains(typedText.toLowerCase())
						|| itemToCompare.equals(typedText),
				prefixTrie));
		dialog.setTitle("Choose Template");
		dialog.setContentText(null);
		dialog.setHeaderText(null);
		return dialog;
	}
}
