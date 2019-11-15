/*
 * Expression.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.compiler.splitter

import com.avail.compiler.splitter.MessageSplitter.Companion.throwMalformedMessageException
import com.avail.descriptor.A_Type
import com.avail.descriptor.MacroDefinitionDescriptor
import com.avail.descriptor.MethodDefinitionDescriptor
import com.avail.descriptor.parsing.A_Phrase
import com.avail.descriptor.parsing.PhraseDescriptor
import com.avail.exceptions.AvailErrorCode.E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION
import com.avail.exceptions.MalformedMessageException
import com.avail.exceptions.SignatureException
import java.util.*

/**
 * An `Expression` represents a structural view of part of the
 * message name.
 *
 * @property positionInName
 *   The 1-based start position of this expression in the message name.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a new Expression, capturing its 1-based start position in the
 * message name.
 *
 * @param positionInName
 *   The 1-based position at which this expression starts in the message name
 *   string.
 */
internal abstract class Expression constructor(val positionInName: Int)
{
	/**
	 * Answer whether reordering with respect to siblings is applicable to this
	 * kind of expression.
	 *
	 * @return
	 *   Whether the expression can in theory be reordered.
	 */
	val canBeReordered: Boolean
		get()
		{
			return yieldsValue
		}

	/**
	 * The one-based explicit numbering for this argument.  To specify this in a
	 * message name, a circled number (0-50) immediately follows the underscore
	 * or ellipsis (or the close guillemet for permuting [Group]s).
	 */
	var explicitOrdinal: Int = -1
		get()
		{
			assert(canBeReordered)
			return field
		}
		set(newValue)
		{
			assert(canBeReordered)
			field = newValue
		}

	/**
	 * `true` iff this [expression][Expression] is expected to produce a value
	 * to be consumed by a [method][MethodDefinitionDescriptor] or [macro
	 * definition][MacroDefinitionDescriptor]. Not applicable to [Sequence]s.
	 */
	internal open val yieldsValue: Boolean
		get() = false

	/**
	 * `true` if and only if this is an argument or group, `false` otherwise.
	 */
	internal open val isGroup: Boolean
		get() = false

	/**
	 * Are all keywords of the expression comprised exclusively of lower case
	 * characters?
	 *
	 * @return
	 *   `true` if all keywords of the expression are comprised exclusively of
	 *   lower case characters, `false` otherwise.
	 */
	internal open val isLowerCase: Boolean
		get() = true

	/**
	 * Transform this expression to be case-insensitive, failing with a
	 * [MalformedMessageException] if this is not meaningful.
	 *
	 * @return
	 *   The case-insensitive expression.
	 * @throws MalformedMessageException
	 *   If the result would not be meaningful.
	 */
	@Throws(MalformedMessageException::class)
	internal abstract fun applyCaseInsensitive(): Expression

	/**
	 * Answer the number of non-backquoted underscores/ellipses that occur
	 * in this section of the method name.
	 *
	 * @return
	 *   The number of non-backquoted underscores/ellipses in the receiver.
	 */
	internal open val underscoreCount: Int
		get() = 0

	/**
	 * Extract all [SectionCheckpoint]s into the specified list.
	 *
	 * @param sectionCheckpoints
	 *   Where to add section checkpoints found within this expression.
	 */
	internal open fun extractSectionCheckpointsInto(
		sectionCheckpoints: MutableList<SectionCheckpoint>)
	{
		// Do nothing by default.
	}

	/**
	 * Answer whether this expression recursively contains any section
	 * checkpoints.
	 *
	 * @return
	 *   `true` if this expression recursively contains any section checkpoints,
	 *   otherwise `false`.
	 */
	internal val hasSectionCheckpoints: Boolean
		get()
		{
			val any = ArrayList<SectionCheckpoint>()
			extractSectionCheckpointsInto(any)
			return any.isNotEmpty()
		}

