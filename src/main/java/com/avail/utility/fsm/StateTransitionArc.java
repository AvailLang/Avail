/*
 * StateTransition.java
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
import java.util.Map;

/**
 * A state transition, effectively the "compiled form" of a single state
 * transition arc.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <State>
 *        The type of the new state.
 * @param <Event>
 *        The type of the triggering event.
 * @param <GuardKey>
 *        The type of guard keys.
 * @param <ActionKey>
 *        The type of action keys.
 * @param <Memento>
 *        The type of object passed to guards and actions.
 */
final class StateTransitionArc<
	State extends Enum<State>,
	Event extends Enum<Event>,
	GuardKey extends Enum<GuardKey>,
	ActionKey extends Enum<ActionKey>,
	Memento>
{
	/** The event upon whose receipt the transition will occur. */
	private final @Nullable
	Event event;

	/**
	 * Answer the event upon whose receipt the transition will occur.
	 *
	 * @return An event.
	 */
	@Nullable
	Event triggeringEvent ()
	{
		return event;
	}

	/**
	 * The guard key whose bound {@linkplain Transformer1 guard} will be
	 * performed to determine if a transition can be taken.
	 */
	private final @Nullable
	GuardKey guardKey;

	/**
	 * Answer the guard key whose bound {@linkplain Transformer1 guard} will be
	 * performed to determine if a transition can be taken.
	 *
	 * @return A guard key.
	 */
	@Nullable
	GuardKey guardKey ()
	{
		return guardKey;
	}

	/**
	 * The actual {@linkplain Transformer1 guard} that will be performed to
	 * determine if a transition can be taken.
	 */
	private @Nullable Transformer1<? super Memento, Boolean> guard;

	/**
	 * Answer the {@linkplain Transformer1 guard} that will be performed to
	 * determine if a transition can be taken.
	 *
	 * @return A {@linkplain Transformer1 guard}.
	 */
	@Nullable Transformer1<? super Memento, Boolean> guard ()
	{
		return guard;
	}

	/**
	 * The action key whose bound {@linkplain Continuation1 action} will be
	 * performed during transition.
	 */
	private final @Nullable
	ActionKey actionKey;

	/**
	 * Answer the action key whose bound {@linkplain Continuation1 action} will
	 * be performed during a transition.
	 *
	 * @return An action key.
	 */
	@Nullable
	ActionKey actionKey ()
	{
		return actionKey;
	}

	/**
	 * The actual {@linkplain Continuation1 action} that will be performed
	 * during transition.
	 */
	private @Nullable Continuation1<? super Memento> action;

	/**
	 * Answer the {@linkplain Continuation1 action} that will be performed
	 * during a transition.
	 *
	 * @return An {@linkplain Continuation1 action}.
	 */
	@Nullable Continuation1<? super Memento> action ()
	{
		return action;
	}

	/** The new state to which a transition will occur. */
	private final State newState;

	/**
	 * Answer the new state to which a transition will occur.
	 *
	 * @return A state.
	 */
	State stateAfterTransition ()
	{
		return newState;
	}

	/**
	 * Construct a new {@code StateTransitionArc}.
	 *
	 * @param event
	 *        An event, possibly {@code null}.
	 * @param guardKey
	 *        A guard key, possibly {@code null}.
	 * @param actionKey
	 *        An action key, possibly {@code null}.
	 * @param newState A state.
	 */
	StateTransitionArc (
		final @Nullable Event event,
		final @Nullable GuardKey guardKey,
		final @Nullable ActionKey actionKey,
		final State newState)
	{
		this.event     = event;
		this.newState  = newState;
		this.guardKey  = guardKey;
		this.actionKey = actionKey;
	}

	/**
	 * Set my {@linkplain Transformer1 guard} and {@linkplain Continuation1
	 * action} based on the supplied mappings from guard keys and action keys,
	 * respectively.
	 *
	 * @param guardMap
	 *        The mapping from GuardKey to guard.
	 * @param actionMap
	 *        The mapping from ActionKey to action.
	 */
	void populateGuardsAndActions (
		final Map<GuardKey, Transformer1<? super Memento, Boolean>>
			guardMap,
		final Map<ActionKey, Continuation1<? super Memento>>
			actionMap)
	{
		guard = guardMap.get(guardKey);
		action = actionMap.get(actionKey);
	}
}
