/*
 * L2SimpleInstruction.kt
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
@file:Suppress("MemberVisibilityCanBePrivate")

package avail.interpreter.levelTwoSimple

import avail.AvailRuntime
import avail.AvailRuntime.HookType.IMPLICIT_OBSERVE
import avail.AvailRuntime.HookType.READ_UNASSIGNED_VARIABLE
import avail.AvailRuntime.HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.numArgs
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.frameAtPut
import avail.descriptor.functions.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_Function.Companion.optionallyNilOuterVar
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationExceptFrame
import avail.descriptor.functions.ContinuationDescriptor.Companion.createLabelContinuation
import avail.descriptor.functions.FunctionDescriptor.Companion.createExceptOuters
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters1
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters2
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters3
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters4
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Method.Companion.lookupByValuesFromList
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.isAbstractDefinition
import avail.descriptor.methods.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.MethodDefinitionException
import avail.exceptions.MethodDefinitionException.Companion.abstractMethod
import avail.exceptions.MethodDefinitionException.Companion.forwardMethod
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.Primitive.Flag
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2AbstractInstruction
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.StackReifier
import avail.optimizer.jvm.JVMChunk
import avail.performance.Statistic
import avail.performance.StatisticReport.REIFICATIONS
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType.Primitive

/**
 * [L2SimpleInstruction] is the abstract class for a simplified level two
 * instruction set.  It acts as a very lightweight translation of level one
 * nybblecodes.  Its invalidation machinery is managed by [L2Chunk], so it
 * subscribes to method dependencies the same way as for full chunks that are
 * translated to [JVMChunk]s.
 *
 * Very little optimization is performed, at this level.  Type deduction helps
 * eliminate spurious checks that would be necessary if method definitions could
 * be added or removed, but the invalidation mechanism handles that.  Calls can
 * often be statically transformed to simple monomorphic invocation, avoiding
 * the dispatch trees.  When the target is proven to be monomorphic, some
 * primitives can be directly embedded (e.g., [P_InvokeWithTuple]), if they can
 * be proven not to fail, skipping unnecessary type safety checks.  Similarly,
 * unnecessary return type checks can often be omitted as well.
 *
 * There is no register coloring, dead code elimination, special rewriting of
 * most primitives, or reworking into unboxed integer or floating point
 * operations.  Folding is attempted, however, if a monomorphic call indicates
 * it would be a [Primitive] function with the [Flag.CanFold] flag set.
 *
 * Flow is linear, and each instruction is responsible for handling reification,
 * if it can happen.  Boxed register values are maintained in an array, in the
 * same manner as for [L1InstructionStepper].  The current function occupies
 * `register[0]`, then the frame slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class L2SimpleInstruction : L2AbstractInstruction()
{
	/**
	 * Perform this instruction, a single step of an [L2SimpleChunk].  The
	 * mutable [Array] of [AvailObject]s acts as a simple set of registers.
	 * Element 0 is the current function, and the remaining elements correspond
	 * with the continuation slots, should one need to be constructed.
	 */
	abstract fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?

	/**
	 * A previously constructed continuation is being resumed in some way, and
	 * the instruction *just before* the continuation's L2 offset has been asked
	 * to do anything specific to reentering the continuation.  For example, a
	 * method call might be forced to reify the stack, but at resumption time
	 * (i.e., when "returning" into it), it will still need to check the type of
	 * the "returned" value against the expected type.
	 *
	 * Most instructions are not suitable places for reentry.
	 */
	open fun reenter(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier? = throw RuntimeException("Should not reenter here")

	override fun toString() = buildString {
		val cls = this@L2SimpleInstruction::class
		//val cls = ::class.memberProperties.toList()[0].get()
		append(cls.simpleName!!.removePrefix("L2Simple_"))

		val pairs = cls.memberProperties.map {
			it.name to fieldValueToString(
				it.getter.call(this@L2SimpleInstruction))
		}
		if (pairs.any { (_, value) -> '\n' in value }
			|| pairs.sumOf { (_, value) -> value.length } > 50)
		{
			pairs.joinTo(this, ",\n", "(\n", ")") { (a,b) -> "$a=$b" }
		}
		else
		{
			pairs.joinTo(this, ", ", "(", ")") { (a,b) -> "$a=$b" }
		}
	}

	/**
	 * Generate a suitable print representation of the value.
	 */
	fun fieldValueToString(value: Any?): String = when (value)
	{
		null -> "null"
		is Array<*> ->
			value.joinToString(",", "[", "]") { fieldValueToString(it) }
		is AvailObject -> when
		{
			value.isInstanceOf(mostGeneralCompiledCodeType()) ->
				value.methodName.toString()
			value.isInstanceOf(mostGeneralFunctionType()) ->
				value.code().methodName.toString()
			else -> value.toString()
		}
		else -> value.toString()
	}
}

