/*
 * L2_LOOKUP_BY_VALUES.kt
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

import avail.descriptor.representation.A_Atom.Companion.atomName
import avail.descriptor.representation.A_Bundle
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Bundle.Companion.message
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Method.Companion.lookupByValuesFromList
import avail.descriptor.representation.A_RawFunction.Companion.encounteredFallbackLookup
import avail.descriptor.representation.A_RawFunction.Companion.lookupStat
import avail.descriptor.representation.A_Sendable.Companion.bodyBlock
import avail.descriptor.representation.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.A_Type.Companion.typeUnion
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.sets.SetDescriptor.Companion.toSet
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.exceptions.AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_FORWARD_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_NO_METHOD
import avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.util.logging.Level

/**
 * Look up the method to invoke. Use the provided vector of arguments to
 * perform a polymorphic lookup. Write the resulting function into the
 * specified destination register. If the lookup fails, then branch to
 * [ifLookupFailed].
 *
 * @constructor
 * Build the instruction.
 *
 * @property messageBundle
 *   The [A_Bundle] in which to look up a method definition.
 * @property arguments
 *   The arguments supplied ot the lookup site.
 * @property trackForReoptimzation
 *   If true, generate code that calls [encounteredFallbackLookup] for the
 *   calling raw function, eventually leading to reoptimization of the chunk.
 *   This is set to false when the complexity of the call indicates the dispatch
 *   should not be inlined, and any attempt to track it would be wasted effort,
 *   since it would be reoptimized into the same code.
 * @property lookedUpFunction
 *   Where to write the looked up function if successful.
 * @property ifLookupSucceeded
 *   Where to jump if successful.
 * @property ifLookupFailed
 *   Where to jump if the lookup was unsuccessful.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_LOOKUP_BY_VALUES
constructor(
	var messageBundle: L2ConstantOperand,
	var arguments: L2ReadBoxedVectorOperand,
	var trackForReoptimzation: L2ArbitraryConstantOperand<Boolean>,
	@On(SUCCESS) var lookedUpFunction: L2WriteBoxedOperand,
	@On(SUCCESS) var ifLookupSucceeded: L2PcOperand,
	@On(FAILURE) var ifLookupFailed: L2PcOperand
) : L2ControlFlowInstruction()
{
	override val hasSideEffect get() = true

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)

		// If the lookup succeeds, the functionReg will be set, and we can also
		// conclude that the arguments satisfied at least one of the found
		// function types.
		val successManifest = ifLookupSucceeded.manifest()
		successManifest.setRestriction(
			lookedUpFunction.pickSemanticValue(),
			lookedUpFunction.restriction())
		// The function type should be an enumeration, so we know that each
		// argument satisfied at least one of the functions' corresponding
		// argument types.
		val arguments = arguments.elements
		val functionType = lookedUpFunction.restriction().type
		if (functionType.isEnumeration)
		{
			val numArgs = arguments.size
			val functions: Set<A_Function> = toSet(functionType.instances)
			val argumentTupleUnionType =
				functions.fold(bottom) { union, function ->
					union.typeUnion(
						function.code().functionType().argsTupleType)
				}
			for (i in 1 .. numArgs)
			{
				val argumentUnion = argumentTupleUnionType.typeAtIndex(i)
				val argument = arguments[i - 1]
				val semanticValue = argument.semanticValue()
				val intersection =
					successManifest.restrictionFor(semanticValue)
						.intersectionWithType(argumentUnion)
				if (intersection.isImpossible)
				{
					// We just discovered statically that the lookup can't
					// actually succeed.  Simply don't strengthen the argument,
					// and continue generating code that's not really reachable.
				}
				else if (!argument.isConstantRead)
				{
					successManifest.setRestriction(semanticValue, intersection)
				}
			}
			// If only one argument wasn't strongly typed enough to prove
			// statically, we could subtract the functions' argument types from
			// that argument register's restriction.  We won't bother here,
			// since this is the failed slow lookup case.
		}
	}

	/** Lookups must not clobber the arguments. */
	override val readsThatMightDestroy: List<L2ReadBoxedOperand>
		get() = emptyList()

	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// :: interpreter
		loadLiteralObject(messageBundle.constant)
		// :: interpreter, bundle
		objectArray(arguments.elements, AvailObject::class.java)
		// :: interpreter, bundle, argsArray
		if (trackForReoptimzation.constant)
		{
			generateCall(lookupWithTrackingMethod)
		}
		else
		{
			generateCall(lookupMethod)
		}

		// :: function?
		method.visitInsn(Opcodes.DUP)
		// :: function?, function?
		val ifFound = Label()
		method.visitJumpInsn(Opcodes.IFNONNULL, ifFound)

		// The function was null, indicating a lookup failure.
		// :: null
		method.visitInsn(Opcodes.POP)
		// ::
		jump(ifLookupFailed)

		method.visitLabel(ifFound)
		// The function was not null, indicating a lookup success.
		// :: function
		store(lookedUpFunction.register())
		// ::
		jumpOrFallThrough(ifLookupSucceeded)
	}

	companion object
	{
		/**
		 * The error codes that can be produced by a failed lookup.
		 */
		@JvmField
		val lookupErrorsType: A_Type =
			enumerationWith(set(
				E_NO_METHOD,
				E_NO_METHOD_DEFINITION,
				E_AMBIGUOUS_METHOD_DEFINITION,
				E_ABSTRACT_METHOD_DEFINITION,
				E_FORWARD_METHOD_DEFINITION))

		/**
		 * Perform the lookup.  Answer the looked-up function if found and
		 * unique, otherwise `null`.  This is invoked by fallback dispatch logic
		 * in L2 chunks.
		 *
		 * @param interpreter
		 *   The [Interpreter].
		 * @param bundle
		 *   The [A_Bundle].
		 * @param values
		 *   The [values][AvailObject] for the lookup.
		 * @return
		 *   The unique [function][A_Function], or `null`.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun lookup(
			interpreter: Interpreter,
			bundle: A_Bundle,
			values: Array<AvailObject>
		): A_Function?
		{
			if (Interpreter.debugL2)
			{
				log(
					Interpreter.loggerDebugL2,
					Level.FINER,
					"{0}Lookup {1}",
					interpreter.debugModeString,
					bundle.message.atomName)
			}
			val definitionToCall = bundle.bundleMethod.lookupByValuesFromList(
				listOf(*values),
				interpreter.function?.code()?.lookupStat)
			if (!definitionToCall.isMethodDefinition()) return null
			return definitionToCall.bodyBlock()
		}

		/**
		 * Perform the lookup.  Answer the looked-up function if found and
		 * unique, otherwise `null`.  This is invoked by fallback dispatch logic
		 * in L2 chunks.
		 *
		 * @param interpreter
		 *   The [Interpreter].
		 * @param bundle
		 *   The [A_Bundle].
		 * @param values
		 *   The [values][AvailObject] for the lookup.
		 * @return
		 *   The unique [function][A_Function], or `null`.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun lookupWithTracking(
			interpreter: Interpreter,
			bundle: A_Bundle,
			values: Array<AvailObject>
		): A_Function?
		{
			if (Interpreter.debugL2)
			{
				log(
					Interpreter.loggerDebugL2,
					Level.FINER,
					"{0}Lookup {1}",
					interpreter.debugModeString,
					bundle.message.atomName)
			}
			val definitionToCall = bundle.bundleMethod.lookupByValuesFromList(
				listOf(*values),
				interpreter.function?.code()?.lookupStat)
			if (!definitionToCall.isMethodDefinition()) return null
			interpreter.chunk!!.code!!.encounteredFallbackLookup()
			return definitionToCall.bodyBlock()
		}

		/**
		 * The [CheckedMethod] for [lookup].
		 */
		private val lookupMethod = staticMethod(
			L2_LOOKUP_BY_VALUES::class.java,
			::lookup.name,
			A_Function::class.java,
			Interpreter::class.java,
			A_Bundle::class.java,
			Array<AvailObject>::class.java)

		/**
		 * The [CheckedMethod] for [lookupWithTracking].
		 */
		private val lookupWithTrackingMethod = staticMethod(
			L2_LOOKUP_BY_VALUES::class.java,
			::lookupWithTracking.name,
			A_Function::class.java,
			Interpreter::class.java,
			A_Bundle::class.java,
			Array<AvailObject>::class.java)
	}
}
