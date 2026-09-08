/*
 * RegisterKind.kt
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
package avail.interpreter.levelTwo.register

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.extractDouble
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_BOXED
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_FLOAT
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_INT
import avail.interpreter.levelTwo.operation.L2_MOVE_FLOAT
import avail.interpreter.levelTwo.operation.L2_MOVE_INT
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_PHI_BOXED
import avail.interpreter.levelTwo.operation.L2_PHI_FLOAT
import avail.interpreter.levelTwo.operation.L2_PHI_INT
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.manifest.L2ValueManifest.Representation
import avail.optimizer.manifest.L2ValueManifest.Representation.Companion.emptyBoxedRepresentation
import avail.optimizer.manifest.L2ValueManifest.Representation.Companion.emptyFloatRepresentation
import avail.optimizer.manifest.L2ValueManifest.Representation.Companion.emptyIntRepresentation
import avail.optimizer.manifest.L2ValueManifest.ValueState
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * One of the kinds of registers that Level Two supports.
 *
 * @property kindName
 *   The descriptive name of this register kind.
 * @property prefix
 *   The prefix to use for registers of this kind.
 * @property jvmTypeString
 *    The JVM [Type] string.
 * @property jvmLoadInstruction
 *   The JVM instruction that loads a register of this kind.
 * @property jvmStoreInstruction
 *   The JVM instruction for storing.
 *
 * @constructor
 * Create an instance of the enum.
 *
 * @param Self
 *   The receiver's statically determinable type.
 * @param ordinal
 *   A unique [Int] for each instance.
 * @param kindName
 *   A descriptive name for this kind of register.
 * @param prefix
 *   The prefix to use when naming registers of this kind.
 * @param jvmTypeString
 *   The canonical [String] used to identify this [Type] of register to the
 *   JVM.
 * @param jvmLoadInstruction
 *   The JVM instruction for loading.
 * @param jvmStoreInstruction
 *   The JVM instruction for storing.
 */