/** Move a constant value into `registers[to]`. */
class L2Simple_MoveConstant(
	val value: AvailObject,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = value
		return null
	}
}

/** Move a value from `registers[from]` into `registers[to]`. */
class L2Simple_Move(
	val from: Int,
	val to: Int
) : L2SimpleInstruction()
{
	init { assert(from != to) }
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = registers[from]
		return null
	}
}

/**
 * Move a value from `registers[from]` into `registers[to]`, while making it
 * immutable.
 */
class L2Simple_MoveAndMakeImmutable(
	val from: Int,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = registers[from].makeImmutable()
		return null
	}
}

/**
 * Get the value of the variable `registers[fromVariable]`, and write it to
 * `registers[stackp]`.
 */
class L2Simple_GetVariable(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val fromVariable: Int
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		try
		{
			registers[stackp] = registers[fromVariable].getValue()
		}
		catch (e: VariableGetException)
		{
			return handleVariableGetException(e, interpreter, registers)
		}
		return null
	}
}

/**
 * Read from the local variable `registers[fromVariable]`, and write the result
 * to `registers[stackp]`.  If the variable is mutable, clear it, otherwise mark
 * the value as immutable.
 */
class L2Simple_GetVariableClearing(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val fromVariable: Int,
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		try
		{
			val variable = registers[fromVariable]
			val value = when (variable.traversed().descriptor().isMutable)
			{
				true -> variable.getValueClearing()
				// Automatically makes the value immutable.
				else -> variable.getValue()
			}
			registers[stackp] = value
		}
		catch (e: VariableGetException)
		{
			return handleVariableGetException(e, interpreter, registers)
		}
		return null
	}
}

/**
 * Get the value of outer number [outerNumber] of the current function, found in
 * `registers[0]`, and write it to `registers[stackp]`.
 */
class L2Simple_GetOuter(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val outerNumber: Int,
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val variable = registers[0].outerVarAt(outerNumber)
		registers[stackp] = try
		{
			variable.getValue()
		}
		catch (e: VariableGetException)
		{
			return handleVariableGetException(e, interpreter, registers)
		}
		return null
	}
}

/**
 * Get the value of outer number [outerNumber] of the current function, found in
 * `registers[0]`, and write it to `registers[stackp]`.  If the variable is
 * mutable, clear that variable, otherwise make the value immutable.
 */
class L2Simple_GetOuterClearing(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val outerNumber: Int,
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val function = registers[0]
		val variable = function.outerVarAt(outerNumber)
		registers[stackp] = try
		{
			if (variable.traversed().descriptor().isMutable)
			{
				variable.getValueClearing().makeImmutable()
			}
			else
			{
				// Automatically makes the value immutable.
				variable.getValue()
			}
		}
		catch (e: VariableGetException)
		{
			return handleVariableGetException(e, interpreter, registers)
		}
		return null
	}
}

/**
 * Read from a literal (module global) [A_Variable], writing its content in
 * `registers[stackp]`.
 */
class L2Simple_GetConstant(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val variable: AvailObject
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		try
		{
			registers[stackp] = variable.getValue()
		}
		catch (e: VariableGetException)
		{
			return handleVariableGetException(e, interpreter, registers)
		}
		return null
	}
}

/**
 * Read a value from `registers[stackp]`, and write it into the [A_Variable]
 * found at `registers[toVariable]`.
 */
class L2Simple_SetVariable(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val toVariable: Int
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		try
		{
			registers[toVariable].setValueNoCheck(registers[stackp])
		}
		catch (e: VariableSetException)
		{
			return handleVariableSetException(
				e,
				registers[toVariable],
				registers[stackp],
				interpreter,
				registers)
		}
		return null
	}
}

