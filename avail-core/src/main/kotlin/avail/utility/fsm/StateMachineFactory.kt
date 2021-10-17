/*
 * StateMachineFactory.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.utility.fsm

import java.util.EnumMap
import java.util.EnumSet

/**
 * A `StateMachineFactory` enables a client to dynamically specify and assemble
 * a [finite&#32;state&#32;machine][StateMachine]. In particular, the factory
 * allows a client to flexibly define a particular FSM while ignoring
 * specification and evaluation order dependency. Validation is postponed until
 * final assembly time, at which time a [ValidationException] will be thrown in
 * the event of incorrect or incomplete specification; otherwise, the
 * constructed FSM provably reflects the client specification.
 *
 * @param State
 *   The type of states (an [Enum]).
 * @param Event
 *   The type of events (an [Enum]).
 * @param GuardKey
 *   The type of guard keys (an [Enum]).
 * @param ActionKey
 *   The type of action keys (an [Enum]).
 * @param Memento
 *   The type of memento.
 * @property stateType
 *   A state's [type][Class].
 * @property eventType
 *   An event's [type][Class].
 * @property guardKeyType
 *   A guard key's [type][Class].
 * @property actionKeyType
 *   An action key's [type][Class].
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `StateMachineFactory` primed to create a new
 * instance of the specified kind of [state&#32;machine][StateMachine].
 *
 * @param stateType
 *   The kind of states.
 * @param eventType
 *   The kind of events.
 * @param guardKeyType
 *   The kind of guard keys.
 * @param actionKeyType
 *   The kind of action keys.
 */
class StateMachineFactory<
	State : Enum<State>,
	Event : Enum<Event>,
	GuardKey : Enum<GuardKey>,
	ActionKey : Enum<ActionKey>,
	Memento>
