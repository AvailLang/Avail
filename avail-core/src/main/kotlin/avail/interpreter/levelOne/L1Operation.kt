/*
 * L1Operation.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.interpreter.levelOne

import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.representation.AbstractDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.variables.VariableDescriptor
import avail.interpreter.levelOne.L1OperandType.IMMEDIATE
import avail.interpreter.levelOne.L1OperandType.LITERAL
import avail.interpreter.levelOne.L1OperandType.LOCAL
import avail.interpreter.levelOne.L1OperandType.OUTER
import avail.interpreter.levelOne.L1Operation.L1_doExtension
import avail.io.NybbleOutputStream

/**
 * An [L1Operation] is encoded within a [ nybblecode
 * stream][A_RawFunction.nybbles] as an opcode followed by operands.  Opcodes less
 * than 16 are encoded as a single nybble, and the others are represented as the
 * [extension][L1_doExtension] nybble (15), followed by the opcode minus 16.
 * This supports up to 31 distinct nybblecodes, the statically most frequently
 * occurring of which should be assigned to the first 15 values (0-14) for
 * compactness.
 *
 * The operands are encoded in such a way that very small values occupy a single
 * nybble, but values up to [Integer.MAX_VALUE] are supported efficiently.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `L1Operation`.  The expected [ordinal][Enum.ordinal] is
 * passed as a cross-check so that each operation's definition shows the
 * ordinal.  The rest of the arguments are the
 * [operand&#32;types][L1OperandType] that this operation expects.
 *
 * @param ordinalCheck
 *   This operation's ordinal.
 * @param operandTypes
 *   This operation's list of [operand&#32;types][L1OperandType].
 */
