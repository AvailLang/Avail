/**
 * AvailArea.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import com.avail.environment.AvailWorkbench;
import com.avail.environment.editor.fx.FXUtility.KeyComboAction;
import com.avail.environment.editor.utility.PrefixNode;
import com.avail.utility.evaluation.Continuation0;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import org.fxmisc.richtext.CodeArea;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An {@code AvailArea} is a {@link CodeArea} where Avail code is displayed and
 * presumably editable.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class AvailArea
extends CodeArea
{
	/**
	 * A reference to the {@link AvailWorkbench}.
	 */
	private final @NotNull AvailWorkbench workbench;

	/**
	 * The last string search value.
	 */
	private String findBuffer;

	private final @NotNull JFrame frame;

	/**
	 * A {@link List} of {@link KeyComboAction}s to be performed on various
	 * key presses.
	 */
	private final @NotNull List<KeyComboAction> keyComboActions =
		new ArrayList<>();

	/**
	 * Construct an {@link AvailArea}.
	 *
	 * @param workbench
	 *        A reference to the {@link AvailWorkbench}.
	 * @param frame
	 *        The {@link JFrame} the {@code AvailArea} is in.
	 */
	public AvailArea (
		final @NotNull AvailWorkbench workbench,
		final @NotNull JFrame frame)
	{
		this.workbench = workbench;
		this.frame = frame;
		addKeyCombos(
			textTemplateAction(),
			gotoLineAction(),
			findAction(),
			findNextAction());
	}

	/**
	 * Add the array of {@link KeyComboAction}s to this {@link AvailArea}.
	 *
	 * @param actions
	 *        The array of {@code KeyComboAction}s to add.
	 */
	private void addKeyCombos (final @NotNull KeyComboAction... actions)
	{
		keyComboActions.addAll(Arrays.asList(actions));
		setOnKeyPressed(event -> keyComboActions.forEach(a -> a.event(event)));
	}

	/**
	 * Answer a {@link KeyComboAction} functionality to replace text with a
	 * template.
	 *
	 * @return A {@code KeyComboAction}.
	 */
	private @NotNull KeyComboAction textTemplateAction ()
	{
		return FXUtility.createKeyCombo(
			() ->
			{
				// Determine the intended prefix for filtering the template
				// list by scanning backward from the caret to the nearest word
				// boundary.
				final int caretPosition = getCaretPosition();
				final int prefixStart;
				final String prefix;
				{
					final String text = getText();
					int start = 0;
					for (
						int i = caretPosition - 1, cp = text.codePointAt(i);
						i >= 0;
						i -= Character.charCount(cp), cp = text.codePointAt(i))
					{
						if (!Character.isLetterOrDigit(cp))
						{
							start = i + Character.charCount(cp);
							break;
						}
					}
					prefixStart = start;
					prefix = text.substring(start, caretPosition);
				}
				// Determine which templates match the prefix.
				final List<String> matches =
					workbench.replaceTextTemplate.choiceList.stream()
						.filter(s -> s.startsWith(prefix))
						.collect(Collectors.toList());
				if (matches.size() == 1)
				{
					// If there is only one possible choice, then execute it
					// immediately.
					replaceText(
						prefixStart,
						caretPosition,
						workbench.replaceTextTemplate.get(matches.get(0)));
				}
				else
				{
					// Otherwise build an appropriate context menu to contain
					// the filtered templates.
					final ContextMenu menu = new ContextMenu();
					menu.getItems().addAll(matches.stream()
						.map(s ->
						{
							final MenuItem item = new MenuItem(s);
							item.setOnAction(
								event ->
								{
									final String choice = item.getText();
									final String template =
										workbench.replaceTextTemplate.get(
											choice);
									replaceText(
										prefixStart,
										caretPosition,
										template);
									requestFollowCaret();
								});
							return item;
						})
						.collect(Collectors.toList()));
					final Optional<Bounds> caretBounds = getCaretBounds();
					caretBounds.ifPresent(
						bounds ->
							menu.show(
								this,
								bounds.getMaxX(),
								bounds.getMaxY()));
				}
			},
			KeyCode.SPACE,
			KeyCombination.CONTROL_DOWN);
	}

	/**
	 *  Answer a {@link KeyComboAction} functionality to goto another line.
	 *
	 * @return A {@code KeyComboAction}.
	 */
	private @NotNull KeyComboAction gotoLineAction ()
	{
		Function<Integer, Integer> clamp =
			i -> Math.max(0, Math.min(i, getLength() - 1));

		return FXUtility.createKeyCombo(
			() ->
			{
				TextInputDialog dialog =
					FXUtility.textInputDialog("Go to Line");
				Optional<String> result = dialog.showAndWait();
				if (result.isPresent())
				{
					try
					{
						final int line = clamp.apply(
							Integer.parseInt(result.get()));
						showParagraphAtTop(line);
						moveTo(line - 1, 0);
						requestFollowCaret();
					}
					catch (NumberFormatException e)
					{
						//Just don't do anything
					}
					catch (IndexOutOfBoundsException e)
					{
						moveTo(getText().length());
						requestFollowCaret();
					}
				}
			},
			KeyCode.L,
			KeyCombination.META_DOWN);
	}

	/**
	 * Answer a {@link Continuation0} that finds in the {@link AvailArea}'s text
	 * the string in the {@link #findBuffer}.
	 *
	 * @return A {@code Continuation0}.
	 */
	private @NotNull Continuation0 finder ()
	{
		return () ->
		{
			int caret = getCaretPosition();
			final String text = getText();
			int position = text
				.substring(caret, text.length())
				.indexOf(findBuffer);
			if (position > -1)
			{
				moveTo(position + caret + findBuffer.length());
				requestFollowCaret();
				caretPositionProperty().getValue();
				selectRange(
					position + caret,
					position + caret + findBuffer.length());
			}
			else
			{
				Alert alert = new Alert(AlertType.INFORMATION);
				alert.setTitle("Find Result");
				alert.setHeaderText(null);
				alert.setContentText("No results!");
				alert.showAndWait();
			}
		};
	}

	/**
	 * Answer a {@link KeyComboAction} functionality to find text in the
	 * {@link AvailArea}.
	 *
	 * @return A {@code KeyComboAction}.
	 */
	private @NotNull KeyComboAction findAction ()
	{
		return FXUtility.createKeyCombo(
			() ->
			{
				TextInputDialog dialog =
					FXUtility.textInputDialog("Find");
				Optional<String> result = dialog.showAndWait();
				if (result.isPresent())
				{
					try
					{
						findBuffer = result.get();
						if (findBuffer.length() > 0)
						{
							finder().value();
						}
						else
						{
							findBuffer = null;
						}
					}
					catch (NumberFormatException e)
					{
						//Just don't do anything
					}
				}
			},
			KeyCode.F,
			KeyCombination.META_DOWN);
	}

	/**
	 * Answer a {@link KeyComboAction} functionality to find the next occurrence
	 * of {@link #findBuffer} in the {@link AvailArea}.
	 *
	 * @return A {@code KeyComboAction}.
	 */
	private @NotNull KeyComboAction findNextAction ()
	{
		return FXUtility.createKeyCombo(
			() ->
			{
				if (findBuffer != null)
				{
					finder().value();
				}
			},
			KeyCode.G,
			KeyCombination.META_DOWN);
	}
}
