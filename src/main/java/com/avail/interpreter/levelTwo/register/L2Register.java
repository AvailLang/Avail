/*
 * L2Register.java
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

package com.avail.interpreter.levelTwo.register;

import com.avail.descriptor.representation.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Entity;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.reoptimizer.L2Inliner;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.Casts;
import org.objectweb.asm.Type;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * {@code L2Register} models the conceptual use of a register by a {@linkplain
 * L2Operation level two Avail operation} in the {@link L2Generator}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2Register implements L2Entity
{
	/**
	 * One of the kinds of registers that Level Two supports.
	 */
	public enum RegisterKind
	{
		/**
		 * The kind of register that holds an {@link AvailObject}.
		 */
		BOXED(
			"boxed",
			"r",
			getDescriptor(AvailObject.class),
			ALOAD,
			ASTORE,
			RestrictionFlagEncoding.BOXED)
		{
			@Override
			public <
				R extends L2Register,
				RR extends L2ReadOperand<R>>
			RR readOperand (
				final L2SemanticValue semanticValue,
				final TypeRestriction restriction,
				final R register)
			{
				return Casts.<L2ReadOperand<?>, RR>cast(
					new L2ReadBoxedOperand(
						semanticValue,
						restriction,
						(L2BoxedRegister) register));
			}
		},

		/**
		 * The kind of register that holds an {@code int}.
		 */
		INTEGER(
			"int",
			"i",
			INT_TYPE.getDescriptor(),
			ILOAD,
			ISTORE,
			RestrictionFlagEncoding.UNBOXED_INT)
		{
			@Override
			public <
				R extends L2Register,
				RR extends L2ReadOperand<R>>
			RR readOperand (
				final L2SemanticValue semanticValue,
				final TypeRestriction restriction,
				final R register)
			{
				return Casts.<L2ReadOperand<?>, RR>cast(
					new L2ReadIntOperand(
						semanticValue,
						restriction,
						(L2IntRegister) register));
			}
		},

		/**
		 * The kind of register that holds a {@code double}.
		 */
		FLOAT(
			"float",
			"f",
			DOUBLE_TYPE.getDescriptor(),
			DLOAD,
			DSTORE,
			RestrictionFlagEncoding.UNBOXED_FLOAT)
			{
				@Override
				public <
					R extends L2Register,
					RR extends L2ReadOperand<R>>
				RR readOperand (
					final L2SemanticValue semanticValue,
					final TypeRestriction restriction,
					final R register)
				{
					return Casts.<L2ReadOperand<?>, RR>cast(
						new L2ReadFloatOperand(
							semanticValue,
							restriction,
							(L2FloatRegister) register));
				}
			};


//		/**
//		 * The kind of register that holds the value of some variable prior to
//		 * the variable having escaped, if ever.
// 		 */
//		UNESCAPED_VARIABLE_VALUE
		;

		/** The prefix to use for registers of this kind. */
		public final String prefix;

		/** The descriptive name of this register kind. */
		public final String kindName;

		/** The JVM {@link Type} string. */
		public final String jvmTypeString;

		/** The JVM instruction that loads a register of this kind. */
		public final int loadInstruction;

		/** The JVM instruction that stores a register of this kind. */
		public final int storeInstruction;

		/**
		 * The {@link RestrictionFlagEncoding} used to indicate a
		 * {@link TypeRestriction} has an available register of this kind.
		 */
		public final RestrictionFlagEncoding restrictionFlag;

		/**
		 * Answer a suitable {@link L2ReadOperand} for extracting the indicated
		 * {@link L2SemanticValue} of this kind.
		 *
		 * @param semanticValue
		 *        The {@link L2SemanticValue} to consume via an
		 *        {@link L2ReadOperand}.
		 * @param restriction
		 *        The {@link TypeRestriction} relevant to this read.
		 * @param register
		 *        The earliest known defining {@link L2Register} of the
		 *        {@link L2SemanticValue}.
		 * @return The new {@link L2ReadOperand}.
		 * @param <R> The {@link L2Register} subclass.
		 * @param <RR> The {@link L2ReadOperand} subclass.
		 */
		public abstract <
			R extends L2Register,
			RR extends L2ReadOperand<R>>
		RR readOperand (
			final L2SemanticValue semanticValue,
			final TypeRestriction restriction,
			final R register);

		/**
		 * Answer a suitable {@link L2_MOVE} operation for transferring values
		 * of this kind.
		 *
		 * @return The new {@link L2ReadOperand}.
		 * @param <R> The {@link L2Register} subclass.
		 * @param <RR> The {@link L2ReadOperand} subclass.
		 * @param <WR> The {@link L2WriteOperand} subclass.
		 */
		public <
			R extends L2Register,
			RR extends L2ReadOperand<R>,
			WR extends L2WriteOperand<R>>
		L2_MOVE<R, RR, WR> move ()
		{
			return Casts.<L2_MOVE<?, ?, ?>, L2_MOVE<R, RR, WR>>cast (
				L2_MOVE.moveByKind(this));
		}

		/**
		 * Create an instance of the enum.
		 *
		 * @param kindName
		 *        A descriptive name for this kind of register.
		 * @param prefix
		 *        The prefix to use when naming registers of this kind.
		 * @param jvmTypeString
		 *        The canonical {@link String} used to identify this
		 *        {@link Type} of register to the JVM.
		 * @param loadInstruction
		 *        The JVM instruction for loading.
		 * @param storeInstruction
		 *        The JVM instruction for storing.
		 * @param restrictionFlag
		 *        The corresponding {@link RestrictionFlagEncoding}.
		 */
		RegisterKind(
			final String kindName,
			final String prefix,
			final String jvmTypeString,
			final int loadInstruction,
			final int storeInstruction,
			final RestrictionFlagEncoding restrictionFlag)
		{
			this.kindName = kindName;
			this.prefix = prefix;
			this.jvmTypeString = jvmTypeString;
			this.loadInstruction = loadInstruction;
			this.storeInstruction = storeInstruction;
			this.restrictionFlag = restrictionFlag;
		}

		/** Don't modify this array. */
		public static final RegisterKind[] all = values();
	}

	/**
	 * Answer the kind of register this is. Different register kinds are
	 * allocated from different virtual banks, and do not interfere in terms of
	 * register liveness computation.
	 *
	 * @return The {@link RegisterKind}.
	 */
	public abstract RegisterKind registerKind ();

	/**
	 * A coloring number to be used by the {@linkplain Interpreter interpreter}
	 * at runtime to identify the storage location of a {@linkplain L2Register
	 * register}.
	 */
	private int finalIndex = -1;

	/**
	 * Answer the coloring number to be used by the {@linkplain Interpreter
	 * interpreter} at runtime to identify the storage location of a {@linkplain
	 * L2Register register}.
	 *
	 * @return An {@code L2Register} coloring number.
	 */
	public int finalIndex ()
	{
		return finalIndex;
	}

	/**
	 * Set the coloring number to be used by the {@linkplain Interpreter
	 * interpreter} at runtime to identify the storage location of an {@code
	 * L2Register}.
	 *
	 * @param theFinalIndex
	 *        An {@code L2Register} coloring number.
	 */
	public void setFinalIndex (final int theFinalIndex)
	{
		assert finalIndex == -1
			: "Only set the finalIndex of an L2RegisterIdentity once";
		finalIndex = theFinalIndex;
	}

	/**
	 * A value used to distinguish distinct registers.
	 */
	public final int uniqueValue;

	/**
	 * Answer the value used to distinguish distinct registers.
	 *
	 * @return The unique value of this register.
	 */
	public int uniqueValue ()
	{
		return uniqueValue;
	}

	/**
	 * Construct a new {@code L2BoxedRegister}.
	 *
	 * @param debugValue
	 *        A value used to distinguish the new instance visually during
	 *        debugging of L2 translations.
	 */
	public L2Register (
		final int debugValue)
	{
		this.uniqueValue = debugValue;
	}

	/**
	 * The {@link L2WriteOperand}s that assign to this register.  While the
	 * {@link L2ControlFlowGraph} is in SSA form, there should be exactly one.
	 */
	private final Set<L2WriteOperand<?>> definitions = new HashSet<>();

	/**
	 * Record this {@link L2WriteOperand} in my set of defining write operands.
	 *
	 * @param write
	 *        An {@link L2WriteOperand} that's an operand of an instruction that
	 *        writes to this register in the control flow graph of basic blocks.
	 */
	public void addDefinition (final L2WriteOperand<?> write)
	{
		definitions.add(write);
	}

	/**
	 * Remove the given {@link L2WriteOperand} as one of the writers to this
	 * register.
	 *
	 * @param write
	 *        The {@link L2WriteOperand} to remove from my set of defining
	 *        write operands.
	 */
	public void removeDefinition (final L2WriteOperand<?> write)
	{
		definitions.remove(write);
	}

	/**
	 * Answer the {@link L2WriteOperand} of an {@link L2Instruction} which
	 * assigns this register in the SSA control flow graph. It must have been
	 * assigned already, and there must be exactly one (when the control flow
	 * graph is in SSA form).
	 *
	 * @return The requested {@code L2WriteOperand}.
	 */
	public L2WriteOperand<?> definition ()
	{
		assert definitions.size() == 1;
		return definitions.iterator().next();
	}

	/**
	 * Answer the {@link L2WriteOperand}s which assign this register in the
	 * control flow graph, which is not necessarily in SSA form. It must be
	 * non-empty.
	 *
	 * @return This register's defining {@link L2WriteOperand}s.
	 */
	public Collection<L2WriteOperand<?>> definitions ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return definitions;
	}

	/**
	 * The {@link L2ReadOperand}s of emitted {@link L2Instruction}s that read
	 * from this register.
	 */
	private final Set<L2ReadOperand<?>> uses = new HashSet<>();

	/**
	 * Capture another {@link L2ReadOperand} of an emitted {@link L2Instruction}
	 * that uses this register.
	 *
	 * @param read
	 *        The {@link L2ReadOperand} that reads from this register.
	 */
	public void addUse (final L2ReadOperand<?> read)
	{
		uses.add(read);
	}

	/**
	 * Drop a use of this register by an {@link L2ReadOperand} of an
	 * {@link L2Instruction} that is now dead.
	 *
	 * @param read
	 *        An {@link L2ReadOperand} that no longer reads from this register
	 *        because its instruction has been removed.
	 */
	public void removeUse (final L2ReadOperand<?> read)
	{
		uses.remove(read);
	}

	/**
	 * Answer the {@link Set} of {@link L2ReadOperand}s that read from this
	 * register. Callers must not modify the returned collection.
	 *
	 * @return A {@link Set} of {@link L2ReadOperand}s.
	 */
	public Set<L2ReadOperand<?>> uses ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return uses;
	}

	/**
	 * Answer a new register like this one.
	 *
	 * @param generator
	 *        The {@link L2Generator} for which copying is requested.
	 * @return The new {@code L2Register}.
	 */
	public abstract L2Register copyForTranslator (
		final L2Generator generator);

	/**
	 * Answer a new register like this one, but where the uniqueValue has been
	 * set to the finalIndex.
	 *
	 * @return The new {@code L2Register}.
	 */
	public abstract L2Register copyAfterColoring ();

	/**
	 * Answer a copy of the receiver. Subclasses can be covariantly stronger in
	 * the return type.
	 *
	 * @param inliner
	 *        The {@link L2Inliner} for which copying is requested.
	 * @return A copy of the receiver.
	 */
	public abstract L2Register copyForInliner (final L2Inliner inliner);

	/**
	 * Answer the prefix for non-constant registers. This is used only for
	 * register printing.
	 *
	 * @return The prefix.
	 */
	public String namePrefix ()
	{
		return registerKind().prefix;
	}

	@Override
	public final String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(namePrefix());
		if (finalIndex() != -1)
		{
			builder.append(finalIndex());
		}
		else
		{
			builder.append(uniqueValue);
		}
		return builder.toString();
	}
}
