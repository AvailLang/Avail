package com.avail.environment.editor.utility

/**
 * A `PrefixTrie` is trie of [PrefixNode]s.
 *
 * @param T
 *   The type of object potentially held at the `PrefixNode`.
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
class PrefixTrie<T>
{
	/**
	 * The root [PrefixNode].
	 */
	val root = PrefixNode<T>(-1)

	/**
	 * A lock for concurrent updates to this trie.
	 */
	private val lock = Any()

	/**
	 * Add a template to this `PrefixTrie`.
	 *
	 * @param word
	 *   The search text, the characters that will functionType the
	 *   [PrefixNode]s.
	 * @param content
	 *   The [content][PrefixNode.content].
	 */
	fun addWord(word: String, content: T)
	{
		var addWord = false
		synchronized(lock)
		{
			val startSize = root.words.size
			root.words.add(word.toLowerCase())
			if (root.words.size > startSize)
			{
				addWord = true
			}
		}
		if (addWord)
		{
			root.addWord(word.toLowerCase(), content)
		}
	}

	/**
	 * Answer the [PrefixNode] for the given String.
	 *
	 * @param word
	 *   The String to find.
	 * @param acceptEachNode
	 *   A lambda that accepts a [PrefixNode].
	 * @return A `PrefixNode` if one exists; `null` otherwise.
	 */
	fun searchNode(word: String, acceptEachNode: (PrefixNode<T>?) -> Unit)
		: PrefixNode<T>? = root.searchTrie(word, acceptEachNode)

	/**
	 * Answer the [PrefixNode] for the given String.
	 *
	 * @param word
	 * The String to find.
	 * @return A `PrefixNode` if one exists; `null` otherwise.
	 */
	fun searchNode(word: String): PrefixNode<T>? = root.searchTrie(word)

	/**
	 * Answer a [List] of [Pair]s of word and corresponding
	 * [content][PrefixNode.content] in this `PrefixTrie`.
	 *
	 * @return A list.
	 */
	fun wordContent(): List<NodeContent<T>>
	{
		val wordTemplates = ArrayList<NodeContent<T>>()
		root.words().forEach { word ->
			val nodeContent = searchNode(word)!!.content()
			if (nodeContent !== null)
			{
				wordTemplates.add(
					NodeContent(word, nodeContent))
			}
		}

		return wordTemplates
	}

	/**
	 * A pairing of the [PrefixNode] word up to that node and the
	 * [content][PrefixNode.content] at the node.
	 *
	 * @param T
	 *   The type of content contained in the node.
	 *
	 * @property word
	 *   The word spelled up to the originating [PrefixNode].
	 * @property content
	 *   The content at the originating [PrefixNode].
	 *
	 * @constructor
	 * Construct a `NodeContent`.
	 *
	 * @param word
	 *   The search word.
	 * @param content
	 *   The [content][PrefixNode.content].
	 */
	data class NodeContent<T> internal constructor(
		val word: String, val content: T)
}
