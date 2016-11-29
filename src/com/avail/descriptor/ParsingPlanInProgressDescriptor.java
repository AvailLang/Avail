/**
 * ParsingPlanInProgressDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParsingPlanInProgressDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ParsingPlanInProgressDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.PARSING_PLAN_IN_PROGRESS;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompilerFragmentCache;
import com.avail.compiler.ParsingOperation;
import com.avail.compiler.splitter.MessageSplitter;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * A definition parsing plan describes the sequence of parsing operations that
 * must be performed to parse an invocation of a {@link A_Definition
 * definition}, possibly a {@link MacroDefinitionDescriptor macro definition}.
 *
 * <p>The sequences of instructions in multiple definition parse plans may have
 * common prefixes with each other, and it's along this commonality that {@link
 * MessageBundleTreeDescriptor message bundle trees} are organized, avoiding the
 * need to parse the same content multiple times as much as possible.</p>
 *
 * <p>This is taken even further by a cache of subexpressions found at each
 * parse point.  See {@link AvailCompilerFragmentCache} for more details.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class ParsingPlanInProgressDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
		implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s for the hash and the parsing pc.  See below.
		 */
		@HideFieldInDebugger
		PARSING_PC_AND_MORE;

		/** The subscript into my parsing plan's parsing instructions. */
		static final BitField PARSING_PC = bitField(
			PARSING_PC_AND_MORE, 0, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@link A_DefinitionParsingPlan} that this will parse invocations
		 * of.
		 */
		PARSING_PLAN
	}

	@Override
	int o_ParsingPc (final AvailObject object)
	{
		return object.slot(PARSING_PC);
	}

	@Override
	A_DefinitionParsingPlan o_ParsingPlan (final AvailObject object)
	{
		return object.slot(PARSING_PLAN);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		if (!another.kind().equals(PARSING_PLAN_IN_PROGRESS.o()))
		{
			return false;
		}
		final A_ParsingPlanInProgress strongAnother =
			(A_ParsingPlanInProgress) another;
		return object.slot(PARSING_PLAN).equals(strongAnother.parsingPlan())
			&& object.slot(PARSING_PC) == strongAnother.parsingPc();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (object.slot(PARSING_PC) ^ 0x92A26142) * multiplier
			- object.slot(PARSING_PLAN).hash();
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return PARSING_PLAN_IN_PROGRESS.o();
	}

	/**
	 * Answer a String consisting of the name of the message with a visual
	 * indication inserted at the keyword or argument position related to the
	 * given program counter.
	 *
	 * @param object
	 *        The {@link ParsingPlanInProgressDescriptor
	 *        parsing-plan-in-progress} to describe.
	 * @return The annotated method name, a Java {@code String}.
	 */
	@Override @AvailMethod
	String o_NameHighlightingPc (final AvailObject object)
	{
		final A_DefinitionParsingPlan plan = object.slot(PARSING_PLAN);
		final int pc = object.slot(PARSING_PC);
		if (pc == 0)
		{
			return "(any method invocation)";
		}
		final A_Tuple instructions = plan.parsingInstructions();
		final A_Bundle bundle = plan.bundle();
		final MessageSplitter messageSplitter = bundle.messageSplitter();
		return bundle.messageSplitter().highlightedNameFor(
			plan.definition().parsingSignature(), pc);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("plan @");
		aStream.append(object.parsingPc());
		aStream.append(" of ");
		aStream.append(object.nameHighlightingPc());
	}

	/**
	 * Create a new {@linkplain ParsingPlanInProgressDescriptor
	 * parsing-plan-in-progress} for the given parameters.
	 *
	 * @param plan The bundle for this plan.
	 * @param pc The definition for this plan.
	 * @return A new {@linkplain ParsingPlanInProgressDescriptor plan}.
	 */
	public static A_ParsingPlanInProgress create (
		final A_DefinitionParsingPlan plan,
		final int pc)
	{
		final AvailObject result = mutable.create();
		result.setSlot(PARSING_PLAN, plan);
		result.setSlot(PARSING_PC, pc);
		return result.makeShared();
	}

	/**
	 * Construct a new {@link ParsingPlanInProgressDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ParsingPlanInProgressDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.PARSING_PLAN_IN_PROGRESS_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link ParsingPlanInProgressDescriptor}. */
	private static final ParsingPlanInProgressDescriptor mutable =
		new ParsingPlanInProgressDescriptor(Mutability.MUTABLE);

	@Override
	ParsingPlanInProgressDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	ParsingPlanInProgressDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	/** The shared {@link ParsingPlanInProgressDescriptor}. */
	private static final ParsingPlanInProgressDescriptor shared =
		new ParsingPlanInProgressDescriptor(Mutability.SHARED);

	@Override
	ParsingPlanInProgressDescriptor shared ()
	{
		return shared;
	}
}
