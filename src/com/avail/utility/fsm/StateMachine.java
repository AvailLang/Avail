/*
 * StateMachine.java
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

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumMap;

/**
 * A finite state machine (<strong>FSM</strong>) comprises a finite set of
 * states, a table of transitions between those states, and a table of
 * {@linkplain Continuation1 actions} to be performed when states are entered,
 * exited, or transitioned between. Each transition may have a guard, allowing
 * conditional selection among transitions for the same event. A transition
 * may have null for its event, in which case the transition is traversed
 * immediately after arriving if the guard permits.
 *
 * <p>The FSM is parametric on the {@linkplain Class type} of states, the type
 * of events which cause transitions between states, the type of action keys,
 * the type of guard keys, and the type of argument that an action will receive.
 * The client provides each parameter for maximum type-safety and code-reuse.
 * </p>
 *
 * <p>States, events, action keys and guard keys are enumerations, thereby
 * allowing the compiler to check the correctness of usages and the runtime
 * environment to validate the comprehensiveness of the FSM model. In
 * particular, all states must be reachable, all events must occur, and all
 * action keys must be bound to executable actions.</p>
 *
 * <p>Executable actions are keyed by members of an action key enumeration. That
 * is, states and transitions are not bound directly to actions, but rather
 * indirectly to keys. This allows optimal type-safety and automated validation.
 * An action accepts a single argument of client-specified type. This object is
 * treated as a memento by the FSM, an opaque argument supplied at
 * {@linkplain ExecutionContext execution context} creation-time and passed
 * through to an action upon its performance.</p>
 *
 * <p>Executable guards are keyed by members of a guard key enumeration for
 * the same reasons as actions. An executable guard accepts a single argument
 * of client-specified type, namely the same memento passed to executable
 * actions.  The executable guard answers a boolean indicating whether the
 * transition may be taken.</p>
 *
 * <p>A new FSM is obtainable only via an appropriately parameterized
 * {@linkplain StateMachineFactory factory}. This allows incremental and
 * arbitrary-order specification of the FSM independent of any runtime assembly
 * constraints.</p>
 *
 * <p>An FSM provides a single protocol operation, namely the creation of a new
 * execution context ({@link #createExecutionContext(Object)}). Transitions are
 * client-instigated by notification of event occurrences ({@link
 * ExecutionContext#handleEvent(Enum)}). Not every state will have a valid
 * transition on every event; should an invalid transition occur, an
 * <code>{@link InvalidTransitionException}</code> will be thrown. This event
 * may be safely discarded to permit continued use of the signaling execution
 * context. Event notification is thread-safe, and multiple contexts may
 * simultaneously execute on the same FSM.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <State>
 *        The kind of states.
 * @param <Event>
 *        The kind of events.
 * @param <ActionKey>
 *        The kind of action keys.
 * @param <GuardKey>
 *        The kind of guard keys.
 * @param <Memento>
 *        The kind of argument that {@linkplain Continuation1 actions} will
 *        receive.
 * @see <a href="http://en.wikipedia.org/wiki/Finite-state_machine">
 *      Finite state machine</a>
 */