/**
 * Read from `registers[stackp]`, and write it into the variable in the current
 * function's outer at index [outerNumber].
 */
class L2Simple_SetOuter(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val outerNumber: Int
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		try
		{
			registers[0].outerVarAt(outerNumber)
				.setValueNoCheck(registers[stackp])
		}
		catch (e: VariableSetException)
		{
			return handleVariableSetException(
				e,
				registers[0].outerVarAt(outerNumber),
				registers[stackp],
				interpreter,
				registers)
		}
		return null
	}
}

/**
 * Read a value from `registers[stackp]`, and write it into the given literal
 * (module global) [A_Variable].
 */
class L2Simple_SetConstant(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val variable: AvailObject
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		try
		{
			variable.setValueNoCheck(registers[stackp])
		}
		catch (e: VariableSetException)
		{
			return handleVariableSetException(
				e,
				variable,
				registers[stackp],
				interpreter,
				registers)
		}
		return null
	}
}

/**
 * Create a function from the constant raw function [code] and one captured
 * (outer) value, found at `registers[to]`.  Write the function back to
 * `registers[to]`.
 */
class L2Simple_CloseFunction1(
	val code: A_RawFunction,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = createWithOuters1(code, registers[to])
		return null
	}
}

/**
 * Create a function from the constant raw function [code] and two captured
 * (outer) values, found at `registers[to]` and `registers[to-1]`.  Write the
 * function back to `registers[to]`.
 */
class L2Simple_CloseFunction2(
	val code: A_RawFunction,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = createWithOuters2(
			code, registers[to], registers[to - 1])
		return null
	}
}

/**
 * Create a function from the constant raw function [code] and three captured
 * (outer) values, found at `registers[to]`, `registers[to-1]`, and
 * `registers[to-2]`.  Write the function back to `registers[to]`.
 */
class L2Simple_CloseFunction3(
	val code: A_RawFunction,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = createWithOuters3(
			code,
			registers[to],
			registers[to - 1],
			registers[to - 2])
		return null
	}
}

/**
 * Create a function from the constant raw function [code] and four captured
 * (outer) values, found at `registers[to]`, `registers[to-1]`,
 * `registers[to-2]`, and `registers[to-3]`.  Write the function back to
 * `registers[to]`.
 */
class L2Simple_CloseFunction4(
	val code: A_RawFunction,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = createWithOuters4(
			code,
			registers[to],
			registers[to - 1],
			registers[to - 2],
			registers[to - 3])
		return null
	}
}

/**
 * Create a function from the constant raw function [code] and the required
 * number of captured (outer) values, as indicated in the [code].  The outer
 * values are found at `registers[to]`, `registers[to-1]`,..`registers[to-N+1]`.
 * Write the function back to `registers[to]`.
 */
class L2Simple_CloseFunctionN(
	val code: A_RawFunction,
	val to: Int
) : L2SimpleInstruction()
{
	val outerCount = code.numOuters

	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val function = createExceptOuters(code, outerCount)
		var outer = to
		for (i in 1 .. outerCount)
		{
			function.outerVarAtPut(i, registers[outer--])
		}
		registers[to] = function
		return null
	}
}

/**
 * Create a one-element tuple <`registers[to]`>, and write it back to
 * `registers[to]`.
 */
class L2Simple_MakeTuple1(
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = tuple(registers[to]) as AvailObject
		return null
	}
}

/**
 * Create a two-element tuple <`registers[to]`, `registers[to-1]`>, and write it
 * back to `registers[to]`.
 */
class L2Simple_MakeTuple2(
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = tuple(
			registers[to],
			registers[to - 1]
		) as AvailObject
		return null
	}
}

/**
 * Create a three-element tuple <`registers[to]`, `registers[to-1]`,
 * `registers[to-2]`>, and write it back to `registers[to]`.
 */
class L2Simple_MakeTuple3(
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = tuple(
			registers[to],
			registers[to - 1],
			registers[to - 2]
		) as AvailObject
		return null
	}
}

/**
 * Create an N-element tuple from `registers[to]`, `registers[to-1]`,...
 * `registers[to-N+1]`, and write it back to `registers[to]`.
 */
