/**
 * L2Operation.java Copyright © 1993-2012, Mark van Gulik and Todd L Smith. All
 * rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

package com.avail.interpreter.levelTwo;

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * The instruction set for the {@linkplain L2Interpreter level two Avail
 * interpreter}.  Avail programs can only see as far down as the level one
 * nybblecode representation.  Level two translations are invisibly created as
 * necessary to boost performance of frequently executed code.  Technically
 * level two is an optional part of the implementation, but modern hardware
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class L2Operation
{
	/**
	 * The {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 */
	protected L2NamedOperandType[] namedOperandTypes;

	/**
	 * Answer the {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 *
	 * @return The named operand types that this operation expects.
	 */
	public L2NamedOperandType[] operandTypes ()
	{
		return namedOperandTypes;
	}

	/**
	 * The name of this level two operation.  This is initialized to be the
	 * {@linkplain Class#getSimpleName() simple name} of the {@link Class}.
	 */
	private String name;

	/**
	 * Answer the name of this {@linkplain L2Operation}.
	 *
	 * @return The operation name, suitable for symbolic debugging of level two
	 *         generated code.
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * The ordinal of this {@link L2Operation}, made available via the {@link
	 * #ordinal()} message.  The assignment of ordinals to operations depends on
	 * the order in which the operations are first encontered in the code
	 * generator (or elsewhere), so don't rely on specific numeric values.
	 */
	private int ordinal;

	/**
	 * The ordinal of this {@link L2Operation}.  Note that the assignment of
	 * ordinals to operations depends on the order in which the operations are
	 * first encountered in the code generator, so don't rely on numeric values.
	 *
	 * <p>
	 * Among other things, this is intended as a disincentive to discourage the
	 * exposition of anything related to level two in media that outlive or
	 * otherwise exceed the scope of an invocation of the Avail virtual machine
	 * (i.e., files, communication channels, etc).  If such a mechanism becomes
	 * truly desirable in the future (despite it potentially acting as a
	 * hindrance against advancement of the level two instruction set and
	 * optimization engine), one could choose to initialize the classes in some
	 * particular order, thus defining the canon of level two operations.
	 * </p>
	 *
	 * <p>
	 * One more warning for good measure:  The level two instructions produced
	 * by the optimizer take into account (and are themselves taken into account
	 * by) the specific sets of implementations of relevant methods.  For
	 * example, the function [1+2;] may be tentatively folded to produce the
	 * constant 3, irrespective of the current environment's definition of a
	 * "_+_" operation.  Within a virtual machine, such level two code will be
	 * made contingent upon the "_+_" method's set of implementations, being
	 * invalidated automatically if an implementation is added or removed.  Any
	 * attempt to simply plug such level two code into another environment is
	 * surely fraught with disaster, or at least great peril.
	 * </p>
	 *
	 * @return The operation's ordinal, used in {@link L2ChunkDescriptor
	 *         level two wordcodes}.
	 */
	public int ordinal ()
	{
		return ordinal;
	}

	/**
	 * The set of {@linkplain L2Operation operations} that have been encountered
	 * thus far, organized as an array indexed by the operations' {@linkplain
	 * #ordinal ordinals}.  The array might be padded with nulls.
	 */
	static L2Operation[] values = new L2Operation[0];  // grown by replacement

	/**
	 * Answer an array of {@linkplain L2Operation operations} which have been
	 * encountered thus far, indexed by {@link #ordinal}.  It may be padded with
	 * nulls.
	 *
	 * <p>
	 * The array may be replaced when new operations are encountered, so do not
	 * cache it elsewhere.
	 * </p>
	 *
	 * @return The known operations.
	 */
	public static L2Operation[] values ()
	{
		return values;
	}

	/**
	 * How many distinct kinds of operations have been encountered so far.
	 */
	private static int numValues = 0;


	/**
	 * Initialize a fresh {@link L2Operation}.
	 *
	 * @param theNamedOperandTypes
	 *            The named operand types that this operation expects.
	 */
	public void init (
		final @NotNull L2NamedOperandType... theNamedOperandTypes)
	{
		assert namedOperandTypes == null;
		namedOperandTypes = theNamedOperandTypes;
		name = this.getClass().getSimpleName();
		if (numValues >= values.length)
		{
			values = Arrays.copyOf(values, numValues * 2 + 10);
		}
		ordinal = numValues;
		values[ordinal] = this;
		numValues++;
	}

	/**
	 * Execute this {@link L2Operation} within an {@link L2Interpreter}.  The
	 * {@linkplain L2Operand operands} are encoded as integers in the wordcode
	 * stream, extracted with {@link L2Interpreter#nextWord()}.
	 *
	 * @param interpreter
	 *            The {@linkplain L2Interpreter interpreter} on behalf of which
	 *            to perform this operation.
	 */
	public abstract void step (
		final @NotNull L2Interpreter interpreter);

	/**
	 * @param instruction
	 * @param registers
	 */
	public void propagateTypesInFor (
		final @NotNull L2Instruction instruction,
		final @NotNull RegisterSet registers)
	{
		// By default just record that the destinations have been overwritten.
		for (final L2Register destinationRegister
			: instruction.destinationRegisters())
		{
			registers.removeConstantAt(destinationRegister);
			registers.removeTypeAt(destinationRegister);
			registers.propagateWriteTo(destinationRegister);
		}
	}

	/**
	 * Answer whether an instruction using this operation should be emitted. For
	 * example, labels are place holders and produce no code.  By default an
	 * instruction should be emitted, so non-emitting operations should override
	 * to return false.
	 *
	 * @return A {@code boolean} indicating if this operation should be emitted.
	 */
	public boolean shouldEmit ()
	{
		return true;
	}

	/**
	 * Answer whether this {@link L2Operation} changes the state of the
	 * interpreter in any way other than by writing to its destination
	 * registers. Most operations are computational and don't have side effects.
	 *
	 * @return Whether this operation has any side effect.
	 */
	public boolean hasSideEffect ()
	{
		return false;
	}

	/**
	 * Answer whether the given {@link L2Instruction} (whose operation must be
	 * the receiver) changes the state of the interpreter in any way other than
	 * by writing to its destination registers. Most operations are
	 * computational and don't have side effects.
	 *
	 * <p>
	 * Most enum instances can override {@link #hasSideEffect()} if
	 * {@code false} isn't good enough, but some might need to know details of
	 * the actual {@link L2Instruction} – in which case they should override
	 * this method instead.
	 * </p>
	 *
	 * @param instruction
	 *            The {@code L2Instruction} for which a side effect test is
	 *            being performed.
	 * @return Whether that L2Instruction has any side effect.
	 */
	public boolean hasSideEffect (final @NotNull L2Instruction instruction)
	{
		assert instruction.operation == this;
		return hasSideEffect();
	}

	/**
	 * Answer whether execution of this instruction can lead to the next
	 * instruction in the sequence being reached.  Most instructions are of this
	 * form, but some might not be (return, unconditional branches, continuation
	 * resumption, etc).
	 *
	 * @return Whether the next instruction is potentially reachable from here.
	 */
	public boolean reachesNextInstruction ()
	{
		return true;
	}
}