sealed class RegisterKind<Self : RegisterKind<Self>>
constructor (
	val ordinal: Int,
	val kindName: String,
	val prefix: String,
	val jvmTypeString: String,
	val jvmLoadInstruction: Int,
	val jvmStoreInstruction: Int)
{
	/**
	 * Answer a suitable [L2ReadOperand] for extracting the indicated
	 * [L2SemanticValue] of this kind.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to consume via an [L2ReadOperand].
	 * @param restriction
	 *   The [TypeRestriction] relevant to this read.
	 * @param register
	 *   The earliest known defining [RegisterKind] of the [L2SemanticValue].
	 * @return
	 *   The new [L2ReadOperand].
	 */
	abstract fun readOperand(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction,
		register: L2Register<Self>? = null
	): L2ReadOperand<Self>

	/**
	 * Synthesize an [L2ReadOperand] of the appropriately strengthened [Self]
	 * kind of register.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] being read.2
	 * @param manifest
	 *   The [L2ValueManifest] from which to extract the semantic value.
	 */
	abstract fun createRead(
		semanticValue: L2SemanticValue,
		manifest: L2ValueManifest
	): L2ReadOperand<Self>

	/**
	 * Synthesize an [L2WriteOperand] of the appropriately strengthened [Self]
	 * kind of register.
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to populate.
	 * @param restriction
	 *   The [TypeRestriction] that the stored values will satisfy.
	 * @return
	 *   A new [L2WriteOperand] of the appropriate [Self] kind of register.
	 */
	abstract fun createWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<Self>? = null
	): L2WriteOperand<Self>

	/**
	 * Create a register of this kind with the given unique id.
	 *
	 * @param id
	 *   The unique id number for the new [L2Register].
	 */
	abstract fun createRegister(id: Int): L2Register<Self>

	/**
	 * Create an [L2ReadVectorOperand] suitable for the [RegisterKind].
	 *
	 * @param elements
	 *   A [List] of [L2ReadOperand]s, strengthened to the [Self] kind of
	 *   register.
	 * @return
	 *   An [L2ReadVectorOperand] for register kind [Self].
	 */
	abstract fun createVector(
		elements: List<L2ReadOperand<Self>>
	): L2ReadVectorOperand<L2ReadOperand<Self>>

	/**
	 * Synthesize an [L2_MOVE] instruction for this [RegisterKind].
	 *
	 * @param source
	 *   An [L2ReadOperand] supplying the value.
	 * @param destination
	 *   An [L2WriteOperand] accepting the value.
	 */
	abstract fun move(
		source: L2ReadOperand<Self>,
		destination: L2WriteOperand<Self>
	): L2_MOVE<Self>

	/**
	 * Synthesize an [L2_MOVE] instruction for this [RegisterKind], but delaying
	 * the check of the [RegisterKind] to runtime.
	 *
	 * @param source
	 *   An [L2SemanticValue] supplying the value.
	 * @param destinations
	 *   The [L2SemanticValue]s to write.
	 * @param manifest
	 *   The current [L2ValueManifest].
	 * @param restriction
	 *   The [TypeRestriction] indicating what type of value is being moved.
	 */
	fun dynamicMove(
		source: L2SemanticValue,
		destinations: Set<L2SemanticValue>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction
	): L2_MOVE<Self>
	{
		return move(
			createRead(source, manifest),
			createWrite(destinations, restriction))
	}

	/**
	 * Return a [L2_MOVE_CONSTANT] of this kind, unboxing the [boxedValue] now
	 * if necessary.
	 *
	 * @param boxedValue
	 *   An [AvailObject] supplying the boxed version of the constant to move.
	 */
	abstract fun moveConstant(
		boxedValue: A_BasicObject,
		destinations: Iterable<L2SemanticValue>
	): L2_MOVE_CONSTANT<*, Self>

	/**
	 * Synthesize a suitable [L2_MOVE_CONSTANT] if the value is not already in a
	 * register of this kind, and answer an [L2ReadOperand] that extracts it.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] on which to write instructions.
	 * @param boxedValue
	 *   An [AvailObject] supplying the boxed version of the value to move.
	 * @return
	 *   An [L2ReadOperand] that retrieves the constant value.
	 */
	abstract fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadOperand<Self>

	abstract fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<Self>>,
		destination: L2WriteOperand<Self>
	): L2_PHI<Self>

	/**
	 * The [Representation] that stands for a value not being held in a register
	 * of this kind at all.
	 *
	 * Absence is a value rather than a `null`, so that asking a [ValueState]
	 * what it knows about a kind always answers something usable.  It is a
	 * getter rather than a stored property to keep this object's initialization
	 * from depending on [Representation]'s, which depends on this object in
	 * turn.
	 */
	abstract val emptyRepresentation: Representation<Self>

	/**
	 * Answer the given [ValueState]'s [Representation] for this kind, which is
	 * [RegisterKind.emptyRepresentation] if the value is not held in a register
	 * of this kind.
	 *
	 * A [ValueState] keeps a separate slot per kind, so reaching the right one
	 * is a three-way choice.  Making it here rather than in the [ValueState]
	 * keeps the choice in the one place that already knows the answer, and
	 * keeps it typed: the caller gets a [Representation] of *this* kind, with
	 * no cast.
	 *
	 * @param state
	 *   The [ValueState] to interrogate.
	 * @return
	 *   That state's [Representation] for this kind.
	 */
	abstract fun representationIn(state: ValueState): Representation<Self>

	/**
	 * Answer a [ValueState] holding the given [Representation] as its
	 * representation for this kind.
	 *
	 * @param members
	 *   The canonical, boxed [L2SemanticValue]s naming the value.
	 * @param restriction
	 *   The [TypeRestriction] bounding it.
	 * @param representation
	 *   This kind's [Representation] of it, which is [emptyRepresentation] if it
	 *   is not held in a register of this kind.
	 * @param otherKinds
	 *   The [ValueState] to take the *other* kinds' representations from, or
	 *   `null` when building a record for a value that is new to a manifest and
	 *   therefore held in this kind alone.
	 * @return
	 *   The new [ValueState].
	 */
	abstract fun stateWith(
		members: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		representation: Representation<Self>,
		otherKinds: ValueState?
	): ValueState

	abstract fun JVMTranslator.jvmLoadConstant(
		constant: AvailObject)

	companion object
	{
		/** Don't modify this array. */
		val all: Array<RegisterKind<*>> = arrayOf(
			BOXED_KIND,
			INTEGER_KIND,
			FLOAT_KIND)

		init
		{
			assert(all.indices.all { all[it].ordinal == it })
		}
	}
}