class L2Simple_MakeTupleN(
	val tupleSize: Int,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = generateObjectTupleFrom(tupleSize) { i ->
			val value = registers[to - i + 1]
			value
		}
		return null
	}
}

/**
 * Extract the outer value at index [outerNumber] from the current function,
 * found in `registers[0]`, and write it to `registers[to]`.  Make the value
 * immutable.
 */
class L2Simple_PushOuter(
	val outerNumber: Int,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		registers[to] = registers[0].outerVarAt(outerNumber).makeImmutable()
		return null
	}
}

/**
 * Extract the outer value at index [outerNumber] from the current function,
 * found in `registers[0]`, and write it to `registers[to]`.  If the function
 * is mutable, write nil into that outer value slot, otherwise make the result
 * immutable.
 */
class L2Simple_PushLastOuter(
	val outerNumber: Int,
	val to: Int
) : L2SimpleInstruction()
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val function = registers[0]
		val value = function.outerVarAt(outerNumber)
		assert(value.notNil)
		registers[to] = when (function.optionallyNilOuterVar(outerNumber))
		{
			true -> value
			else -> value.makeImmutable()
		}
		return null
	}
}

/**
 * Construct a label for the restarting or exiting the current continuation. The
 * current [pc] isn't strictly needed here, since invalidation can't happen
 * during the reification and label creation, but it's provided to conform to
 * the parent class's protocol.
 *
 * Write the label (an [A_Continuation]) to `registers[stackp]`, and continue
 * execution where it left off.  If the label is later restarted, its caller
 * will be the same as the current virtual continuation, and its arguments will
 * be the same if using [P_RestartContinuation], or the ones provided explicitly
 * if using [P_RestartContinuationWithArguments].
 */
class L2Simple_PushLabel(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val thisChunk = interpreter.chunk as L2SimpleChunk
		val function = registers[0]
		val arguments = (1..function.code().numArgs()).map {
			registers[it].makeImmutable()
		}
		if (interpreter.callerIsReified())
		{
			// Skip the reification step, since the caller is already
			// conveniently reified.
			val label = createLabelContinuation(
				function,
				interpreter.getReifiedContinuation()!!.makeImmutable(),
				thisChunk,
				0, // Indicates a label
				arguments)
			registers[stackp] = label as AvailObject
			return null
		}
		// Slower path.  Reify the caller.  If this is a loop, the next pass's
		// label creation will see the caller has already been reified, and be
		// able to use the fast path.
		return StackReifier(true, reificationBeforeLabelCreationStat) {
			assert(!interpreter.returnNow)
			val caller = interpreter.getReifiedContinuation()!!.makeImmutable()
			val label = createLabelContinuation(
				function,
				caller,
				thisChunk,
				0, // A block can't have both a primitive and a label.
				arguments)
			// Freeze all fields of the new object, including
			// its caller, function, and args.
			label.makeSubobjectsImmutable()

			// Push that label.
			registers[stackp] = label as AvailObject
			val continuation = createContinuationExceptFrame(
				function,
				caller,
				nil,
				pc,
				stackp,
				thisChunk,
				nextOffset)
			for (i in 1 .. liveIndices.size)
			{
				val index = liveIndices[i - 1]
				continuation.frameAtPut(
					i, if (index == 0) nil else registers[index])
			}
			interpreter.setReifiedContinuation(continuation)
			// And now we tell the interpreter to resume the continuation.
			interpreter.function = function
			interpreter.chunk = thisChunk
			interpreter.offset = nextOffset
			interpreter.exitNow = false
			interpreter.returnNow = false
		}
	}

	/**
	 * The original continuation has resumed after a pushLabel.  The label has
	 * already been pushed by the reifier's [StackReifier.postReificationAction]
	 * (inside [step], above) at this point.
	 */
	override fun reenter(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		// Restore the registers from the continuation and pop it.
		val con = interpreter.getReifiedContinuation()!!
		for (i in 1 until registers.size)
		{
			registers[i] = con.frameAt(i)
		}
		interpreter.popContinuation()
		return null
	}

	companion object
	{
		/**
		 * Statistic for when a pushLabel in an L2Simple chunk is forced to
		 * perform reification (so that the label's caller is correct).
		 */
		private val reificationBeforeLabelCreationStat = Statistic(
			REIFICATIONS, "L2Simple reification before label creation")
	}
}

