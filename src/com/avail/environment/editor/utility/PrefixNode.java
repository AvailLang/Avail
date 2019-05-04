package com.avail.environment.editor.utility;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

import static java.util.Collections.unmodifiableList;

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
	private final Map<Character, PrefixNode<T>> children =
		new HashMap<>();

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
	private final Object lock = new Object();

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
	 * The list of viable complete prefix word from this point in the
	 * {@link PrefixTrie}.
	 */
	private List<String> words = new ArrayList<>();

	/**
	 * Add a word to this {@code PrefixNode}.
	 *
	 * @param word
	 *        The word to add.
	 */
	private void addWord (final String word)
	{
		synchronized (lock)
		{
			final TreeSet<String> set = new TreeSet<>(words);
			set.add(word);
			words = new ArrayList<>(set);
		}
	}

	/**
	 * Answer the {@link #words} from this {@code PrefixNode}.
	 *
	 * @return A {@link List}.
	 */
	public final List<String> words ()
	{
		synchronized (lock)
		{
			return unmodifiableList(words);
		}
	}

	/**
	 * Add a word to this {@code PrefixNode}.
	 *
	 * @param word
	 *        The word branch to add.
	 * @param contents
	 *        The target content of the target node if any exist, otherwise
	 *        {@code null}.
	 */
	public void addWord (
		final String word,
		final T contents)
	{

		addWord(word);

		if (word.length() - 1 > depth)
		{
			final PrefixNode<T> nextNode;
			synchronized (lock)
			{
				nextNode = children.computeIfAbsent(
					word.charAt(depth + 1),
					key -> new PrefixNode<>(depth + 1));
			}
			nextNode.addWord(word, contents);
		}
		else
		{
			this.content = contents;
		}
	}

	/**
	 * Search the {@link PrefixTrie} starting at this {@code PrefixNode}.
	 *
	 * @param word
	 *        The word to search.
	 */
	public @Nullable PrefixNode<T> searchTrie (
		final String word)
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
	 * Search the {@link PrefixTrie} starting at this {@code PrefixNode}. Add
	 * visited nodes to the consumer.
	 *
	 * @param word
	 *        The word to search.
	 * @param acceptEachNode
	 *        A {@link Consumer} that accepts a {@code PrefixNode}.
	 */
	public @Nullable PrefixNode<T> searchTrie (
		final String word,
		final Consumer<PrefixNode<T>> acceptEachNode)
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
	 * Answer the next {@code PrefixNode} for the given character.
	 *
	 * @param c
	 *        The char to get.
	 * @return A {@code PrefixNode} if it exists; {@code null} otherwise.
	 */
	public @Nullable PrefixNode<T> nextNodeFor (final char c)
	{
		return children.getOrDefault(c, null);
	}

	/**
	 * Construct an empty {@code PrefixNode}.
	 *
	 * @param depth
	 *        Record its depth in the trie.
	 */
	public PrefixNode (
		final int depth)
	{
		this.depth = depth;
	}
}