/**
 * The kind of register that holds an [AvailObject].
 */
object BOXED_KIND : RegisterKind<BOXED_KIND>(
	ordinal = 0,
	kindName = "boxed",
	prefix = "r",
	jvmTypeString = Type.getDescriptor(AvailObject::class.java),
	jvmLoadInstruction = Opcodes.ALOAD,
	jvmStoreInstruction = Opcodes.ASTORE)
{
	override fun readOperand(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction,
		register: L2Register<BOXED_KIND>?
	) = L2ReadBoxedOperand(semanticValue, restriction, register)

	override fun createRead(
		semanticValue: L2SemanticValue,
		manifest: L2ValueManifest
	): L2ReadBoxedOperand
	{
		val restriction = manifest.restrictionFor(semanticValue)
		return L2ReadBoxedOperand(semanticValue, restriction)
	}

	override fun createWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<BOXED_KIND>?
	) = L2WriteBoxedOperand(semanticValues, restriction, forceRegister)

	override fun createRegister(id: Int) = L2BoxedRegister(id)

	override fun createVector(
		elements: List<L2ReadOperand<BOXED_KIND>>
	) = L2ReadBoxedVectorOperand(elements.cast())

	override fun move(
		source: L2ReadOperand<BOXED_KIND>,
		destination: L2WriteOperand<BOXED_KIND>
	) = L2_MOVE_BOXED(source.cast(), destination.cast())

	override fun moveConstant(
		boxedValue: A_BasicObject,
		destinations: Iterable<L2SemanticValue>
	) = L2_MOVE_CONSTANT_BOXED(
		L2ConstantOperand(boxedValue),
		L2WriteBoxedOperand(
			destinations.toSet(),
			restrictionForConstant(boxedValue)))

	override fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadBoxedOperand = generator.boxedConstant(boxedValue)

	override fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<BOXED_KIND>>,
		destination: L2WriteOperand<BOXED_KIND>
	) = L2_PHI_BOXED(sources.cast(), destination.cast())

	override val emptyRepresentation get() = emptyBoxedRepresentation

	override fun representationIn(
		state: ValueState
	): Representation<BOXED_KIND> = state.boxedRepresentation

	override fun stateWith(
		members: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		representation: Representation<BOXED_KIND>,
		otherKinds: ValueState?
	) = ValueState(
		members,
		restriction,
		representation,
		otherKinds?.intRepresentation ?: emptyIntRepresentation,
		otherKinds?.floatRepresentation ?: emptyFloatRepresentation)

	override fun JVMTranslator.jvmLoadConstant(
		constant: AvailObject)
	{
		loadLiteralObject(constant)
	}
}

/**
 * The kind of register that holds an [Int].
 */