/**
 * Read from the array of [reads] positions in `registers`, and write those
 * values to corresponding [writes] positions in `registers`.  Do all reads
 * before all writes, using temporary storage as needed.
 */
class L2Simple_Permute(
	val reads: Array<Int>,
	val writes: Array<Int>,
) : L2SimpleInstruction()
{
	init { assert(reads.size == writes.size) }

	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		// Permute the stack using a temporary array.
		val temp = Array(reads.size) {
			registers[reads[it]]
		}
		for (i in temp.indices)
		{
			registers[writes[i]] = temp[i]
		}
		return null
	}
}

/**
 * An abstraction for instructions that can trigger reification during their
 * execution.  This includes instructions that invoke functions, create a label,
 * or read/write a variable (which may fail and invoke handler code that may
 * reify).
 */
abstract class L2Simple_AbstractReifiableInstruction
constructor(
	val stackp: Int,
	val pc: Int,
	val nextOffset: Int,
	val liveIndices: Array<Int>
): L2SimpleInstruction()
{
	override fun reenter(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		// The invoke cases are handled in subclasses, so this is a "resume"
		// situation, as for trapped reads or writes of variables.
		if (!interpreter.checkValidity(
				ChunkEntryPoint.TO_RESUME.offsetInDefaultChunk))
		{
			return null
		}
		assert(!interpreter.returnNow && !interpreter.exitNow)
		// Pop the continuation, repopulating the registers.
		val con = interpreter.getReifiedContinuation()!!
		for (i in 1 until registers.size)
		{
			registers[i] = con.frameAt(i)
		}
		interpreter.popContinuation()
		return null
	}

	/**
	 * Create a continuation with the given data.  The previously captured
	 * [liveIndices] indicate which registers to read to
	 * populate the continuation's slots.  A value of `0` indicates that [nil]
	 * should be used instead.
	 */
	protected fun createContinuation(
		caller: A_Continuation,
		registers: Array<AvailObject>,
		thisChunk: L2Chunk,
		offset: Int = nextOffset
	): A_Continuation
	{
		assert(registers.size - 1 == liveIndices.size)
		val continuation = createContinuationExceptFrame(
			registers[0],
			caller,
			nil,
			pc,
			stackp,
			thisChunk,
			offset)
		for (i in 1..liveIndices.size)
		{
			val index = liveIndices[i - 1]
			continuation.frameAtPut(
				i, if (index == 0) nil else registers[index])
		}
		return continuation
	}

	/**
	 * A [VariableGetException] has occurred.  Invoke the
	 * [READ_UNASSIGNED_VARIABLE] hook function, which, because it is ⊥-valued,
	 * can only reify.  Eventually answer that [StackReifier].
	 */
	fun handleVariableGetException(
		e: VariableGetException,
		interpreter: Interpreter,
		registers: Array<AvailObject>
	): StackReifier
	{
		// The variable had no value.
		assert(e.numericCode.equals(
			E_CANNOT_READ_UNASSIGNED_VARIABLE.numericCode()))
		val thisChunk = interpreter.chunk!!
		val unassignedVariableFunction =
			interpreter.runtime[READ_UNASSIGNED_VARIABLE]
		val args = interpreter.argsBuffer
		args.clear()
		val reifier = interpreter.invokeFunction(unassignedVariableFunction)
		// It's bottom-valued, so it must eventually reify, not return.
		if (reifier!!.actuallyReify())
		{
			reifier.pushAction {
				registers[stackp] = bottom as AvailObject
				val continuation = createContinuation(
					it.getReifiedContinuation()!!,
					registers,
					thisChunk)
				it.setReifiedContinuation(continuation)
			}
		}
		return reifier
	}

	/**
	 * A [VariableSetException] has occurred.  Run the implicit-observe handler,
	 * and answer null if it completes, or a suitable [StackReifier] if it
	 * eventually reifies.
	 */
	fun handleVariableSetException(
		e: VariableSetException,
		variable: A_Variable,
		value: AvailObject,
		interpreter: Interpreter,
		registers: Array<AvailObject>
	): StackReifier?
	{
		// The variable had no value.
		assert(e.numericCode.equals(
			E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode()))
		val thisChunk = interpreter.chunk!!
		val args = interpreter.argsBuffer
		args.clear()
		interpreter.argsBuffer.add(
			Interpreter.assignmentFunction() as AvailObject)
		interpreter.argsBuffer.add(
			tuple(variable, value) as AvailObject)
		val reifier =
			interpreter.invokeFunction(interpreter.runtime[IMPLICIT_OBSERVE])
		// It's top-valued, so it *might* reify, or might not.
		if (reifier !== null)
		{
			if (reifier.actuallyReify())
			{
				reifier.pushAction {
					registers[stackp] = TOP.o
					val continuation = createContinuation(
						it.getReifiedContinuation()!!,
						registers,
						thisChunk)
					it.setReifiedContinuation(continuation)
				}
			}
		}
		return reifier
	}
}

