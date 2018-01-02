/**
 * A_RawFunction.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.optimizer.L2Translator;
import com.avail.performance.Statistic;
import com.avail.utility.MutableInt;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;

/**
 * {@code A_RawFunction} is an interface that specifies the operations specific
 * to {@linkplain CompiledCodeDescriptor function implementations} in Avail.
 *
 * <p>An {@link A_Function} refers to its raw function, plus any outer values
 * capture during function {@link L1Operation#L1_doClose closure}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_RawFunction
extends A_BasicObject
{
	/**
	 * Answer the {@linkplain A_Type type} of the {@code index}-th local
	 * constant.
	 *
	 * @param index
	 *        The one-based ordinal of the desired local constant.
	 * @return The requested type.
	 */
	A_Type constantTypeAt (int index);

	/**
	 * Set the countdown until reoptimization by the {@linkplain L2Translator
	 * Level Two translator}.
	 *
	 * @param value
	 *        The countdown until reoptimization.
	 */
	void countdownToReoptimize (int value);

	/**
	 * Atomically decrement the countdown to reoptimization by the {@linkplain
	 * L2Translator Level Two translator}. If the count reaches zero
	 * ({@code 0}), then lock this raw function, thereby blocking concurrent
	 * applications of {@linkplain A_Function functions} derived from this raw
	 * function, and then evaluate the argument in order to effect
	 * reoptimization.
	 *
	 * @param continuation
	 *        The {@linkplain Continuation0 continuation} responsible for
	 *        reoptimizing this function implementation in the event that the
	 *        countdown reaches zero ({@code 0}).
	 */
	void decrementCountdownToReoptimize (Continuation1NotNull<Boolean> continuation);

	/**
	 * Answer the {@linkplain FunctionTypeDescriptor function type} associated
	 * with this raw function.
	 *
	 * @return The function type associated with this function implementation.
	 */
	A_Type functionType ();

	/**
	 * Answer the tuple of line number deltas for this ram function.  Each entry
	 * encodes a signed offset in an unsigned entry.  There's an entry for each
	 * nybblecode (not for each nybble).  The encoding uses the absolute value
	 * of the delta from the previous instruction's line number, shifted left
	 * once, adding one for negatives.  This allows nybble tuples and byte
	 * tuples to be the usual representations for small functions.
	 *
	 * @return The function type associated with this function implementation.
	 */
	A_Tuple lineNumberEncodedDeltas ();

	/**
	 * Answer the {@code index}-th literal value of this {@linkplain
	 * A_RawFunction function implementation}.
	 *
	 * @param index
	 *        The one-based ordinal of the desired literal value.
	 * @return The requested literal value.
	 */
	AvailObject literalAt (int index);

	/**
	 * Answer the {@linkplain A_Type type} of the {@code index}-th local
	 * variable.
	 *
	 * @param index
	 *        The one-based ordinal of the desired local variable.
	 * @return The requested type.
	 */
	A_Type localTypeAt (int index);

	/**
	 * Answer the maximum depth of the stack needed by a {@linkplain
	 * A_Continuation continuation} based on this {@linkplain A_Function
	 * function implementation}.
	 *
	 * @return The maximum stack depth for this function implementation.
	 */
	int maxStackDepth ();

	/**
	 * Answer the name of the {@linkplain A_Method method} associated with this
	 * raw function.
	 *
	 * @return The method name associated with this function implementation, or
	 *         a {@linkplain A_String string} that indicates that the provenance
	 *         of the function implementation is not known.
	 * @see #setMethodName(A_String)
	 */
	A_String methodName ();

	/**
	 * Answer the {@linkplain A_Module module} that contains the {@linkplain
	 * BlockNodeDescriptor block} that defines this raw function.
	 *
	 * @return The module, or {@linkplain NilDescriptor#nil nil} for synthetic
	 *         function implementations.
	 */
	A_Module module ();

	/**
	 * Extract the nybblecode operation at the given pc (program counter),
	 * updating it to the start of the first operand, or just past the operation
	 * if none.
	 *
	 * @param pc
	 *        The {@link MutableInt} holding the index into the nybblecodes at
	 *        which the operation is, and which will be updated to just after
	 *        the operation, or the start of any operands.
	 * @return The {@link L1Operation} found at the specified position.
	 */
	L1Operation nextNybblecodeOperation (final MutableInt pc);

	/**
	 * Extract the nybblecode operand at the given pc (program counter),
	 * updating it to just past the operand.
	 *
	 * @param pc
	 *        The {@link MutableInt} holding the index into the nybblecodes at
	 *        which the operand is, and which will be updated to just after
	 *        the operand.
	 * @return The {@code int} operand at the specified position.
	 */
	int nextNybblecodeOperand (final MutableInt pc);

	/**
	 * Answer the arity of this raw function.
	 *
	 * @return The arity of this raw function.
	 */
	int numArgs ();

	/**
	 * Answer the number of slots to reserve for {@linkplain A_Continuation
	 * continuations} based on this raw function. This is the arity, plus number
	 * of local variables and constants, plus number of stack slots.
	 *
	 * @return The number of continuation slots to reserve for executing this
	 *         raw function.
	 */
	int numSlots ();

	/**
	 * Answer the number of literal values embedded into this {@linkplain
	 * A_RawFunction function implementation}.
	 *
	 * @return The number of literal values of this function implementation.
	 */
	int numLiterals ();

	/**
	 * Answer the number of local variables specified by this {@link
	 * A_RawFunction}.
	 *
	 * @return The number of local variables of this function implementation.
	 */
	int numLocals ();

	/**
	 * Answer the number of local constants specified by this {@link
	 * A_RawFunction}.
	 *
	 * @return The number of local constants of this function implementation.
	 */
	int numConstants ();

	/**
	 * Answer how many nybbles are taken up by the nybblecodes of this raw
	 * function.
	 *
	 * @return The {@link A_Tuple#tupleSize() size} of this raw function's
	 *         {@link #nybbles()}.
	 */
	int numNybbles ();

	/**
	 * Answer the number of outer variables specified by this {@linkplain
	 * A_RawFunction function implementation}.
	 *
	 * @return The number of outer variables of this function implementation.
	 */
	int numOuters ();

	/**
	 * Answer the {@linkplain A_Tuple tuple} of nybblecodes that implements this
	 * raw function.
	 *
	 * @return The instruction tuple for this function implementation.
	 */
	A_Tuple nybbles ();

	/**
	 * Answer the block {@link A_Phrase phrase} from which this raw function was
	 * constructed.  Answer {@link NilDescriptor#nil nil} if this information
	 * is not available.
	 *
	 * @return The phrase or nil from which this raw function was created.
	 */
	A_Phrase originatingPhrase ();

	/**
	 * Answer the {@linkplain A_Type type} of the {@code index}-th outer
	 * variable.
	 *
	 * @param index
	 *        The one-based ordinal of the desired outer variable.
	 * @return The requested type.
	 */
	A_Type outerTypeAt (int index);

	/**
	 * Answer this raw function's {@link Primitive} or {@code null}.
	 *
	 * @return The Primitive, or null if this raw function is not primitive.
	 */
	@Nullable Primitive primitive ();

	/**
	 * Answer the {@linkplain Primitive primitive} {@linkplain
	 * Primitive#primitiveNumber number} associated with this {@linkplain
	 * A_RawFunction function implementation}. The {@linkplain Interpreter
	 * interpreter} will execute the indicated primitive before falling back on
	 * the Avail code (in the event of failure only).
	 *
	 * @return The primitive number, or zero ({@code 0}) if the function
	 *         implementation is not linked to a primitive.
	 */
	int primitiveNumber();

	/**
	 * Answer a {@link Statistic} for recording returns from this raw function.
	 *
	 * @return The statistic.
	 */
	Statistic returnerCheckStat();

	/**
	 * Answer a {@link Statistic} for recording returns into this raw function.
	 *
	 * @return The statistic.
	 */
	Statistic returneeCheckStat();

	/**
	 * Specify that a {@linkplain A_Method method} with the given name includes
	 * a {@linkplain A_Definition definition} that (indirectly) includes this
	 * raw function.
	 *
	 * @param methodName
	 *        The method name to associate with this function implementation.
	 */
	void setMethodName (A_String methodName);

	/**
	 * Set the {@linkplain L2Chunk chunk} that implements this {@linkplain
	 * A_RawFunction function implementation} and the countdown to
	 * reoptimization by the {@linkplain L2Translator Level Two translator}.
	 *
	 * @param chunk
	 *        The chunk to invoke whenever the {@linkplain Interpreter
	 *        interpreter} starts execution of this function implementation}.
	 * @param countdown
	 *        The countdown to reoptimization by the Level Two translator.
	 */
	void setStartingChunkAndReoptimizationCountdown (
		L2Chunk chunk,
		long countdown);

	/**
	 * Answer the {@linkplain L2Chunk chunk} that the {@linkplain Interpreter
	 * interpreter} will run to simulate execution of this {@linkplain
	 * A_RawFunction function implementation}.
	 *
	 * @return The backing chunk for this function implementation. This will be
	 *         the {@linkplain L2Chunk#unoptimizedChunk special unoptimized
	 *         chunk} prior to conversion by the {@linkplain L2Translator
	 *         Level Two translator}.
	 */
	L2Chunk startingChunk ();

	/**
	 * Answer the starting line number for the {@linkplain BlockNodeDescriptor
	 * block} that defines this raw function.
	 *
	 * @return The starting line number, or zero ({@code 0}) for synthetic
	 *         function implementations.
	 */
	int startingLineNumber ();

	/**
	 * Atomically increment the total number of invocations of {@linkplain
	 * A_Function functions} based on this raw function.
	 */
	void tallyInvocation ();

	/**
	 * Answer the total number of invocations of this raw function.
	 *
	 * @return The total number of invocations of this function implementation.
	 */
	long totalInvocations ();
}
