/*
 * StateTransitionArc.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

/**
 * A state transition, effectively the "compiled form" of a single state
 * transition arc.
 *
 * @param State
 *   The type of the new state.
 * @param Event
 *   The type of the triggering event.
 * @param GuardKey
 *   The type of guard keys.
 * @param ActionKey
 *   The type of action keys.
 * @param Memento
 *   The type of object passed to guards and actions.
 * @property event
 *   The event upon whose receipt the transition will occur.
 * @property guardKey
 *   The guard key whose bound guard will be performed to determine if a
 *   transition can be taken.
 * @property actionKey
 *   The action key whose bound action will be performed during transition.
 * @property newState
 *   The new state to which a transition will occur.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `StateTransitionArc`.
 *
 * @param event
 *   An event, possibly `null`.
 * @param guardKey
 *   A guard key, possibly `null`.
 * @param actionKey
 *   An action key, possibly `null`.
 * @param newState
 *   A state.
 */
internal class StateTransitionArc<
	State : Enum<State>,
	Event : Enum<Event>,
	GuardKey : Enum<GuardKey>,
	ActionKey : Enum<ActionKey>,
	Memento>
	constructor(
		val event: Event?,
		val guardKey: GuardKey?,
		val actionKey: ActionKey?,
		val newState: State)
{
	/**
	 * The actual guard that will be performed to determine if a transition can
	 * be taken.
	 */
	var guard: ((Memento) -> Boolean)? = null
		private set

	/**
	 * The actual action that will be performed during transition.
	 */
	var action: ((Memento) -> Unit)? = null
		private set

	/**
	 * Set my guard and action based on the supplied mappings from guard keys
	 * and action keys, respectively.
	 *
	 * @param guardMap
	 *   The mapping from GuardKey to guard.
	 * @param actionMap
	 *   The mapping from ActionKey to action.
	 */
	fun populateGuardsAndActions(
		guardMap: Map<GuardKey, (Memento) -> Boolean>,
		actionMap: Map<ActionKey, ((Memento) -> Unit)>)
	{
		guard = guardMap[guardKey]
		action = actionMap[actionKey]
	}
}
