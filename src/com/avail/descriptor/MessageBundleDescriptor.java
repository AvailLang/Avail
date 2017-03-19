/**
 * MessageBundleDescriptor.java
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

import static com.avail.descriptor.MessageBundleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.avail.annotations.AvailMethod;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.exceptions.MalformedMessageException;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
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
public final class MessageBundleDescriptor
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
		 * The {@link MessageSplitter} that describes how to parse invocations
		 * of this message bundle.
		 */
		MESSAGE_SPLITTER_POJO,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * GrammaticalRestrictionDescriptor grammatical restrictions} that apply
		 * to this message bundle.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * The {@link A_Map} from {@link A_Definition} to {@link
		 * A_DefinitionParsingPlan}.  The keys should always agree with the
		 * {@link A_Method}'s collection of definitions and macro definitions.
		 */
		DEFINITION_PARSING_PLANS,

		/**
		 * A pojo holding the {@link Statistic} for dynamic lookups of this
		 * bundle, or others with the same name (say from reloading a module).
		 */
		DYNAMIC_LOOKUP_STATS_POJO;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == METHOD
			|| e == GRAMMATICAL_RESTRICTIONS
			|| e == DEFINITION_PARSING_PLANS;
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
	void o_AddDefinitionParsingPlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan)
	{
		if (isShared())
		{
			synchronized (object)
			{
				addDefinitionParsingPlan(object, plan);
			}
		}
		else
		{
			addDefinitionParsingPlan(object, plan);
		}
	}

	@Override @AvailMethod
	A_Method o_BundleMethod (final AvailObject object)
	{
		return object.mutableSlot(METHOD);
	}

	@Override
	A_Map o_DefinitionParsingPlans (final AvailObject object)
	{
		return object.slot(DEFINITION_PARSING_PLANS);
	}

	@Override
	Statistic o_DynamicLookupStatistic (final AvailObject object)
	{
		final A_BasicObject pojo = object.slot(DYNAMIC_LOOKUP_STATS_POJO);
		return (Statistic)pojo.javaObjectNotNull();
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	A_Set o_GrammaticalRestrictions (final AvailObject object)
	{
		return object.mutableSlot(GRAMMATICAL_RESTRICTIONS);
	}

	@Override @AvailMethod
	boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		return object.mutableSlot(GRAMMATICAL_RESTRICTIONS).setSize() > 0;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.message().hash() ^ 0x0312CAB9;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MESSAGE_BUNDLE.o();
	}

	@Override @AvailMethod
	A_Atom o_Message (final AvailObject object)
	{
		return object.slot(MESSAGE);
	}

	@Override @AvailMethod
	A_Tuple o_MessageParts (final AvailObject object)
	{
		final A_BasicObject splitterPojo = object.slot(MESSAGE_SPLITTER_POJO);
		MessageSplitter messageSplitter =
			(MessageSplitter) splitterPojo.javaObject();
		assert messageSplitter != null;
		return messageSplitter.messageParts();
	}

	@Override @AvailMethod
	MessageSplitter o_MessageSplitter(final AvailObject object)
	{
		final A_BasicObject splitterPojo = object.slot(MESSAGE_SPLITTER_POJO);
		return (MessageSplitter)splitterPojo.javaObject();
	}

	@Override @AvailMethod
	void o_RemovePlanForDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		if (isShared())
		{
			synchronized (object)
			{
				removePlanForDefinition(object, definition);
			}
		}
		else
		{
			removePlanForDefinition(object, definition);
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
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MESSAGE_BUNDLE;
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

	/**
	 * Add a {@link DefinitionParsingPlanDescriptor definition parsing plan} to
	 * this bundle.  This is performed to make the bundle agree with the
	 * method's definitions and macro definitions.
	 *
	 * @param object The affected message bundle.
	 * @param plan A definition parsing plan.
	 */
	private void addDefinitionParsingPlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan)
	{
		A_Map plans = object.slot(DEFINITION_PARSING_PLANS);
		plans = plans.mapAtPuttingCanDestroy(plan.definition(), plan, true);
		object.setSlot(DEFINITION_PARSING_PLANS, plans.makeShared());
	}

	/**
	 * Remove a {@link A_DefinitionParsingPlan} from this bundle, specifically
	 * the one associated with the give {@link A_Definition}.  This is performed
	 * to make the bundle agree with the method's definitions and macro
	 * definitions.
	 *
	 * @param object The affected message bundle.
	 * @param definition A definition whose plan should be removed.
	 */
	private void removePlanForDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		A_Map plans = object.mutableSlot(DEFINITION_PARSING_PLANS);
		assert plans.hasKey(definition);
		plans = plans.mapWithoutKeyCanDestroy(definition, true);
		object.setMutableSlot(DEFINITION_PARSING_PLANS, plans.makeShared());
	}

	/**
	 * Add a grammatical restriction to the specified {@linkplain
	 * MessageBundleDescriptor message bundle}.
	 *
	 * @param object The affected message bundle.
	 * @param grammaticalRestriction A grammatical restriction.
	 */
	private void addGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		A_Set restrictions = object.slot(GRAMMATICAL_RESTRICTIONS);
		restrictions = restrictions.setWithElementCanDestroy(
			grammaticalRestriction, true);
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
			GRAMMATICAL_RESTRICTIONS, restrictions.makeShared());
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
		aStream.append("bundle \"");
		aStream.append(object.message().atomName().asNativeString());
		aStream.append("\"");
	}

	/**
	 * Statistics about dynamic lookups, keyed by the message bundle's message's
	 * print representation (an Avail string).
	 */
	private static final Map<A_String, Statistic> dynamicLookupStatsByString =
		new HashMap<>();

	/**
	 * Create a new {@linkplain MessageBundleDescriptor message bundle} for the
	 * given message.  Add the bundle to the method's collection of {@linkplain
	 * MethodDescriptor.ObjectSlots#OWNING_BUNDLES owning bundles}.
	 *
	 * @param methodName The message name, an {@linkplain AtomDescriptor atom}.
	 * @param method The method that this bundle represents.
	 * @param splitter A MessageSplitter for this message name.
	 * @return A new {@linkplain MessageBundleDescriptor message bundle}.
	 * @throws MalformedMessageException If the message name is malformed.
	 */
	public static A_Bundle newBundle (
		final A_Atom methodName,
		final A_Method method,
		final MessageSplitter splitter)
	{
		assert methodName.isAtom();
		assert splitter.numberOfArguments() == method.numArgs();
		assert splitter.messageName().equals(methodName.atomName());

		final AvailObject splitterPojo =
			RawPojoDescriptor.identityWrap(splitter);
		final AvailObject result = mutable.create();
		result.setSlot(METHOD, method);
		result.setSlot(MESSAGE, methodName);
		result.setSlot(MESSAGE_SPLITTER_POJO, splitterPojo);
		result.setSlot(GRAMMATICAL_RESTRICTIONS, SetDescriptor.empty());
		A_Map plans = MapDescriptor.empty();
		for (final A_Definition definition : method.definitionsTuple())
		{
			final A_DefinitionParsingPlan plan =
				DefinitionParsingPlanDescriptor.createPlan(
					result, definition);
			plans = plans.mapAtPuttingCanDestroy(definition, plan, true);
		}
		for (final A_Definition definition : method.macroDefinitionsTuple())
		{
			final A_DefinitionParsingPlan plan =
				DefinitionParsingPlanDescriptor.createPlan(
					result, definition);
			plans = plans.mapAtPuttingCanDestroy(definition, plan, true);
		}
		result.setSlot(DEFINITION_PARSING_PLANS, plans);

		// Look up the statistic by name, so that multiple loads of a module
		// will accumulate.
		final String nameString = methodName.toString();
		final A_String name = StringDescriptor.from(nameString).makeShared();
		Statistic stat;
		synchronized (dynamicLookupStatsByString)
		{
			stat = dynamicLookupStatsByString.get(name);
			if (stat == null)
			{
				stat = new Statistic(
					"Lookup " + nameString,
					StatisticReport.DYNAMIC_LOOKUPS);
				dynamicLookupStatsByString.put(name, stat);
			}
		}
		A_BasicObject pojo = RawPojoDescriptor.identityWrap(stat);
		result.setSlot(DYNAMIC_LOOKUP_STATS_POJO, pojo);

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
		super(mutability, TypeTag.BUNDLE_TAG, ObjectSlots.class, null);
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
