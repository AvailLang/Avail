/**
 * SyntheticMessageBundleDescriptor.java
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

import static com.avail.descriptor.SyntheticMessageBundleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.exceptions.SignatureException;


/**
 * A {@linkplain SyntheticMessageBundleDescriptor synthetic message bundle} is
 * created when a {@linkplain MessageBundleTreeDescriptor message bundle tree}
 * has to branch to something finer-grained than a regular {@linkplain
 * MessageBundleDescriptor message bundle}.  A synthetic message bundle refers
 * to its name (an atom which can be used to look up a {@linkplain
 * MethodDescriptor method}), and a set of {@linkplain DefinitionDescriptor
 * definitions} of that method which are considered included by the synthetic
 * message bundle.  Thus, a synthetic message bundle acts as a portion of a
 * message bundle designated by a subset of the message bundle's definitions.
 *
 * <p>By including some method definitions but excluding others, a message
 * bundle tree can use synthetic message bundles to eliminate candidate method
 * invocations based on argument types.  Since a message bundle tree may be used
 * to attempt parsing of many bundles simultaneously, filtering by types after
 * parsing each argument expression is a very effective way of eliminating
 * spurious parses as early as possible.</p>
 *
 * <p>For example, say "_+_" and "_++" are two method names that are in scope,
 * but the former takes two numbers and the latter takes a variable holding an
 * integer.  In the message bundle tree representing the parse instruction that
 * immediately follows parsing of the first argument, the argument's expression
 * type is tested via a decision tree to determine which successor message
 * bundle tree to advance to.  These successor trees may contain some of the
 * same bundles as the originating tree, but they may also contain synthetic
 * bundles to immediately eliminate all definitions which are inapplicable.  If
 * this is made <em>precise</em>, then the final type check after successfully
 * parsing an entire method invocation can be completely eliminated.  It will
 * still be necessary to run any semantic restriction bodies, of course.</p>
 *
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class SyntheticMessageBundleDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain MessageBundleDescriptor message bundle} for which
		 * this is a portion.
		 */
		BASE_MESSAGE_BUNDLE,

		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain
		 * DefinitionDescriptor definitions} which are to be considered included
		 * in this synthetic bundle.
		 */
		INCLUDED_DEFINITIONS
	}

	@Override @AvailMethod
	boolean o_HasGrammaticalRestrictions (
		final AvailObject object)
	{
		final A_Bundle baseMessageBundle =
			object.slot(BASE_MESSAGE_BUNDLE);
		return baseMessageBundle.hasGrammaticalRestrictions();
	}

	@Override @AvailMethod
	A_Set o_GrammaticalRestrictions (
		final AvailObject object)
	{
		final A_Bundle baseMessageBundle =
			object.slot(BASE_MESSAGE_BUNDLE);
		return baseMessageBundle.grammaticalRestrictions();
	}

	@Override @AvailMethod
	A_Atom o_Message (
		final AvailObject object)
	{
		final A_Bundle baseMessageBundle =
			object.slot(BASE_MESSAGE_BUNDLE);
		return baseMessageBundle.message();
	}

	@Override @AvailMethod
	A_Tuple o_MessageParts (
		final AvailObject object)
	{
		final A_Bundle baseMessageBundle =
			object.slot(BASE_MESSAGE_BUNDLE);
		return baseMessageBundle.messageParts();
	}

	@Override @AvailMethod
	A_Tuple o_ParsingInstructions (
		final AvailObject object)
	{
		final A_Bundle baseMessageBundle =
			object.slot(BASE_MESSAGE_BUNDLE);
		return baseMessageBundle.parsingInstructions();
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == BASE_MESSAGE_BUNDLE
			|| e == INCLUDED_DEFINITIONS;
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
		if (isMutable())
		{
			aStream.append("(mut)");
		}
		aStream.append("synthetic bundle\"");
		aStream.append(object.message().atomName().asNativeString());
		aStream.append("\"");
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		return object.message().hash() ^ 0x048EE0A4;
	}

	@Override @AvailMethod
	A_Type o_Kind (
		final AvailObject object)
	{
		return MESSAGE_BUNDLE.o();
	}


	/**
	 * Create a new {@linkplain SyntheticMessageBundleDescriptor synthetic
	 * message bundle} for the given message bundle.  There are initially no
	 * {@linkplain DefinitionDescriptor definitions} included.
	 *
	 * @param baseMessageBundle
	 *            The message bundle on which the synthetic message bundle will
	 *            be based.
	 * @return A new {@linkplain SyntheticMessageBundleDescriptor message bundle}.
	 * @throws SignatureException If the message name is malformed.
	 */
	public static A_BasicObject newSyntheticBundle (
		final AvailObject baseMessageBundle)
	throws SignatureException
	{
		final AvailObject result = mutable.create();
		result.setSlot(BASE_MESSAGE_BUNDLE, baseMessageBundle);
		result.setSlot(INCLUDED_DEFINITIONS, SetDescriptor.empty());
		result.makeShared();
		return result;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * Construct a new {@link SyntheticMessageBundleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SyntheticMessageBundleDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link SyntheticMessageBundleDescriptor}. */
	private static final SyntheticMessageBundleDescriptor mutable =
		new SyntheticMessageBundleDescriptor(Mutability.MUTABLE);

	@Override
	SyntheticMessageBundleDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SyntheticMessageBundleDescriptor}. */
	private static final SyntheticMessageBundleDescriptor shared =
		new SyntheticMessageBundleDescriptor(Mutability.SHARED);

	@Override
	SyntheticMessageBundleDescriptor immutable ()
	{
		// There isn't an immutable descriptor, just the shared one.
		return shared;
	}

	@Override
	SyntheticMessageBundleDescriptor shared ()
	{
		return shared;
	}
}
