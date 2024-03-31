/*
 * L2_MOVE.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.new.InstructionLayout
import avail.interpreter.levelTwo.new.L2NewInstruction
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.Strings.truncateTo
import avail.utility.cast
import avail.utility.notNullAnd
import org.objectweb.asm.MethodVisitor

/**
 * Move an [AvailObject] from the source to the destination.  The [L2Generator]
 * creates more moves than are strictly necessary, but various mechanisms
 * cooperate to remove redundant inter-register moves.
 *
 * The object being moved is not made immutable by this operation, as that is
 * the responsibility of the [L2_MAKE_IMMUTABLE] operation, injected at
 * necessary points during very late analysis.
 *
 * @param K
 *   The [RegisterKind] of [L2Register] to be moved.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property kind
 *   The kind of data moved by this operation.
 * @constructor
 * Construct an `L2_MOVE` operation.
 *
 * @param kind
 *   The [RegisterKind] serviced by this operation.
 * @param theNamedOperandTypes
 *   An array of [L2NamedOperandType]s that describe this particular
 *   L2Operation, allowing it to be specialized by register type.
 */
abstract class L2_MOVE<K: RegisterKind<K>>
constructor(
	val kind: K,
) : L2NewInstruction()
{
	/**
	 * The source of this move.  This is a member function instead of a field,
	 * to simplify the reflection logic in [InstructionLayout].
	 */
	abstract fun source(): L2ReadOperand<K>

	/**
	 * The destination of this move.  This is a member function instead of a
	 * field, to simplify the reflection logic in [InstructionLayout].
	 */
	abstract fun destination(): L2WriteOperand<K>

	override fun cloneFor(block: L2BasicBlock): L2_MOVE<K> =
		super.cloneFor(block).cast()

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Ensure the new write ends up in the same synonym as the source.
		source().instructionWasAdded(manifest)
		destination().instructionWasAddedForMove(
			source().semanticValue(), manifest)
	}

	/**
	 * Omit the move if the source and destination registers have the same
	 * color (finalIndex).
	 */
	override val shouldEmit: Boolean get() =
		source().finalIndex() != destination().finalIndex()

	override fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean)->Unit)
	{
		val source = source()
		val destination = destination()
		renderPreamble(builder)
		builder.append(' ')
		if (destination.restriction().constantOrNull.notNullAnd { isNil })
		{
			// Assume propagation of nil into a new semantic value will be
			// both successful and uninteresting.
			val tempDest = StringBuilder()
			destination.appendWithWarningsTo(tempDest, 0) { }
			builder.append(tempDest.toString().truncateTo(30))
			builder.append(" ← ")
			val tempSource = StringBuilder()
			source.appendWithWarningsTo(tempSource, 0) { }
			builder.append(tempSource.toString().truncateTo(20))
		}
		else
		{
			destination.appendWithWarningsTo(builder, 0, warningStyleChange)
			builder.append(" ← ")
			source.appendWithWarningsTo(builder, 0, warningStyleChange)
		}
	}

	override fun toString(): String = name

	override fun extractTupleElement(
		tupleReg: L2ReadBoxedOperand,
		index: Int,
		write: L2WriteBoxedOperand,
		generator: L2Generator)
	{
		generator.extractTupleElement(source().cast(), index, write)
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		val source = source()
		val destination = destination()
		val manifest = regenerator.currentManifest
		val restriction = manifest.restrictionFor(source.semanticValue())
		val newDestination = kind.createWrite(
			regenerator::nextUnique,
			destination.semanticValues(),
			restriction,
			destination.register())
		val clone = clone()
		clone.layout.updateOperands(this) { operand ->
			if (operand == source) source
			else newDestination
		}
		regenerator.addInstruction(clone)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		assert(source().register() != destination().register()) {
			"vacuous move should have been skipped by shouldEmit."
		}
		// :: destination = source;
		translator.load(method, source().register())
		translator.store(method, destination().register())
	}
}

class L2_MOVE_BOXED
constructor(
	var source: L2ReadBoxedOperand,
	var destination: L2WriteBoxedOperand
): L2_MOVE<BOXED_KIND>(BOXED_KIND)
{
	override fun source(): L2ReadBoxedOperand = source

	override fun destination(): L2WriteBoxedOperand = destination

	override val constantCode: A_RawFunction?
		get() = source().definition().instruction.constantCode
}

class L2_MOVE_INT
constructor(
	var source: L2ReadIntOperand,
	var destination: L2WriteIntOperand
): L2_MOVE<INTEGER_KIND>(INTEGER_KIND)
{
	override fun source(): L2ReadIntOperand = source

	override fun destination(): L2WriteIntOperand = destination
}

class L2_MOVE_FLOAT
constructor(
	var source: L2ReadFloatOperand,
	var destination: L2WriteFloatOperand
): L2_MOVE<FLOAT_KIND>(FLOAT_KIND)
{
	override fun source(): L2ReadFloatOperand = source

	override fun destination(): L2WriteFloatOperand = destination
}