/**
 * An abstract class for instructions whose primary purpose is to invoke some
 * other function.  The [expectedType] indicates what type should be pushed on
 * the stack if reification happens.  If [mustCheck] is true, the [expectedType]
 * is also used to check that the eventually returned value is of the correct
 * type.
 */
abstract class L2Simple_AbstractInvokerInstruction
constructor(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	val expectedType: A_Type,
	val mustCheck: Boolean
): L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	/**
	 * A utility for invoking a given function, handling reification and return
	 * type checking as needed.
	 */
	protected fun invocationHelper(
		interpreter: Interpreter,
		registers: Array<AvailObject>,
		function: A_Function
	): StackReifier?
	{
		val thisChunk = interpreter.chunk!!
		//assert(function.code().functionType().acceptsListOfArgValues(
		//	interpreter.argsBuffer))

		var reifier = interpreter.invokeFunction(function)
		interpreter.chunk = thisChunk
		interpreter.function = registers[0]
		if (reifier === null)
		{
			// We returned normally from the call, which is the fast path.
			interpreter.returnNow = false
			val result = interpreter.latestResultOrNull()!!
			if (!mustCheck || result.isInstanceOf(expectedType))
			{
				// Passed the return check, or didn't need to check.  This is
				// the fastest path.
				registers[stackp] = result
				return null
			}
			// Rare - the result did not conform to the expected type.
			val args = interpreter.argsBuffer
			args.clear()
			args.add(function as AvailObject)
			args.add(expectedType as AvailObject)
			val wrappedReturnValue = newVariableWithContentType(ANY.o)
			if (result.notNil) wrappedReturnValue.setValueNoCheck(result)
			args.add(wrappedReturnValue)
			reifier = interpreter.invokeFunction(
				interpreter.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE])
		}
		assert(reifier != null)
		// We're reifying either the original call or the return check failure.
		if (reifier!!.actuallyReify())
		{
			reifier.pushAction {
				registers[stackp] = expectedType as AvailObject
				val continuation = createContinuation(
					it.getReifiedContinuation()!!,
					registers,
					thisChunk)
				it.setReifiedContinuation(continuation)
			}
		}
		return reifier
	}

	/**
	 * This is called when the invocation for this step had to reify, and now
	 * we've finished the actual Avail call and we're attempting to continue
	 * where we left off.  We have to check the returned value and either
	 * capture it on the stack or invoke the result check failure function.
	 *
	 * Note that at this time, only registers[0] has been set up, so if this
	 * reentry succeeds (i.e., the return value satisfies the expectedType), we
	 * need to transfer the top continuation's slots into the registers array
	 * and pop that continuation off the call stack.
	 *
	 * Also, if the current chunk has become invalid, we will alter the
	 * interpreter's current chunk to the [unoptimizedChunk] to indicate this,
	 * allowing the (Kotlin) caller to immediately return to the interpreter
	 * loop for L1 interpretation.
	 */
	override fun reenter(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		if (!interpreter.checkValidity(
				ChunkEntryPoint.TO_RETURN_INTO.offsetInDefaultChunk))
		{
			return null
		}
		val result = interpreter.getLatestResult()
		val con = interpreter.getReifiedContinuation()!!
		val thisChunk = con.levelTwoChunk()
		if (!mustCheck || result.isInstanceOf(expectedType))
		{
			// Passed the return check, or didn't need to check.  This is
			// the fastest path.  Restore the registers from the continuation
			// and pop it.
			assert(con.frameAt(stackp).equals(expectedType))
			for (i in 1 until registers.size)
			{
				registers[i] = con.frameAt(i)
			}
			// Now replace the top-of-stack register with the (correctly typed)
			// returned result.
			registers[stackp] = result
			interpreter.popContinuation()
			return null
		}
		else
		{
			// Rare - the return check failed, so we need to invoke the return
			// check failure function.  It's ⊥-valued, so it won't return, but
			// it will eventually reify.
			val args = interpreter.argsBuffer
			args.clear()
			args.add(registers[0])
			args.add(expectedType as AvailObject)
			val wrappedReturnValue = newVariableWithContentType(ANY.o)
			if (result.notNil) wrappedReturnValue.setValueNoCheck(result)
			args.add(wrappedReturnValue)
			val reifier = interpreter.invokeFunction(
				interpreter.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE])
			assert(reifier != null)
			// We're reifying either the original call or the return check failure.
			if (reifier!!.actuallyReify())
			{
				reifier.pushAction {
					registers[stackp] = expectedType
					val continuation = createContinuation(
						it.getReifiedContinuation()!!,
						registers,
						thisChunk)
					it.setReifiedContinuation(continuation)
				}
			}
			return reifier
		}
	}

	/**
	 * A runtime lookup failed.  Invoke the invalidMessageSendFunction, while
	 * supporting reification inside the call.
	 */
	protected fun handleFailedLookup(
		interpreter: Interpreter,
		args: MutableList<AvailObject>,
		e: MethodDefinitionException,
		registers: Array<AvailObject>,
		bundle: A_Bundle
	): StackReifier
	{
		val thisChunk = interpreter.chunk!!
		val argumentsTuple = tupleFromList(args)
		args.clear()
		args.add(e.errorCode.numericCode() as AvailObject)
		args.add(bundle.bundleMethod as AvailObject)
		args.add(argumentsTuple as AvailObject)
		val reifier = interpreter.invokeFunction(
			interpreter.runtime.invalidMessageSendFunction())!!
		// The function cannot return, so we got a StackReifier back.
		if (reifier.actuallyReify())
		{
			reifier.pushAction {
				registers[stackp] = expectedType as AvailObject
				val continuation = createContinuation(
					it.getReifiedContinuation()!!,
					registers,
					thisChunk)
				it.setReifiedContinuation(continuation)
			}
		}
		return reifier
	}
}