object INTEGER_KIND : RegisterKind<INTEGER_KIND>(
	ordinal = 1,
	kindName = "int",
	prefix = "i",
	jvmTypeString = Type.INT_TYPE.descriptor,
	jvmLoadInstruction = Opcodes.ILOAD,
	jvmStoreInstruction = Opcodes.ISTORE)
{
	override fun readOperand(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction,
		register: L2Register<INTEGER_KIND>?
	) = L2ReadIntOperand(semanticValue, restriction, register)

	override fun createRead(
		semanticValue: L2SemanticValue,
		manifest: L2ValueManifest
	): L2ReadIntOperand
	{
		val restriction = manifest.restrictionFor(semanticValue)
		return L2ReadIntOperand(semanticValue, restriction)
	}

	override fun createWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<INTEGER_KIND>?
	): L2WriteIntOperand
	{
		return L2WriteIntOperand(semanticValues, restriction, forceRegister)
	}

	override fun createRegister(id: Int) = L2IntRegister(id)

	override fun createVector(
		elements: List<L2ReadOperand<INTEGER_KIND>>
	) = L2ReadIntVectorOperand(elements.cast())

	override fun move(
		source: L2ReadOperand<INTEGER_KIND>,
		destination: L2WriteOperand<INTEGER_KIND>
	) = L2_MOVE_INT(source.cast(), destination.cast())

	override fun moveConstant(
		boxedValue: A_BasicObject,
		destinations: Iterable<L2SemanticValue>
	) = L2_MOVE_CONSTANT_INT(
		L2IntImmediateOperand((boxedValue as AvailObject).extractInt),
		L2WriteIntOperand(
			destinations.toSet(),
			restrictionForConstant(boxedValue)))

	override fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadIntOperand = generator.unboxedIntConstant(boxedValue.extractInt)

	override fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<INTEGER_KIND>>,
		destination: L2WriteOperand<INTEGER_KIND>
	) = L2_PHI_INT(sources.cast(), destination.cast())

	override val emptyRepresentation get() = emptyIntRepresentation

	override fun representationIn(
		state: ValueState
	): Representation<INTEGER_KIND> = state.intRepresentation

	override fun stateWith(
		members: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		representation: Representation<INTEGER_KIND>,
		otherKinds: ValueState?
	) = ValueState(
		members,
		restriction,
		otherKinds?.boxedRepresentation ?: emptyBoxedRepresentation,
		representation,
		otherKinds?.floatRepresentation ?: emptyFloatRepresentation)

	override fun JVMTranslator.jvmLoadConstant(
		constant: AvailObject)
	{
		intConstant(constant.extractInt)
	}
}

/**
 * The kind of register that holds a `double`.
 */
object FLOAT_KIND : RegisterKind<FLOAT_KIND>(
	ordinal = 2,
	kindName = "float",
	prefix = "f",
	jvmTypeString = Type.DOUBLE_TYPE.descriptor,
	jvmLoadInstruction = Opcodes.DLOAD,
	jvmStoreInstruction = Opcodes.DSTORE)
{
	override fun readOperand(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction,
		register: L2Register<FLOAT_KIND>?
	) = L2ReadFloatOperand(semanticValue, restriction, register)

	override fun createRead(
		semanticValue: L2SemanticValue,
		manifest: L2ValueManifest
	): L2ReadFloatOperand
	{
		val restriction = manifest.restrictionFor(semanticValue)
		return L2ReadFloatOperand(semanticValue, restriction)
	}

	override fun createWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<FLOAT_KIND>?
	): L2WriteFloatOperand
	{
		return L2WriteFloatOperand(semanticValues, restriction, forceRegister)
	}

	override fun createRegister(id: Int) = L2FloatRegister(id)

	override fun createVector(
		elements: List<L2ReadOperand<FLOAT_KIND>>
	) = L2ReadFloatVectorOperand(elements.cast())

	override fun move(
		source: L2ReadOperand<FLOAT_KIND>,
		destination: L2WriteOperand<FLOAT_KIND>
	) = L2_MOVE_FLOAT(source.cast(), destination.cast())

	override fun moveConstant(
		boxedValue: A_BasicObject,
		destinations: Iterable<L2SemanticValue>
	) = L2_MOVE_CONSTANT_FLOAT(
		L2FloatImmediateOperand((boxedValue as AvailObject).extractDouble),
		L2WriteFloatOperand(
			destinations.toSet(),
			restrictionForConstant(boxedValue)))

	override fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadFloatOperand =
		generator.unboxedFloatConstant(boxedValue.extractDouble)

	override fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<FLOAT_KIND>>,
		destination: L2WriteOperand<FLOAT_KIND>
	) = L2_PHI_FLOAT(sources.cast(), destination.cast())

	override val emptyRepresentation get() = emptyFloatRepresentation

	override fun representationIn(
		state: ValueState
	): Representation<FLOAT_KIND> = state.floatRepresentation

	override fun stateWith(
		members: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		representation: Representation<FLOAT_KIND>,
		otherKinds: ValueState?
	) = ValueState(
		members,
		restriction,
		otherKinds?.boxedRepresentation ?: emptyBoxedRepresentation,
		otherKinds?.intRepresentation ?: emptyIntRepresentation,
		representation)

	override fun JVMTranslator.jvmLoadConstant(
		constant: AvailObject)
	{
		doubleConstant(constant.extractDouble)
	}
}
