/**
 * MessageBundleDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.MessageBundleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.compiler.MessageSplitter;
import com.avail.exceptions.SignatureException;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

/**
 * A message bundle is how a message name is bound to a {@linkplain
 * MethodDescriptor method}.  Besides the message name, the bundle also
 * contains information useful for parsing its invocations.  This information
 * includes parsing instructions which, when aggregated with other bundles,
 * forms a {@linkplain MessageBundleTreeDescriptor message bundle tree}.  This
 * allows parsing of multiple similar methods <em>in aggregate</em>, avoiding
 * the cost of repeatedly parsing the same constructs (tokens and
 * subexpressions) for different purposes.
 *
 * <p>
 * Additionally, the message bundle's {@link
 * ObjectSlots#GRAMMATICAL_RESTRICTIONS grammatical restrictions} are held here,
 * rather than with the {@linkplain MethodDescriptor method}, since these rules
 * are intended to work with the actual tokens that occur (how sends are
 * written), not their underlying semantics (what the methods do).
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class MessageBundleDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain MethodDescriptor method} for which this is a message
		 * bundle.  That is, if a use of this bundle is parsed, the resulting
		 * code will ultimately invoke this method.  A method may have multiple
		 * such bundles due to renaming of imports.
		 */
		METHOD,

		/**
		 * An {@linkplain AtomDescriptor atom} which is the "true name" of this
		 * bundle.  Due to import renaming, a {@linkplain MethodDescriptor
		 * method} might have multiple such names, one per bundle.
		 */
		MESSAGE,

		/**
		 * The tuple of {@linkplain StringDescriptor strings} comprising the
		 * method name's tokens. These tokens may be a single operator
		 * character, a sequence of alphanumerics, the underscore "_", an open
		 * guillemet "«", a close guillemet "»", the double-dagger "‡", the
		 * ellipsis "…", or any backquoted character "`x". Some of the parsing
		 * instructions index this tuple (e.g., to represent parsing a
		 * particular keyword). This tuple is produced by the {@link
		 * MessageSplitter}.
		 */
		MESSAGE_PARTS,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * GrammaticalRestrictionDescriptor grammatical restrictions} that apply
		 * to this message bundle.
		 */
		GRAMMATICAL_RESTRICTIONS,

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
		PARSING_INSTRUCTIONS
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == METHOD || e == GRAMMATICAL_RESTRICTIONS;
	}

	@Override @AvailMethod
	A_Method o_BundleMethod (final AvailObject object)
	{
		return object.mutableSlot(METHOD);
	}

	@Override @AvailMethod
	A_Set o_GrammaticalRestrictions (final AvailObject object)
	{
		return object.mutableSlot(GRAMMATICAL_RESTRICTIONS);
	}

	@Override @AvailMethod
	A_Atom o_Message (final AvailObject object)
	{
		return object.slot(MESSAGE);
	}

	@Override @AvailMethod
	A_Tuple o_MessageParts (final AvailObject object)
	{
		return object.slot(MESSAGE_PARTS);
	}

	@Override @AvailMethod
	A_Tuple o_ParsingInstructions (final AvailObject object)
	{
		return object.slot(PARSING_INSTRUCTIONS);
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MESSAGE_BUNDLE;
	}


	@Override @AvailMethod
	void o_AddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		if (isShared())
		{
			synchronized (object)
			{
				addGrammaticalRestriction(object, grammaticalRestriction);
			}
		}
		else
		{
			addGrammaticalRestriction(object, grammaticalRestriction);
		}
	}

	@Override @AvailMethod
	void o_RemoveGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		if (isShared())
		{
			synchronized (object)
			{
				removeGrammaticalRestriction(object, obsoleteRestriction);
			}
		}
		else
		{
			removeGrammaticalRestriction(object, obsoleteRestriction);
		}
	}

	@Override @AvailMethod
	boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		return object.mutableSlot(GRAMMATICAL_RESTRICTIONS).setSize() > 0;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.message().hash() ^ 0x0312CAB9;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("message bundle");
		writer.write("method");
		object.slot(MESSAGE).atomName().writeTo(writer);
		writer.endObject();
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MESSAGE_BUNDLE.o();
	}

	/**
	 * Add a grammatical restriction to the specified {@linkplain
	 * MessageBundleDescriptor message bundle}.
	 *
	 * @param object The affected message bundle.
	 * @param grammaticalRestriction A grammatical restriction.
	 */
	private void addGrammaticalRestriction  (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		A_Set restrictions = object.slot(GRAMMATICAL_RESTRICTIONS);
		restrictions = restrictions.setWithElementCanDestroy(
			grammaticalRestriction,
			true);
		object.setSlot(GRAMMATICAL_RESTRICTIONS, restrictions.makeShared());
	}

	/**
	 * Remove a grammatical restriction from this {@linkplain
	 * MessageBundleDescriptor message bundle}.
	 *
	 * @param object A message bundle.
	 * @param obsoleteRestriction The grammatical restriction to remove.
	 */
	private void removeGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		A_Set restrictions = object.mutableSlot(GRAMMATICAL_RESTRICTIONS);
		restrictions = restrictions.setWithoutElementCanDestroy(
			obsoleteRestriction,
			true);
		object.setMutableSlot(
			GRAMMATICAL_RESTRICTIONS,
			restrictions.makeShared());
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		// The existing definitions are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		aStream.append("bundle\"");
		aStream.append(object.message().atomName().asNativeString());
		aStream.append("\"");
	}

	/**
	 * Create a new {@linkplain MessageBundleDescriptor message bundle} for the
	 * given message.  Add the bundle to the method's collection of {@linkplain
	 * MethodDescriptor.ObjectSlots#OWNING_BUNDLES owning bundles}.
	 *
	 * @param methodName The message name, an {@linkplain AtomDescriptor atom}.
	 * @param method The method that this bundle represents.
	 * @return A new {@linkplain MessageBundleDescriptor message bundle}.
	 * @throws SignatureException If the message name is malformed.
	 */
	public static A_Bundle newBundle (
		final A_Atom methodName,
		final A_Method method)
	throws SignatureException
	{
		assert methodName.isAtom();

		final MessageSplitter splitter = new MessageSplitter(
			methodName.atomName());
		final AvailObject result = mutable.create();
		result.setSlot(METHOD, method);
		result.setSlot(MESSAGE, methodName);
		result.setSlot(MESSAGE_PARTS, splitter.messageParts());
		result.setSlot(GRAMMATICAL_RESTRICTIONS, SetDescriptor.empty());
		result.setSlot(PARSING_INSTRUCTIONS, splitter.instructionsTuple());
		result.makeShared();
		method.methodAddBundle(result);
		return result;
	}

	/**
	 * Construct a new {@link MessageBundleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MessageBundleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link MessageBundleDescriptor}. */
	private static final MessageBundleDescriptor mutable =
		new MessageBundleDescriptor(Mutability.MUTABLE);

	@Override
	MessageBundleDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	MessageBundleDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	/** The shared {@link MessageBundleDescriptor}. */
	private static final MessageBundleDescriptor shared =
		new MessageBundleDescriptor(Mutability.SHARED);

	@Override
	MessageBundleDescriptor shared ()
	{
		return shared;
	}
}
