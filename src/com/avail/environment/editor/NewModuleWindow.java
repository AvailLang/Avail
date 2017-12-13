package com.avail.environment.editor;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.editor.fx.FXUtility;
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
import javafx.scene.paint.Paint;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code NewModuleWindow} is a {@link Scene} used to functionType a new module.
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
		final File directory,
		final AvailWorkbench workbench,
		final NewModuleTask task)
	{
		super(
			createWindowContent(task, directory, workbench),
			width,
			height);
		getStylesheets().add(ModuleEditorStyle.editorStyleSheet);
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
	private static VBox createWindowContent (
		final NewModuleTask task,
		final File directory,
		final AvailWorkbench workbench)
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

		final Label errorLabel = FXUtility.label(
			"", 5.0, 0, 0, 0);
		errorLabel.setTextFill(Paint.valueOf("red"));

		final Button create = FXUtility.button(
			"Create",
			evt ->
			{
				final String moduleName = moduleNameField.getText();
				task.setQualifiedName(moduleName);
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
						//noinspection ResultOfMethodCallIgnored
						newModule.createNewFile();
						final List<String> input = new ArrayList<>();
						input.add(contents);
						Files.write(
							newModule.toPath(),
							input,
							StandardCharsets.UTF_8);
						task.clearCanceTask();
						task.closeCleanly();
					}
					catch (final IOException e)
					{
						task.clearCanceTask();
						task.erroredClose("Module Creation Failed!");
					}
				}
			},
			Bindings.createBooleanBinding(
				() -> moduleNameField.getText().isEmpty(),
				moduleNameField.textProperty()));

		final Button cancel = FXUtility.button(
			"Cancel",
			evt -> task.cancelTask());

		final HBox buttonRow =
			FXUtility.hbox(5, errorLabel, create, cancel);
		buttonRow.setAlignment(Pos.CENTER_RIGHT);

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
