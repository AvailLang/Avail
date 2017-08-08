/**
 * FilterComboBox.java
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

package com.avail.environment.editor.fx;
import com.avail.tools.options.Option;
import com.avail.utility.evaluation.Continuation0;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * An {@code FilterComboBox} is a {@link ComboBox} that filters the options as
 * the user types.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class FilterComboBox<T>
extends ComboBox<T>
{
	/**
	 * A {@link BiFunction} that accepts the typed String and an object from
	 * the {@link ComboBox} option list.
	 */
	final @NotNull BiFunction<String, T, Boolean> matcher;

	/**
	 * A {@link Continuation0} that is run when {@link KeyCode#ENTER} is hit.
	 */
	private @NotNull Continuation0 enterBehavior = () -> {};

	/**
	 * Perform an action when the following characters are not pressed:
	 * <ul>
	 * <li>{@link KeyCode#UP}</li>
	 * <li>{@link KeyCode#DOWN}</li>
	 * <li>{@link KeyCode#BACK_SPACE}</li>
	 * <li>{@link KeyCode#DELETE}</li>
	 * <li>{@link KeyCode#ENTER}</li>
	 * <li>{@link KeyCode#RIGHT}</li>
	 * <li>{@link KeyCode#LEFT}</li>
	 * <li>{@link KeyCode#SHIFT}</li>
	 * <li>{@link KeyCode#CAPS}</li>
	 * <li>{@link KeyCode#CONTROL}</li>
	 * <li>{@link KeyCode#HOME}</li>
	 * <li>{@link KeyCode#END}</li>
	 * <li>{@link KeyCode#TAB}</li>
	 * <li>{@link KeyCode#ALT}</li>
	 * <li>{@link KeyCode#META}</li>
	 * <li>{@link KeyCode#COMMAND}</li>
	 * <li>{@link KeyCode#ESCAPE}</li>
	 * </ul>
	 *
	 * and when {@link KeyCode#isFunctionKey()} is false.
	 */
	protected void generalKeyAction ()
	{
		//Do nothing
	}

	/**
	 * A special action to take when {@link KeyCode#BACK_SPACE} is pressed.
	 */
	protected void backspaceAction ()
	{
		//Do nothing
	}

	/**
	 * A special action to take when {@link KeyCode#BACK_SPACE} is pressed.
	 */
	protected void deleteAction ()
	{
		//Do nothing
	}

	/**
	 * A special action to take when {@link KeyCode#DOWN} is pressed.
	 */
	protected void downSelectAction ()
	{
		//Do nothing
	}

	/**
	 * Answer the {@link Continuation0} that is run when {@link KeyCode#ENTER}
	 * is hit.
	 *
	 * @return A {@code Continuation0}.
	 */
	@NotNull Continuation0 enterBehavior ()
	{
		return enterBehavior;
	}

	/**
	 * Set the {@link #enterBehavior}.
	 *
	 * @param enterBehavior
	 *        A {@link Continuation0}.
	 */
	public void enterBehavior (final @NotNull Continuation0 enterBehavior)
	{
		this.enterBehavior = enterBehavior;
	}

	/**
	 * The initial static option list.
	 */
	private @NotNull ObservableList<T> optionList;

	/**
	 * Answer the selected {@link ComboBox} value.
	 *
	 * @return The selected object
	 */
	public T getSelection()
	{
		if (getSelectionModel().getSelectedIndex() < 0)
		{
			return null;
		}
		else
		{
			return getItems()
				.get(getSelectionModel().getSelectedIndex());
		}
	}

	/**
	 * Add the {@link Option} {@link Collection} that populates the drop down.
	 *
	 * @param options
	 *        The collection to add.
	 */
	public void addOptions (final @NotNull Collection<T> options)
	{
		getItems().addAll(options);
		optionList = getItems();
	}

	/**
	 * Generate the {@linkplain Collection options} that are visible at this
	 * point.
	 *
	 * @return A {@link Collection}.
	 */
	protected @NotNull Collection<T> generateVisibleList ()
	{
		final List<T> list = new ArrayList<>();
		for (final T aData : optionList)
		{
			if (aData != null
				&& getEditor().getText() != null
				&& matcher.apply(
				getEditor().getText(), aData))
			{
				list.add(aData);
			}
		}

		return list;
	}

	/**
	 * Construct a {@link FilterComboBox}.
	 *
	 * @param matcher
	 *        A {@link BiFunction} that accepts the typed String and an object
	 *        from the {@link ComboBox} option list.
	 * @param enterBehavior
	 *        A {@link Continuation0} that is run when {@link KeyCode#ENTER}
	 *        is hit.
	 */
	public FilterComboBox (
		final @NotNull  BiFunction<String, T, Boolean> matcher,
		final @NotNull Continuation0 enterBehavior)
	{
		this.matcher = matcher;
		this.enterBehavior = enterBehavior;
		setEditable(true);
		getEditor().focusedProperty().addListener(observable ->
		{
			if (getSelectionModel().getSelectedIndex() < 0)
			{
				getEditor().setText(null);
			}
		});
		addEventHandler(
			KeyEvent.KEY_PRESSED,
			t -> hide());
		addEventHandler(
			KeyEvent.KEY_RELEASED,
			this.eventHandler);
	}

	/**
	 * Construct a {@link FilterComboBox}.
	 *
	 * @param matcher
	 *        A {@link BiFunction} that accepts the typed String and an object
	 *        from the {@link ComboBox} option list.
	 */
	public FilterComboBox (
		final @NotNull  BiFunction<String, T, Boolean> matcher)
	{
		this(matcher, () -> {});
	}

	/**
	 * Construct a {@link FilterComboBox}.
	 *
	 * @param items
	 *        The items in the list.
	 * @param matcher
	 *        A {@link BiFunction} that accepts the typed String and an object
	 *        from the {@link ComboBox} option list.
	 * @param enterBehavior
	 *        A {@link Continuation0} that is run when {@link KeyCode#ENTER}
	 */
	public FilterComboBox (
		final @NotNull Collection<T> items,
		final @NotNull BiFunction<String, T, Boolean> matcher,
		final @Nullable Continuation0 enterBehavior)
	{
		this(matcher, enterBehavior);
		addOptions(items);

	}

	/**
	 * Construct a {@link FilterComboBox}.
	 *
	 * @param items
	 *        The items in the list.
	 * @param matcher
	 *        A {@link BiFunction} that accepts the typed String and an object
	 *        from the {@link ComboBox} option list.
	 */
	public FilterComboBox (
		final @NotNull Collection<T> items,
		final @NotNull BiFunction<String, T, Boolean> matcher)
	{
		this(matcher, () -> {});
		addOptions(items);
	}

	/**
	 * The {@link EventHandler} associated with this {@link FilterComboBox}.
	 */
	private final @NotNull EventHandler<KeyEvent> eventHandler =
		new EventHandler<KeyEvent>()
	{
		/**
		 * Indicates whether or not the caret should be moved. {@code
		 * true} indicates it should; {@code false} otherwise.
		 */
		private boolean moveCaretToPosition = false;

		/**
		 * The position of the caret in the typable area of the {@link
		 * FilterComboBox}.
		 */
		private int caretPosition;

		/**
		 * Move the {@link #caretPosition} to the new position if the
		 * current position is at the beginning (-1), otherwise, just
		 * move to the present {@link #caretPosition}.
		 *
		 * @param newPosition
		 *        The new position to move to.
		 */
		private void moveCaret(
			final int newPosition)
		{
			if (caretPosition == -1)
			{
				getEditor().positionCaret(newPosition);
			}
			else
			{
				getEditor().positionCaret(caretPosition);
			}
			moveCaretToPosition = false;
		}

		@Override
		public void handle (final KeyEvent event)
		{
			final KeyCode keycode = event.getCode();

			switch (keycode)
			{
				case UP:
				{
					caretPosition = -1;
					final String selection = getEditor().getText();
					if (selection != null)
					{
						moveCaret(selection.length());
					}
					return;
				}
				case DOWN:
				{
					if (!isShowing())
					{
						show();
					}
					caretPosition = -1;
					final String selection = getEditor().getText();
					if (selection != null)
					{
						downSelectAction();
						moveCaret(selection.length());
					}
					return;
				}
				case BACK_SPACE:
				{
					caretPosition = getEditor().getCaretPosition();
					if (caretPosition != -1)
					{
						moveCaretToPosition = true;
					}
					backspaceAction();
					break;
				}
				case DELETE:
				{
					moveCaretToPosition = true;
					caretPosition = getEditor().getCaretPosition();
					if (caretPosition > -1)
					{
						final String text = getEditor().getText();
						getEditor().setText(text.substring(0, caretPosition));
					}
					else
					{
						getEditor().setText("");
					}
					deleteAction();
					break;
				}
				case ENTER:
				{
					if (getItems().contains(getValue()))
					{
						enterBehavior().value();
					}
					return;
				}
				case RIGHT:
				case LEFT:
				case SHIFT:
				case CAPS:
				case CONTROL:
				case HOME:
				case END:
				case TAB:
				case ALT:
				case META:
				case COMMAND:
				case ESCAPE:
					return;
				default:
				{
					if (!keycode.isFunctionKey())
					{
						generalKeyAction();
					}
					break;
				}
			}

			final ObservableList<T> list =
				FXCollections.observableArrayList();
			String t = getEditor().getText();
			t = t == null ? "" : t;
			list.addAll(generateVisibleList());

			getEditor().setText(t);
			if (!moveCaretToPosition)
			{
				caretPosition = -1;
			}
			moveCaret(t.length());

			final ObservableList<T> origList = getItems();

			if (!origList.equals(list))
			{
				hide();
				setItems(list);
				show();
			}
		}
	};
}
