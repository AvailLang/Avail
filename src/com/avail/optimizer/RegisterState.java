/**
 * RegisterState.java
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

package com.avail.optimizer;

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2Register;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avail.utility.PrefixSharingList.*;

/**
 * This class maintains information about one {@linkplain L2Register} on behalf
 * of a {@link RegisterSet} held by an {@linkplain L2Instruction} inside an
 * {@linkplain L2Chunk} constructed by the {@link L2Translator}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class RegisterState
{
	/**
	 * The exact value in this register.  If the exact value is not known, this
	 * field is {@code null}.
	 */
	private @Nullable AvailObject constant;

	/** The type of value in this register. */
	private @Nullable A_Type type;

	/**
	 * The list of other registers that are known to have the same value (if
	 * any).  These occur in the order in which the registers acquired the
	 * common value.
	 *
	 * <p>
	 * The inverse map is kept in {@link #invertedOrigins}, to more
	 * efficiently disconnect this information.
	 * </p>
	 */
	private List<L2Register<?>> origins = Collections.emptyList();

	/**
	 * The inverse of {@link #origins}.  For each key, the value is the
	 * collection of registers that this value has been copied into (and not yet
	 * been overwritten).
	 */
	private List<L2Register<?>> invertedOrigins = Collections.emptyList();

	/**
	 * The {@link Set} of {@link L2Instruction}s that may have provided the
	 * current value in that register.  There may be more than one such
	 * instruction due to multiple paths converging by jumping to labels.
	 */
	private List<L2Instruction> sourceInstructions = Collections.emptyList();

	/**
	 * Indicates whether this RegisterState may have been shared among multiple
	 * {@link RegisterSet}s.  If so, the RegisterSet must not modify this
	 * object, but must make a copy first.
	 */
	private boolean isShared;

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @return Whether this register has most recently been assigned a constant.
	 */
	public boolean hasConstant ()
	{
		return constant != null;
	}

	/**
	 * Return the constant {@link AvailObject} that this register has in it at
	 * this point, or {@code null} if no such exact value is known.
	 *
	 * @return The constant value or null.
	 */
	public @Nullable AvailObject constant ()
	{
		return constant;
	}

	/**
	 * Set the constant {@link AvailObject} that this register has in it at
	 * this point.  Accept {@code null} to indicate no such value is known.
	 *
	 * @param newConstant The constant value or null.
	 */
	public void constant (final @Nullable AvailObject newConstant)
	{
		assert !isShared;
		this.constant = newConstant;
	}

	/**
	 * Return the {@link A_Type type} that constrains this register at this
	 * point, or {@code null} if no type constraint is known.
	 *
	 * @return The constraining type or null.
	 */
	public @Nullable A_Type type ()
	{
		return type;
	}

	/**
	 * Set the {@link A_Type type} that constrains this register at this
	 * point.  Accepts {@code null} if no type constraint is known.
	 *
	 * @param newType The constraining type or null.
	 */
	public void type (final @Nullable A_Type newType)
	{
		assert !isShared;
		this.type = newType;
	}

	/**
	 * Return the immutable {@link List} of {@link L2Instruction}s that may have
	 * provided the current value in that register.
	 *
	 * @return An immutable list of L2Instructions.
	 */
	public List<L2Instruction> sourceInstructions ()
	{
//		return Collections.unmodifiableList(sourceInstructions);
		return sourceInstructions;
	}

	/**
	 * Empty my list of source {@link L2Instruction}s which may have provided
	 * values for this register at this point.
	 */
	public void clearSources ()
	{
		assert !isShared;
		sourceInstructions = Collections.emptyList();
	}

	/**
	 * Add an L2Instruction to my list of instructions that are potential
	 * sources of the value in the register at this point.
	 *
	 * @param newSource
	 *        An instruction that may provide a value for this register.
	 */
	public void addSource (final L2Instruction newSource)
	{
		assert !isShared;
		if (!sourceInstructions.contains(newSource))
		{
			sourceInstructions = append(sourceInstructions, newSource);
		}
	}

	/**
	 * Answer the immutable {@link List} of {@link L2Register}s that provided
	 * values for the represented register.
	 *
	 * @return The source registers.
	 */
	public List<L2Register<?>> origins ()
	{
//		return Collections.unmodifiableList(origins);
		return origins;
	}

	/**
	 * Replace the immutable {@link List} of {@link L2Register}s that provided
	 * values for the represented register.
	 *
	 * @param originRegisters The source registers.
	 */
	public void origins (final List<L2Register<?>> originRegisters)
	{
		assert !isShared;
		assert originRegisters.size() <= 1
			|| new HashSet<>(originRegisters).size() == originRegisters.size();
		origins = originRegisters;
	}

	/**
	 * Update my {@link #origins} to exclude the specified {@link L2Register}.
	 * The receiver must be mutable.
	 *
	 * @param origin
	 *        The {@link L2Register} that the current register is no longer fed
	 *        from.
	 */
	public void removeOrigin (final L2Register<?> origin)
	{
		assert !isShared;
		if (!origins.isEmpty())
		{
			if (last(origins) == origin)
			{
				origins = withoutLast(origins);
			}
			else
			{
				origins = new ArrayList<>(origins);
				origins.remove(origin);
			}
		}
	}

	/**
	 * Answer the {@link L2Register}s that this one feeds (via a move).
	 *
	 * @return An immutable list of registers.
	 */
	public List<L2Register<?>> invertedOrigins ()
	{
//		return Collections.unmodifiableList(invertedOrigins);
		return invertedOrigins;
	}

	/**
	 * Replace the immutable {@link List} of {@link L2Register}s that were
	 * supplied values from the represented register.
	 *
	 * @param invertedOriginRegisters The destination registers.
	 */
	public void invertedOrigins (final List<L2Register<?>> invertedOriginRegisters)
	{
		assert !isShared;
		assert invertedOriginRegisters.size() <= 1
			|| new HashSet<>(invertedOriginRegisters).size()
				== invertedOriginRegisters.size();
		invertedOrigins = invertedOriginRegisters;
	}

	/**
	 * Update my {@link #invertedOrigins} to include the specified {@link
	 * L2Register}.  The receiver must be mutable.
	 *
	 * @param invertedOrigin
	 *        The {@link L2Register} that the current register feeds.
	 */
	public void addInvertedOrigin (final L2Register<?> invertedOrigin)
	{
		assert !isShared;
		invertedOrigins = append(invertedOrigins, invertedOrigin);
	}

	/**
	 * Update my {@link #invertedOrigins} to exclude the specified {@link
	 * L2Register}.  The receiver must be mutable.
	 *
	 * @param invertedOrigin
	 *        The {@link L2Register} that the current register no longer is
	 *        considered to feed.
	 */
	public void removeInvertedOrigin (final L2Register<?> invertedOrigin)
	{
		assert !isShared;
		if (!invertedOrigins.isEmpty())
		{
			if (last(invertedOrigins) == invertedOrigin)
			{
				invertedOrigins = withoutLast(invertedOrigins);
			}
			else
			{
				invertedOrigins = new ArrayList<>(invertedOrigins);
				invertedOrigins.remove(invertedOrigin);
			}
		}
	}

	/**
	 * Mark this {@link RegisterState} as shared, preventing subsequent
	 * modifications.  However, a mutable copy can be produced with the {@link
	 * RegisterState#RegisterState(RegisterState) copy constructor}.
	 */
	public void share ()
	{
		isShared = true;
	}

	/**
	 * Answer whether writing is allowed to this {@link RegisterState}.  {@link
	 * RegisterSet}s that wish to write to a shared RegisterState must first
	 * functionType a copy.  Cloning a RegisterState causes {@link #share()} to be
	 * sent to each of its RegisterStates.
	 *
	 * @return
	 */
	public boolean isShared ()
	{
		return isShared;
	}

	/** The immutable initial register state in which nothing is known. */
	private static final RegisterState blank = new RegisterState();

	/**
	 * Answer the immutable initial register state in which nothing is known.
	 * @return The blank RegisterState.
	 */
	public static RegisterState blank ()
	{
		return blank;
	}

	/**
	 * Construct a new {@link RegisterState}.
	 */
	private RegisterState ()
	{
		// Initialized by individual field declarations.
		this.isShared = true;
	}

	/**
	 * Copy a {@code RegisterState}.
	 *
	 * @param original The original RegisterSet to copy.
	 */
	RegisterState (final RegisterState original)
	{
		this.isShared = false;
		this.constant = original.constant;
		this.type = original.type;
		this.origins = original.origins;
		this.invertedOrigins = original.invertedOrigins;
		this.sourceInstructions = original.sourceInstructions;
	}
}
