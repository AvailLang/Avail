/*
 * L2_JUMP_IF_KIND_OF_OBJECT.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.isDoubleMethod
import avail.descriptor.representation.A_Number.Companion.isIntMethod
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2SplitCondition.Companion.unboxedIntConditions
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.utility.notNullAnd
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if the value is an instance of the type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_JUMP_IF_KIND_OF_OBJECT(
	var value: L2ReadBoxedOperand,
	var type: L2ReadBoxedOperand,
	@On(SUCCESS) var ifKind: L2PcOperand,
	@On(FAILURE) var ifNotKind: L2PcOperand
): L2ConditionalJump()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(value.registerString())
		append(" ∈ ")
		append(type.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::value, ::type)
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		// Restrict the value to the type along the ifKind branch
		ifKind.manifest()
			.intersectType(value.semanticValue(), type.type().instance)
		type.constantOrNull?.let { constantType ->
			// The type is a constant, so we can exclude it along the ifNotkind
			// path.
			ifNotKind.manifest()
				.subtractType(value.semanticValue(), constantType)
		}
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		if (replaceWithJumpIfPossible(this)) return
		type.constantOrNull?.let { constantType ->
			jumpIfKindOfConstant(
				value,
				constantType,
				ifKind.targetBlock(),
				ifNotKind.targetBlock())
			return
		}
		+this@L2_JUMP_IF_KIND_OF_OBJECT
	}

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		val constantType = type.constantOrNull ?: return emptyList()
		if (!ifKind.targetBlock().isCold)
		{
			// The ifKind target is warm, so allow a split back to a point where
			// the value is known to be of the requested kind.
			val constantTypeWhenInt = constantType.typeIntersection(i32)
			if (!constantTypeWhenInt.isVacuousType)
			{
				addAll(unboxedIntConditions(listOf(value.register())))
			}
			addAll(
				typeRestrictionConditions(
					listOf(value.register()),
					restrictionForType(constantType)))
		}
		if (!ifNotKind.targetBlock().isCold)
		{
			// The ifNotKind target is warm, so allow a split back to a point
			// where the value is known *not* to be an instance.
			addAll(
				typeRestrictionConditions(
					listOf(value.register()),
					restrictionForType(ANY()).minusType(constantType)))
		}
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun JVMTranslator.translateToJVM()
	{
		when
		{
			// See if this is just a test for the i32 range.  The type may have
			// been narrowed, but if intersecting the value's restriction type
			// with i32 yields the same as intersecting the value's restriction
			// type with the test type, then there is no additional narrowing
			// and we can use the isInt method (to avoid the more general and
			// more expensive boundary tests).
			type.constantOrNull.notNullAnd {
				val valueType = value.restriction().type
				isSubtypeOf(i32)
					&& !isEnumeration
					&& valueType.typeIntersection(this).equals(
						valueType.typeIntersection(i32)) } ->
			{
				// :: (value.isInt)
				load(value)
				generateCall(isIntMethod)
			}

			// Same, but for doubles.
			type.constantOrNull.notNullAnd {
				isSubtypeOf(Types.DOUBLE())
					&& !isEnumeration
					&& equals(
					value.restriction()
						.intersectionWithType(Types.DOUBLE()).type) } ->
			{
				// :: (value.isDouble)
				load(value)
				generateCall(isDoubleMethod)
			}

			else ->
			{
				// :: (value.isInstanceOf(type))
				load(value)
				load(type)
				generateCall(A_BasicObject.isInstanceOfMethod)
			}
		}
		// :: if (...) goto isKind;
		// :: else goto isNotKind;
		emitBranch(
			this@L2_JUMP_IF_KIND_OF_OBJECT,
			Opcodes.IFNE,
			ifKind,
			ifNotKind)
	}
}
