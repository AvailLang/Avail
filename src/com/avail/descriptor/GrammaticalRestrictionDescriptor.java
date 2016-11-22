/**
 * GrammaticalRestrictionDescriptor.java
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

import static com.avail.descriptor.GrammaticalRestrictionDescriptor.IntegerSlots.*;
import static com.avail.descriptor.GrammaticalRestrictionDescriptor.ObjectSlots.*;
import com.avail.AvailRuntime;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;

/**
 * A {@code GrammaticalRestrictionDescriptor grammatical restriction} serves to
 * exclude specific parses of nested method sends, thereby defining the negative
 * space of a grammar.  As it happens, this negative space is significantly more
 * modular than traditional positive grammars, in which precedence between all
 * operations is specified explicitly.  Not only is the negative space scheme
 * more modular, but the class of grammars specifiable this way is much more
 * powerful.
 *
 * <p>
 * For example, one may specify that a call to "_*_" may not have a call to
 * "_+_" as either argument.  Thus, 1+2*3 can only be parsed as 1+(2*3).
 * Similarly, by forbidding a send of "_-_" to be the right argument of a send
 * of "_-_", this forbids interpreting 1-2-3 as 1-(2-3), leaving only the
 * traditional interpretation (1-2)-3.  But "_min_" and "_max_" have no natural
 * or conventional precedence relationship between each other.  Therefore an
 * expression like 1 min 2 max 3 should be marked as ambiguous.  However, we can
 * still treat 1 min 2 min 3 as (1 min 2) min 3 by saying "_min_" can't have
 * "_min_" as its right argument.  Similarly "_max_" can't have "_max_" as
 * its right argument.  As another example we can disallow "(_)" from occurring
 * as the sole argument of "(_)".  This still allows redundant parentheses for
 * clarity in expressions like 1+(2*3), but forbids ((1*2))+3.  This rule is
 * intended to catch manual rewriting errors in which the open and close
 * parenthesis counts happen to match in some complex expression, but multiple
 * parenthesis pairs could mislead a reader.
 * </p>
 *
 * <p>
 * Grammatical restrictions are syntactic only, and as such apply to {@linkplain
 * MessageBundleDescriptor message bundles} rather than {@linkplain
 * MethodDescriptor methods}.
 * </p>
 *
 * <p>
 * A grammatical restriction only affects the grammar of expressions in the
 * current module and any modules that recursively use or extend that module.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class GrammaticalRestrictionDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the {@link #HASH}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the hash value, a random value computed at
		 * construction time.
		 */
		static final BitField HASH = bitField(HASH_AND_MORE, 0, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain SetDescriptor
		 * sets} of {@linkplain MessageBundleDescriptor message bundles} which
		 * are restricted from occurring in the corresponding underscore
		 * positions.  Due to guillemet expressions («»), the underscore
		 * positions do not necessarily agree with the tuple of arguments passed
		 * in a method send.
		 * </p>
		 */
		ARGUMENT_RESTRICTION_SETS,

		/**
		 * The {@link MessageBundleDescriptor message bundle} for which this is
		 * a grammatical restriction.
		 */
		RESTRICTED_BUNDLE,

		/**
		 * The {@link ModuleDescriptor module} in which this grammatical
		 * restriction was added.
		 */
		DEFINITION_MODULE;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	@Override
	A_Bundle o_RestrictedBundle (final AvailObject object)
	{
		return object.slot(RESTRICTED_BUNDLE);
	}

	@Override
	A_Tuple o_ArgumentRestrictionSets (final AvailObject object)
	{
		return object.slot(ARGUMENT_RESTRICTION_SETS);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		// Compare by identity.
		return object.sameAddressAs(another);
	}

	@Override @AvailMethod
	A_Module o_DefinitionModule (final AvailObject object)
	{
		return object.slot(DEFINITION_MODULE);
	}

	/**
	 * Create a new {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restriction} with the specified information.  Make it {@link
	 * Mutability#SHARED SHARED}.
	 *
	 * @param argumentRestrictionSets
	 *            The tuple of sets of message bundles that restrict some
	 *            message bundle.
	 * @param restrictedBundle
	 *            The message bundle restricted by this grammatical restriction.
	 * @param module
	 *            The {@linkplain ModuleDescriptor module} in which this
	 *            grammatical restriction was defined.
	 * @return The new grammatical restriction.
	 */
	public static A_GrammaticalRestriction create (
		final A_Tuple argumentRestrictionSets,
		final A_Bundle restrictedBundle,
		final A_Module module)
	{
		final AvailObject result = mutable.create();
		result.setSlot(HASH, AvailRuntime.nextHash());
		result.setSlot(ARGUMENT_RESTRICTION_SETS, argumentRestrictionSets);
		result.setSlot(RESTRICTED_BUNDLE, restrictedBundle);
		result.setSlot(DEFINITION_MODULE, module);
		result.makeShared();
		return result;
	}

	/**
	 * Construct a new {@link GrammaticalRestrictionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private GrammaticalRestrictionDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.GRAMMATICAL_RESTRICTION_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link GrammaticalRestrictionDescriptor}. */
	private final static GrammaticalRestrictionDescriptor mutable =
		new GrammaticalRestrictionDescriptor(Mutability.MUTABLE);

	@Override
	GrammaticalRestrictionDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link GrammaticalRestrictionDescriptor}. */
	private final static GrammaticalRestrictionDescriptor shared =
		new GrammaticalRestrictionDescriptor(Mutability.SHARED);

	@Override
	GrammaticalRestrictionDescriptor immutable ()
	{
		// There is no immutable variant; answer the shared descriptor.
		return shared;
	}

	@Override
	GrammaticalRestrictionDescriptor shared ()
	{
		return shared;
	}
}
