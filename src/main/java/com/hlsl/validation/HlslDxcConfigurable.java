package com.hlsl.validation;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class HlslDxcConfigurable implements Configurable {

    private TextFieldWithBrowseButton dxcPathField;
    private JTextField defaultProfileField;
    private JTextField defaultEntryPointField;
    private JTextField hlslVersionField;
    private JCheckBox enableValidationCheckBox;
    private JCheckBox treatWarningsAsErrorsCheckBox;
    private JTextField additionalArgsField;

    // Warning group checkboxes: flag name -> checkbox
    private final Map<String, JCheckBox> warningCheckBoxes = new LinkedHashMap<>();

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "HLSL / DXC";
    }

    @Override
    public @Nullable JComponent createComponent() {
        dxcPathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
        chooserDescriptor.setTitle("Select DXC Executable");
        chooserDescriptor.setDescription("Path to dxc or dxc.exe");
        dxcPathField.addBrowseFolderListener(new TextBrowseFolderListener(chooserDescriptor));

        defaultProfileField = new JTextField();
        defaultEntryPointField = new JTextField();
        hlslVersionField = new JTextField();
        enableValidationCheckBox = new JCheckBox("Enable DXC validation");
        treatWarningsAsErrorsCheckBox = new JCheckBox("Treat warnings as errors (-WX)");
        additionalArgsField = new JTextField();

        // Warning group checkboxes
        warningCheckBoxes.put("unused-variable", new JCheckBox("Unused variables"));
        warningCheckBoxes.put("unused-parameter", new JCheckBox("Unused parameters"));
        warningCheckBoxes.put("unreachable-code", new JCheckBox("Unreachable code"));
        warningCheckBoxes.put("uninitialized", new JCheckBox("Uninitialized variables"));
        warningCheckBoxes.put("implicit-truncation", new JCheckBox("Implicit truncation"));
        warningCheckBoxes.put("conversion", new JCheckBox("Implicit conversions"));
        warningCheckBoxes.put("payload-access", new JCheckBox("Payload access (raytracing)"));
        warningCheckBoxes.put("effects-syntax", new JCheckBox("Effects syntax (deprecated)"));

        JPanel warningsPanel = new JPanel();
        warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
        warningsPanel.setBorder(BorderFactory.createTitledBorder("Warning Groups"));
        for (JCheckBox cb : warningCheckBoxes.values()) {
            warningsPanel.add(cb);
        }

        // Show auto-detected path as hint
        String autoDetected = HlslDxcSettings.autoDetectDxc();
        String hint = autoDetected != null
                ? "Leave empty to use auto-detected: " + autoDetected
                : "DXC not found in PATH, Windows SDK, or Vulkan SDK";

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("DXC executable path:", dxcPathField)
                .addComponentToRightColumn(new JLabel("<html><small>" + hint + "</small></html>"))
                .addLabeledComponent("Default shader profile:", defaultProfileField)
                .addLabeledComponent("Default entry point:", defaultEntryPointField)
                .addLabeledComponent("HLSL version (-HV):", hlslVersionField)
                .addLabeledComponent("Additional DXC arguments:", additionalArgsField)
                .addComponent(enableValidationCheckBox)
                .addComponent(treatWarningsAsErrorsCheckBox)
                .addComponent(warningsPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        return !dxcPathField.getText().equals(settings.getDxcPath())
                || !defaultProfileField.getText().equals(settings.getDefaultProfile())
                || !defaultEntryPointField.getText().equals(settings.getDefaultEntryPoint())
                || !hlslVersionField.getText().equals(settings.getHlslVersion())
                || !additionalArgsField.getText().equals(settings.getAdditionalArgs())
                || enableValidationCheckBox.isSelected() != settings.isEnableValidation()
                || treatWarningsAsErrorsCheckBox.isSelected() != settings.isTreatWarningsAsErrors()
                || warningCheckBoxes.get("unused-variable").isSelected() != settings.isWarnUnusedVariable()
                || warningCheckBoxes.get("unused-parameter").isSelected() != settings.isWarnUnusedParameter()
                || warningCheckBoxes.get("unreachable-code").isSelected() != settings.isWarnUnreachableCode()
                || warningCheckBoxes.get("uninitialized").isSelected() != settings.isWarnUninitializedVariable()
                || warningCheckBoxes.get("implicit-truncation").isSelected() != settings.isWarnImplicitTruncation()
                || warningCheckBoxes.get("conversion").isSelected() != settings.isWarnConversion()
                || warningCheckBoxes.get("payload-access").isSelected() != settings.isWarnPayloadAccess()
                || warningCheckBoxes.get("effects-syntax").isSelected() != settings.isWarnEffectsSyntax();
    }

    @Override
    public void apply() {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        settings.setDxcPath(dxcPathField.getText());
        settings.setDefaultProfile(defaultProfileField.getText());
        settings.setDefaultEntryPoint(defaultEntryPointField.getText());
        settings.setHlslVersion(hlslVersionField.getText());
        settings.setAdditionalArgs(additionalArgsField.getText());
        settings.setEnableValidation(enableValidationCheckBox.isSelected());
        settings.setTreatWarningsAsErrors(treatWarningsAsErrorsCheckBox.isSelected());
        settings.setWarnUnusedVariable(warningCheckBoxes.get("unused-variable").isSelected());
        settings.setWarnUnusedParameter(warningCheckBoxes.get("unused-parameter").isSelected());
        settings.setWarnUnreachableCode(warningCheckBoxes.get("unreachable-code").isSelected());
        settings.setWarnUninitializedVariable(warningCheckBoxes.get("uninitialized").isSelected());
        settings.setWarnImplicitTruncation(warningCheckBoxes.get("implicit-truncation").isSelected());
        settings.setWarnConversion(warningCheckBoxes.get("conversion").isSelected());
        settings.setWarnPayloadAccess(warningCheckBoxes.get("payload-access").isSelected());
        settings.setWarnEffectsSyntax(warningCheckBoxes.get("effects-syntax").isSelected());
    }

    @Override
    public void reset() {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        dxcPathField.setText(settings.getDxcPath());
        defaultProfileField.setText(settings.getDefaultProfile());
        defaultEntryPointField.setText(settings.getDefaultEntryPoint());
        hlslVersionField.setText(settings.getHlslVersion());
        additionalArgsField.setText(settings.getAdditionalArgs());
        enableValidationCheckBox.setSelected(settings.isEnableValidation());
        treatWarningsAsErrorsCheckBox.setSelected(settings.isTreatWarningsAsErrors());
        warningCheckBoxes.get("unused-variable").setSelected(settings.isWarnUnusedVariable());
        warningCheckBoxes.get("unused-parameter").setSelected(settings.isWarnUnusedParameter());
        warningCheckBoxes.get("unreachable-code").setSelected(settings.isWarnUnreachableCode());
        warningCheckBoxes.get("uninitialized").setSelected(settings.isWarnUninitializedVariable());
        warningCheckBoxes.get("implicit-truncation").setSelected(settings.isWarnImplicitTruncation());
        warningCheckBoxes.get("conversion").setSelected(settings.isWarnConversion());
        warningCheckBoxes.get("payload-access").setSelected(settings.isWarnPayloadAccess());
        warningCheckBoxes.get("effects-syntax").setSelected(settings.isWarnEffectsSyntax());
    }
}