/**
 * Invoke a constant [A_Function], perhaps the result of a lookup that was
 * proven at translation time to be monomorphic.
 */
class L2Simple_Invoke
constructor(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	expectedType: A_Type,
	mustCheck: Boolean,
	val function: A_Function
) : L2Simple_AbstractInvokerInstruction(
	stackp,
	pc,
	nextOffset,
	liveIndices,
	expectedType,
	mustCheck)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val args = interpreter.argsBuffer
		args.clear()
		for (i in stackp downTo stackp - function.code().numArgs() + 1)
		{
			args.add(registers[i])
		}
		return invocationHelper(interpreter, registers, function)
	}
}

/**
 * Extract arguments from `registers[stackp]`, registers[stackp-1]..
 * `registers[stackp-N+1]`, and use them to look up a method definition in a
 * polymorphic method.  If the lookup is successful, invoke that function,
 * otherwise invoke the [AvailRuntime.invalidMessageSendFunction] with suitably
 * packaged arguments and the lookup failure code.
 */
class L2Simple_GeneralCall(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	expectedType: A_Type,
	mustCheck: Boolean,
	val bundle: A_Bundle
) : L2Simple_AbstractInvokerInstruction(
	stackp,
	pc,
	nextOffset,
	liveIndices,
	expectedType,
	mustCheck)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val args = interpreter.argsBuffer
		args.clear()
		for (i in stackp downTo stackp - bundle.numArgs + 1)
		{
			args.add(registers[i])
		}
		val matching: A_Definition = try
		{
			bundle.bundleMethod
				.lookupByValuesFromList(interpreter.argsBuffer)
				.also {
					when
					{
						it.isAbstractDefinition() -> throw abstractMethod()
						it.isForwardDefinition() -> throw forwardMethod()
					}
				}
		}
		catch (e: MethodDefinitionException)
		{
			return handleFailedLookup(interpreter, args, e, registers, bundle)
		}
		// Lookup was successful.  Invoke it, with the arguments that are still
		// in the interpreter's argsBuffer.
		return invocationHelper(interpreter, registers, matching.bodyBlock())
	}
}

