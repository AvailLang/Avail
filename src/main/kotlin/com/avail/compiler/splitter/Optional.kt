/*
 * Optional.kt
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

import com.avail.compiler.ParsingOperation
import com.avail.compiler.ParsingOperation.DISCARD_SAVED_PARSE_POSITION
import com.avail.compiler.ParsingOperation.ENSURE_PARSE_PROGRESS
import com.avail.compiler.ParsingOperation.PUSH_LITERAL
import com.avail.compiler.ParsingOperation.SAVE_PARSE_POSITION
import com.avail.compiler.splitter.InstructionGenerator.Label
import com.avail.compiler.splitter.MessageSplitter.Companion.indexForFalse
import com.avail.compiler.splitter.MessageSplitter.Companion.indexForTrue
import com.avail.compiler.splitter.MessageSplitter.Companion.throwSignatureException
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.compiler.splitter.WrapState.SHOULD_NOT_HAVE_ARGUMENTS
import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.ListPhraseTypeDescriptor.emptyListPhraseType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP
import com.avail.exceptions.SignatureException
import java.util.*

/**
 * An `Optional` is a [Sequence] wrapped in guillemets («»), and followed by a
 * question mark (?).  It may not contain [Argument]s or subgroups, and since it
 * is not a group it may not contain a
 * [double&#32;dagger][Metacharacter.DOUBLE_DAGGER] (‡).
 *
 * At a call site, an optional produces a
 * [boolean][EnumerationTypeDescriptor.booleanType] that indicates whether there
 * was an occurrence of the group.  For example, the message "«very»?good"
 * accepts a single argument: a boolean that is
 * [true][AtomDescriptor.trueObject] if the token "very" occurred and
 * [false][AtomDescriptor.falseObject] if it did not.
 *
 * @property sequence
 *   The governed [sequence][Sequence].
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Optional`.
 *
 * @param positionInName
 *   The position of the group or token in the message name.
 * @param sequence
 *   The governed [sequence][Sequence].
 */
internal class Optional constructor(
	positionInName: Int,
	private val sequence: Sequence) : Expression(positionInName)
{
	override val yieldsValue: Boolean
		get() = true

	override val isLowerCase: Boolean
		get() = sequence.isLowerCase

	override fun applyCaseInsensitive() =
		Optional(positionInName, sequence.applyCaseInsensitive())

	override val underscoreCount: Int
		get()
		{
			assert(sequence.underscoreCount == 0)
			return 0
		}

	override fun extractSectionCheckpointsInto(
			sectionCheckpoints: MutableList<SectionCheckpoint>) =
		sequence.extractSectionCheckpointsInto(sectionCheckpoints)

	@Throws(SignatureException::class)
	override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		// The declared type of the subexpression must be a subtype of boolean.
		if (!argumentType.isSubtypeOf(booleanType()))
		{
			throwSignatureException(E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP)
		}
	}

	@Suppress("LocalVariableName")
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		/* branch to @absent
		 * push the current parse position on the mark stack
		 * ...the sequence's expressions...
		 * check progress and update saved position or abort.
		 * discard the saved parse position from the mark stack.
		 * push literal true
		 * jump to @groupSkip
		 * @absent:
		 * push literal false
		 * @groupSkip:
		 */
		generator.flushDelayed()
		val needsProgressCheck = sequence.mightBeEmpty(emptyListPhraseType())
		val `$absent` = Label()
		generator.emitBranchForward(this, `$absent`)
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION)
		assert(sequence.yieldersAreReordered !== java.lang.Boolean.TRUE)
		sequence.emitOn(
			emptyListPhraseType(), generator, SHOULD_NOT_HAVE_ARGUMENTS)
		generator.flushDelayed()
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION)
		generator.emit(this, PUSH_LITERAL, indexForTrue)
		val `$after` = Label()
		generator.emitJumpForward(this, `$after`)
		generator.emit(`$absent`)
		generator.emit(this, PUSH_LITERAL, indexForFalse)
		generator.emit(`$after`)
		return wrapState.processAfterPushedArgument(this, generator)
	}

	/**
	 * On the given [InstructionGenerator], output [ParsingOperation]s to handle
	 * this `Optional`'s present and absent cases, invoking the continuation
	 * within each.
	 *
	 * @param generator
	 *   Where to emit parsing instructions.
	 * @param function
	 *   A function that generates code both in the case that the optional
	 *   tokens are present and the case that they are absent.
	 */
	@Suppress("LocalVariableName")
	fun emitInRunThen(generator: InstructionGenerator, function: () -> Unit)
	{
		// emit branch $absent.
		//    emit the inner sequence, which cannot push arguments.
		//    run the continuation.
		//    emit push true.
		//    emit jump $merge.
		// emit $absent.
		//    run the continuation.
		//    emit push false.
		// emit $merge.
		// (the stack now has values pushed by the continuation, followed by the
		//  new boolean, which will need to be permuted into its correct place)
		assert(!hasSectionCheckpoints)
		val needsProgressCheck = sequence.mightBeEmpty(emptyListPhraseType())
		generator.flushDelayed()
		val `$absent` = Label()
		generator.emitBranchForward(this, `$absent`)
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION)
		assert(sequence.yieldersAreReordered !== java.lang.Boolean.TRUE)
		sequence.emitOn(
			emptyListPhraseType(), generator, SHOULD_NOT_HAVE_ARGUMENTS)
		generator.flushDelayed()
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION)
		function()
		generator.flushDelayed()
		generator.emit(this, PUSH_LITERAL, indexForTrue)
		val `$merge` = Label()
		generator.emitJumpForward(this, `$merge`)
		generator.emit(`$absent`)
		function()
		generator.flushDelayed()
		generator.emit(this, PUSH_LITERAL, indexForFalse)
		generator.emit(`$merge`)
	}

	override fun toString() = "${javaClass.simpleName}($sequence)"

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		val literal = arguments!!.next()
		assert(
			literal.isInstanceOf(
				PhraseKind.LITERAL_PHRASE.mostGeneralType()))
		val flag = literal.token().literal().extractBoolean()
		if (flag)
		{
			builder.append('«')
			sequence.printWithArguments(
				Collections.emptyIterator(), builder, indent)
			builder.append("»?")
		}
	}

	override val shouldBeSeparatedOnLeft: Boolean
		// For now.  Eventually we could find out whether there were even
		// any tokens printed by passing an argument iterator.
		get() = true

	override val shouldBeSeparatedOnRight: Boolean
		// For now.  Eventually we could find out whether there were even
		// any tokens printed by passing an argument iterator.
		get() = true

	override fun mightBeEmpty(phraseType: A_Type): Boolean
	{
		// Optional things can be absent.
		return true
	}
}
