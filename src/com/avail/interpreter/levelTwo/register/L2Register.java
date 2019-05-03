/*
 * L2Register.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2Synonym;
import com.avail.optimizer.reoptimizer.L2Inliner;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code L2Register} models the conceptual use of a register by a {@linkplain
 * L2Operation level two Avail operation} in the {@link L2Generator}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2Register
{
	public enum RegisterKind
	{
		/**
		 * The kind of register that holds an {@link AvailObject}.
		 */
		OBJECT
		{
			@Override
			public @Nullable L2ReadPointerOperand getDefaultRegister (
				final L2Synonym synonym)
			{
				return synonym.defaultObjectRead();
			}

			@Override
			public Set<L2ObjectRegister> getRegistersCopy (
				final L2Synonym synonym)
			{
				return synonym.objectRegistersCopy();
			}
		},

		/**
		 * The kind of register that holds an {@code int}.
		 */
		INTEGER
		{
			@Override
			public @Nullable L2ReadIntOperand getDefaultRegister (
			final L2Synonym synonym)
			{
				return synonym.defaultIntRead();
			}

			@Override
			public Set<L2IntRegister> getRegistersCopy (
				final L2Synonym synonym)
			{
				return synonym.intRegistersCopy();
			}
		},

		/**
		 * The kind of register that holds a {@code double}.
		 */
		FLOAT
		{
			@Override
			public @Nullable L2ReadFloatOperand getDefaultRegister (
				final L2Synonym synonym)
			{
				return synonym.defaultFloatRead();
			}

			@Override
			public Set<L2FloatRegister> getRegistersCopy (
				final L2Synonym synonym)
			{
				return synonym.floatRegistersCopy();
			}
		},