public final class StateMachine<
	State extends Enum<State>,
	Event extends Enum<Event>,
	GuardKey extends Enum<GuardKey>,
	ActionKey extends Enum<ActionKey>,
	Memento>
{
	/**
	 * The state in which to start a new {@linkplain ExecutionContext state
	 * machine context}.
	 */
	private final State initialState;

	/**
	 * Answer this {@code StateMachine}'s initial state.
	 *
	 * @return The initial state.
	 */
	State initialState ()
	{
		return initialState;
	}

	/**
	 * The complete transition table, a {@linkplain EnumMap map} from states to
	 * {@linkplain StateSummary state summaries}.
	 */
	private final EnumMap<
			State, StateSummary<State, Event, GuardKey, ActionKey, Memento>>
		transitionTable;

	/**
	 * Add the specified {@linkplain StateSummary state summary} to the
	 * transition table. Ensure that a summary for the same state is not already
	 * present.
	 *
	 * @param summary A {@linkplain StateSummary state summary}.
	 */
	private void addStateSummary (
		final StateSummary<State, Event, GuardKey, ActionKey, Memento> summary)
	{
		assert !transitionTable.containsKey(summary.state());
		transitionTable.put(summary.state(), summary);
	}

	/**
	 * Construct a new {@code StateMachine}.
	 *
	 * @param initialState
	 *        The state in which a new {@link ExecutionContext context} will
	 *        start.
	 * @param summaries
	 *        The collection of {@link StateSummary state summaries}.
	 */
	StateMachine (
		final State initialState,
		final Collection<
				StateSummary<State, Event, GuardKey, ActionKey, Memento>>
			summaries)
	{
		this.initialState    = initialState;
		this.transitionTable = new EnumMap<>(initialState.getDeclaringClass());
		for (final StateSummary<State, Event, GuardKey, ActionKey, Memento>
			summary : summaries)
		{
			addStateSummary(summary);
		}
	}

	/**
	 * Create a {@linkplain ExecutionContext context} for executing this {@code
	 * StateMachine}.
	 *
	 * @param memento
	 *        The memento to pass to each {@linkplain Continuation1 action}.
	 * @return A new {@linkplain ExecutionContext execution context}.
	 */
	public ExecutionContext<State, Event, GuardKey, ActionKey, Memento>
		createExecutionContext (
			final Memento memento)
	{
		final ExecutionContext<State, Event, GuardKey, ActionKey, Memento>
			context = new ExecutionContext<>(this, memento);

		synchronized (context)
		{
			context.executeAction(
				transitionTable.get(initialState).getEntryAction());
			followAutomaticTransitions(context);
		}
		return context;
	}

	/**
	 * Follow automatic transitions from the current state until there are no
	 * more viable transitions.
	 *
	 * @param executionContext
	 *        The {@linkplain ExecutionContext execution context} to advance.
	 */
	private void followAutomaticTransitions (
		final ExecutionContext<State, Event, GuardKey, ActionKey, Memento>
			executionContext)
	{
		while (true)
		{
			final @Nullable State sourceState =
				executionContext.currentState();
			if (sourceState == null)
			{
				return;
			}
			final @Nullable StateTransitionArc<
					State, Event, GuardKey, ActionKey, Memento>
				arc = transitionTable.get(sourceState).getTransitionArc(
					null, executionContext);
			if (arc == null)
			{
				return;
			}
			final State targetState = arc.stateAfterTransition();
			executionContext.executeAction(
				transitionTable.get(sourceState).getExitAction());
			executionContext.justSetState(null);
			executionContext.executeAction(arc.action());
			executionContext.justSetState(targetState);
			executionContext.executeAction(
				transitionTable.get(targetState).getEntryAction());
		}
	}

	/**
	 * Handle an event. In particular, run the <em>exit</em> {@linkplain
	 * Continuation1 action} for the current state, run the action on the
	 * appropriate {@linkplain StateTransitionArc transition arc}, and run the
	 * <em>entry</em> action for the target state. Set the current state to
	 * the target state.
	 *
	 * @param event
	 *        The event to process.
	 * @param context
	 *        The {@linkplain ExecutionContext context} to update.
	 * @throws InvalidContextException
	 *         If the specified {@linkplain ExecutionContext context} was
	 *         rendered invalid by an {@linkplain Exception exception} thrown
	 *         during a previous state transition.
	 * @throws InvalidTransitionException
	 *         If the current state does not have a transition on the
	 *         specified event.
	 */
	void handleEvent (
			final Event event,
			final ExecutionContext<State, Event, GuardKey, ActionKey, Memento>
				context)
		throws InvalidContextException, InvalidTransitionException
	{
		final @Nullable State sourceState = context.currentState();
		if (sourceState == null)
		{
			throw new InvalidContextException(
				"event " + event + " signaled on invalid context");
		}
		final @Nullable StateSummary<State, Event, GuardKey, ActionKey, Memento>
			summary = transitionTable.get(sourceState);
		assert summary != null;
		final @Nullable StateTransitionArc<
				State, Event, GuardKey, ActionKey, Memento>
			arc = summary.getTransitionArc(event, context);
		if (arc == null)
		{
			throw new InvalidTransitionException(
				"state \"" + sourceState
				+ "\" could not transition on event \"" + event +"\"");
		}

		final State targetState = arc.stateAfterTransition();
		context.executeAction(summary.getExitAction());
		context.justSetState(null);
		context.executeAction(arc.action());
		context.justSetState(targetState);
		context.executeAction(
			transitionTable.get(targetState).getEntryAction());
		followAutomaticTransitions(context);
	}
}
