package com.avail.environment.editor.utility;

import com.avail.utility.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
	//TODO write remove logic
	/**
	 * An array of all the branches in this {@link PrefixTrie}
	 */
	private ArrayList<String> branches = new ArrayList<>();

	/**
	 * The root {@link PrefixNode}.
	 */
	private final @NotNull PrefixNode<T> root =
		new PrefixNode<>(-1, indices ->
		{
			final List<String> words = new ArrayList<>();
			for (int i = 0; i < indices.size(); i++)
			{
				words.add(branches.get(indices.get(i)));
			}
			return words;
		});

	/**
	 * Answer the {@link #root}.
	 *
	 * @return A {@link PrefixNode}.
	 */
	public @NotNull PrefixNode<T> root ()
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
	 *        The search text, the characters that will create the {@link
	 *        PrefixNode}s.
	 * @param content
	 *        The {@link PrefixNode#content}.
	 */
	public void addBranch (
		final @NotNull String word,
		final @NotNull T content)
	{
		boolean addWord = false;
		int index = -1;
		synchronized (lock)
		{
			final Set<String> currentBranches =
				new HashSet<>(branches);
			final int startSize = currentBranches.size();
			currentBranches.add(word);
			if (currentBranches.size() > startSize)
			{
				addWord = true;
				branches.add(word);
				index = branches.size() - 1;
			}
		}
		if (addWord)
		{
			root.addWord(
				word.toLowerCase(),
				index,
				content,
				indices ->
				{
					final Map<String, Integer> textToId = new HashMap<>();
					List<String> words = new ArrayList<>();
					for (int i = 0; i < indices.size(); i++)
					{
						int idx = indices.get(i);
						String w = branches.get(idx);
						textToId.put(w, idx);
						words.add(w);
					}
					Collections.sort(words);
					final List<Integer> sortedInts = new ArrayList<>();
					for (int i = 0; i < indices.size(); i++)
					{
						sortedInts.add(textToId.get(words.get(i)));
					}
					return sortedInts;
				});
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
		final @NotNull String word,
		final @NotNull Consumer<PrefixNode<T>> acceptEachNode)
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
	public @Nullable PrefixNode<T> searchNode (final @NotNull String word)
	{
		return root.searchTrie(word);
	}

	/**
	 * Answer a {@link List} of {@link Pair}s of word and corresponding
	 * {@link PrefixNode#content} in this {@link PrefixTrie}.
	 *
	 * @return A list.
	 */
	public @NotNull List<NodeContent<T>> wordContent ()
	{
		final List<NodeContent<T>> wordTemplates = new ArrayList<>();
		root.wordList().forEach(word ->
			{
				final T nodeContent = searchNode(word).content();
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
	 * {@linkplain PrefixNode#content} at the node.
	 * @param <T>
	 *        The type of content contained in the node.
	 */
	public static class NodeContent<T>
	{
		/**
		 * The word spelled up to the originating {@link PrefixNode}.
		 */
		public final @NotNull String word;

		/**
		 * The content at the originating {@link PrefixNode}.
		 */
		public final @NotNull T content;

		/**
		 * Construct a {@link NodeContent}.
		 *
		 * @param word
		 *        The search word.
		 * @param content
		 *        The {@link PrefixNode#content}.
		 */
		public NodeContent (
			final @NotNull String word,
			final @NotNull T content)
		{
			this.word = word;
			this.content = content;
		}
	}
}
