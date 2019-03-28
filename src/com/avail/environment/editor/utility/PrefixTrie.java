package com.avail.environment.editor.utility;

import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@code PrefixTrie} is trie of {@link PrefixNode}s.
 *
 * @param <T>
 *        The type of object potentially held at the {@code PrefixNode}.
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class PrefixTrie<T>
{
	/**
	 * The root {@link PrefixNode}.
	 */
	private final PrefixNode<T> root = new PrefixNode<>(-1);

	/**
	 * Answer the {@link #root}.
	 *
	 * @return A {@link PrefixNode}.
	 */
	public PrefixNode<T> root ()
	{
		return root;
	}

	/**
	 * A lock for concurrent updates to this trie.
	 */
	private final Object lock = new Object();

	/**
	 * Add a template to this {@link PrefixTrie}.
	 *
	 * @param word
	 *        The search text, the characters that will functionType the {@link
	 *        PrefixNode}s.
	 * @param content
	 *        The {@linkplain PrefixNode#content() content}.
	 */
	public void addWord (
		final String word,
		final T content)
	{
		boolean addWord = false;
		synchronized (lock)
		{
			final Set<String> currentWords =
				new HashSet<>(root.words());
			final int startSize = currentWords.size();
			currentWords.add(word.toLowerCase());
			if (currentWords.size() > startSize)
			{
				addWord = true;
			}
		}
		if (addWord)
		{
			root.addWord(word.toLowerCase(), content);
		}
	}

	/**
	 * Answer the {@link PrefixNode} for the given String.
	 *
	 * @param word
	 *        The String to find.
	 * @param acceptEachNode
	 *        A {@link Consumer} that accepts a {@link PrefixNode}.
	 * @return A {@code PrefixNode} if one exists; {@code null} otherwise.
	 */
	public @Nullable PrefixNode<T> searchNode (
		final String word,
		final Consumer<PrefixNode<T>> acceptEachNode)
	{
		return root.searchTrie(word, acceptEachNode);
	}

	/**
	 * Answer the {@link PrefixNode} for the given String.
	 *
	 * @param word
	 *        The String to find.
	 * @return A {@code PrefixNode} if one exists; {@code null} otherwise.
	 */
	public @Nullable PrefixNode<T> searchNode (final String word)
	{
		return root.searchTrie(word);
	}

	/**
	 * Answer a {@link List} of {@link Pair}s of word and corresponding
	 * {@link PrefixNode#content() content} in this {@link PrefixTrie}.
	 *
	 * @return A list.
	 */
	public List<NodeContent<T>> wordContent ()
	{
		final List<NodeContent<T>> wordTemplates = new ArrayList<>();
		root.words().forEach(word ->
			{
				final @Nullable T nodeContent = searchNode(word).content();
				if (nodeContent != null)
				{
					wordTemplates.add(
						new NodeContent<>(word, nodeContent));
				}
			});

		return wordTemplates;
	}

	/**
	 * A pairing of the {@link PrefixNode} word up to that node and the
	 * {@linkplain PrefixNode#content() content} at the node.
	 *
	 * @param <T>
	 *        The type of content contained in the node.
	 */
	public static class NodeContent<T>
	{
		/**
		 * The word spelled up to the originating {@link PrefixNode}.
		 */
		public final String word;

		/**
		 * The content at the originating {@link PrefixNode}.
		 */
		public final T content;

		/**
		 * Construct a {@link NodeContent}.
		 *
		 * @param word
		 *        The search word.
		 * @param content
		 *        The {@link PrefixNode#content() content}.
		 */
		NodeContent (
			final String word,
			final T content)
		{
			this.word = word;
			this.content = content;
		}
	}
}
