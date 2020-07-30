/*
 * StateSummary.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.utility.fsm

import java.util.Collections
import java.util.EnumMap

/**
 * The complete runtime representation of a state.
 *
 * @param Event
 *   The event type.
 * @param GuardKey
 *   The guard key type.
 * @param ActionKey
 *   The action key type.
 * @param State
 *   The state type.
 * @param Memento
 *   The type of memento passed to guards and actions.
 * @property state
 *   The state for which the receiver is a [summary][StateSummary].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `StateSummary` which does nothing on entry and exit
 * of a state. Also include no automatic transitions.
 *
 * @param state
 *   The summarized state.
 * @param eventType
 *   The [type][Class] of events.
 */
internal class StateSummary<
	State : Enum<State>,
	Event : Enum<Event>,
	GuardKey : Enum<GuardKey>,
	ActionKey : Enum<ActionKey>,
	Memento>
	constructor(val state: State, eventType: Class<Event>)
{

	/**
	 * The action key whose action should be invoked when entering a particular
	 * state.
	 */
	var entryActionKey: ActionKey? = null
		set(actionKey)
		{
			assert(this.entryActionKey === null)
			field = actionKey
		}

	/**
	 * The action to perform upon entering my state.
	 */
	var entryAction: ((Memento) -> Unit)? = null
		private set

	/**
	 * The action key whose action should be invoked when exiting a particular
	 * state.
	 */
	var exitActionKey: ActionKey? = null
		set(actionKey)
		{
			assert(this.exitActionKey === null)
			field = actionKey
		}

	/**
	 * The action to perform upon exiting my state.
	 */
	var exitAction: ((Memento) -> Unit)? = null
		private set

	/** The transition table.  */
	private val transitionTable:
		MutableMap<
			Event,
			MutableCollection<StateTransitionArc<
				State, Event, GuardKey, ActionKey, Memento>>> =
		EnumMap(eventType)

	/** The automatic transitions list.  */
	private val automaticTransitionTable:
		MutableCollection<StateTransitionArc<
			State, Event, GuardKey, ActionKey, Memento>>

	init
	{
		automaticTransitionTable = mutableListOf()
	}

	/**
	 * Set my guards and actions based on the supplied mappings from guard keys
	 * and action keys, respectively.
	 *
	 * @param guardMap
	 *   The mapping from GuardKey to guard.
	 * @param actionMap
	 *   The mapping from ActionKey to action.
	 */
	fun populateGuardsAndActions(
		guardMap: Map<GuardKey, (Memento) -> Boolean>,
		actionMap: Map<ActionKey, (Memento) -> Unit>)
	{
		entryAction = actionMap[this.entryActionKey]
		exitAction = actionMap[this.exitActionKey]
		for (transition in allTransitionArcs())
		{
			transition.populateGuardsAndActions(guardMap, actionMap)
		}
	}

	/**
	 * Set the [state&#32;transition&#32;arc][StateTransitionArc] for the
	 * specified event. If the event is null, define an automatic transition.
	 *
	 * @param event
	 *   An event, null to indicate an automatic transition.
	 * @param arc
	 *   A [state&#32;transition&#32;arc][StateTransitionArc].
	 */
	fun addTransitionArc(
		event: Event?,
		arc: StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>)
	{
		val collection: MutableCollection<StateTransitionArc<
			State, Event, GuardKey, ActionKey, Memento>> =
				if (event === null)
				{
					automaticTransitionTable
				}
				else
				{
					transitionTable.computeIfAbsent(event) { mutableListOf() }
				}
		collection.add(arc)
	}

	/**
	 * Obtain the events for which [state][StateTransitionArc] have been
	 * defined.
	 *
	 * @return
	 *   A [collection][Collection] of events.
	 */
	fun transitionEvents(): Collection<Event>
	{
		return transitionTable.keys
	}

	/**
	 * Obtain all [state&#32;transition&#32;arcs][StateTransitionArc] for the
	 * `StateSummary`'s state.
	 *
	 * @return
	 *   A [collection][Collection] of
	 *   [state&#32;transition&#32;arcs][StateTransitionArc].
	 */
	fun allTransitionArcs(): Collection<StateTransitionArc<
		State, Event, GuardKey, ActionKey, Memento>>
	{
		val aggregate = mutableListOf<StateTransitionArc<
			State, Event, GuardKey, ActionKey, Memento>>()
		for (transitionArcs in transitionTable.values)
		{
			aggregate.addAll(transitionArcs)
		}
		aggregate.addAll(automaticTransitionTable)
		return Collections.unmodifiableCollection(aggregate)
	}

	/**
	 * Answer the appropriate [transition&#32;arc][StateTransitionArc] for the
	 * starting state and the specified event. The event may be null, in which
	 * case an automatic transition may be found. Guards are performed at this
	 * time to determine which, if any, transition should be taken. The
	 * [executionContext][ExecutionContext]'s memento is passed to the guard.
	 * Answer null if no suitable transition arc can be found.
	 *
	 * @param event
	 *   An event, or null if searching for automatic transitions.
	 * @param executionContext
	 *   The [execution&#32;context][ExecutionContext].
	 * @return
	 *   A [state&#32;transition&#32;arc][StateTransitionArc], or null if none
	 *   are applicable.
	 */
	fun getTransitionArc(
			event: Event?,
			executionContext: ExecutionContext<
				State, Event, GuardKey, ActionKey, Memento>):
		StateTransitionArc<State, Event, GuardKey, ActionKey, Memento>?
	{
		val transitions =
			if (event === null)
			{
				automaticTransitionTable
			}
			else
			{
				transitionTable[event] ?: return null
			}
		for (transition in transitions)
		{
			if (executionContext.testGuard(transition.guard))
			{
				return transition
			}
		}
		// Indicate no matching transition arc.
		return null
	}
}
