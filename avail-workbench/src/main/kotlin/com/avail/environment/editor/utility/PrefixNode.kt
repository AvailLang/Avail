package com.avail.environment.editor.utility

/**
 * A `PrefixNode` is a node in a [PrefixTrie].
 *
 * @param T
 *   The type of object held at the `PrefixNode`.
 * @property depth
 *   Record its depth in the trie.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an empty `PrefixNode`.
 *
 * @param depth
 *   Record its depth in the trie.
 */
class PrefixNode<T> constructor(private val depth: Int)
{
	/**
	 * The [Map] that holds the next nodes in the trie.
	 */
	private val children = mutableMapOf<Char, PrefixNode<T>>()

	/**
	 * The target content of this node if any exist, otherwise `null`.
	 */
	private var content: T? = null

	/**
	 * An object to lock editing to this [PrefixNode].
	 */
	private val lock = Any()

	/**
	 * The list of viable complete prefix word from this point in the
	 * [PrefixTrie].
	 */
	val words = mutableSetOf<String>()

	/**
	 * Answer the [content].
	 *
	 * @return A String.
	 */
	fun content(): T?
	{
		return content
	}

	/**
	 * Add a word to this `PrefixNode`.
	 *
	 * @param word
	 *   The word to add.
	 */
	private fun addWord(word: String)
	{
		synchronized(lock) { words.add(word) }
	}

	/**
	 * Answer the [words] from this `PrefixNode`.
	 *
	 * @return A [Set].
	 */
	fun words(): Set<String>
	{
		synchronized(lock) {
			return words.toSet()
		}
	}

	/**
	 * Add a word to this `PrefixNode`.
	 *
	 * @param word
	 *   The word branch to add.
	 * @param contents
	 *   The target content of the target node if any exist, otherwise `null`.
	 */
	fun addWord(word: String, contents: T)
	{
		addWord(word)
		if (word.length - 1 > depth)
		{
			val nextNode: PrefixNode<T>
			synchronized(lock)
			{
				nextNode = (children).computeIfAbsent(word[depth + 1])
					{ PrefixNode(depth + 1) }
			}
			nextNode.addWord(word, contents)
		}
		else
		{
			this.content = contents
		}
	}

	/**
	 * Search the [PrefixTrie] starting at this `PrefixNode`.
	 *
	 * @param word
	 *   The word to search.
	 */
	fun searchTrie(word: String): PrefixNode<T>? =
		if (word.length - 1 > depth)
		{
			val nextNode = nextNodeFor(word[depth + 1])
			nextNode?.searchTrie(word)
		}
		else
		{
			this
		}

	/**
	 * Search the [PrefixTrie] starting at this `PrefixNode`. Add visited nodes
	 * to the consumer.
	 *
	 * @param word
	 *   The word to search.
	 * @param acceptEachNode
	 *   A lambda that accepts a `PrefixNode`.
	 */
	fun searchTrie(word: String, acceptEachNode: (PrefixNode<T>?) -> Unit)
		: PrefixNode<T>? =
			if (word.length - 1 > depth)
			{
				val nextNode = nextNodeFor(word[depth + 1])
				acceptEachNode.invoke(nextNode)
				nextNode?.searchTrie(word, acceptEachNode)
			}
			else
			{
				acceptEachNode.invoke(this)
				this
			}

	/**
	 * Answer the next `PrefixNode` for the given character.
	 *
	 * @param c
	 *   The char to get.
	 * @return A `PrefixNode` if it exists; `null` otherwise.
	 */
	fun nextNodeFor(c: Char): PrefixNode<T>?
	{
		return children.getOrDefault(c, null)
	}
}
