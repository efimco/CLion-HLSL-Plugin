package com.hlsl.formatting;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class HlslClangFormatConfigurable implements Configurable {

    private TextFieldWithBrowseButton pathField;
    private JTextField fallbackStyleField;
    private JCheckBox enabledCheckBox;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "HLSL / clang-format";
    }

    @Override
    public @Nullable JComponent createComponent() {
        pathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor chooser = FileChooserDescriptorFactory.createSingleFileDescriptor();
        chooser.setTitle("Select clang-format Executable");
        chooser.setDescription("Path to clang-format or clang-format.exe");
        pathField.addBrowseFolderListener(new TextBrowseFolderListener(chooser));

        fallbackStyleField = new JTextField();
        enabledCheckBox = new JCheckBox("Enable clang-format for HLSL files (uses Reformat Code action)");

        String auto = HlslClangFormatSettings.autoDetect();
        String hint = auto != null
                ? "Leave empty to use auto-detected: " + auto
                : "clang-format not found in PATH";

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("clang-format executable path:", pathField)
                .addComponentToRightColumn(new JLabel("<html><small>" + hint + "</small></html>"))
                .addLabeledComponent("Fallback style (when no .clang-format found):", fallbackStyleField)
                .addComponentToRightColumn(new JLabel("<html><small>e.g. LLVM, Google, Chromium, Mozilla, WebKit, Microsoft</small></html>"))
                .addComponent(enabledCheckBox)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        HlslClangFormatSettings s = HlslClangFormatSettings.getInstance();
        return !pathField.getText().equals(s.getClangFormatPath())
                || !fallbackStyleField.getText().equals(s.getFallbackStyle())
                || enabledCheckBox.isSelected() != s.isEnabled();
    }

    @Override
    public void apply() {
        HlslClangFormatSettings s = HlslClangFormatSettings.getInstance();
        s.setClangFormatPath(pathField.getText());
        s.setFallbackStyle(fallbackStyleField.getText());
        s.setEnabled(enabledCheckBox.isSelected());
    }

    @Override
    public void reset() {
        HlslClangFormatSettings s = HlslClangFormatSettings.getInstance();
        pathField.setText(s.getClangFormatPath());
        fallbackStyleField.setText(s.getFallbackStyle());
        enabledCheckBox.setSelected(s.isEnabled());
    }
}
