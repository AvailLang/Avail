/**
 * MessageBundleDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.MessageSplitter;

/**
 *
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MessageBundleDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * An {@linkplain AtomDescriptor atom} which is the "true name" of this
		 * method.
		 */
		MESSAGE,

		/**
		 * The tuple of {@linkplain StringDescriptor strings} comprising the
		 * method name's tokens.  These tokens may be a single operator
		 * character, a sequence of alphanumerics, the underscore "_", an open
		 * chevron "«", a close chevron "»", the double-dagger "‡", the ellipsis
		 * "…", or any backquoted character "`x".  Some of the parsing
		 * instructions index this tuple (e.g., to represent parsing a
		 * particular keyword).  This tuple is produced by the {@link
		 * MessageSplitter}.
		 */
		MESSAGE_PARTS,

		/**
		 * A tuple of sets, one for each underscore that occurs in the method
		 * name.  The sets contain {@linkplain AtomDescriptor atoms} that name
		 * methods that must not directly occur at the corresponding argument
		 * position when parsing this method.  If such a method invocation does
		 * occur in that argument position, that parse tree is simply eliminated
		 * as malformed.  This allows the grammar to be specified by its
		 * negative space, a <em>much</em> more powerful (and modular) concept
		 * than the traditional specification of the positive space of the
		 * grammar.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * A tuple of integers that describe how to parse an invocation of this
		 * method.  The integers encode parsing instructions, many of which can
		 * be executed en masse against a piece of Avail source code for
		 * multiple potential methods.  This is facilitated by the incremental
		 * construction of a {@linkplain MessageBundleTreeDescriptor message
		 * bundle tree}.  The instructions are produced during analysis of the
		 * method name by the {@link MessageSplitter}, which has a description
		 * of the complete instruction set.
		 */
		PARSING_INSTRUCTIONS
	}

	@Override @AvailMethod
	void o_AddGrammaticalRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictions)
	{
		assert restrictions.isTuple();
		restrictions.makeImmutable();
		AvailObject merged = object.slot(ObjectSlots.GRAMMATICAL_RESTRICTIONS);
		for (int i = merged.tupleSize(); i >= 1; i--)
		{
			merged = merged.tupleAtPuttingCanDestroy(
				i,
				merged.tupleAt(i).setUnionCanDestroy(
					restrictions.tupleAt(i),
					true),
				true);
		}
		object.setSlot(ObjectSlots.GRAMMATICAL_RESTRICTIONS, merged);
	}

	@Override @AvailMethod
	void o_RemoveGrammaticalRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject obsoleteRestrictions)
	{
		assert obsoleteRestrictions.isTuple();
		AvailObject reduced = object.slot(ObjectSlots.GRAMMATICAL_RESTRICTIONS);
		for (int i = reduced.tupleSize(); i >= 1; i--)
		{
			reduced = reduced.tupleAtPuttingCanDestroy(
				i,
				reduced.tupleAt(i).setMinusCanDestroy(
					obsoleteRestrictions.tupleAt(i),
					true),
				true);
		}
		object.setSlot(ObjectSlots.GRAMMATICAL_RESTRICTIONS, reduced);
	}

	@Override @AvailMethod
	boolean o_HasGrammaticalRestrictions (
		final @NotNull AvailObject object)
	{
		final AvailObject restrictions =
			object.slot(ObjectSlots.GRAMMATICAL_RESTRICTIONS);
		for (final AvailObject setForArgument : restrictions)
		{
			if (setForArgument.setSize() > 0)
			{
				return true;
			}
		}
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_GrammaticalRestrictions (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.GRAMMATICAL_RESTRICTIONS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Message (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.MESSAGE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MessageParts (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.MESSAGE_PARTS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ParsingInstructions (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.PARSING_INSTRUCTIONS);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.GRAMMATICAL_RESTRICTIONS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		// The existing implementations are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		if (isMutable)
		{
			aStream.append("(mut)");
		}
		aStream.append("bundle\"");
		aStream.append(object.message().name().asNativeString());
		aStream.append("\"");
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.message().hash() ^ 0x312CAB9;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return MESSAGE_BUNDLE.o();
	}


	/**
	 * A list of tuples whose elements are all the empty set.  Subscript N is an
	 * immutable tuple of size N whose elements are all the empty set.
	 */
	static List<AvailObject> tuplesOfEmptySets;

	/**
	 * Return an immutable tuple of the specified size consisting of empty sets.
	 *
	 * @param size The size of the resulting tuple.
	 * @return An immutable tuple of empty sets.
	 */
	static AvailObject tupleOfEmptySetsOfSize (final int size)
	{
		while (tuplesOfEmptySets.size() <= size)
		{
			final AvailObject lastTuple =
				tuplesOfEmptySets.get(tuplesOfEmptySets.size() - 1);
			final AvailObject newTuple = TupleDescriptor.append(
				lastTuple,
				SetDescriptor.empty());
			newTuple.makeImmutable();
			tuplesOfEmptySets.add(newTuple);
		}
		return tuplesOfEmptySets.get(size);
	}

	/**
	 * Create a list of reusable immutable tuples of empty sets.
	 */
	static void createWellKnownObjects ()
	{
		tuplesOfEmptySets = new ArrayList<AvailObject>(20);
		tuplesOfEmptySets.add(TupleDescriptor.empty());
	}

	/**
	 * Discard the cache of tuples of empty sets.
	 */
	static void clearWellKnownObjects ()
	{
		tuplesOfEmptySets = null;
	}

	/**
	 * Create a new {@linkplain MessageBundleDescriptor message bundle} for the
	 * given message.  Also use the provided tuple of message parts and parsing
	 * instructions.
	 *
	 * @param message The message name, an {@linkplain AtomDescriptor atom}.
	 * @return A new {@linkplain MessageBundleDescriptor message bundle}.
	 */
	public static AvailObject newBundle (
		final AvailObject message)
	{
		assert message.isAtom();
		final MessageSplitter splitter = new MessageSplitter(message.name());
		final AvailObject restrictions = tupleOfEmptySetsOfSize(
			splitter.numberOfUnderscores());
		final AvailObject result = mutable().create();
		result.setSlot(ObjectSlots.MESSAGE, message);
		result.setSlot(ObjectSlots.MESSAGE_PARTS, splitter.messageParts());
		result.setSlot(ObjectSlots.GRAMMATICAL_RESTRICTIONS, restrictions);
		result.setSlot(
			ObjectSlots.PARSING_INSTRUCTIONS,
			splitter.instructionsTuple());
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@link MessageBundleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MessageBundleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MessageBundleDescriptor}.
	 */
	private final static MessageBundleDescriptor mutable =
		new MessageBundleDescriptor(true);

	/**
	 * Answer the mutable {@link MessageBundleDescriptor}.
	 *
	 * @return The mutable {@link MessageBundleDescriptor}.
	 */
	public static MessageBundleDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MessageBundleDescriptor}.
	 */
	private final static MessageBundleDescriptor immutable =
		new MessageBundleDescriptor(false);

	/**
	 * Answer the immutable {@link MessageBundleDescriptor}.
	 *
	 * @return The immutable {@link MessageBundleDescriptor}.
	 */
	public static MessageBundleDescriptor immutable ()
	{
		return immutable;
	}
}
