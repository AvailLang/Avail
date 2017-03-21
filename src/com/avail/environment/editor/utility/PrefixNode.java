package com.avail.environment.editor.utility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@code PrefixNode} is a node in a {@link PrefixTrie}.
 * 
 * @param <T>
 *        The type of object held at the {@code PrefixNode}.
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class PrefixNode<T>
{
	/**
	 * The {@link Map} that holds the next nodes in the trie.
	 */
	private final @NotNull Map<Character, PrefixNode<T>> children =
		new HashMap<>();

	/**
	 * A {@link Function} that accepts the {@link #indexList} and returns a
	 * {@link List} of word options from {@link PrefixTrie#branches}.
	 */
	private final @NotNull Function<List<Integer>, List<String>> listGetter;

	/**
	 * The target content of this node if any exist, otherwise {@code null}.
	 */
	private @Nullable T content;

	/**
	 * The height position in the {@link PrefixTrie}.
	 */
	private final int depth;

	/**
	 * An object to lock editing to this {@link PrefixNode}.
	 */
	private final @NotNull Object lock = new Object();

	/**
	 * The ordered integer list.
	 */
	private List<Integer> indexList = new ArrayList<>();

	/**
	 * Answer the {@link #content}.
	 *
	 * @return A String.
	 */
	public @Nullable T content ()
	{
		return content;
	}

	/**
	 * Answer the options from this node.
	 *
	 * @return A {@link List}.
	 */
	public final @NotNull List<String> wordList ()
	{
		return listGetter.apply(indexList);
	}

	/**
	 * Add a branch to this {@link PrefixNode}.
	 *
	 * @param word
	 *        The word branch to add.
	 * @param wordIndex
	 *        The index of word into {@link PrefixTrie#branches}.
	 * @param contents
	 *        The target content of the target node if any exist, otherwise
	 *        {@code null}.
	 * @param sortedListGetter
	 *        A function that accepts {@link #indexList} and returns the list
	 *        in sorted order according to the natural ordering of the
	 *        corresponding string words in {@link PrefixTrie#branches}.
	 */
	public void addWord (
		final @NotNull String word,
		final int wordIndex,
		final @NotNull T contents,
		final @NotNull Function<List<Integer>, List<Integer>> sortedListGetter)
	{
		synchronized (lock)
		{
			indexList.add(wordIndex);
			indexList = sortedListGetter.apply(indexList);
		}

		if (word.length() - 1 > depth)
		{
			final Character next = word.charAt(depth + 1);
			final PrefixNode<T> nextNode = children.computeIfAbsent(
				next,
				key -> new PrefixNode(depth + 1, listGetter));
			nextNode.addWord(word, wordIndex, contents, sortedListGetter);
		}
		else
		{
			this.content = contents;
		}
	}

	/**
	 * Add a branch to this {@link PrefixNode}.
	 *
	 * @param word
	 *        The word to search.
	 */
	public @Nullable PrefixNode<T> searchTrie (
		final @NotNull String word)
	{
		if (word.length() - 1 > depth)
		{
			final PrefixNode<T> nextNode = nextNodeFor(word.charAt(depth + 1));
			return nextNode != null
				? nextNode.searchTrie(word)
				: null;
		}
		else
		{
			return this;
		}
	}

	/**
	 * Add a branch to this {@link PrefixNode} passing each node on that path
	 * to the provided {@link Consumer}.
	 *
	 * @param word
	 *        The word to search.
	 * @param acceptEachNode
	 *        A {@link Consumer} that accepts a {@link PrefixNode}.
	 */
	public @Nullable PrefixNode<T> searchTrie (
		final @NotNull String word,
		final @NotNull Consumer<PrefixNode<T>> acceptEachNode)
	{
		if (word.length() - 1 > depth)
		{
			final PrefixNode<T> nextNode = nextNodeFor(word.charAt(depth + 1));
			acceptEachNode.accept(nextNode);
			return nextNode != null
				? nextNode.searchTrie(word, acceptEachNode)
				: null;
		}
		else
		{
			acceptEachNode.accept(this);
			return this;
		}
	}

	/**
	 * Answer the next {@link PrefixNode} for the given character.
	 *
	 * @param c
	 *        The char to get.
	 * @return A {@link PrefixNode} if it exists; {@code null} otherwise.
	 */
	public @Nullable PrefixNode<T> nextNodeFor (final char c)
	{
		return children.getOrDefault(c, null);
	}

	/**
	 * Construct an empty {@link PrefixNode}.
	 */
	public PrefixNode (
		final int depth,
		final @NotNull Function<List<Integer>, List<String>> listGetter)
	{
		this.depth = depth;
		this.listGetter = listGetter;

	}
}
