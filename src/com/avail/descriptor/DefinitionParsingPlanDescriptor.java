/**
 * DefinitionParsingPlanDescriptor.java
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
import static com.avail.descriptor.DefinitionParsingPlanDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.DEFINITION_PARSING_PLAN;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompilerFragmentCache;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.compiler.ParsingConversionRule;
import com.avail.compiler.ParsingOperation;

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
public class DefinitionParsingPlanDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@link A_Bundle message bundle} that this will parse invocations
		 * of.
		 */
		BUNDLE,

		/**
		 * The {@link A_Definition definition} that this will parse invocations
		 * of.  Note that the exact argument type information is included in the
		 * parsing operations, but this doesn't statically determine which
		 * actual definition will be invoked.
		 */
		DEFINITION,

		/**
		 * A tuple of integers that describe how to parse an invocation of this
		 * method. The integers encode parsing instructions, many of which can
		 * be executed en masse against a piece of Avail source code for
		 * multiple potential methods. This is facilitated by the incremental
		 * construction of a {@linkplain MessageBundleTreeDescriptor message
		 * bundle tree}. The instructions are produced during analysis of the
		 * method name by the {@link MessageSplitter}, which has a description
		 * of the complete instruction set.
		 */
		PARSING_INSTRUCTIONS,
	}

	/**
	 * Used for describing logical aspects of the bundle in the Eclipse
	 * debugger.
	 */
	private enum FakeSlots
	implements ObjectSlotsEnum
	{
		/** Used for showing the parsing instructions symbolically. */
		SYMBOLIC_INSTRUCTIONS,

		/**
		 * Used to indicate a problem producing the parsing instructions, or
		 * printing them symbolically.
		 */
		ERROR_PRODUCING_INSTRUCTIONS;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Show the types of local variables and outer variables.
	 */
	@Override
	AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		// Weaken the plan's type to make sure we're not sending something it
		// won't understand.
		final List<AvailObjectFieldHelper> fields = new ArrayList<>();
		final A_DefinitionParsingPlan plan = object;
		fields.addAll(Arrays.asList(super.o_DescribeForDebugger(object)));
		try
		{
			final A_Tuple instructionsTuple = plan.parsingInstructions();
			final List<String> descriptionsList = new ArrayList<>();
			for (
				int i = 1, end = instructionsTuple.tupleSize();
				i <= end;
				i++)
			{
				final int encodedInstruction = instructionsTuple.tupleIntAt(i);
				final ParsingOperation operation = decode(encodedInstruction);
				final int operand = operand(encodedInstruction);
				final StringBuilder builder = new StringBuilder();
				builder.append(i);
				builder.append(". ");
				builder.append(operation.name());
				if (operand > 0)
				{
					builder.append(" (");
					builder.append(operand);
					builder.append(")");
					switch (operation)
					{
						case PARSE_PART:
						case PARSE_PART_CASE_INSENSITIVELY:
						{
							builder.append(" Part = '");
							builder.append(
								plan.bundle().messageParts().tupleAt(operand)
									.asNativeString());
							builder.append("'");
							break;
						}
						case PERMUTE_LIST:
						{
							builder.append(" Permutation = ");
							builder.append(
								MessageSplitter.permutationAtIndex(operand));
							break;
						}
						case TYPE_CHECK_ARGUMENT:
						{
							builder.append(" Type = ");
							builder.append(
								MessageSplitter.typeToCheck(operand));
							break;
						}
						case CONVERT:
						{
							builder.append(" Conversion = ");
							builder.append(
								ParsingConversionRule.ruleNumber(operand));
							break;
						}
						default:
							// Do nothing.
					}
				}
				descriptionsList.add(builder.toString());
			}
			fields.add(new AvailObjectFieldHelper(
				plan,
				FakeSlots.SYMBOLIC_INSTRUCTIONS,
				-1,
				descriptionsList.toArray(new String[descriptionsList.size()])));
		}
		catch (Exception e)
		{
			final CharArrayWriter trace = new CharArrayWriter();
			e.printStackTrace(new PrintWriter(trace));
			final String[] stackStrings = trace.toString().split("\n");
			int lineNumber = 0;
			for (final String line : stackStrings)
			{
				fields.add(new AvailObjectFieldHelper(
					plan,
					FakeSlots.ERROR_PRODUCING_INSTRUCTIONS,
					++lineNumber,
					line));
			}
		}
		return fields.toArray(new AvailObjectFieldHelper[fields.size()]);
	}

	@Override
	A_Bundle o_Bundle (final AvailObject object)
	{
		return object.slot(BUNDLE);
	}

	@Override
	A_Definition o_Definition (final AvailObject object)
	{
		return object.slot(DEFINITION);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		if (!another.kind().equals(DEFINITION_PARSING_PLAN.o()))
		{
			return false;
		}
		final A_DefinitionParsingPlan strongAnother =
			(A_DefinitionParsingPlan) another;
		return object.slot(DEFINITION) == strongAnother.definition()
			&& object.slot(BUNDLE) == strongAnother.bundle();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(DEFINITION).hash() ^ 0x92A26142
			- object.slot(BUNDLE).hash();
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return DEFINITION_PARSING_PLAN.o();
	}

	@Override @AvailMethod
	A_Tuple o_ParsingInstructions (final AvailObject object)
	{
		return object.slot(PARSING_INSTRUCTIONS);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		// The existing definitions are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		aStream.append("plan for ");
		aStream.append(object.bundle().message());
		aStream.append(" at ");
		aStream.append(object.definition().parsingSignature());
	}

	/**
	 * Create a new {@linkplain DefinitionParsingPlanDescriptor definition
	 * parsing plan} for the given parameters.  Do not install it.
	 *
	 * @param bundle The bundle for this plan.
	 * @param definition The definition for this plan.
	 * @return A new {@linkplain DefinitionParsingPlanDescriptor plan}.
	 */
	static A_DefinitionParsingPlan createPlan (
		final A_Bundle bundle,
		final A_Definition definition)
	{
		final AvailObject result = mutable.create();
		result.setSlot(BUNDLE, bundle);
		result.setSlot(DEFINITION, definition);
		result.setSlot(
			PARSING_INSTRUCTIONS,
			bundle.messageSplitter().instructionsTupleFor(
				definition.parsingSignature()));
		return result;
	}

	/**
	 * Construct a new {@link DefinitionParsingPlanDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private DefinitionParsingPlanDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.PARSING_PLAN_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link DefinitionParsingPlanDescriptor}. */
	private static final DefinitionParsingPlanDescriptor mutable =
		new DefinitionParsingPlanDescriptor(Mutability.MUTABLE);

	@Override
	DefinitionParsingPlanDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	DefinitionParsingPlanDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	/** The shared {@link DefinitionParsingPlanDescriptor}. */
	private static final DefinitionParsingPlanDescriptor shared =
		new DefinitionParsingPlanDescriptor(Mutability.SHARED);

	@Override
	DefinitionParsingPlanDescriptor shared ()
	{
		return shared;
	}
}
