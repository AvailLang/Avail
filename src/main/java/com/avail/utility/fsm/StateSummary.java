/*
 * StateVisitActions.java
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

package com.avail.utility.fsm;

import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.util.*;

/**
 * The complete runtime representation of a state.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <Event>
 *        The event type.
 * @param <GuardKey>
 *        The guard key type.
 * @param <ActionKey>
 *        The action key type.
 * @param <State>
 *        The state type.
 * @param <Memento>
 *        The type of memento passed to guards and actions.
 */
final class StateSummary<
	State extends Enum<State>,
	Event extends Enum<Event>,
	GuardKey extends Enum<GuardKey>,
	ActionKey extends Enum<ActionKey>,
	Memento>
{
	/**
	 * The state for which the receiver is a {@linkplain StateSummary summary}.
	 */
	private final State state;

	/**
	 * Answer the state for which the receiver is a {@code StateSummary}.
	 *
	 * @return A state.
	 */
	State state ()
	{
		return state;
	}

	/**
	 * The action key whose {@linkplain Continuation1 action} should be invoked
	 * when entering a particular state.
	 */
	private @Nullable
	ActionKey entryActionKey;

	/**
	 * Answer the action key whose {@linkplain Continuation1 action} should be
	 * performed when entering a particular state.
	 *
	 * @return The action key for the <em>entry</em> action.
	 */
	@Nullable
	ActionKey getEntryActionKey ()
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
	void setEntryActionKey (final ActionKey actionKey)
	{
		assert entryActionKey == null;
		entryActionKey = actionKey;
	}

	/**
	 * The {@linkplain Continuation1 action} to perform upon entering my state.
	 */
	private @Nullable Continuation1<? super Memento> entryAction;

	/**
	 * Get the {@linkplain Continuation1 action} to perform upon entering my
	 * state.
	 *
	 * @return The action to perform upon entry.
	 */
	@Nullable Continuation1<? super Memento> getEntryAction ()
	{
		return entryAction;
	}

	/**
	 * The action key whose {@linkplain Continuation1 action} should be invoked
	 * when exiting a particular state.
	 */
	private @Nullable
	ActionKey exitActionKey;

	/**
	 * Answer the action key whose {@linkplain Continuation1 action} should be
	 * performed when exiting a particular state.
	 *
	 * @return The action key for the <em>exit</em> action.
	 */
	@Nullable
	ActionKey getExitActionKey ()
	{
		return exitActionKey;
	}

	/**
	 * Set the action key whose {@linkplain Continuation1 action} should be
	 * performed when exiting a particular state.
	 *
	 * @param actionKey An action key.
	 */
	void setExitActionKey (final ActionKey actionKey)
	{
		assert exitActionKey == null;
		exitActionKey = actionKey;
	}

	/**
	 * The {@linkplain Continuation1 action} to perform upon exiting my state.
	 */
	private @Nullable Continuation1<? super Memento> exitAction;

	/**
	 * Get the {@linkplain Continuation1 action} to perform upon exiting my
	 * state.
	 *
	 * @return The action to perform upon exit.
	 */
	@Nullable Continuation1<? super Memento> getExitAction ()
	{
		return exitAction;
	}

	/** The transition table. */
	private final EnumMap<
			Event,
			Collection<
				StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>>
		transitionTable;

	/** The automatic transitions list. */
	private final Collection<
			StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>
		automaticTransitionTable;

	/**
	 * Construct a new {@code StateSummary} which does nothing on entry and exit
	 * of a state. Also include no automatic transitions.
	 *
	 * @param state
	 *        The summarized state.
	 * @param eventType
	 *        The {@linkplain Class type} of events.
	 */
	StateSummary (
		final State state,
		final Class<Event> eventType)
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
	 *        The mapping from GuardKey to guard.
	 * @param actionMap
	 *        The mapping from ActionKey to action.
	 */
	void populateGuardsAndActions (
		final Map<GuardKey, Transformer1<? super Memento, Boolean>> guardMap,
		final Map<ActionKey, Continuation1<? super Memento>> actionMap)
	{
		entryAction = actionMap.get(entryActionKey);
		exitAction = actionMap.get(exitActionKey);
		for (final StateTransitionArc<
				State, Event, GuardKey, ActionKey, Memento>
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
		final @Nullable Event event,
		final StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>
			arc)
	{
		final Collection<
				StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>
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
	Collection<Event> transitionEvents ()
	{
		return transitionTable.keySet();
	}

	/**
	 * Obtain all {@linkplain StateTransitionArc state transition arcs} for the
	 * {@code StateSummary}'s state.
	 *
	 * @return A {@linkplain Collection collection} of {@link StateTransitionArc
	 *         state transition arcs}.
	 */
	Collection<
			StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>
		allTransitionArcs ()
	{
		final Collection<
				StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>
			aggregate = new ArrayList<>();
		for (final Collection<
				StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>
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
	@Nullable StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>
		getTransitionArc (
			final @Nullable Event event,
			final ExecutionContext<State, Event, GuardKey, ActionKey, Memento>
				executionContext)
	{
		final Collection<
				StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>>
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
			final StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>
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
