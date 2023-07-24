/*
 * PhrasePathRecord.kt
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

package avail.persistence.cache.record

import avail.compiler.splitter.MessageSplitter
import avail.descriptor.module.A_Module
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MACRO_SUBSTITUTION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.utility.Mutable
import avail.utility.decodeString
import avail.utility.iterableWith
import avail.utility.removeLast
import avail.utility.sizedString
import avail.utility.unvlqInt
import avail.utility.unzigzagInt
import avail.utility.vlq
import avail.utility.zigzag
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Information for efficiently navigating from any position in a module's
 * source to the hierarchy of [A_Phrase]s containing that position.
 *
 * @constructor
 * Construct an instance from an optional list of [PhraseNode]s.
 *
 * @property rootTrees
 *   The [PhraseNode]s representing the sequence of top-level phrases of the
 *   module.
 *
 */
class PhrasePathRecord
constructor (
	val rootTrees: MutableList<PhraseNode> = mutableListOf())
{
	/**
	 * For each [PhraseNode] in this module, working top-down, invoke the action
	 * with that [PhraseNode].
	 *
	 * @param action
	 *   What to do with each [PhraseNode].
	 */
	fun phraseNodesDo(
		action: (PhraseNode) -> Unit)
	{
		val workStack = mutableListOf<PhraseNode>()
		workStack.addAll(rootTrees.reversed())
		while (workStack.isNotEmpty())
		{
			val node = workStack.removeLast()
			action(node)
			workStack.addAll(node.children.reversed())
		}
	}

	/**
	 * Output information about all the [rootTrees] to the provided
	 * [DataOutputStream]. It can later be reconstructed via the constructor
	 * taking a [DataInputStream].
	 *
	 * @param binaryStream
	 *   A DataOutputStream on which to write this phrase path record.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal fun write(binaryStream: DataOutputStream)
	{
		val moduleNameMap = mutableMapOf<A_String, Int>()
		val atomNameMap = mutableMapOf<A_String, Int>()
		// First pre-scan all the trees to populate the maps.
		val workStack = rootTrees.reversed().toMutableList()
		while (workStack.isNotEmpty())
		{
			val node = workStack.removeLast()
			node.atomModuleName?.let {
				moduleNameMap.computeIfAbsent(it) { moduleNameMap.size + 1 }
			}
			node.atomName?.let {
				atomNameMap.computeIfAbsent(it) { atomNameMap.size + 1 }
			}
			workStack.addAll(node.children.reversed())
		}
		// Output these strings first, so they'll be available early during
		// reconstruction.  Start with the module names.
		binaryStream.vlq(moduleNameMap.size)
		moduleNameMap.entries.sortedBy { it.value }.forEach { (string, _) ->
			binaryStream.sizedString(string.asNativeString())
		}
		// Now do the same with all the atom names.
		binaryStream.vlq(atomNameMap.size)
		atomNameMap.entries.sortedBy { it.value }.forEach { (string, _) ->
			binaryStream.sizedString(string.asNativeString())
		}
		// Now traverse it all again, producing instructions for assembling the
		// trees.  Output the nodes top-down, left-to-right.
		binaryStream.vlq(rootTrees.size)
		workStack.addAll(rootTrees.reversed())
		val tokenCursor = Mutable(0)
		while (workStack.isNotEmpty())
		{
			val node = workStack.removeLast()
			node.write(
				binaryStream, moduleNameMap, atomNameMap, tokenCursor)
			binaryStream.vlq(node.children.size)
			workStack.addAll(node.children.reversed())
		}
	}

	override fun toString(): String =
		"PhrasePathRecord (${rootTrees.size} top-level phrases)"

	/**
	 * Reconstruct a [PhrasePathRecord], having previously been written via
	 * [write].
	 *
	 * @param bytes
	 *   Where to read the [PhrasePathRecord] from.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(bytes: ByteArray) : this()
	{
		val binaryStream = DataInputStream(ByteArrayInputStream(bytes))
		val moduleNames = List(binaryStream.unvlqInt()) {
			StringDescriptor.stringFrom(binaryStream.decodeString())
		}
		val atomNames = List(binaryStream.unvlqInt()) {
			StringDescriptor.stringFrom(binaryStream.decodeString())
		}
		val fakeRoot = PhraseNode(null, null, emptyList(), null)
		// Two parallel stacks of phrases and countdowns, indicating how many
		// more subphrases to add to the corresponding phrase before considering
		// that phrase complete.  This allows reconstruction of the forest of
		// PhraseNodes without recursion.
		val phraseStack = mutableListOf(fakeRoot)
		val countdownStack = mutableListOf(Mutable(binaryStream.unvlqInt()))
		val tokenCursor = Mutable(0)
		while (countdownStack.isNotEmpty())
		{
			val countdown = countdownStack.last()
			if (countdown.value == 0)
			{
				countdownStack.removeLast()
				phraseStack.removeLast()
				continue
			}
			countdown.value--
			val child = PhraseNode.read(
				binaryStream,
				moduleNames,
				atomNames,
				tokenCursor,
				phraseStack.last())
			phraseStack.add(child)
			countdownStack.add(Mutable(binaryStream.unvlqInt()))
		}
		rootTrees.addAll(fakeRoot.children)
		rootTrees.forEach { it.parent = null }
		assert(binaryStream.available() == 0)
	}

	/**
	 * A node of a tree that represents an occurrence of an [A_Phrase] in this
	 * [A_Module].  If the phrase is a send or macro invocation, the information
	 * about which atom's bundle was sent is available, as are the tokens that
	 * are part of the phrase (but not its subphrases).
	 *
	 * @constructor
	 * Create a [PhraseNode] from its parts.  The list of [children] is mutable,
	 * and can be provided here or added later.
	 *
	 * @property atomModuleName
	 *   If this node is a [SEND_PHRASE] or [MACRO_SUBSTITUTION_PHRASE], this is
	 *   the name of the module in which the sent bundle's atom was defined.
	 *   Otherwise `null`.
	 * @property atomName
	 *   If this node is a [SEND_PHRASE] or [MACRO_SUBSTITUTION_PHRASE], this is
	 *   the name of the sent bundle's atom.  Otherwise `null`.
	 * @property tokenSpans
	 *   The regions of the file that tokens of this phrase occupy.  Each region
	 *   is a [PhraseNodeToken] representing the one-based start and pastEnd
	 *   positions in the source string, adjusted to UCS-2 ("Char") positions.
	 *   It also contains an index into the [splitter]'s tuple of parts, to say
	 *   what the token was, or zero if it was not a literal part of the message
	 *   name.
	 * @property parent
	 *   The node representing the optional parent phrase of this node's phrase.
	 *   This can be provided here, or left null to be set later.  If present,
	 *   the new node will be automatically added as a child of the [parent].
	 * @property children
	 *   The children of this phrase, which roughly correspond to subphrases.
	 *   For a send phrase or macro send phrase, these may be the argument
	 *   phrases or the [list][ListPhraseDescriptor] phrases that group them,
	 *   depending on the structure of the sent bundle's name (see
	 *   [MessageSplitter]).
	 */
	class PhraseNode
	constructor(
		val atomModuleName: A_String?,
		val atomName: A_String?,
		val tokenSpans: List<PhraseNodeToken>,
		var parent: PhraseNode?,
		val children: MutableList<PhraseNode> = mutableListOf())
	{
		init
		{
			parent?.children?.add(this)
		}

		/**
		 * An entry in the [tokenSpans] of a PhraseNode.  The [start] and
		 * [pastEnd] identify where the token occurs in the UCS-2 source
		 * [String], but using one-based indices.  The [tokenIndexInName] is
		 * either zero or a one-based index into the atom's [MessageSplitter]'s
		 * [MessageSplitter.messageParts], indicating the part of the message
		 * that this token matched during parsing.
		 *
		 * @property start
		 *   The one-based index into the UCS-2 [String] at which the token
		 *   begins.
		 * @property pastEnd
		 *   The one-based index into the UCS-2 [String] just past the token.
		 * @property tokenIndexInName
		 *   Either zero to indicate this was not a token that occurred in the
		 *   actual method name, or the one-based index into the [PhraseNode]'s
		 *   [MessageSplitter]'s tuple of tokenized parts.
		 */
		data class PhraseNodeToken(
			val start: Int,
			val pastEnd: Int,
			val tokenIndexInName: Int)

		/**
		 * Add the given [PhraseNode] as the last child of the receiver.
		 *
		 * @param child
		 *   The new child of the receiver.
		 */
		private fun addChild(child: PhraseNode)
		{
			children.add(child)
		}

		/**
		 * As a nicety, a [PhraseNode] can answer the optional [NameInModule]
		 * that represents the name of what's being called, if anything.
		 */
		val nameInModule: NameInModule? get() =
			atomModuleName?.let { mod ->
				atomName?.let { name ->
					NameInModule(mod.asNativeString(), name.asNativeString())
				}
			}

		/**
		 * If the [atomName] is not null, this is a lazily-computed
		 * [MessageSplitter] derived from that name.  Otherwise, this is `null`.
		 */
		var splitter: MessageSplitter? = null
			get() = field ?: atomName?.let {
				field = MessageSplitter.split(it)
				field
			}
			private set

		/**
		 * This is the 1-based index of this node within its parent, or -1 if
		 * there is no parent.
		 */
		@Volatile
		var indexInParent: Int = -1
			get()
			{
				if (field == -1)
				{
					// Fill in the index of every child, for efficiency.
					parent?.children?.forEachIndexed { i, child ->
						child.indexInParent = i + 1
					}
				}
				return field
			}
			private set

		/**
		 * Write this [PhraseNode] to the provided stream.  Translate the
		 * [atomModuleName] and [atomName], if present, to [Int]s using the
		 * provided already-populated [Map]s.  Note that the indices in the map
		 * values are 1-based.
		 */
		@Throws(IOException::class)
		internal fun write(
			binaryStream: DataOutputStream,
			moduleNameMap: Map<A_String, Int>,
			atomNameMap: Map<A_String, Int>,
			tokenCursor: Mutable<Int>)
		{
			binaryStream.vlq(atomModuleName?.let(moduleNameMap::get) ?: 0)
			binaryStream.vlq(atomName?.let(atomNameMap::get) ?: 0)
			binaryStream.vlq(tokenSpans.size)
			tokenSpans.forEach { (start, pastEnd, tokenIndexInName) ->
				binaryStream.zigzag(start - tokenCursor.value)
				tokenCursor.value = start
				binaryStream.zigzag(pastEnd - tokenCursor.value)
				tokenCursor.value = pastEnd
				binaryStream.vlq(tokenIndexInName)
			}
		}

		fun depth() = iterableWith(PhraseNode::parent).count()

		override fun toString(): String
		{
			val indexInfo = when (val p = parent)
			{
				null -> ""
				else -> "($indexInParent/${p.children.size}) "
			}
			return tokenSpans.joinToString(
				", ",
				"PhraseNode $indexInfo$atomName: "
			) { (start, pastEnd, tokenIndex) ->
				"[$start..$pastEnd #$tokenIndex)"
			}
		}

		companion object
		{
			/**
			 * Extract this node from the input [binaryStream], using the
			 * pre-constructed lists of module names and atom names to decode
			 * the atom information from the original phrase.
			 *
			 * This should mirror the data produced by [write].
			 */
			fun read(
				binaryStream: DataInputStream,
				moduleNameList: List<A_String>,
				atomNameList: List<A_String>,
				tokenCursor: Mutable<Int>,
				parent: PhraseNode?
			): PhraseNode
			{
				val atomModuleName = when (val index = binaryStream.unvlqInt())
				{
					0 -> null
					else -> moduleNameList[index - 1]
				}
				val atomName = when (val index = binaryStream.unvlqInt())
				{
					0 -> null
					else -> atomNameList[index - 1]
				}
				val tokenSpans = (1..binaryStream.unvlqInt()).map {
					val start = tokenCursor.value + binaryStream.unzigzagInt()
					tokenCursor.value = start
					val pastEnd = tokenCursor.value + binaryStream.unzigzagInt()
					tokenCursor.value = pastEnd
					val tokenIndexInName = binaryStream.unvlqInt()
					PhraseNodeToken(start, pastEnd, tokenIndexInName)
				}
				return PhraseNode(atomModuleName, atomName, tokenSpans, parent)
			}
		}
	}
}