@Suppress("EnumEntryName")
enum class L1Operation constructor(
	ordinalCheck: Int,
	vararg operandTypes: L1OperandType)
{
	/**
	 * Invoke a method.
	 *
	 * The first operand is an index into the current code's
	 * [literals][A_RawFunction.literalAt], which specifies an [A_Bundle] which
	 * names a [method][MethodDescriptor] that contains a collection of
	 * [method&#32;definitions][MethodDefinitionDescriptor] that might be
	 * invoked.  The arguments are expected to already have been pushed. They
	 * are popped from the stack and the literal specified by the second operand
	 * is pushed. This is the expected type of the send.  When the invoked
	 * method eventually returns, the proposed return value is checked against
	 * the pushed type, and if it agrees then this stack entry is replaced by
	 * the returned value. If it disagrees, some sort of runtime exception
	 * should take place instead.
	 */
	L1_doCall(0, LITERAL, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doCall()
	},

	/**
	 * Push the literal whose index is specified by the operand.
	 */
	L1_doPushLiteral(1, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doPushLiteral()
	},

	/**
	 * Push a local variable -- not its value, but the variable itself.  This
	 * should be the last use of the variable, so erase it from the continuation
	 * at the same time.
	 *
	 * Clearing the variable keeps the variable's reference count from changing,
	 * so it may stay [mutable][AbstractDescriptor.isMutable] if it was before.
	 *
	 * If an argument is specified then push the value, since there is no actual
	 * [variable][VariableDescriptor] to operate on.  Clear the slot of the
	 * continuation reserved for the argument.  Constants are treated like
	 * ordinary local variables, except that they can not be assigned after
	 * their definition, nor can a reference to the constant be taken.
	 */
	L1_doPushLastLocal(2, LOCAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doPushLastLocal()
	},

	/**
	 * Push a local variable -- not its value, but the variable itself.  If an
	 * argument or constant is specified then push the value, since there is no
	 * actual [variable][VariableDescriptor] to operate on.
	 */
	L1_doPushLocal(3, LOCAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doPushLocal()
	},

	/**
	 * Push an outer variable, i.e. a variable lexically captured by the current
	 * function.  This should be the last use of the variable, so clear it from
	 * the function if the function is still mutable.
	 */
	L1_doPushLastOuter(4, OUTER)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doPushLastOuter()
	},

	/**
	 * Create a function from the specified number of pushed outer variables and
	 * the specified literal [compiled&#32;code][CompiledCodeDescriptor].
	 */
	L1_doClose(5, IMMEDIATE, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doClose()
	},

	/**
	 * Pop the stack and write the value into the specified local variable.
	 */
	L1_doSetLocal(6, LOCAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doSetLocal()
	},

	/**
	 * Extract the value from the specified local variable or constant.  If the
	 * variable is mutable, null it out in the continuation.  Raise a suitable
	 * runtime exception if the variable does not have a value.
	 */
	L1_doGetLocalClearing(7, LOCAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doGetLocalClearing()
	},

	/**
	 * Push the specified outer variable of the [function][FunctionDescriptor].
	 */
	L1_doPushOuter(8, OUTER)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doPushOuter()
	},

	/**
	 * Discard the top element of the stack.
	 */
	L1_doPop(9)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doPop()
	},

	/**
	 * Push the current value of the specified outer variable.  The outer
	 * variable is part of the [function][FunctionDescriptor] being executed.
	 * Clear this outer variable if it is mutable.
	 */
	L1_doGetOuterClearing(10, OUTER)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doGetOuterClearing()
	},

	/**
	 * Pop the stack and write it to the specified outer variable of the
	 * [function][FunctionDescriptor].
	 */
	L1_doSetOuter(11, OUTER)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doSetOuter()
	},

	/**
	 * Push the value of the specified local variable or constant.  Make it
	 * immutable, since it may still be needed by subsequent instructions.
	 */
	L1_doGetLocal(12, LOCAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doGetLocal()
	},

	/**
	 * Pop the specified number of elements from the stack and assemble them
	 * into a tuple.  Push the tuple.
	 */
	L1_doMakeTuple(13, IMMEDIATE)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doMakeTuple()
	},

	/**
	 * Push the current value of the specified outer variable of the
	 * [function][FunctionDescriptor].
	 */
	L1_doGetOuter(14, OUTER)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doGetOuter()
	},

	/**
	 * Process an extension nybblecode, which involves consuming the next nybble
	 * and dispatching it as though 16 were added to it.
	 */
	L1_doExtension(15)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1_doExtension()
	},

	/**
	 * Push a continuation just like the current one, such that if it is ever
	 * resumed it will have the same effect as restarting the current one.
	 */
	L1Ext_doPushLabel(16)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doPushLabel()
	},

	/**
	 * Get the value of a [variable][VariableDescriptor] literal. This is used
	 * only to read from module variables.
	 */
	L1Ext_doGetLiteral(17, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doGetLiteral()
	},

	/**
	 * Pop the stack and write the value into a [variable][VariableDescriptor]
	 * literal.  This is used to write to module variables.
	 */
	L1Ext_doSetLiteral(18, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doSetLiteral()
	},

	/**
	 * Duplicate the top stack element (i.e., push another occurrence of the top
	 * of stack}.  Make the object immutable since it now has an additional
	 * reference.
	 */
	L1Ext_doDuplicate(19)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doDuplicate()
	},

	/**
	 * Permute the top N stack elements based on the literal which is an N-tuple
	 * of distinct integers in [1..N] (i.e., a permutation).  The mutability of
	 * the values is unaffected.
	 *
	 * The first pushed value is considered position 1, and the most recently
	 * pushed value (the top of stack) is considered position N.  The algorithm
	 * behaves as though a scratch N-array is available.  The elements of the
	 * stack and of the permutation tuple are examined in lock-step, and each
	 * value is placed into the temporary array at the position indicated in the
	 * permutation tuple.  The entire array is then pushed back on the stack
	 * (starting with the first element of the array).
	 *
	 * As an example, if the nybblecodes have already pushed A, B, and C, in
	 * that order, the permute nybblecode with the tuple &lt;2,3,1&gt; would
	 * transfer A into array slot 2, B into array slot 3, and C into array slot
	 * 1, yielding the array [C,A,B].  These would then replace the original
	 * values as though C, A, and B had been pushed, in that order.
	 */
	L1Ext_doPermute(20, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doPermute()
	},

	/**
	 * Invoke a method with a supercall.
	 *
	 * The first operand is an index into the current code's
	 * [literals][A_RawFunction.literalAt], which specifies a
	 * [message&#32;bundle][MessageBundleDescriptor] that is a particular naming
	 * of a [method][MethodDescriptor] which itself contains a collection of
	 * [method&#32;definitions][MethodDefinitionDescriptor] that might be
	 * invoked.  The stack is expected to contain the top-level arguments, from
	 * which their types will be extracted and assembled into a tuple type,
	 * which itself will undergo a [A_Type.typeUnion] with this instruction's
	 * third operand, a literal tuple type.  The resulting tuple type (the
	 * union) will be used to select the method definition to invoke.
	 *
	 * The second operand specifies a literal which is the expected return type
	 * of the end.  When the invoked method eventually returns, the proposed
	 * return value is checked against the pushed type, and if it agrees then
	 * this stack entry is replaced by the returned value.  If it disagrees, a
	 * runtime exception is thrown instead.
	 *
	 * The third operand specifies a literal which directs the method search.
	 * The union of the tuple type derived from the actual arguments' types and
	 * this literal tuple type provides a tuple type that can be used to select
	 * the method definition that will be invoked.
	 */
	L1Ext_doSuperCall(21, LITERAL, LITERAL, LITERAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doSuperCall()
	},

	/**
	 * Pop the stack, writing the value directly to the current continuation in
	 * the indicated local slot.
	 */
	L1Ext_doSetLocalSlot(22, LOCAL)
	{
		override fun dispatch(operationDispatcher: L1OperationDispatcher) =
			operationDispatcher.L1Ext_doSetSlot()
	};

	/** This operation's collection of [operand types][L1OperandType]. */
	val operandTypes = operandTypes.also {
		assert(ordinalCheck == ordinal)
	}

	private val shortName = name.replace(Regex("L1_do|L1Ext_do"), "")

	fun shortName() = shortName

	/**
	 * Dispatch this operation through an [L1OperationDispatcher].
	 *
	 * @param operationDispatcher
	 *   The [L1OperationDispatcher] that will accept this operation.
	 */
	abstract fun dispatch(operationDispatcher: L1OperationDispatcher)

	/**
	 * Write this operation to a [NybbleOutputStream].  Do not output operands.
	 *
	 * @param stream
	 *   The [NybbleOutputStream] on which to write the nybble(s)
	 *   representing this operation.
	 */
	fun writeTo(stream: NybbleOutputStream)
	{
		val nybble = ordinal
		if (nybble < 16)
		{
			stream.write(nybble)
		}
		else
		{
			assert(nybble < 32)
			stream.write(L1_doExtension.ordinal)
			stream.write(nybble - 16)
		}
	}

	companion object
	{
		/** An array of all [L1Operation] enumeration values. */
		private val all = values()

		/**
		 * Answer the `L1Operation` enumeration value having the given ordinal.
		 * Must be in bounds of the defined ordinals.
		 *
		 * @param ordinal
		 *   The ordinal of the `L1Operation` to look up.
		 * @return
		 *  The looked up `L1Operation`.
		 */
		fun lookup(ordinal: Int): L1Operation = all[ordinal]
	}
}
