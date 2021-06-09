/*
 * ExecutionContext.kt
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

package com.avail.utility.fsm

/**
 * An `ExecutionContext` represents a running
 * [finite&#32;state&#32;machine][StateMachine]. In order to execute a step, it
 * maintains a reference to the executor, the current state, and a
 * client-provided memento which will be passed as the sole argument of every
 * action invocation.
 *
 * To obtain a new execution context, a client should call
 * [StateMachine.createExecutionContext] on an established FSM.
 *
 * To execute a step, a client should call [handleEvent] with an appropriate
 * event.
 *
 * @param State
 *   The state type.
 * @param Event
 *   The event type.
 * @param GuardKey
 *   The guard key type.
 * @param ActionKey
 *   The action key type.
 * @param Memento
 *   The memento type.
 * @property machine
 *   The [state&#32;machine][StateMachine].
 * @property memento
 *   The memento to pass to each action that runs.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ExecutionContext`.
 *
 * @param machine
 *   The [state&#32;machine][StateMachine] to instantiate as an
 *   `ExecutionContext`.
 * @param memento
 *   The object which should be passed to all actions.
 */
class ExecutionContext<
	State : Enum<State>,
	Event : Enum<Event>,
	GuardKey : Enum<GuardKey>,
	ActionKey : Enum<ActionKey>,
	Memento>
internal constructor(
	private val machine: StateMachine<
		State, Event, GuardKey, ActionKey, Memento>,
	private val memento: Memento)
{
	/** The current state. */
	var currentState: State? = null
		get()
		{
			assert(Thread.holdsLock(this))
			return field
		}

	init
	{
		this.currentState = machine.initialState
	}

	/**
	 * Test the given guard.
	 *
	 * @param guard
	 *   The guard to invoke, or `null` for the trivial always-true guard.
	 * @return
	 *   Whether the guard is satisfied.
	 */
	internal fun testGuard(guard: ((Memento) -> Boolean)?) =
		guard?.invoke(memento) ?: true

	/**
	 * Execute the given action.
	 *
	 * @param action
	 *   The action to invoke, or `null` if no action should be performed.
	 */
	internal fun executeAction(action: ((Memento) -> Unit)?) =
		action?.invoke(memento)

	/**
	 * Handle the specified event.
	 *
	 * @param event
	 *   An event.
	 * @throws InvalidContextException
	 *   If the `ExecutionContext` was rendered invalid by an
	 *   [exception][Exception] thrown during a previous state transition.
	 * @throws InvalidTransitionException
	 *   If the current states does not have a transition on the specified
	 *   event.
	 */
	@Synchronized
	@Throws(InvalidContextException::class, InvalidTransitionException::class)
	fun handleEvent(event: Event) =	machine.handleEvent(event, this)
}
