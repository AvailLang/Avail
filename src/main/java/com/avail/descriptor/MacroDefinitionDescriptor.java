/*
 * MacroDefinitionDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import static com.avail.descriptor.ListPhraseTypeDescriptor.createListNodeType;
import static com.avail.descriptor.MacroDefinitionDescriptor.ObjectSlots.BODY_BLOCK;
import static com.avail.descriptor.MacroDefinitionDescriptor.ObjectSlots.DEFINITION_METHOD;
import static com.avail.descriptor.MacroDefinitionDescriptor.ObjectSlots.MACRO_PREFIX_FUNCTIONS;
import static com.avail.descriptor.MacroDefinitionDescriptor.ObjectSlots.MODULE;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeFromTupleOfTypes;
import static com.avail.descriptor.TypeDescriptor.Types.MACRO_DEFINITION;

/**
 * Macros are extremely hygienic in Avail.  They are defined almost exactly like
 * ordinary multimethods.  The first difference is which primitive is used to
 * define a macro versus a method.  The other difference is that instead of
 * generating code at an occurrence to call a method (a call site), the macro
 * body is immediately invoked, passing the {@link PhraseDescriptor phrases}
 * that occupy the corresponding argument positions in the method/macro name.
 * The macro body will then do what it does and return a suitable phrase.
 *
 * <p>Instead of returning a new phrase, a macro body may instead reject
 * parsing, the same way a {@link SemanticRestrictionDescriptor semantic
 * restriction may}.  As you might expect, the diagnostic message provided to
 * the parse rejection primitive will be presented to the user.</p>
 *
 * <p>As with methods, repeated arguments of macros are indicated with
 * guillemets («») and the double-dagger (‡).  The type of such an argument for
 * a method is a tuple of tuples whose elements correspond to the underscores
 * (_) and guillemet groups contained therein.  When exactly one underscore or
 * guillemet group occurs within a group, then a simple tuple of values is
 * expected (rather than a tuple of tuples).  Macros expect tuples in an
 * analogous way, but (1) the bottom-level pieces are always phrases, and (2)
 * the grouping is actually via {@link ListPhraseDescriptor list phrases} rather
 * than tuples.  Thus, a macro always operates on phrases.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MacroDefinitionDescriptor
extends DefinitionDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * Duplicated from parent.  The method in which this definition occurs.
		 */
		DEFINITION_METHOD,

		/**
		 * The {@link ModuleDescriptor module} in which this definition occurs.
		 */
		MODULE,

		/**
		 * The {@linkplain FunctionDescriptor function} to invoke to transform
		 * the (complete) argument phrases into a suitable replacement phrase.
		 */
		BODY_BLOCK,

		/**
		 * A {@linkplain A_Tuple tuple} of {@linkplain A_Function functions}
		 * corresponding with occurrences of section checkpoints ("§") in the
		 * message name.  Each function takes the collected argument phrases
		 * thus far, and has the opportunity to reject the parse or read/write
		 * parse-specific information in a fiber-specific variable with the key
		 * {@link SpecialAtom#CLIENT_DATA_GLOBAL_KEY}.
		 */
		MACRO_PREFIX_FUNCTIONS;

		static
		{
			assert DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD.ordinal()
				== DEFINITION_METHOD.ordinal();
			assert DefinitionDescriptor.ObjectSlots.MODULE.ordinal()
				== MODULE.ordinal();
		}
	}

	@Override @AvailMethod
	A_Function o_BodyBlock (
		final AvailObject object)
	{
		return object.slot(BODY_BLOCK);
	}

	/**
	 * Answer my signature.
	 */
	@Override @AvailMethod
	A_Type o_BodySignature (
		final AvailObject object)
	{
		return object.slot(BODY_BLOCK).kind();
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		return object.bodyBlock().hash() ^ 0x67f6ec56 + 0x0AFB0E62;
	}

	@Override @AvailMethod
	boolean o_IsMacroDefinition (
		final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	A_Type o_Kind (
		final AvailObject object)
	{
		return MACRO_DEFINITION.o();
	}

	@Override
	A_Type o_ParsingSignature (final AvailObject object)
	{
		// A macro definition's parsing signature is a list phrase type whose
		// covariant subexpressions type is the body block's kind's arguments
		// type.
		final A_Type argsTupleType =
			object.slot(BODY_BLOCK).kind().argsTupleType();
		final A_Type sizes = argsTupleType.sizeRange();
		// TODO MvG - Maybe turn this into a check.
		assert sizes.lowerBound().extractInt()
			== sizes.upperBound().extractInt();
		assert sizes.lowerBound().extractInt()
			== object.slot(DEFINITION_METHOD).numArgs();
		// TODO MvG - 2016-08-21 deal with permutation of main list.
		return createListNodeType(
			PhraseKind.LIST_PHRASE,
			tupleTypeFromTupleOfTypes(argsTupleType, A_Type::expressionType),
			argsTupleType);
	}

	@Override
	A_Tuple o_PrefixFunctions (final AvailObject object)
	{
		return object.slot(MACRO_PREFIX_FUNCTIONS);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MACRO_DEFINITION;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("macro definition");
		writer.write("definition method");
		object.slot(DEFINITION_METHOD).methodName().writeTo(writer);
		writer.write("definition module");
		object.definitionModuleName().writeTo(writer);
		writer.write("body block");
		object.slot(BODY_BLOCK).writeTo(writer);
		writer.write("macro prefix functions");
		object.slot(MACRO_PREFIX_FUNCTIONS).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("macro definition");
		writer.write("definition method");
		object.slot(DEFINITION_METHOD).methodName().writeTo(writer);
		writer.write("definition module");
		object.definitionModuleName().writeTo(writer);
		writer.write("body block");
		object.slot(BODY_BLOCK).writeSummaryTo(writer);
		writer.write("macro prefix functions");
		object.slot(MACRO_PREFIX_FUNCTIONS).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new macro signature from the provided argument.
	 *
	 * @param method
	 *            The {@linkplain MethodDescriptor method} in which to define
	 *            this macro definition.
	 * @param definitionModule
	 *            The module in which this definition is added.
	 * @param bodyBlock
	 *            The body of the signature.  This will be invoked when a call
	 *            site is compiled, passing the sub<em>expressions</em>
	 *            ({@linkplain PhraseDescriptor phrases}) as arguments.
	 * @param prefixFunctions
	 *            The tuple of prefix functions that correspond with the section
	 *            checkpoints ("§") in the macro's name.
	 * @return
	 *            A macro signature.
	 */
	public static AvailObject newMacroDefinition (
		final A_Method method,
		final A_Module definitionModule,
		final A_Function bodyBlock,
		final A_Tuple prefixFunctions)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(DEFINITION_METHOD, method);
		instance.setSlot(MODULE, definitionModule);
		instance.setSlot(BODY_BLOCK, bodyBlock);
		instance.setSlot(MACRO_PREFIX_FUNCTIONS, prefixFunctions);
		return instance.makeShared();
	}

	/**
	 * Construct a new {@code MacroDefinitionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MacroDefinitionDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link MacroDefinitionDescriptor}. */
	private static final MacroDefinitionDescriptor mutable =
		new MacroDefinitionDescriptor(Mutability.MUTABLE);

	@Override
	MacroDefinitionDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	MacroDefinitionDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	/** The shared {@link MacroDefinitionDescriptor}. */
	private static final MacroDefinitionDescriptor shared =
		new MacroDefinitionDescriptor(Mutability.SHARED);

	@Override
	MacroDefinitionDescriptor shared ()
	{
		return shared;
	}
}
