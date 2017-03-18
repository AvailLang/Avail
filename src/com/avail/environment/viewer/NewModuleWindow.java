package com.avail.environment.viewer;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.FXUtility;
import com.avail.environment.tasks.NewModuleTask;
import javafx.beans.NamedArg;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code NewModuleWindow} is a {@link Scene} used to create a new module.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class NewModuleWindow
extends Scene
{
	/**
	 * Construct a {@link NewModuleWindow}.
	 *
	 * @param width
	 *        The width of the {@code Scene}.
	 * @param height
	 *        The height of the {@code Scene}.
	 * @param directory
	 *        The {@link File} directory this new module will be placed in.
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param task
	 *        The {@link NewModuleTask} that spawned this
	 *        {@code NewModuleWindow}.
	 */
	public NewModuleWindow (
		@NamedArg("width") final double width,
		@NamedArg("height") final double height,
		final @NotNull File directory,
		final @NotNull AvailWorkbench workbench,
		final @NotNull NewModuleTask task)
	{
		super(
			createWindowContent(task, directory, workbench),
			width,
			height);
		getStylesheets().add(ModuleViewer.class.getResource(
			"/workbench/module_viewer_styles.css").toExternalForm());
	}

	/**
	 * Answer the main {@link VBox} where the window content exists.
	 *
	 * @param task
	 *        The {@link NewModuleTask} that spawned this
	 *        {@code NewModuleWindow}.
	 * @param directory
	 *        The {@link File} directory this new module will be placed in.
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @return A {@code VBox}.
	 */
	private static @NotNull VBox createWindowContent (
		final @NotNull NewModuleTask task,
		final @NotNull File directory,
		final @NotNull AvailWorkbench workbench)
	{
		final Label moduleNameLabel = FXUtility.label(
			"Module Name", 5.0, 0, 0, 0);

		final TextField moduleNameField = FXUtility.textField(
			0, 0, 0, 2, 189, 27);

		moduleNameField.setPromptText("Enter Module Name");

		final HBox nameRow = FXUtility.hbox(
			5, moduleNameLabel, moduleNameField);

		final Label templateLabel = FXUtility.label(
			"Template", 5, 0, 0, 0);

		final ChoiceBox<String> templateChoices = FXUtility.choiceBox(
			0,
			0,
			0,
			0,
			220,
			27,
			workbench.templateOptions());
		templateChoices.getSelectionModel().selectFirst();

		final HBox templateRow = FXUtility.hbox(
			5, templateLabel, templateChoices);

		final Button create = new Button("Create");
		final Button cancel = new Button("Cancel");
		final Label errorLabel = FXUtility.label(
			"", 5.0, 0, 0, 0);
		errorLabel.setTextFill(Paint.valueOf("red"));

		create.disableProperty().bind(Bindings.createBooleanBinding(
			() -> moduleNameField.getText().isEmpty(),
			moduleNameField.textProperty()));

		final HBox buttonRow =
			FXUtility.hbox(5, errorLabel, create, cancel);
		buttonRow.setAlignment(Pos.CENTER_RIGHT);

		cancel.setOnAction(evt -> task.closeCleanly());

		create.setOnAction(evt ->
		{
			final String moduleName = moduleNameField.getText();
			final String leafFileName = moduleName + ".avail";
			final File newModule = new File(
				directory.getAbsolutePath() + "/" + leafFileName);
			if (newModule.exists())
			{
				errorLabel.setText("Module Already Exists!");
			}
			else
			{
				final String choice = templateChoices.getValue();
				final String contents = choice != null && !choice.isEmpty()
					? workbench.moduleTemplates
					.createNewModuleFromTemplate(choice, moduleName)
					: "";
				try
				{
					newModule.createNewFile();
					final List<String> input = new ArrayList<>();
					input.add(contents);
					Files.write(
						newModule.toPath(),
						input,
						StandardCharsets.UTF_8);
					task.closeCleanly();
				}
				catch (IOException e)
				{
					task.erroredClose("Module Creation Failed!");
				}
			}
		});

		return FXUtility.vbox(
			10,
			10,
			10,
			10,
			5,
			nameRow,
			templateRow,
			buttonRow);
	}
}
