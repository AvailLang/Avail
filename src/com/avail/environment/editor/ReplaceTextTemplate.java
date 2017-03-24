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
import com.avail.utility.Pair;
import javafx.scene.control.ChoiceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@code ReplaceTextTemplate} is a holder of replacement text for a given
 * key word.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ReplaceTextTemplate
{
	/**
	 * The map with the options.
	 */
	private final @NotNull Map<String, String> templateMap = new HashMap<>();

	/**
	 * The {@link PrefixTrie} that contains the templates.
	 */
	private final @NotNull PrefixTrie prefixTrie = new PrefixTrie();

	/**
	 * Answer the template string for the given key.
	 *
	 * @param template
	 *        The template key.
	 * @return A template.
	 */
	public String get(final @NotNull String template)
	{
		return templateMap.get(template);
	}

	/**
	 * The list of choices.
	 */
	public final @NotNull List<String> choiceList;

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
				prefixTrie.addTemplate(pair[0], pair[1]);
			}
		}
	}

	/**
	 * Construct a {@link ReplaceTextTemplate}.
	 */
	public ReplaceTextTemplate ()
	{
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

		templateMap.forEach(prefixTrie::addTemplate);
		choiceList = new ArrayList<>(templateMap.keySet());
		Collections.sort(choiceList);
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
		final List<Pair<String, String>> wordTemplates =
			prefixTrie.wordTemplates();

		int size = wordTemplates.size();
		for (int i = 0; i < size - 1; i++)
		{
			final Pair<String, String> pair = wordTemplates.get(i);
			sb.append(pair.first())
				.append('\0')
				.append(pair.second())
				.append(0x1e);
		}

		final Pair<String, String> finalPair = wordTemplates.get(size - 1);
		return sb.append(finalPair.first())
			.append('\0')
			.append(finalPair.second()).toString();
	}

	/**
	 * Answer a {@link ChoiceDialog} with the templates.
	 *
	 * @return A {@link ChoiceDialog}.
	 */
	public @NotNull FilterDropDownDialog<String> dialog ()
	{
		FilterDropDownDialog<String> dialog = new FilterDropDownDialog<>(
			"",
			choiceList,
			(typedText, itemToCompare) ->
				itemToCompare.toLowerCase().contains(typedText.toLowerCase())
					|| itemToCompare.equals(typedText));
		dialog.setTitle("Choose Template");
		dialog.setContentText(null);
		dialog.setHeaderText(null);
		return dialog;
	}

	/**
	 * A {@code PrefixNode} is a node in a {@link PrefixTrie}.
	 */
	public static class PrefixNode
	{
		/**
		 * The {@link Map} that holds the next nodes in the trie.
		 */
		private final @NotNull Map<Character, PrefixNode> children =
			new HashMap<>();

		/**
		 * The target template of this node.
		 */
		private @NotNull String template = "";

		/**
		 * Answer the {@link #template}.
		 *
		 * @return A String.
		 */
		public @NotNull String template ()
		{
			return template;
		}

		/**
		 * The list of viable complete word branches from this point in the
		 * {@link PrefixTrie}.
		 */
		private final @NotNull List<String> wordList = new ArrayList<>();

		/**
		 * Answer the {@link #wordList}.
		 *
		 * @return A {@link List}.
		 */
		public final @NotNull List<String> wordList ()
		{
			return wordList;
		}

		/**
		 * Add a branch to this {@link PrefixNode}.
		 *
		 * @param word
		 *        The word branch to add.
		 * @param nodeDepth
		 *        The height position presently in the {@link PrefixTrie} that
		 *        this node is at.
		 */
		public void addWord (
			final @NotNull String word,
			final int nodeDepth,
			final @NotNull String template)
		{
			wordList.add(word);
			Collections.sort(wordList);
			if (word.length() - 1 > nodeDepth)
			{
				final int newDepth = nodeDepth + 1;
				final Character next = word.charAt(newDepth);
				final PrefixNode nextNode = children.computeIfAbsent(
					next,
					key -> new PrefixNode());
				nextNode.addWord(word, newDepth, template);
			}
			else
			{
				this.template = template;
			}
		}

		/**
		 * Add a branch to this {@link PrefixNode}.
		 *
		 * @param word
		 *        The word to search.
		 * @param nodeDepth
		 *        The height position presently in the {@link PrefixTrie} that
		 *        this node is at.
		 */
		public @Nullable PrefixNode searchTrie (
			final @NotNull String word,
			final int nodeDepth)
		{
			if (word.length() - 1 > nodeDepth)
			{
				final int newDepth = nodeDepth + 1;
				final Character next = word.charAt(newDepth);
				final PrefixNode nextNode =
					children.getOrDefault(next, null);
				return nextNode != null
					? nextNode.searchTrie(word, newDepth)
					: null;
			}
			else
			{
				return this;
			}
		}

		/**
		 * Construct an empty {@link PrefixNode}.
		 */
		public PrefixNode () {}
	}

	/**
	 * A {@code PrefixTrie} is trie of {@linkplain PrefixNode PrefixNodes}
	 * that represent a path of words made up by characters.
	 */
	public static class PrefixTrie
	{
		/**
		 * The root {@link PrefixNode}.
		 */
		private final @NotNull PrefixNode root = new PrefixNode();

		/**
		 * Add a template to this {@link PrefixTrie}.
		 *
		 * @param searchText
		 *        The search text, the characters that will create the {@link
		 *        PrefixNode}s.
		 * @param template
		 *        The {@link PrefixNode#template}.
		 */
		public void addTemplate (
			final @NotNull String searchText,
			final @NotNull String template)
		{
			root.addWord(searchText.toLowerCase(), 0, template);
		}

		/**
		 * Answer the {@link PrefixNode} for the given String.
		 *
		 * @param word
		 *        The String to find.
		 * @return A {@code PrefixNode} if one exists; {@code null} otherwise.
		 */
		public @Nullable PrefixNode searchNode (final @NotNull String word)
		{
			return root.searchTrie(word, 0);
		}

		/**
		 * Answer a {@link List} of {@link Pair}s of word and corresponding
		 * {@link PrefixNode#template} in this {@link PrefixTrie}.
		 *
		 * @return A list.
		 */
		public @NotNull List<Pair<String, String>> wordTemplates ()
		{
			final List<Pair<String, String>> wordTemplates = new ArrayList<>();
			root.wordList().forEach(word ->
				wordTemplates.add(
					new Pair<>(word, searchNode(word).template())));

			return wordTemplates;
		}
	}
}
