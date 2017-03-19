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
import com.avail.utility.evaluation.Continuation0;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import org.fxmisc.richtext.CodeArea;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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

	private final @NotNull List<KeyComboAction> keyComboActions =
		new ArrayList<>();

	/**
	 * Construct an {@link AvailArea}.
	 *
	 * @param workbench
	 *        A reference to the {@link AvailWorkbench}.
	 */
	public AvailArea (final @NotNull AvailWorkbench workbench)
	{
		this.workbench = workbench;
		addGotoLine();
		addFind();
		addTextTemplate();
		setOnKeyPressed(event ->
			keyComboActions.forEach(a -> a.event(event)));
	}

	/**
	 * Add functionality to replace text with a template.
	 */
	private void addTextTemplate ()
	{
		//Template text replacement
		keyComboActions.add(FXUtility.createKeyCombo(
			() ->
			{
				ChoiceDialog<String> dialog =
					workbench.replaceTextTemplate.dialog();
				// Traditional way to get the response value.
				Optional<String> result = dialog.showAndWait();

				// The Java 8 way to get the response value (with lambda expression).
				result.ifPresent(choice ->
					{
						final String template =
							workbench.replaceTextTemplate.get(choice);
						int carret = getCaretPosition();
						insertText(carret, template);
						requestFollowCaret();
					});
			},
			KeyCode.SPACE,
			KeyCombination.CONTROL_DOWN));
	}

	/**
	 * Add functionality to goto another line.
	 */
	private void addGotoLine ()
	{
		Function<Integer, Integer> clamp =
			i -> Math.max(0, Math.min(i, getLength() - 1));

		keyComboActions.add(FXUtility.createKeyCombo(
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
				}
			},
			KeyCode.L,
			KeyCombination.META_DOWN));
	}

	/**
	 * Add functionality to goto another line.
	 */
	private void addFind ()
	{
		final Continuation0 find = () ->
		{
			int carret = getCaretPosition();
			final String text = getText();
			int position = text.substring(carret, text.length())
				.indexOf(findBuffer);
			if (position > -1)
			{
				moveTo(position + carret + findBuffer.length());
				requestFollowCaret();
				caretPositionProperty().getValue();
				selectRange(position + carret,
					position + carret + findBuffer.length());
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

		keyComboActions.add(FXUtility.createKeyCombo(
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
							find.value();
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
			KeyCombination.META_DOWN));

		//Repeat previous search from current position.
		keyComboActions.add(FXUtility.createKeyCombo(
			() ->
			{
				if (findBuffer != null)
				{
					find.value();
				}
			},
			KeyCode.G,
			KeyCombination.META_DOWN));
	}
}
