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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompilerFragmentCache;
import com.avail.compiler.MessageSplitter;
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
	public static enum FakeSlots
	implements ObjectSlotsEnum
	{
		/** Used for showing the parsing instructions symbolically. */
		SYMBOLIC_INSTRUCTIONS;
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
		final List<AvailObjectFieldHelper> fields = new ArrayList<>();
		fields.addAll(Arrays.asList(super.o_DescribeForDebugger(object)));

		final A_Tuple instructionsTuple = object.parsingInstructions();
		final List<A_String> descriptionsList = new ArrayList<>();
		for (
			int i = 1, end = instructionsTuple.tupleSize();
			i <= end;
			i++)
		{
			final int encodedInstruction = instructionsTuple.tupleIntAt(i);
			final ParsingOperation operation =
				ParsingOperation.decode(encodedInstruction);
			final int operand = operation.operand(encodedInstruction);
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
						builder.append(" P=<");
						builder.append(
							object.messageParts().tupleAt(operand)
								.asNativeString());
						builder.append(">");
						break;
					}
					default:
						// Do nothing.
				}
			}
			descriptionsList.add(StringDescriptor.from(builder.toString()));
		}
		final A_Tuple descriptionsTuple = TupleDescriptor.fromList(descriptionsList);
		fields.add(new AvailObjectFieldHelper(
			object,
			FakeSlots.SYMBOLIC_INSTRUCTIONS,
			-1,
			descriptionsTuple));
		return fields.toArray(new AvailObjectFieldHelper[fields.size()]);
	}

	@Override
	A_Definition o_Definition (final AvailObject object)
	{
		return object.slot(DEFINITION);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
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

	/**
	 * Answer a String consisting of the name of the message with a visual
	 * indication inserted at the keyword or argument position related to the
	 * given program counter.
	 *
	 * @param parsingPlan
	 *        The {@link DefinitionParsingPlanDescriptor parsing plan} to
	 *        describe.
	 * @param pc
	 *        The 1-based instruction index into my {@linkplain
	 *        #o_ParsingInstructions(AvailObject) parsing instructions}.
	 * @return The annotated method name, a Java {@code String}.
	 */
	@Override @AvailMethod
	String o_NameHighlightingPc (
		final AvailObject parsingPlan,
		final int pc)
	{
		if (pc == 0)
		{
			return "(any method invocation)";
		}
		final A_Tuple instructions = parsingPlan.parsingInstructions();
		final A_Bundle bundle = parsingPlan.bundle();
		final MessageSplitter messageSplitter = bundle.messageSplitter();
		final int instruction = instructions.tupleIntAt(pc);
		final ParsingOperation op = ParsingOperation.decode(instruction);
		int argCounter = 0;
		final int position;
		if (op == PARSE_PART || op == PARSE_PART_CASE_INSENSITIVELY)
		{
			position = messageSplitter.messagePartPositions().get(
				op.keywordIndex(instruction) - 1);
		}
		else
		{
			for (int index = 1; index <= pc + 1; index++)
			{
				final int eachInstruction = instructions.tupleIntAt(index);
				final ParsingOperation eachOp =
					ParsingOperation.decode(eachInstruction);
				switch (eachOp)
				{
					case PARSE_ARGUMENT:
					case PARSE_TOP_VALUED_ARGUMENT:
					case PARSE_VARIABLE_REFERENCE:
					case PARSE_ARGUMENT_IN_MODULE_SCOPE:
					case PARSE_ANY_RAW_TOKEN:
					case PARSE_RAW_KEYWORD_TOKEN:
					case PARSE_RAW_STRING_LITERAL_TOKEN:
					case PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN:
					{
						argCounter++;
						break;
					}
					default:
						break;
				}
			}
			assert argCounter > 0;
			final int argPartNumber =
				messageSplitter.underscorePartNumbers().get(argCounter - 1);
			position = messageSplitter.messagePartPositions().get(
				argPartNumber - 1);
		}
		final String string = bundle.message().atomName().asNativeString();
		final String annotatedString =
			string.substring(0, position - 1)
			+ AvailCompiler.errorIndicatorSymbol
			+ string.substring(position - 1);
		final A_String availString = StringDescriptor.from(annotatedString);
		return availString.toString();
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
		aStream.append("parsing plan for definition ");
		aStream.append(object.definition());
		aStream.append(" of bundle ");
		aStream.append(object.bundle());
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
		final List<A_Type> typesForTesting = new ArrayList<A_Type>();
		final AvailObject result = mutable.create();
		result.setSlot(BUNDLE, bundle);
		result.setSlot(DEFINITION, definition);
		result.setSlot(
			PARSING_INSTRUCTIONS,
			bundle.messageSplitter().instructionsTupleFor(
				definition.bodySignature()));
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
		super(mutability, ObjectSlots.class, null);
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