constructor(
	private val stateType: Class<State>,
	private val eventType: Class<Event>,
	private val guardKeyType: Class<GuardKey>,
	private val actionKeyType: Class<ActionKey>)
{
	/** The mapping from guard keys to guards. */
	private val guardMap: EnumMap<GuardKey, (Memento) -> Boolean> =
		EnumMap(guardKeyType)

	/** The mapping from action keys to actions. */
	private val actionMap: EnumMap<ActionKey, (Memento) -> Unit> =
		EnumMap(actionKeyType)

	/**
	 * The complete transition table, a [map][EnumMap] from states to
	 * [state&#32;summaries][StateSummary].
	 */
	private val summaries: EnumMap<
			State, StateSummary<State, Event, GuardKey, ActionKey, Memento>> =
		EnumMap(stateType)

	/**
	 * The initial state of the target [state&#32;machine][StateMachine].
	 */
	private var initialState: State? = null

	/**
	 * Record the canonical initial state of the target
	 * [state&#32;machine][StateMachine].
	 *
	 * @param initialState
	 *   The initial state.
	 */
	@Suppress("unused")
	fun setInitialState(initialState: State)
	{
		this.initialState = initialState
	}

	/**
	 * Answer the specified state's [summary][StateSummary], possibly a hitherto
	 * unused summary.
	 *
	 * @param state
	 *   A state.
	 * @return
	 *   The state's [summary][StateSummary].
	 */
	private fun getSummary(state: State):
			StateSummary<State, Event, GuardKey, ActionKey, Memento> =
		(summaries as MutableMap<
			State, StateSummary<State, Event, GuardKey, ActionKey, Memento>>)
			.computeIfAbsent(state) { s -> StateSummary(s, eventType) }

	/**
	 * Set the *entry* action key that indicates which action
	 * to invoke when entering the specified state.
	 *
	 * @param state
	 *   A state.
	 * @param actionKey
	 *   An action key that specifies an action.
	 */
	@Suppress("unused") fun setEntryAction(state: State, actionKey: ActionKey)
	{
		assert(getSummary(state).entryActionKey === null)
		getSummary(state).entryActionKey = actionKey
	}

	/**
	 * Set the *exit* action key that indicates which action to
	 * invoke when exiting the specified state.
	 *
	 * @param state
	 *   A state.
	 * @param actionKey
	 *   An action key that specifies an action.
	 */
	@Suppress("unused") fun setExitAction(state: State, actionKey: ActionKey)
	{
		assert(getSummary(state).exitActionKey === null)
		getSummary(state).exitActionKey = actionKey
	}

	/**
	 * Add a [transition&#32;arc][StateTransitionArc].
	 *
	 * @param startState
	 *   The starting state for the [arc][StateTransitionArc].
	 * @param event
	 *   The event that triggers the [transition][StateTransitionArc], possibly
	 *   `null` if the [transition][StateTransitionArc] should be attempted
	 *   automatically upon entry of the state.
	 * @param guardKey
	 *   The guard that must be satisfied for a [transition][StateTransitionArc]
	 *   to be taken, possibly `null` if no guard should be checked.
	 * @param actionKey
	 *   The action to run during the [transition][StateTransitionArc], possibly
	 *   `null` if no action should be performed during
	 *   transition.
	 * @param endState
	 *   The ending state.
	 */
	@Suppress("unused") fun addTransition(
		startState: State,
		event: Event?,
		guardKey: GuardKey?,
		actionKey: ActionKey?,
		endState: State)
	{
		// Remove assertion after it's fully implemented.
		assert(event !== null)
		getSummary(startState).addTransitionArc(
			event,
			StateTransitionArc(event, guardKey, actionKey, endState))
	}

	/**
	 * Add an automatically taken [transition][StateTransitionArc].
	 *
	 * @param startState
	 *   The starting state for the [arc][StateTransitionArc].
	 * @param guardKey
	 *   The guard that must be satisfied for a [transition][StateTransitionArc]
	 *   to be taken, possibly `null` if no guard should be checked.
	 * @param actionKey
	 *   The action to run during the [transition][StateTransitionArc], possibly
	 *   `null` if no action should be performed during
	 *   transition.
	 * @param endState
	 *   The ending state.
	 */
	@Suppress("unused") fun addAutomaticTransition(
		startState: State,
		guardKey: GuardKey?,
		actionKey: ActionKey?,
		endState: State)
	{
		getSummary(startState).addTransitionArc(
			null,
			StateTransitionArc(null, guardKey, actionKey, endState))
	}

	/**
	 * Bind a guard key to a guard.
	 *
	 * @param guardKey
	 *   The guard key.
	 * @param guard
	 *   The guard to perform.
	 */
	@Suppress("unused") fun defineGuard(
		guardKey: GuardKey,
		guard: (Memento) -> Boolean)
	{
		assert(!guardMap.containsKey(guardKey))
		guardMap[guardKey] = guard
	}

	/**
	 * Bind an action key to an action.
	 *
	 * @param actionKey
	 *   The action key.
	 * @param action
	 *   The action to perform.
	 */
	@Suppress("unused") fun defineAction(
		actionKey: ActionKey,
		action: (Memento) -> Unit)
	{
		assert(!actionMap.containsKey(actionKey))
		actionMap[actionKey] = action
	}

	/**
	 * If the specified state has not yet been reached, then add it to the
	 * provided reachability vector and recursively add any states reachable
	 * via the specified state's [transition][StateTransitionArc].
	 *
	 * @param state
	 *   The state to visit.
	 * @param reachable
	 *   The reachability vector.
	 */
	private fun recursivelyReachState(
		state: State,
		reachable: EnumSet<State>)
	{
		if (!reachable.contains(state))
		{
			reachable.add(state)
			for (arc in getSummary(state).allTransitionArcs())
			{
				recursivelyReachState(arc.newState, reachable)
			}
		}
	}

	/**
	 * Check that the resulting [finite&#32;state&#32;machine][StateMachine]
	 * will have no defects. In particular:
	 *  * There must be an initial state
	 *  * All states must be reachable by transitions from the initial state
	 *  * All events must be handled
	 *  * All guard keys must be bound to guards
	 *  * All guard keys must be used on a transition
	 *  * In the list of transitions from a state, after an unguarded
	 *    transition for an event there must not be any other transitions
	 *    for the same event (they would be unreachable)
	 *  * All action keys must be bound to actions
	 *  * All action keys must be invoked, either during transition or upon
	 *    *entry* or *exit* of some state
	 *
	 * @throws ValidationException
	 *   If the specified [finite&#32;state&#32;machine][StateMachine] fails
	 *   validation for any reason.
	 */
	@Throws(ValidationException::class)
	private fun validate()
	{
		// Verify that an initial state was specified.
		val startState = initialState
			?: throw ValidationException("no start state is specified")

		// Verify that every state is reachable.
		val statesReached = EnumSet.noneOf(stateType)
		recursivelyReachState(startState, statesReached)
		if (statesReached.size != stateType.enumConstants.size)
		{
			throw ValidationException(
				"some states of ${stateType.canonicalName} are unreachable")
		}

		// Verify that every event is handled by at least one transition.
		val eventsHandled = EnumSet.noneOf(eventType)
		for (state in stateType.enumConstants)
		{
			eventsHandled.addAll(getSummary(state).transitionEvents())
		}
		if (eventsHandled.size != eventType.enumConstants.size)
		{
			throw ValidationException(
				"some events of ${eventType.canonicalName} are unhandled")
		}

		// Verify that every guard key is bound to a guard.
		if (guardMap.keys.size != guardKeyType.enumConstants.size)
		{
			throw ValidationException(
				"some guard keys of ${guardKeyType.canonicalName} are not " +
					"bound to guards")
		}

		// Verify that every guard key is invoked.
		val guardKeysInvoked = EnumSet.noneOf(guardKeyType)
		for (state in stateType.enumConstants)
		{
			for (arc in getSummary(state).allTransitionArcs())
			{
				val guardKey = arc.guardKey
				if (guardKey !== null)
				{
					guardKeysInvoked.add(guardKey)
				}
			}
		}
		if (guardKeysInvoked.size != guardKeyType.enumConstants.size)
		{
			throw ValidationException(
				"some guard keys of ${guardKeyType.canonicalName} are never " +
					"invoked")
		}

		// Verify that an unguarded transition for an event from a state is
		// the last-added transition for that event/state combination.
		for (state in stateType.enumConstants)
		{
			// Allow null element in the following set to handle the
			// automatic transitions.
			val unguardedArcsFound = mutableSetOf<Event>()
			for (arc in getSummary(state).allTransitionArcs())
			{
				val event = arc.event
				if (unguardedArcsFound.contains(event))
				{
					throw ValidationException(
						"state $state has an unreachable arc for event " +
							"$event due to a previous unguarded arc for the " +
							"same event")
				}
				if (arc.guardKey === null)
				{
					unguardedArcsFound.add(event!!)
				}
			}
		}

		// Verify that every action key is bound to an action.
		if (actionMap.keys.size != actionKeyType.enumConstants.size)
		{
			throw ValidationException(
				"some action keys of ${actionKeyType.canonicalName} are not " +
					"bound to actions")
		}

		// Verify that every action key is invoked.
		val actionKeysInvoked = EnumSet.noneOf(actionKeyType)
		for (state in stateType.enumConstants)
		{
			for (arc in getSummary(state).allTransitionArcs())
			{
				val actionKey = arc.actionKey
				if (actionKey !== null)
				{
					actionKeysInvoked.add(actionKey)
				}
			}
			val entryKey = getSummary(state).entryActionKey
			if (entryKey !== null)
			{
				actionKeysInvoked.add(entryKey)
			}
			val exitKey = getSummary(state).exitActionKey
			if (exitKey !== null)
			{
				actionKeysInvoked.add(exitKey)
			}
		}
		if (actionKeysInvoked.size != actionKeyType.enumConstants.size)
		{
			throw ValidationException(
				"some action keys of ${actionKeyType.canonicalName} are " +
					"never invoked")
		}
	}

	/**
	 * Create an instance of the [finite&#32;state&#32;machine][StateMachine]
	 * described by the `StateMachineFactory`.
	 *
	 * @return
	 *   The new validated [state&#32;machine][StateMachine].
	 * @throws ValidationException
	 *   If validation fails.
	 */
	@Suppress("unused")
	@Throws(ValidationException::class)
	fun createStateMachine():
		StateMachine<State, Event, GuardKey, ActionKey, Memento>
	{
		validate()
		for (summary in summaries.values)
		{
			summary.populateGuardsAndActions(guardMap, actionMap)
		}
		return StateMachine(initialState!!, summaries.values)
	}
}
