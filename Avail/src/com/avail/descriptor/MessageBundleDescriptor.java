/**
 * MessageBundleDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.compiler.MessageSplitter;
import com.avail.exceptions.SignatureException;
import com.avail.serialization.SerializerOperation;

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
 * Additionally, the message bundle's {@link #GRAMMATICAL_RESTRICTIONS} are held
 * here, rather than with the {@linkplain MethodDescriptor method}, since these
 * rules are intended to work with the actual tokens that occur (how sends are
 * written), not their underlying semantics (what the methods do).
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
		 * A tuple of sets, one for each underscore that occurs in the method
		 * name. The sets contain {@linkplain AtomDescriptor atoms} that name
		 * methods that must not directly occur at the corresponding argument
		 * position when parsing this method. If such a method invocation does
		 * occur in that argument position, that parse tree is simply eliminated
		 * as malformed. This allows the grammar to be specified by its
		 * negative space, a <em>much</em> more powerful (and modular) concept
		 * than the traditional specification of the positive space of the
		 * grammar.
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
		return e == ObjectSlots.GRAMMATICAL_RESTRICTIONS;
	}

	@Override @AvailMethod
	A_Method o_BundleMethod (final AvailObject object)
	{
		return object.mutableSlot(METHOD);
	}

	@Override @AvailMethod
	A_Tuple o_GrammaticalRestrictions (final AvailObject object)
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


	/**
	 * Add a tuple of grammatical restrictions to the specified {@linkplain
	 * MessageBundleDescriptor object}.
	 *
	 * @param object An object.
	 * @param restrictions Some restrictions.
	 */
	private void addGrammaticalRestrictions  (
		final AvailObject object,
		final A_Tuple restrictions)
	{
		assert restrictions.isTuple();
		restrictions.makeImmutable();
		A_Tuple merged = object.slot(GRAMMATICAL_RESTRICTIONS);
		for (int i = merged.tupleSize(); i >= 1; i--)
		{
			merged = merged.tupleAtPuttingCanDestroy(
				i,
				merged.tupleAt(i).setUnionCanDestroy(
					restrictions.tupleAt(i),
					true),
				true);
		}
		if (isShared())
		{
			merged = merged.traversed().makeShared();
		}
		object.setSlot(GRAMMATICAL_RESTRICTIONS, merged);
	}

	@Override @AvailMethod
	void o_AddGrammaticalRestrictions (
		final AvailObject object,
		final A_Tuple restrictions)
	{
		if (isShared())
		{
			synchronized (object)
			{
				addGrammaticalRestrictions(object, restrictions);
			}
		}
		else
		{
			addGrammaticalRestrictions(object, restrictions);
		}
	}

	/**
	 * Remove a tuple of grammatical restrictions from the specified {@linkplain
	 * MessageBundleDescriptor object}.
	 *
	 * @param object An object.
	 * @param obsoleteRestrictions Some restrictions.
	 */
	private void removeGrammaticalRestrictions  (
		final AvailObject object,
		final A_Tuple obsoleteRestrictions)
	{
		assert obsoleteRestrictions.isTuple();
		A_Tuple reduced = object.slot(GRAMMATICAL_RESTRICTIONS);
		for (int i = reduced.tupleSize(); i >= 1; i--)
		{
			reduced = reduced.tupleAtPuttingCanDestroy(
				i,
				reduced.tupleAt(i).setMinusCanDestroy(
					obsoleteRestrictions.tupleAt(i),
					true),
				true);
		}
		if (isShared())
		{
			reduced = reduced.traversed().makeShared();
		}
		object.setSlot(GRAMMATICAL_RESTRICTIONS, reduced);
	}

	@Override @AvailMethod
	void o_RemoveGrammaticalRestrictions (
		final AvailObject object,
		final A_Tuple obsoleteRestrictions)
	{
		if (isShared())
		{
			synchronized (object)
			{
				removeGrammaticalRestrictions(object, obsoleteRestrictions);
			}
		}
		else
		{
			removeGrammaticalRestrictions(object, obsoleteRestrictions);
		}
	}

	@Override @AvailMethod
	boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		final A_Tuple restrictions =
			object.mutableSlot(GRAMMATICAL_RESTRICTIONS);
		for (final A_Set setForArgument : restrictions)
		{
			if (setForArgument.setSize() > 0)
			{
				return true;
			}
		}
		return false;
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
		aStream.append(object.message().name().asNativeString());
		aStream.append("\"");
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.message().hash() ^ 0x312CAB9;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MESSAGE_BUNDLE.o();
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

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final AvailObject result = mutable.create();
		result.setSlot(METHOD, method);
		result.setSlot(MESSAGE, methodName);
		result.setSlot(MESSAGE_PARTS, splitter.messageParts());
		result.setSlot(
			GRAMMATICAL_RESTRICTIONS,
			tupleOfEmptySetsOfSize(splitter.numberOfUnderscores()));
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
		super(mutability);
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

	/**
	 * A list of tuples whose elements are all the empty set. Subscript N is an
	 * immutable tuple of size N whose elements are all the empty set.
	 */
	private static final List<A_Tuple> tuplesOfEmptySets =
		new ArrayList<A_Tuple>(
			Collections.singletonList(TupleDescriptor.empty()));

	/**
	 * Return an immutable tuple of the specified size consisting of empty sets.
	 *
	 * @param size The size of the resulting tuple.
	 * @return An immutable tuple of empty sets.
	 */
	private static synchronized A_Tuple tupleOfEmptySetsOfSize (final int size)
	{
		while (tuplesOfEmptySets.size() <= size)
		{
			final A_Tuple lastTuple =
				tuplesOfEmptySets.get(tuplesOfEmptySets.size() - 1);
			final A_Tuple newTuple = lastTuple.appendCanDestroy(
				SetDescriptor.empty(),
				true);
			newTuple.makeShared();
			tuplesOfEmptySets.add(newTuple);
		}
		return tuplesOfEmptySets.get(size);
	}
}