//		/**
//		 * The kind of register that holds the value of some variable prior to
//		 * the variable having escaped, if ever.
// 		 */
//		UNESCAPED_VARIABLE_VALUE
//		{
//
//		}
		;

		/**
		 * Extract an {@link L2ReadOperand} for the default {@link L2Register}
		 * of this kind from the given {@link L2Synonym}.
		 *
		 * @param synonym
		 *        The {@link L2Synonym} from which to request a default
		 *        register.
		 * @return A read of the default register, if one exists, otherwise
		 *         {@code null}.
		 */
		public abstract @Nullable L2ReadOperand<?> getDefaultRegister (
			L2Synonym synonym);

		/**
		 * Extract the set of {@link L2Register}s of this kind from the given
		 * {@link L2Synonym}.
		 *
		 * @param synonym
		 *        The {@link L2Synonym} from which to request registers.
		 * @return The {@link L2Synonym}'s set of registers of this kind.
		 */
		public abstract Set<? extends L2Register> getRegistersCopy (
			L2Synonym synonym);

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
	 * The {@link TypeRestriction} that constrains this register's content.
	 */
	protected final TypeRestriction restriction;

	/**
	 * Answer this register's basic {@link TypeRestriction}.
	 *
	 * @return A {@link TypeRestriction}.
	 */
	public final TypeRestriction restriction ()
	{
		return restriction;
	}

	/**
	 * Construct a new {@code L2ObjectRegister}.
	 *
	 * @param debugValue
	 *        A value used to distinguish the new instance visually during
	 *        debugging of L2 translations.
	 * @param restriction
	 * 	      The {@link TypeRestriction}.
	 */
	public L2Register (
		final int debugValue,
		final TypeRestriction restriction)
	{
		this.uniqueValue = debugValue;
		this.restriction = restriction;
	}

	/**
	 * Create an appropriate {@link L2ReadOperand} for this register, using the
	 * provided {@link TypeRestriction}.
	 *
	 * @param typeRestriction
	 *        The {@code TypeRestriction}.
	 * @return The requested {@code L2ReadOperand}.
	 */
	public abstract L2ReadOperand<? extends L2Register> read (
		TypeRestriction typeRestriction);

	/**
	 * Create an appropriate {@link L2WriteOperand} for this register.
	 *
	 * @return The requested {@code L2WriteOperand}.
	 */
	public abstract L2WriteOperand<? extends L2Register> write ();

	/**
	 * The instructions that assigns to this register. While the {@link
	 * L2ControlFlowGraph} is in SSA form, there should be exactly one.
	 */
	private final Set<L2Instruction> definitions = new HashSet<>();

	/**
	 * Record this {@link L2Instruction} in my set of defining instructions.
	 *
	 * @param instruction
	 *        An instruction that writes to this register in the control flow
	 *        graph of basic blocks.
	 */
	public void addDefinition (final L2Instruction instruction)
	{
		definitions.add(instruction);
	}

	/**
	 * Remove the given {@link L2Instruction} as one of the writers to this
	 * register.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to remove from my set of defining
	 *        instructions.
	 */
	public void removeDefinition (final L2Instruction instruction)
	{
		definitions.remove(instruction);
	}

	/**
	 * Answer the {@link L2Instruction} which assigns this register in the SSA
	 * control flow graph. It must have been assigned already, and there must be
	 * exactly one (this is a property of SSA).
	 *
	 * @return The requested {@code L2Instruction}.
	 */
	public L2Instruction definition ()
	{
		assert definitions.size() == 1;
		return definitions.iterator().next();
	}

	/**
	 * Answer the {@link L2Instruction} which generates the value that will
	 * populate this register. Skip over move instructions. The containing graph
	 * must be in SSA form.
	 *
	 * @return The requested {@code L2Instruction}.
	 */
	public L2Instruction definitionSkippingMoves ()
	{
		L2Register other = this;
		while (true)
		{
			assert other.definitions.size() == 1;
			final L2Instruction definition =
				other.definitions.iterator().next();
			if (definition.operation().isMove())
			{
				final List<L2Register> sources =
					definition.sourceRegisters();
				assert sources.size() == 1;
				other = sources.get(0);
				continue;
			}
			return definition;
		}
	}

	/**
	 * Answer the {@link L2Instruction}s which assign this register in the
	 * control flow graph, which is not necessarily in SSA form. It must be
	 * non-empty.
	 *
	 * @return This register's defining instructions.
	 */
	public Collection<L2Instruction> definitions ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return definitions;
	}

	/**
	 * The instructions that read from this register. This is a {@link Set}, so
	 * that an instruction that uses the same register twice only counts once.
	 */
	private final Set<L2Instruction> uses = new HashSet<>();

	/**
	 * Capture another instruction that uses this register.
	 *
	 * @param instruction
	 *        An instruction that reads from this register.
	 */
	public void addUse (final L2Instruction instruction)
	{
		uses.add(instruction);
	}

	/**
	 * Drop a use of this register by an instruction that is now dead.
	 *
	 * @param instruction
	 *        An instruction that reads from this register.
	 */
	public void removeUse (final L2Instruction instruction)
	{
		uses.remove(instruction);
	}

	/**
	 * Answer the set of instructions that read from this register. Do not
	 * modify the returned collection.
	 *
	 * @return A {@link Set} of {@link L2Instruction}s.
	 */
	public Set<L2Instruction> uses ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return uses;
	}

	/**
	 * Answer a new register like this one.
	 *
	 * @param generator
	 *        The {@link L2Generator} for which copying is requested.
	 * @param typeRestriction
	 *        The {@link TypeRestriction}.
	 * @return The new {@code L2Register}.
	 */
	public abstract <R extends L2Register> R copyForTranslator (
		final L2Generator generator,
		final TypeRestriction typeRestriction);

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
	 * Answer an {@link L2Operation} that implements a phi move for the
	 * receiver.
	 *
	 * @return The requested instruction.
	 */
	public abstract L2Operation phiMoveOperation ();

	/**
	 * Answer the prefix for non-constant registers. This is used only for
	 * register printing.
	 *
	 * @return The prefix.
	 */
	public abstract String namePrefix ();

	@Override
	public final String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		//noinspection VariableNotUsedInsideIf
		builder.append(restriction.constantOrNull == null ? namePrefix() : "c");
		if (finalIndex() != -1)
		{
			builder.append(finalIndex());
		}
		else
		{
			builder.append(uniqueValue);
		}
		if (restriction.constantOrNull != null)
		{
			builder.append('[');
			String constString = restriction.constantOrNull.toString();
			if (constString.length() > 50)
			{
				constString = constString.substring(0, 50) + '…';
			}
			//noinspection DynamicRegexReplaceableByCompiledPattern
			constString = constString
				.replace("\n", "\\n")
				.replace("\t", "\\t");
			builder.append(constString);
			builder.append(']');
		}
		return builder.toString();
	}
}
