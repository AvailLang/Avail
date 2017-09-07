/**
 * FilterDropDownDialog.java
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

import com.sun.javafx.scene.control.skin.resources.ControlResources;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * A {@code FilterDropDownDialog} is a {@link DialogPane} with a combo box that
 * filters selections.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class FilterDropDownDialog<
	T extends FilterComboBox<S>, S> extends Dialog<S>
{
	/**
	 * The {@link GridPane} the {@link FilterDropDownDialog} displayed on.
	 */
	private final @Nonnull GridPane grid;

	/**
	 * The label next to the {@link ComboBox}.
	 */
	private final @Nonnull Label label;

	/**
	 * The main {@link ComboBox}.
	 */
	private final @Nonnull T comboBox;

	public @Nonnull T getComboBox ()
	{
		return comboBox;
	}

	/**
	 * The default choice when dialog is opened.
	 */
	private final @Nullable S defaultChoice;

	/**
	 * Construct a {@link FilterDropDownDialog}.
	 *
	 * @param defaultChoice
	 *        The item to display as the pre-selected choice in the dialog.
	 *        This item must be contained within the choices array.
	 * @param choices
	 *        The {@link Collection} of choices for the {@link ComboBox}.
	 * @param comboBox
	 *        The {@link FilterComboBox} used in this dialog.
	 */
	public FilterDropDownDialog (
		final @Nonnull S defaultChoice,
		final @Nonnull Collection<S> choices,
		final @Nonnull T comboBox)
	{
		final DialogPane dialogPane = getDialogPane();

		// -- grid
		this.grid = new GridPane();
		this.grid.setHgap(10);
		this.grid.setMaxWidth(Double.MAX_VALUE);
		this.grid.setAlignment(Pos.CENTER_LEFT);

		// -- label
		label = new Label(dialogPane.getContentText());
		label.setMaxWidth(Double.MAX_VALUE);
		label.setMaxHeight(Double.MAX_VALUE);
		label.getStyleClass().add("content");
		label.setWrapText(true);
		label.setPrefWidth(360);
		label.setPrefWidth(Region.USE_COMPUTED_SIZE);
		label.textProperty().bind(dialogPane.contentTextProperty());

		dialogPane.contentTextProperty().addListener(o -> updateGrid());

		setTitle(ControlResources.getString("Dialog.confirm.title"));
		dialogPane.setHeaderText(ControlResources
			.getString("Dialog.confirm.header"));
		dialogPane.getStyleClass().add("choice-dialog");
		dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		final Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);

		this.comboBox = comboBox;
		this.comboBox.enterBehavior(okButton::fire);
		final double MIN_WIDTH = 150;
		this.comboBox.setMinWidth(MIN_WIDTH);
		if (choices != null)
		{
			this.comboBox.addOptions(choices);
		}
		this.comboBox.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(this.comboBox, Priority.ALWAYS);
		GridPane.setFillWidth(this.comboBox, true);

		this.defaultChoice = this.comboBox.getItems().contains(defaultChoice)
			? defaultChoice : null;

		if (defaultChoice == null)
		{
			this.comboBox.getSelectionModel().selectFirst();
		}
		else
		{
			this.comboBox.getSelectionModel().select(defaultChoice);
		}

		updateGrid();
		setGraphic(null);
		setResultConverter(dialogButton ->
		{
			final ButtonData data = dialogButton == null
				? null : dialogButton.getButtonData();
			return data == ButtonData.OK_DONE ? getSelectedItem() : null;
		});
	}

	/**
	 * Answer the currently selected item in the dialog.
	 */
	public final @Nullable S getSelectedItem()
	{
		return comboBox.getSelection();
	}

	/**
	 * Answer the property representing the currently selected item in the
	 * dialog.
	 */
	public final @Nonnull ReadOnlyObjectProperty<S> selectedItemProperty()
	{
		return comboBox.getSelectionModel().selectedItemProperty();
	}

	/**
	 * Sets the currently selected item in the dialog.
	 *
	 * @param item
	 *        The item to select in the dialog.
	 */
	public final void setSelectedItem(final @Nonnull S item)
	{
		comboBox.getSelectionModel().select(item);
	}

	/**
	 * Answers the list of all items that will be displayed to users. This list
	 * can be modified by the developer to add, remove, or reorder the items
	 * to present to the user.
	 */
	public final @Nonnull ObservableList<S> getItems()
	{
		return comboBox.getItems();
	}

	/**
	 * Answers the default choice that was specified in the constructor.
	 */
	public final @Nullable S getDefaultChoice()
	{
		return defaultChoice;
	}

	/**
	 * Draw the grid.
	 */
	private void updateGrid()
	{
		grid.getChildren().clear();

		grid.add(label, 0, 0);
		grid.add(comboBox, 1, 0);
		getDialogPane().setContent(grid);

		Platform.runLater(comboBox::requestFocus);
	}
}
