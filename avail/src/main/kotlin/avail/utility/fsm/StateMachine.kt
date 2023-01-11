/*
 * StateMachine.kt
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

import java.util.EnumMap

/**
 * A finite state machine (**FSM**) comprises a finite set of states, a table of
 * transitions between those states, and a table of actions to be performed when
 * states are entered, exited, or transitioned between. Each transition may have
 * a guard, allowing conditional selection among transitions for the same event.
 * A transition may have null for its event, in which case the transition is
 * traversed immediately after arriving if the guard permits.
 *
 * The FSM is parametric on the [type][Class] of states, the type of events
 * which cause transitions between states, the type of action keys, the type of
 * guard keys, and the type of argument that an action will receive. The client
 * provides each parameter for maximum type-safety and code-reuse.
 *
 * States, events, action keys and guard keys are enumerations, thereby allowing
 * the compiler to check the correctness of usages and the runtime environment
 * to validate the comprehensiveness of the FSM model. In particular, all states
 * must be reachable, all events must occur, and all action keys must be bound
 * to executable actions.
 *
 * Executable actions are keyed by members of an action key enumeration. That
 * is, states and transitions are not bound directly to actions, but rather
 * indirectly to keys. This allows optimal type-safety and automated validation.
 * An action accepts a single argument of client-specified type. This object is
 * treated as a memento by the FSM, an opaque argument supplied at
 * [execution&#32;context][ExecutionContext] creation-time and passed through to
 * an action upon its performance.
 *
 * Executable guards are keyed by members of a guard key enumeration for the
 * same reasons as actions. An executable guard accepts a single argument of
 * client-specified type, namely the same memento passed to executable actions.
 * The executable guard answers a boolean indicating whether the transition may
 * be taken.
 *
 * A new FSM is obtainable only via an appropriately parameterized
 * [factory][StateMachineFactory]. This allows incremental and arbitrary-order
 * specification of the FSM independent of any runtime assembly constraints.
 *
 * An FSM provides a single protocol operation, namely the creation of a new
 * execution context ([createExecutionContext]). Transitions are
 * client-instigated by notification of event occurrences
 * ([ExecutionContext.handleEvent]). Not every state will have a valid
 * transition on every event; should an invalid transition occur, an
 * `[InvalidTransitionException]` will be thrown. This event may be safely
 * discarded to permit continued use of the signaling execution context. Event
 * notification is thread-safe, and multiple contexts may simultaneously execute
 * on the same FSM.
 *
 * @param State
 *   The kind of states.
 * @param Event
 *   The kind of events.
 * @param ActionKey
 *   The kind of action keys.
 * @param GuardKey
 *   The kind of guard keys.
 * @param Memento
 *   The kind of argument that actions will receive.
 * @property initialState
 *   The state in which to start a new [state][ExecutionContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see [Finite&#32;state&#32;machine](http://en.wikipedia.org/wiki/Finite-state_machine)
 *
 * @constructor
 *
 * Construct a new `StateMachine`.
 *
 * @param initialState
 *   The state in which a new [context][ExecutionContext] will start.
 * @param summaries
 *   The collection of [state&#32;summaries][StateSummary].
 */
class StateMachine<
	State : Enum<State>,
	Event : Enum<Event>,
	GuardKey : Enum<GuardKey>,
	ActionKey : Enum<ActionKey>,
	Memento>
internal constructor(
	internal val initialState: State,
	summaries: Collection<
		StateSummary<State, Event, GuardKey, ActionKey, Memento>>)
{
	/**
	 * The complete transition table, a [map][EnumMap] from states to
	 * [state&#32;summaries][StateSummary].
	 */
	private val transitionTable = EnumMap<
			State,
			StateSummary<State, Event, GuardKey, ActionKey, Memento>>(
		initialState.declaringClass)

	/**
	 * Add the specified [state&#32;summary][StateSummary] to the transition
	 * table. Ensure that a summary for the same state is not already present.
	 *
	 * @param summary
	 *   A [state&#32;summary][StateSummary].
	 */
	private fun addStateSummary(
		summary: StateSummary<State, Event, GuardKey, ActionKey, Memento>)
	{
		assert(!transitionTable.containsKey(summary.state))
		transitionTable[summary.state] = summary
	}

	init
	{
		for (summary in summaries)
		{
			addStateSummary(summary)
		}
	}

	/**
	 * Create a [context][ExecutionContext] for executing this `StateMachine`.
	 *
	 * @param memento
	 *   The memento to pass to each action.
	 * @return
	 *   A new [execution&#32;context][ExecutionContext].
	 */
	fun createExecutionContext(memento: Memento):
		ExecutionContext<State, Event, GuardKey, ActionKey, Memento>
	{
		val context = ExecutionContext(this, memento)

		synchronized(context) {
			context.executeAction(transitionTable[initialState]!!.entryAction)
			followAutomaticTransitions(context)
		}
		return context
	}

	/**
	 * Follow automatic transitions from the current state until there are no
	 * more viable transitions.
	 *
	 * @param executionContext
	 *   The [execution&#32;context][ExecutionContext] to advance.
	 */
	private fun followAutomaticTransitions(
		executionContext: ExecutionContext<
			State, Event, GuardKey, ActionKey, Memento>)
	{
		while (true)
		{
			val sourceState = executionContext.currentState ?: return
			val sourceStateSummary = transitionTable[sourceState]!!
			val arc = sourceStateSummary.getTransitionArc(
				null,
				executionContext)
				?: return
			val targetState = arc.newState
			executionContext.executeAction(sourceStateSummary.exitAction)
			executionContext.currentState = null
			executionContext.executeAction(arc.action)
			executionContext.currentState = targetState
			executionContext.executeAction(
				transitionTable[targetState]!!.entryAction)
		}
	}

	/**
	 * Handle an event. In particular, run the *exit* action for the current
	 * state, run the action on the appropriate
	 * [transition&#32;arc][StateTransitionArc], and run the *entry* action for
	 * the target state. Set the current state to the target state.
	 *
	 * @param event
	 *   The event to process.
	 * @param context
	 *   The [context][ExecutionContext] to update.
	 * @throws InvalidContextException
	 *   If the specified [context][ExecutionContext] was rendered invalid by an
	 *   [exception][Exception] thrown during a previous state transition.
	 * @throws InvalidTransitionException
	 *   If the current state does not have a transition on the specified event.
	 */
	@Throws(InvalidContextException::class, InvalidTransitionException::class)
	internal fun handleEvent(
		event: Event,
		context: ExecutionContext<State, Event, GuardKey, ActionKey, Memento>)
	{
		val sourceState = context.currentState
			?: throw InvalidContextException(
				"event $event signaled on invalid context")
		val summary = transitionTable[sourceState]!!
		val arc = summary.getTransitionArc(event, context)
			?: throw InvalidTransitionException(
				"state \"" + sourceState
					+ "\" could not transition on event \"" + event + "\"")

		val targetState = arc.newState
		context.executeAction(summary.exitAction)
		context.currentState = null
		context.executeAction(arc.action)
		context.currentState = targetState
		context.executeAction(transitionTable[targetState]!!.entryAction)
		followAutomaticTransitions(context)
	}
}
