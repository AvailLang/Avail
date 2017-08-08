/**
 * FilterTrieComboBox.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.environment.editor.fx;
import com.avail.environment.editor.utility.PrefixNode;
import com.avail.environment.editor.utility.PrefixTrie;
import com.avail.utility.evaluation.Continuation0;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * A {@code FilterTrieComboBox} is a {@link FilterComboBox} that is backed by
 * a {@link PrefixTrie}.
 *
 * @param <T>
 *        The type of the {@linkplain PrefixNode#content content} in the prefix
 *        tree.
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class FilterTrieComboBox<T>
extends FilterComboBox<String>
{
	/**
	 * The {@link PrefixTrie} that backs
	 */
	private final @NotNull PrefixTrie<T> prefixTrie;

	/**
	 * An {@link ArrayDeque} that represents the currently navigated path on
	 * the {@link #prefixTrie}.
	 */
	private @NotNull
	final ArrayDeque<PrefixNode<T>> path = new ArrayDeque<>();

	/**
	 * Add a {@link PrefixNode} to the {@link #path}.
	 *
	 * @param node
	 *        The {@code PrefixNode} to add,
	 */
	private void addNode (final PrefixNode<T> node)
	{
		path.add(node);
	}

	/**
	 * The {@link PrefixNode} to the {@link #path} that is last and currently
	 * filtering the options.
	 *
	 * @return A {@code PrefixNode}.
	 */
	private @NotNull PrefixNode<T> currentNode ()
	{
		return path.getLast();
	}

	/**
	 * A {@link Collection} of Strings that represent the content presently
	 * available from this point forward in the {@link #path}.
	 */
	private @NotNull Collection<String> visibleWordList =
		Collections.emptyList();

	/**
	 * Answer the {@link #currentNode()}.
	 *
	 * @return A {@link PrefixNode}.
	 */
	public @Nullable PrefixNode<T> getNode ()
	{
		return currentNode();
	}

	@Override
	protected @NotNull Collection<String> generateVisibleList ()
	{
		return visibleWordList;
	}

	@Override
	protected void generalKeyAction ()
	{
		final String value = getEditor().getText();
		if (value != null && value.length() > 0)
		{
			currentNode().searchTrie(value, node ->
			{
				if (node != null)
				{
					addNode(node);
					visibleWordList = node.words();
				}
				else
				{
					visibleWordList = Collections.emptyList();
				}
			});
		}
		else
		{
			visibleWordList = prefixTrie.root().words();
		}
	}

	@Override
	protected void downSelectAction ()
	{
		final String value = getEditor().getText();
		if (value != null && value.length() > 0)
		{
			currentNode().searchTrie(value, node ->
			{
				if (node != null)
				{
					addNode(node);
					visibleWordList = node.words();
				}
				else
				{
					visibleWordList = Collections.emptyList();
				}
			});
		}
	}

	@Override
	protected void backspaceAction ()
	{
		path.clear();
		path.add(prefixTrie.root());
		final String value = getEditor().getText();
		if (value != null && value.length() > 0)
		{
			currentNode().searchTrie(value, node ->
			{
				if (node != null)
				{
					addNode(node);
					visibleWordList = node.words();
				}
				else
				{
					visibleWordList = Collections.emptyList();
				}
			});
		}
		else
		{
			visibleWordList = prefixTrie.root().words();
		}
	}

	@Override
	protected void deleteAction ()
	{
		path.clear();
		path.add(prefixTrie.root());
		final String value = getEditor().getText();
		if (value != null && value.length() > 0)
		{
			currentNode().searchTrie(value, node ->
			{
				if (node != null)
				{
					addNode(node);
					visibleWordList = node.words();
				}
				else
				{
					visibleWordList = Collections.emptyList();
				}
			});
		}
		else
		{
			visibleWordList = prefixTrie.root().words();
		}
	}

	/**
	 * Construct a {@link FilterTrieComboBox}.
	 *
	 * @param matcher
	 *        A {@link BiFunction} that accepts the typed String and an object
	 *        from the {@link ComboBox} option list.
	 * @param enterBehavior
	 *        A {@link Continuation0} that is run when {@link KeyCode#ENTER}
	 *        is hit.
	 * @param prefixTrie
	 *        The backing {@link PrefixTrie}.
	 */
	public FilterTrieComboBox (
		final @NotNull BiFunction<String, String, Boolean> matcher,
		final @NotNull Continuation0 enterBehavior,
		final @NotNull PrefixTrie<T> prefixTrie)
	{
		super(prefixTrie.root().words(), matcher, enterBehavior);
		this.prefixTrie = prefixTrie;
		this.path.add(prefixTrie.root());
	}

	/**
	 *
	 * @param matcher
	 *        A {@link BiFunction} that accepts the typed String and an object
	 *        from the {@link ComboBox} option list.
	 * @param prefixTrie
	 *        The backing {@link PrefixTrie}.
	 */
	public FilterTrieComboBox (
		final @NotNull BiFunction<String, String, Boolean> matcher,
		final @NotNull PrefixTrie<T> prefixTrie)
	{
		super(prefixTrie.root().words(), matcher);
		this.prefixTrie = prefixTrie;
		this.path.add(prefixTrie.root());
	}
}