/**
 * Perform a lookup using arguments taken from the stack as for
 * [L2Simple_GeneralCall], but where the lookup is forced to look at or above
 * the constraining tuple type.
 */
class L2Simple_SuperCall(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>,
	expectedType: A_Type,
	mustCheck: Boolean,
	val bundle: A_Bundle,
	val superUnionType: A_Type
) : L2Simple_AbstractInvokerInstruction(
	stackp,
	pc,
	nextOffset,
	liveIndices,
	expectedType,
	mustCheck)
{
	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		val args = interpreter.argsBuffer
		args.clear()
		for (i in stackp downTo stackp - bundle.numArgs + 1)
		{
			args.add(registers[i])
		}
		val typesTuple: A_Tuple =
			generateObjectTupleFrom(args.size) { index: Int ->
				val arg = args[index - 1]
				instanceTypeOrMetaOn(arg).typeUnion(
					superUnionType.typeAtIndex(index))
			}
		val matching: A_Definition = try
		{
			bundle.bundleMethod
				.lookupByTypesFromTuple(typesTuple)
				.also {
					when
					{
						it.isAbstractDefinition() -> throw abstractMethod()
						it.isForwardDefinition() -> throw forwardMethod()
					}
				}
		}
		catch (e: MethodDefinitionException)
		{
			return handleFailedLookup(interpreter, args, e, registers, bundle)
		}
		// Lookup was successful.  Invoke it, with the arguments that are still
		// in the interpreter's argsBuffer.
		return invocationHelper(interpreter, registers, matching.bodyBlock())
	}
}

/**
 * Poll to see if an interrupt has been requested.  The bulk of this is handled
 * directly by [L2SimpleExecutableChunk], but reentry still has to be handled
 * here.
 */
class L2Simple_CheckForInterrupt(
	stackp: Int,
	pc: Int,
	nextOffset: Int,
	liveIndices: Array<Int>
) : L2Simple_AbstractReifiableInstruction(
	stackp, pc, nextOffset, liveIndices)
{
	override fun reenter(
		registers: Array<AvailObject>,
		interpreter: Interpreter): StackReifier?
	{
		// The invoke cases are handled in subclasses, so this is a "resume"
		// situation, as for trapped reads or writes of variables.
		if (!interpreter.checkValidity(
				ChunkEntryPoint.TO_RESUME.offsetInDefaultChunk))
		{
			return null
		}
		assert(!interpreter.returnNow && !interpreter.exitNow)
		// Pop the continuation, repopulating the registers.
		val con = interpreter.getReifiedContinuation()!!
		for (i in 1 until registers.size)
		{
			registers[i] = con.frameAt(i)
		}
		interpreter.popContinuation()
		return null
	}

	override fun step(
		registers: Array<AvailObject>,
		interpreter: Interpreter
	): StackReifier?
	{
		if (!interpreter.isInterruptRequested) return null
		// An interrupt has been requested.  Reify and process it.
		val function = registers[0]
		val thisChunk = interpreter.chunk!!
		return StackReifier(true, interruptStatistic) {
			assert(!interpreter.returnNow)
			val caller = interpreter.getReifiedContinuation()!!
			val continuation = createContinuationExceptFrame(
				function,
				caller,
				nil,
				pc,
				stackp,
				thisChunk,
				nextOffset)
			liveIndices.forEachIndexed { zeroIndex, source ->
				continuation.frameAtPut(
					zeroIndex + 1,
					if (source == 0) nil else registers[source])
			}
			interpreter.setReifiedContinuation(continuation)
			interpreter.function = function
			interpreter.chunk = thisChunk
			interpreter.offset = nextOffset
			interpreter.processInterrupt(continuation)
		}
	}

	companion object
	{
		/** Interrupts that happen in []L2SimpleExecutableChunk]s. */
		val interruptStatistic = Statistic(REIFICATIONS, "L2Simple interrupt")
	}
}
