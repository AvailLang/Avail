/**
 * StateVisitActions.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.utility.fsm;

import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The complete runtime representation of a state.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <EventType>
 *        The event type.
 * @param <GuardKeyType>
 *        The guard key type.
 * @param <ActionKeyType>
 *        The action key type.
 * @param <StateType>
 *        The state type.
 * @param <MementoType>
 *        The type of memento passed to guards and actions.
 */
final class StateSummary<
	StateType extends Enum<StateType>,
	EventType extends Enum<EventType>,
	GuardKeyType extends Enum<GuardKeyType>,
	ActionKeyType extends Enum<ActionKeyType>,
	MementoType>
{
	/**
	 * The state for which the receiver is a {@linkplain StateSummary summary}.
	 */
	private final StateType state;

	/**
	 * Answer the state for which the receiver is a {@link StateSummary
	 * summary}.
	 *
	 * @return A state.
	 */
	StateType state ()
	{
		return state;
	}

	/**
	 * The action key whose {@linkplain Continuation1 action} should be invoked
	 * when entering a particular state.
	 */
	private @Nullable ActionKeyType entryActionKey;

	/**
	 * Answer the action key whose {@linkplain Continuation1 action} should be
	 * performed when entering a particular state.
	 *
	 * @return The action key for the <em>entry</em> action.
	 */
	@Nullable ActionKeyType getEntryActionKey ()
	{
		return entryActionKey;
	}

	/**
	 * Set the action key whose {@linkplain Continuation1 action} should be
	 * performed when entering a particular state.
	 *
	 * @param actionKey
	 *        An action key.
	 */
	void setEntryActionKey (final ActionKeyType actionKey)
	{
		assert entryActionKey == null;
		entryActionKey = actionKey;
	}

	/**
	 * The {@linkplain Continuation1 action} to perform upon entering my state.
	 */
	private @Nullable Continuation1<? super MementoType> entryAction;

	/**
	 * Get the {@linkplain Continuation1 action} to perform upon entering my
	 * state.
	 *
	 * @return The action to perform upon entry.
	 */
	@Nullable Continuation1<? super MementoType> getEntryAction ()
	{
		return entryAction;
	}


	/**
	 * The action key whose {@linkplain Continuation1 action} should be invoked
	 * when exiting a particular state.
	 */
	private @Nullable ActionKeyType exitActionKey;

	/**
	 * Answer the action key whose {@linkplain Continuation1 action} should be
	 * performed when exiting a particular state.
	 *
	 * @return The action key for the <em>exit</em> action.
	 */
	@Nullable ActionKeyType getExitActionKey ()
	{
		return exitActionKey;
	}

	/**
	 * Set the action key whose {@linkplain Continuation1 action} should be
	 * performed when exiting a particular state.
	 *
	 * @param actionKey An action key.
	 */
	void setExitActionKey (final ActionKeyType actionKey)
	{
		assert exitActionKey == null;
		exitActionKey = actionKey;
	}

	/**
	 * The {@linkplain Continuation1 action} to perform upon exiting my state.
	 */
	private @Nullable Continuation1<? super MementoType> exitAction;

	/**
	 * Get the {@linkplain Continuation1 action} to perform upon exiting my
	 * state.
	 *
	 * @return The action to perform upon exit.
	 */
	@Nullable Continuation1<? super MementoType> getExitAction ()
	{
		return exitAction;
	}


	/** The transition table. */
	private final EnumMap<
			EventType,
			Collection<
				StateTransitionArc<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>>>
		transitionTable;

	/** The automatic transitions list. */
	private final Collection<
			StateTransitionArc<
				StateType,
				EventType,
				GuardKeyType,
				ActionKeyType,
				MementoType>>
		automaticTransitionTable;

	/**
	 * Construct a new {@link StateSummary} which does nothing on entry and exit
	 * of a state. Also include no automatic transitions.
	 *
	 * @param state
	 *        The summarized state.
	 * @param eventType
	 *        The {@linkplain Class type} of events.
	 */
	StateSummary (
		final StateType state,
		final Class<EventType> eventType)
	{
		this.state = state;
		transitionTable = new EnumMap<>(eventType);
		automaticTransitionTable = new ArrayList<>();
	}

	/**
	 * Set my {@linkplain Transformer1 guards} and {@linkplain Continuation1
	 * actions} based on the supplied mappings from guard keys and action keys,
	 * respectively.
	 *
	 * @param guardMap
	 *        The mapping from GuardKeyType to guard.
	 * @param actionMap
	 *        The mapping from ActionKeyType to action.
	 */
	void populateGuardsAndActions (
		final Map<GuardKeyType, Transformer1<? super MementoType, Boolean>>
			guardMap,
		final Map<ActionKeyType, Continuation1<? super MementoType>>
			actionMap)
	{
		entryAction = actionMap.get(entryActionKey);
		exitAction = actionMap.get(exitActionKey);
		for (final StateTransitionArc<
				StateType,
				EventType,
				GuardKeyType,
				ActionKeyType,
				MementoType>
			transition : allTransitionArcs())
		{
			transition.populateGuardsAndActions(guardMap, actionMap);
		}
	}

	/**
	 * Set the {@linkplain StateTransitionArc state transition arc} for the
	 * specified event. If the event is null, define an automatic transition.
	 *
	 * @param event
	 *        An event, null to indicate an automatic transition.
	 * @param arc
	 *        A {@linkplain StateTransitionArc state transition arc}.
	 */
	void addTransitionArc (
		final @Nullable EventType event,
		final StateTransitionArc<
				StateType,
				EventType,
				GuardKeyType,
				ActionKeyType,
				MementoType>
			arc)
	{
		final Collection<
				StateTransitionArc<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>>
			collection;
		if (event == null)
		{
			collection = automaticTransitionTable;
		}
		else
		{
			collection = transitionTable.computeIfAbsent(
				event, k -> new ArrayList<>());
		}
		collection.add(arc);
	}

	/**
	 * Obtain the events for which {@linkplain StateTransitionArc state
	 * transition arcs} have been defined.
	 *
	 * @return A {@linkplain Collection collection} of events.
	 */
	Collection<EventType> transitionEvents ()
	{
		return transitionTable.keySet();
	}

	/**
	 * Obtain all {@linkplain StateTransitionArc state transition arcs} for the
	 * {@linkplain StateSummary summary}'s state.
	 *
	 * @return A {@linkplain Collection collection} of {@link StateTransitionArc
	 *         state transition arcs}.
	 */
	Collection<
			StateTransitionArc<
				StateType,
				EventType,
				GuardKeyType,
				ActionKeyType,
				MementoType>>
		allTransitionArcs ()
	{
		final Collection<
				StateTransitionArc<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>>
			aggregate = new ArrayList<>();
		for (final Collection<
				StateTransitionArc<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>>
			transitionArcs : transitionTable.values())
		{
			aggregate.addAll(transitionArcs);
		}
		aggregate.addAll(automaticTransitionTable);
		return Collections.unmodifiableCollection(aggregate);
	}

	/**
	 * Answer the appropriate {@linkplain StateTransitionArc transition arc} for
	 * the starting state and the specified event. The event may be null, in
	 * which case an automatic transition may be found. Guards are performed
	 * at this time to determine which, if any, transition should be taken.
	 * The {@linkplain ExecutionContext executionContext}'s memento is passed to
	 * the guard. Answer null if no suitable transition arc can be found.

	 * @param event
	 *        An event, or null if searching for automatic transitions.
	 * @param executionContext
	 *        The {@linkplain ExecutionContext execution context}.
	 * @return A {@linkplain StateTransitionArc state transition arc}, or null
	 *         if none are applicable.
	 */
	@Nullable StateTransitionArc<
			StateType,
			EventType,
			GuardKeyType,
			ActionKeyType,
			MementoType>
		getTransitionArc (
			final @Nullable EventType event,
			final ExecutionContext<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>
				executionContext)
	{
		final Collection<
				StateTransitionArc<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>>
			transitions;
		if (event == null)
		{
			transitions = automaticTransitionTable;
		}
		else
		{
			transitions = transitionTable.get(event);
			if (transitions == null)
			{
				return null;
			}
		}
		for (
			final StateTransitionArc<
					StateType,
					EventType,
					GuardKeyType,
					ActionKeyType,
					MementoType>
				transition : transitions)
		{
			if (executionContext.testGuard(transition.guard()))
			{
				return transition;
			}
		}
		// Indicate no matching transition arc.
		return null;
	}
}