	/**
	 * Check that the given type signature is appropriate for this message
	 * expression. If not, throw a [SignatureException].
	 *
	 * This is also called recursively on subcomponents, and it checks that
	 * [group arguments][Argument] have the correct structure for what will be
	 * parsed. The method may reject parses based on the number of repetitions
	 * of a [group][Group] at a call site, but not the number of arguments
	 * actually delivered by each repetition. For example, the message "«_:_‡,»"
	 * can limit the number of _:_ pairs to at most 5 by declaring the tuple
	 * type's size to be [5..5]. However, the message ```"«_:_‡[_]»"``` will
	 * always produce a tuple of 3-tuples followed by a 2-tuple (if any elements
	 * at all occur). Attempting to add a method implementation for this message
	 * that only accepted a tuple of 7-tuples would be inappropriate (and
	 * ineffective). Instead, it should be required to accept a tuple whose size
	 * is in the range [2..3].
	 *
	 * Note that the outermost (pseudo)group represents the entire message, so
	 * the caller should synthesize a fixed-length [tuple
	 * type][TupleTypeDescriptor] for the outermost check.
	 *
	 * @param argumentType
	 *   A [tuple type][TupleTypeDescriptor] describing the types of arguments
	 *   that a method being added will accept.
	 * @param sectionNumber
	 *   Which [SectionCheckpoint] section marker this list of argument types
	 *   are being validated against.  To validate the final method or macro
	 *   body rather than a prefix function, use any value greater than the
	 *   [MessageSplitter.numberOfSectionCheckpoints].
	 * @throws SignatureException
	 *   If the argument type is inappropriate.
	 */
	@Throws(SignatureException::class)
	internal abstract fun checkType(argumentType: A_Type, sectionNumber: Int)

	/**
	 * Write instructions for parsing me to the given list.
	 *
	 * @param phraseType
	 *   The type of the phrase being parsed at and inside this parse point.
	 *   Note that when this is for a list phrase type, it's used for unrolling
	 *   leading iterations of loops up to the end of the variation (typically
	 *   just past the list phrase's tuple type's [A_Type.typeTuple]).
	 * @param generator
	 *   The [InstructionGenerator] that accumulates the parsing instructions.
	 * @param wrapState
	 *   The initial [WrapState] that indicates what has been pushed and what
	 *   the desired stack structure is.
	 * @return
	 *   The resulting [WrapState], indicating the state of the stack.
	 */
	internal abstract fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState

	override fun toString(): String = javaClass.simpleName

	/**
	 * Pretty-print this part of the message, using the provided argument
	 * [phrases][PhraseDescriptor].
	 *
	 * @param arguments
	 *   An [Iterator] that provides phrases to fill in for arguments and
	 *   subgroups.
	 * @param builder
	 *   The [StringBuilder] on which to print.
	 * @param indent
	 *   The indent at which to present the arguments.
	 */
	internal abstract fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)

	/**
	 * Answer whether the pretty-printed representation of this [Expression]
	 * should be separated from its left sibling with a space.
	 *
	 * @return
	 *   Whether this expression should be preceded by a space if it has a left
	 *   sibling.
	 */
	internal abstract val shouldBeSeparatedOnLeft: Boolean

	/**
	 * Answer whether the pretty-printed representation of this [Expression]
	 * should be separated from its right sibling with a space.
	 *
	 * @return
	 *   Whether this expression should be followed by a space if it has a right
	 *   sibling.
	 */
	internal abstract val shouldBeSeparatedOnRight: Boolean

	/**
	 * Answer whether this expression might match an empty sequence of tokens.
	 *
	 * @param phraseType
	 *   The phrase type for this, if it parses an argument.
	 * @return
	 *   Whether what this expression matches could be empty.
	 */
	internal open fun mightBeEmpty(phraseType: A_Type): Boolean
	{
		// Most expressions can't match an empty sequence of tokens.
		return false
	}
}
