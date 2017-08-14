/**
 * FXUtility.java
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
import com.avail.utility.evaluation.Continuation0;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * An {@code FXUtility} is a utility class with utility methods for making
 * JavaFX components more easily.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public final class FXUtility
{
	/**
	 * Create a new {@link Button} with attached action.
	 *
	 * @param name
	 *        The button label.
	 * @param eventHandler
	 *        The {@link EventHandler} parameterized on an {@link ActionEvent}
	 *        that performs an action when the button is clicked.
	 * @return A button.
	 */
	public static @NotNull Button button (
		final @NotNull String name,
		final @NotNull EventHandler<ActionEvent> eventHandler)
	{
		final Button btn = new Button(name);
		btn.defaultButtonProperty().bind(btn.focusedProperty());
		btn.setOnAction(eventHandler);
		return btn;
	}

	/**
	 * Create a new {@link Button} with attached action.
	 *
	 * @param name
	 *        The button label.
	 * @param eventHandler
	 *        The {@link EventHandler} parameterized on an {@link ActionEvent}
	 *        that performs an action when the button is clicked.
	 * @param booleanBinding
	 *        The {@link BooleanBinding} that indicates a condition, when {@code
	 *        true}, the button is disabled.
	 * @return A button.
	 */
	public static @NotNull Button button (
		final @NotNull String name,
		final @NotNull EventHandler<ActionEvent> eventHandler,
		final @NotNull BooleanBinding booleanBinding)
	{
		final Button btn = new Button(name);
		btn.defaultButtonProperty().bind(btn.focusedProperty());
		btn.setOnAction(eventHandler);
		btn.disableProperty().bind(booleanBinding);
		return btn;
	}

	/**
	 * Answer a {@link JFXPanel} with the provided {@link Scene}.
	 *
	 * @param scene
	 *        The {@code Scene} to add to the panel.
	 * @return A {@code JFXPanel}.
	 */
	public static @NotNull JFXPanel fxPanel (final @NotNull Scene scene)
	{
		final JFXPanel panel = new JFXPanel();
		panel.setScene(scene);
		return panel;
	}

	/**
	 * Answer a {@link JFrame} with an imbeded {@link JFXPanel}.
	 *
	 * @param frameName
	 *        The name of the window.
	 * @param fxPanel
	 *        The {@code JFXPanel} to set.
	 * @return A {@code JFrame}.
	 */
	public static @NotNull JFrame jFrame (
		final @NotNull String frameName,
		final @NotNull JFXPanel fxPanel)
	{
		final JFrame frame = new JFrame(frameName);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);
		frame.getContentPane().add(BorderLayout.CENTER, panel);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setResizable(true);
		frame.add(fxPanel);
		return frame;
	}

	/**
	 * Answer a {@link JFrame} with an imbeded {@link JFXPanel}.
	 *
	 * @param frameName
	 *        The name of the window.
	 * @param fxPanel
	 *        The {@code JFXPanel} to set.
	 * @param width
	 *        The {@linkplain Component#width width}.
	 * @param height
	 *        The {@linkplain Component#height height}.
	 * @return A {@code JFrame}.
	 */
	public static @NotNull JFrame jFrame (
		final @NotNull String frameName,
		final @NotNull JFXPanel fxPanel,
		final int width,
		final int height)
	{
		final JFrame frame = new JFrame(frameName);
		Platform.setImplicitExit(false);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);

		frame.getContentPane().add(BorderLayout.CENTER, panel);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setVisible(true);
		frame.setResizable(true);
		frame.add(fxPanel);
		frame.setSize(width, height);
		return frame;
	}

	/**
	 * Answer a {@link JFrame} with an imbeded {@link JFXPanel} that contains
	 * a {@link Scene}.
	 *
	 * @param frameName
	 *        The name of the window.
	 * @param scene
	 *        The {@code Scene} to set.
	 * @return A {@code JFrame}.
	 */
	public static @NotNull JFrame jFrame (
		final @NotNull String frameName,
		final @NotNull Scene scene)
	{
		final JFrame frame = new JFrame(frameName);
		Platform.setImplicitExit(false);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);

		frame.getContentPane().add(BorderLayout.CENTER, panel);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setVisible(true);
		frame.setResizable(true);
		frame.add(fxPanel(scene));
		return frame;
	}

	/**
	 * Answer a {@link JFrame} with an imbeded {@link JFXPanel} that contains
	 * a {@link Scene}.
	 *
	 * @param frameName
	 *        The name of the window.
	 * @param scene
	 *        The {@code Scene} to set.
	 * @param width
	 *        The {@linkplain Component#width width}.
	 * @param height
	 *        The {@linkplain Component#height height}.
	 * @return A {@code JFrame}.
	 */
	public static @NotNull JFrame jFrame (
		final @NotNull String frameName,
		final @NotNull Scene scene,
		final int width,
		final int height)
	{
		final JFrame frame = new JFrame(frameName);
		Platform.setImplicitExit(false);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);

		frame.getContentPane().add(BorderLayout.CENTER, panel);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setVisible(true);
		frame.setResizable(true);
		frame.add(fxPanel(scene));
		frame.setSize(width, height);
		return frame;
	}

	/**
	 * Answer a {@link JFrame} with the specified size.
	 *
	 * @param width
	 *        The {@linkplain Component#width width}.
	 * @param height
	 *        The {@linkplain Component#height height}.
	 * @return A {@code JFrame}.
	 */
	public static @NotNull JFrame jFrame (
		final int width,
		final int height)
	{
		final JFrame frame = new JFrame();
		Platform.setImplicitExit(false);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);

		frame.getContentPane().add(BorderLayout.CENTER, panel);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setVisible(true);
		frame.setResizable(true);
		frame.setSize(width, height);
		return frame;
	}

	/**
	 * Answer a {@link Label}.
	 *
	 * @param text
	 *        The actual text of the {@code Label}.
	 * @param top
	 *        The the {@link Insets#top top offset}.
	 * @param right
	 *        The the {@link Insets#right right offset}.
	 * @param bottom
	 *        The the {@link Insets#bottom bottom offset}.
	 * @param left
	 *        The the {@link Insets#left left offset}.
	 * @return A {@code Label}.
	 */
	public static @NotNull Label label (
		final @NotNull String text,
		final double top,
		final double right,
		final double bottom,
		final double left)
	{
		final Label label = new Label(text);
		label.setPadding(new Insets(top, right, bottom, left));
		return label;
	}

	/**
	 * Answer a {@link Label}.
	 *
	 * @param text
	 *        The actual text of the {@code Label}.
	 * @param top
	 *        The the {@link Insets#top top offset}.
	 * @param right
	 *        The the {@link Insets#right right offset}.
	 * @param bottom
	 *        The the {@link Insets#bottom bottom offset}.
	 * @param left
	 *        The the {@link Insets#left left offset}.
	 * @param style
	 *        Set the {@link Node#setStyle(String) style of the label}.
	 * @return A {@code Label}.
	 */
	public static @NotNull Label label (
		final @NotNull String text,
		final double top,
		final double right,
		final double bottom,
		final double left,
		final @NotNull String style)
	{
		final Label label = label(text, top, right, bottom, left);
		label.setStyle(style);
		return label;
	}

	/**
	 * Answer a {@link TextField}.
	 *
	 * @param top
	 *        The the {@link Insets#top top offset}.
	 * @param right
	 *        The the {@link Insets#right right offset}.
	 * @param bottom
	 *        The the {@link Insets#bottom bottom offset}.
	 * @param left
	 *        The the {@link Insets#left left offset}.
	 * @param prefWidth
	 *        The {@link Node#prefWidth preferred width} of the text field.
	 * @param prefHeight
	 *        The {@link Node#prefHeight}  preferred height} of the text field.
	 * @return A {@code TextField}.
	 */
	public static @NotNull TextField textField (
		final double top,
		final double right,
		final double bottom,
		final double left,
		final double prefWidth,
		final double prefHeight)
	{
		final TextField textField = new TextField();
		textField.setPadding(new Insets(top, right, bottom, left));
		textField.setPrefWidth(prefWidth);
		textField.setPrefHeight(prefHeight);

		return textField;
	}

	/**
	 * Answer a {@link TextField}.
	 *
	 * @param top
	 *        The the {@link Insets#top top offset}.
	 * @param right
	 *        The the {@link Insets#right right offset}.
	 * @param bottom
	 *        The the {@link Insets#bottom bottom offset}.
	 * @param left
	 *        The the {@link Insets#left left offset}.
	 * @param prefWidth
	 *        The {@link Node#prefWidth preferred width} of the text field.
	 * @param prefHeight
	 *        The {@link Node#prefHeight}  preferred height} of the text field.
	 * @param style
	 *        Set the {@link Node#setStyle(String) style of the label}.
	 * @return A {@code TextField}.
	 */
	public static @NotNull TextField textField (
		final double top,
		final double right,
		final double bottom,
		final double left,
		final double prefWidth,
		final double prefHeight,
		final @NotNull String style)
	{
		final TextField textField =
			textField(top, right, bottom, left, prefWidth, prefHeight);
		textField.setStyle(style);
		return textField;
	}

	/**
	 * Answer a {@link ChoiceBox}.
	 *
	 * @param top
	 *        The the {@link Insets#top top offset}.
	 * @param right
	 *        The the {@link Insets#right right offset}.
	 * @param bottom
	 *        The the {@link Insets#bottom bottom offset}.
	 * @param left
	 *        The the {@link Insets#left left offset}.
	 * @param prefWidth
	 *        The {@link Node#prefWidth preferred width} of the choice box.
	 * @param prefHeight
	 *        The {@link Node#prefHeight}  preferred height} of the choice box.
	 * @param choices
	 *        The choice box {@link ChoiceBox#items items}.
	 * @param <T>
	 *        The type of item held in the choice box.
	 * @return A {@code ChoiceBox}.
	 */
	public static <T> @NotNull ChoiceBox<T> choiceBox (
		final double top,
		final double right,
		final double bottom,
		final double left,
		final double prefWidth,
		final double prefHeight,
		final @NotNull T... choices)
	{
		final ChoiceBox<T> choiceBox = new ChoiceBox<>(
			FXCollections.observableArrayList(choices));
		choiceBox.setPadding(new Insets(top, right, bottom, left));
		choiceBox.setPrefWidth(prefWidth);
		choiceBox.setPrefHeight(prefHeight);
		choiceBox.addEventFilter(
			KeyEvent.KEY_PRESSED,
			event ->
			{
				if (event.getCode() == KeyCode.UP
					|| event.getCode() == KeyCode.DOWN)
				{
					event.consume();
				}
			});
		return choiceBox;
	}

	/**
	 * Answer a {@link ChoiceBox}.
	 *
	 * @param top
	 *        The the {@link Insets#top top offset}.
	 * @param right
	 *        The the {@link Insets#right right offset}.
	 * @param bottom
	 *        The the {@link Insets#bottom bottom offset}.
	 * @param left
	 *        The the {@link Insets#left left offset}.
	 * @param prefWidth
	 *        The {@link Node#prefWidth preferred width} of the choice box.
	 * @param prefHeight
	 *        The {@link Node#prefHeight}  preferred height} of the choice box.
	 * @param style
	 *        Set the {@link Node#setStyle(String) style of the label}.
	 * @param choices
	 *        The choice box {@link ChoiceBox#items items}.
	 * @param <T>
	 *        The type of item held in the choice box.
	 * @return A {@code ChoiceBox}.
	 */
	public static <T> @NotNull ChoiceBox<T> choiceBox (
		final double top,
		final double right,
		final double bottom,
		final double left,
		final double prefWidth,
		final double prefHeight,
		final @NotNull String style,
		final @NotNull T... choices)
	{
		final ChoiceBox<T> choiceBox =
			choiceBox(top, right, bottom, left, prefWidth, prefHeight, choices);
		choiceBox.setStyle(style);
		return choiceBox;
	}

	/**
	 * Answer an {@link HBox} with the given nodes and spacing.
	 *
	 * @param spacing
	 *        The {@link HBox#spacing}.
	 * @param nodes
	 *        The {@linkplain Node Nodes} to add.
	 * @return An {@link HBox}.
	 */
	public static @NotNull HBox hbox (
		final double spacing,
		final @NotNull Node... nodes)
	{
		final HBox hBox = new HBox(nodes);
		hBox.setSpacing(spacing);
		return hBox;
	}

	/**
	 * Answer an {@link HBox} with the given nodes and spacing.
	 *
	 * @param style
	 *        Set the {@link Node#setStyle(String) style of the label}.
	 * @param spacing
	 *        The {@link HBox#spacing}.
	 * @param nodes
	 *        The {@linkplain Node Nodes} to add.
	 * @return An {@link HBox}.
	 */
	public static @NotNull HBox hbox (
		final @NotNull String style,
		final double spacing,
		final @NotNull Node... nodes)
	{
		final HBox hBox = hbox(spacing, nodes);
		hBox.setStyle(style);
		return hBox;
	}

	/**
	 * Answer an {@link VBox} with the given nodes and spacing.
	 *
	 * @param spacing
	 *        The {@link VBox#spacing}.
	 * @param nodes
	 *        The {@linkplain Node Nodes} to add.
	 * @return An {@link VBox}.
	 */
	public static @NotNull VBox vbox (
		final double top,
		final double right,
		final double bottom,
		final double left,
		final double spacing,
		final @NotNull Node... nodes)
	{
		final VBox vBox = vbox(spacing, nodes);
		vBox.setPadding(new Insets(top, right, bottom, left));
		return vBox;
	}

	/**
	 * Answer an {@link VBox} with the given nodes and spacing.
	 *
	 * @param spacing
	 *        The {@link VBox#spacing}.
	 * @param nodes
	 *        The {@linkplain Node Nodes} to add.
	 * @return An {@link VBox}.
	 */
	public static @NotNull VBox vbox (
		final double spacing,
		final @NotNull Node... nodes)
	{
		final VBox vBox = new VBox(nodes);
		vBox.setSpacing(spacing);
		return vBox;
	}

	/**
	 * Answer an {@link VBox} with the given nodes and spacing.
	 *
	 * @param style
	 *        Set the {@link Node#setStyle(String) style of the label}.
	 * @param spacing
	 *        The {@link VBox#spacing}.
	 * @param nodes
	 *        The {@linkplain Node Nodes} to add.
	 * @return An {@link VBox}.
	 */
	public static @NotNull VBox vbox (
		final @NotNull String style,
		final double spacing,
		final @NotNull Node... nodes)
	{
		final VBox vBox = vbox(spacing, nodes);
		vBox.setStyle(style);
		return vBox;
	}

	/**
	 * Answer a {@link KeyComboAction}.
	 *
	 * <p>
	 * An example usage for pressing down CONTROL + SPACE keys would be:
	 * </p>
	 *
	 * <pre>
	 * {@code FXUtility.createKeyCombo(
	 *      () -> System.out.println("I'm Pressed!"),
	 *      KeyCode.SPACE,
	 *      KeyCombination.CONTROL_DOWN);}
	 * </pre>
	 *
	 * @param action
	 *        The {@link Continuation0} action to take on event occurrence.
	 * @param keyCode
	 *        The {@link KeyCode} in the combination.
	 * @param modifiers
	 *        An array of {@link Modifier}s.
	 */
	public static @NotNull KeyComboAction createKeyCombo (
		final @NotNull Continuation0 action,
		final @NotNull KeyCode keyCode,
		final @NotNull Modifier... modifiers)
	{
		return new KeyComboAction(
			new KeyCodeCombination(keyCode, modifiers), action);
	}

	/**
	 * A {@code KeyComboAction} is a pairing of a {@link KeyCombination} and
	 * a {@link Continuation0} that occurs when the key combination occurs.
	 */
	public static class KeyComboAction
	{
		/**
		 * The {@link KeyCombination}.
		 */
		private final @NotNull KeyCombination keyCombination;

		/**
		 * The {@link Continuation0} to perform.
		 */
		private final @NotNull Continuation0 action;

		/**
		 * Perform the {@link #action} if the given {@link KeyEvent} matches
		 * the {@link #keyCombination}.
		 *
		 * @param event
		 *        The event to check.
		 */
		public void event (final KeyEvent event)
		{
			if (keyCombination.match(event))
			{
				action.value();
			}
		}

		/**
		 * Construct a {@link KeyComboAction}
		 *
		 * @param keyCombination
		 *        The {@link KeyCombination}.
		 * @param action
		 *        The {@link Continuation0} to perform.
		 */
		public KeyComboAction (
			final @NotNull KeyCombination keyCombination,
			final @NotNull Continuation0 action)
		{
			this.keyCombination = keyCombination;
			this.action = action;
		}
	}

	/**
	 * Create a {@link TextInputDialog}.
	 *
	 * @param title
	 *        The dialog {@link TextInputDialog#setTitle(String) title}.
	 * @return A {@code TextInputDialog}.
	 */
	public static TextInputDialog textInputDialog(
		final @NotNull String title)
	{
		final TextInputDialog textInputDialog = new TextInputDialog();
		textInputDialog.setTitle(title);
		textInputDialog.setHeaderText(null);
		final ButtonType ok = new ButtonType("OK", ButtonData.OK_DONE);
		final ButtonType cancel =
			new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
		textInputDialog.getDialogPane().getButtonTypes().setAll(ok, cancel);
		textInputDialog.setGraphic(null);
		return textInputDialog;
	}

	//Shouldn't be built
	private FXUtility () {}
}
