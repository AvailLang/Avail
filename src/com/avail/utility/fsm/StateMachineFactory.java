/*
 * StateMachineFactory.java
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static com.avail.utility.Nulls.stripNull;

/**
 * A {@code StateMachineFactory} enables a client to dynamically specify and
 * assemble a {@linkplain StateMachine finite state machine}. In particular, the
 * factory allows a client to flexibly define a particular FSM while ignoring
 * specification and evaluation order dependency. Validation is postponed until
 * final assembly time, at which time a {@link ValidationException} will be
 * thrown in the event of incorrect or incomplete specification; otherwise, the
 * constructed FSM provably reflects the client specification.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <State>
 *        The type of states (an {@link Enum}).
 * @param <Event>
 *        The type of events (an {@link Enum}).
 * @param <GuardKey>
 *        The type of guard keys (an {@link Enum}).
 * @param <ActionKey>
 *        The type of action keys (an {@link Enum}).
 * @param <Memento>
 *        The type of memento.
 */
public final class StateMachineFactory<
	State extends Enum<State>,
	Event extends Enum<Event>,
	GuardKey extends Enum<GuardKey>,
	ActionKey extends Enum<ActionKey>,
	Memento>
{
	/** A state's {@linkplain Class type}. */
	private final Class<State> stateType;

	/** An event's {@linkplain Class type}. */
	private final Class<Event> eventType;

	/** An action key's {@linkplain Class type}. */
	private final Class<ActionKey> actionKeyType;

	/** A guard key's {@linkplain Class type}. */
	private final Class<GuardKey> guardKeyType;

	/** The mapping from guard keys to {@linkplain Transformer1 guards}. */
	private final EnumMap<
		GuardKey, Transformer1<? super Memento, Boolean>> guardMap;

	/** The mapping from action keys to {@linkplain Continuation1 actions}. */
	private final EnumMap<
		ActionKey, Continuation1<? super Memento>> actionMap;

	/**
	 * The complete transition table, a {@linkplain EnumMap map} from states to
	 * {@linkplain StateSummary state summaries}.
	 */
	private final EnumMap<
		State,
			StateSummary<
				State,
				Event,
				GuardKey,
				ActionKey,
				Memento>>
		summaries;

	/**
	 * Construct a new {@code StateMachineFactory} primed to create a new
	 * instance of the specified kind of {@link StateMachine state machine}.
	 *
	 * @param stateType
	 *        The kind of states.
	 * @param eventType
	 *        The kind of events.
	 * @param guardKeyType
	 *        The kind of guard keys.
	 * @param actionKeyType
	 *        The kind of action keys.
	 */
	public StateMachineFactory (
		final Class<State> stateType,
		final Class<Event> eventType,
		final Class<GuardKey> guardKeyType,
		final Class<ActionKey> actionKeyType)
	{
		this.stateType = stateType;
		this.eventType = eventType;
		this.guardKeyType = guardKeyType;
		this.actionKeyType = actionKeyType;
		this.guardMap = new EnumMap<>(guardKeyType);
		this.actionMap = new EnumMap<>(actionKeyType);
		this.summaries = new EnumMap<>(stateType);
	}

	/**
	 * The initial state of the target {@linkplain StateMachine state machine}.
	 */
	private @Nullable
	State initialState;

	/**
	 * Record the canonical initial state of the target {@link StateMachine
	 * state machine}.
	 *
	 * @param initialState
	 *        The initial state.
	 */
	public void setInitialState (final State initialState)
	{
		this.initialState = initialState;
	}

	/**
	 * Answer the specified state's {@linkplain StateSummary summary}, possibly
	 * a hitherto unused summary.
	 *
	 * @param state
	 *        A state.
	 * @return The state's {@linkplain StateSummary summary}.
	 */
	private
	StateSummary<State, Event, GuardKey, ActionKey, Memento>
		getSummary (final State state)
	{

		return summaries.computeIfAbsent(
			state, s -> new StateSummary<>(s, eventType));
	}

	/**
	 * Set the <em>entry</em> action key that indicates which {@linkplain
	 * Continuation1 action} to invoke when entering the specified state.
	 *
	 * @param state
	 *        A state.
	 * @param actionKey
	 *        An action key that specifies an {@linkplain Continuation1 action}.
	 */
	public void setEntryAction (
		final State state,
		final ActionKey actionKey)
	{
		assert getSummary(state).getEntryActionKey() == null;

		getSummary(state).setEntryActionKey(actionKey);
	}

	/**
	 * Set the <em>exit</em> action key that indicates which {@linkplain
	 * Continuation1 action} to invoke when exiting the specified state.
	 *
	 * @param state
	 *        A state.
	 * @param actionKey
	 *        An action key that specifies an {@linkplain Continuation1 action}.
	 */
	public void setExitAction (
		final State state,
		final ActionKey actionKey)
	{
		assert getSummary(state).getExitActionKey() == null;

		getSummary(state).setExitActionKey(actionKey);
	}

	/**
	 * Add a {@linkplain StateTransitionArc transition arc}.
	 *
	 * @param startState
	 *        The starting state for the {@link StateTransitionArc arc}.
	 * @param event
	 *        The event that triggers the {@link StateTransitionArc transition},
	 *        possibly {@code null} if the {@linkplain StateTransitionArc
	 *        transition} should be attempted automatically upon entry of the
	 *        state.
	 * @param guardKey
	 *        The guard that must be satisfied for a {@linkplain
	 *        StateTransitionArc transition} to be taken, possibly {@code null}
	 *        if no guard should be checked.
	 * @param actionKey
	 *        The action to run during the {@link StateTransitionArc
	 *        transition}, possibly {@code null} if no {@link Continuation1
	 *        action} should be performed during transition.
	 * @param endState
	 *        The ending state.
	 */
	public void addTransition (
		final State startState,
		final @Nullable Event event,
		final @Nullable GuardKey guardKey,
		final @Nullable ActionKey actionKey,
		final State endState)
	{
		// Remove assertion after it's fully implemented.
		assert event != null;
		getSummary(startState).addTransitionArc(
			event,
			new StateTransitionArc<>(event, guardKey, actionKey, endState));
	}

	/**
	 * Add an automatically taken {@linkplain StateTransitionArc transition
	 * arc}.
	 *
	 * @param startState
	 *        The starting state for the {@link StateTransitionArc arc}.
	 * @param guardKey
	 *        The guard that must be satisfied for a {@linkplain
	 *        StateTransitionArc transition} to be taken, possibly {@code null}
	 *        if no guard should be checked.
	 * @param actionKey
	 *        The action to run during the {@link StateTransitionArc
	 *        transition}, possibly {@code null} if no {@link Continuation1
	 *        action} should be performed during transition.
	 * @param endState
	 *        The ending state.
	 */
	public void addAutomaticTransition (
		final State startState,
		final @Nullable GuardKey guardKey,
		final @Nullable ActionKey actionKey,
		final State endState)
	{
		getSummary(startState).addTransitionArc(
			null,
			new StateTransitionArc<>(null, guardKey, actionKey, endState));
	}

	/**
	 * Bind a guard key to a {@linkplain Transformer1 guard}.
	 *
	 * @param guardKey The guard key.
	 * @param guard The guard to perform.
	 */
	public void defineGuard (
		final GuardKey guardKey,
		final Transformer1<? super Memento, Boolean> guard)
	{
		assert !guardMap.containsKey(guardKey);
		guardMap.put(guardKey, guard);
	}

	/**
	 * Bind an action key to an {@linkplain Continuation1 action}.
	 *
	 * @param actionKey
	 *        The action key.
	 * @param action
	 *        The {@linkplain Continuation1 action} to perform.
	 */
	public void defineAction (
		final ActionKey actionKey,
		final Continuation1<? super Memento> action)
	{
		assert !actionMap.containsKey(actionKey);
		actionMap.put(actionKey, action);
	}

	/**
	 * If the specified state has not yet been reached, then add it to the
	 * provided reachability vector and recursively add any states reachable
	 * via the specified state's {@linkplain StateTransitionArc transition
	 * arcs}.
	 *
	 * @param state
	 *        The state to visit.
	 * @param reachable
	 *        The reachability vector.
	 */
	private void recursivelyReachState (
		final State state,
		final EnumSet<State> reachable)
	{
		if (!reachable.contains(state))
		{
			reachable.add(state);
			for (final StateTransitionArc<
					State, Event, GuardKey, ActionKey, Memento>
				arc : getSummary(state).allTransitionArcs())
			{
				recursivelyReachState(arc.stateAfterTransition(), reachable);
			}
		}
	}

	/**
	 * Check that the resulting {@linkplain StateMachine finite state machine}
	 * will have no defects. In particular:
	 *
	 * <ul>
	 *    <li>There must be an initial state</li>
	 *    <li>All states must be reachable by transitions from the
	 *        initial state</li>
	 *    <li>All events must be handled</li>
	 *    <li>All guard keys must be bound to guards</li>
	 *    <li>All guard keys must be used on a transition</li>
	 *    <li>In the list of transitions from a state, after an unguarded
	 *        transition for an event there must not be any other transitions
	 *        for the same event (they would be unreachable)</li>
	 *    <li>All action keys must be bound to actions</li>
	 *    <li>All action keys must be invoked, either during transition or upon
	 *        <em>entry</em> or <em>exit</em> of some state</li>
	 * </ul>
	 *
	 * @throws ValidationException
	 *         If the specified {@linkplain StateMachine finite state machine}
	 *         fails validation for any reason.
	 */
	private void validate () throws ValidationException
	{
		// Verify that an initial state was specified.
		final @Nullable State startState = initialState;
		if (startState == null)
		{
			throw new ValidationException("no start state is specified");
		}

		// Verify that every state is reachable.
		final EnumSet<State> statesReached = EnumSet.noneOf(stateType);
		recursivelyReachState(startState, statesReached);
		if (statesReached.size() != stateType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some states of "
				+ stateType.getCanonicalName()
				+ " are unreachable");
		}

		// Verify that every event is handled by at least one transition.
		final EnumSet<Event> eventsHandled = EnumSet.noneOf(eventType);
		for (final State state : stateType.getEnumConstants())
		{
			eventsHandled.addAll(getSummary(state).transitionEvents());
		}
		if (eventsHandled.size() != eventType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some events of "
				+ eventType.getCanonicalName()
				+ " are unhandled");
		}

		// Verify that every guard key is bound to a guard.
		if (guardMap.keySet().size()
				!= guardKeyType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some guard keys of "
				+ guardKeyType.getCanonicalName()
				+ " are not bound to guards");
		}

		// Verify that every guard key is invoked.
		final EnumSet<GuardKey> guardKeysInvoked =
			EnumSet.noneOf(guardKeyType);
		for (final State state : stateType.getEnumConstants())
		{
			for (final StateTransitionArc<
					State, Event, GuardKey, ActionKey, Memento>
				arc : getSummary(state).allTransitionArcs())
			{
				if (arc.guardKey() != null)
				{
					guardKeysInvoked.add(arc.guardKey());
				}
			}
		}
		if (guardKeysInvoked.size() != guardKeyType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some guard keys of "
				+ guardKeyType.getCanonicalName()
				+ " are never invoked");
		}

		// Verify that an unguarded transition for an event from a state is
		// the last-added transition for that event/state combination.
		for (final State state : stateType.getEnumConstants())
		{
			// Allow null element in the following set to handle the
			// automatic transitions.
			final Set<Event> unguardedArcsFound =
				new HashSet<>();
			for (final StateTransitionArc<
					State, Event, GuardKey, ActionKey, Memento>
				arc : getSummary(state).allTransitionArcs())
			{
				if (unguardedArcsFound.contains(arc.triggeringEvent()))
				{
					throw new ValidationException(
						"state " + state
						+ " has an unreachable arc for event "
						+ arc.triggeringEvent()
						+ " due to a previous unguarded arc for the same "
						+ "event");
				}
				if (arc.guardKey() == null)
				{
					unguardedArcsFound.add(arc.triggeringEvent());
				}
			}
		}

		// Verify that every action key is bound to an action.
		if (actionMap.keySet().size()
				!= actionKeyType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some action keys of "
				+ actionKeyType.getCanonicalName()
				+ " are not bound to actions");
		}

		// Verify that every action key is invoked.
		final EnumSet<ActionKey> actionKeysInvoked =
			EnumSet.noneOf(actionKeyType);
		for (final State state : stateType.getEnumConstants())
		{
			for (final StateTransitionArc<
					State, Event, GuardKey, ActionKey, Memento>
				arc : getSummary(state).allTransitionArcs())
			{
				if (arc.actionKey() != null)
				{
					actionKeysInvoked.add(arc.actionKey());
				}
			}
			final @Nullable ActionKey entryKey =
				getSummary(state).getEntryActionKey();
			if (entryKey != null)
			{
				actionKeysInvoked.add(entryKey);
			}
			final @Nullable ActionKey exitKey =
				getSummary(state).getExitActionKey();
			if (exitKey != null)
			{
				actionKeysInvoked.add(exitKey);
			}
		}
		if (actionKeysInvoked.size() != actionKeyType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some action keys of "
				+ actionKeyType.getCanonicalName()
				+ " are never invoked");
		}
	}

	/**
	 * Create an instance of the {@linkplain StateMachine finite state machine}
	 * described by the {@code StateMachineFactory}.
	 *
	 * @return The new validated {@linkplain StateMachine state machine}.
	 * @throws ValidationException
	 *         If validation fails.
	 */
	public StateMachine<State, Event, GuardKey, ActionKey, Memento>
		createStateMachine ()
	throws ValidationException
	{
		validate();
		for (final StateSummary<State, Event, GuardKey, ActionKey, Memento>
			summary : summaries.values())
		{
			summary.populateGuardsAndActions(guardMap, actionMap);
		}
		final State startState = stripNull(initialState);
		return new StateMachine<>(startState, summaries.values());
	}
}
