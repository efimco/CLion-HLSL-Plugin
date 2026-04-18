package com.hlsl.intentions;

import com.hlsl.psi.HlslFile;
import com.hlsl.validation.HlslDxcSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class HlslAddDxcPragmasIntention implements IntentionAction {

    private static final Pattern PROFILE_PRAGMA = Pattern.compile(
            "^\\s*(?://)?\\s*#pragma\\s+hlsl\\s+profile\\s+\\S+",
            Pattern.MULTILINE);
    private static final Pattern ENTRY_PRAGMA = Pattern.compile(
            "^\\s*(?://)?\\s*#pragma\\s+hlsl\\s+entry\\s+\\S+",
            Pattern.MULTILINE);

    @Override
    public @NotNull String getText() {
        return "Add HLSL DXC pragmas";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "HLSL";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof HlslFile)) return false;
        if (editor == null) return false;
        String text = editor.getDocument().getText();
        boolean hasProfile = PROFILE_PRAGMA.matcher(text).find();
        boolean hasEntry = ENTRY_PRAGMA.matcher(text).find();
        return !(hasProfile && hasEntry);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException {
        if (editor == null) return;
        Document document = editor.getDocument();
        String text = document.getText();
        HlslDxcSettings settings = HlslDxcSettings.getInstance();

        StringBuilder toInsert = new StringBuilder();
        if (!PROFILE_PRAGMA.matcher(text).find()) {
            toInsert.append("// #pragma hlsl profile ")
                    .append(settings.getDefaultProfile())
                    .append('\n');
        }
        if (!ENTRY_PRAGMA.matcher(text).find()) {
            toInsert.append("// #pragma hlsl entry ")
                    .append(settings.getDefaultEntryPoint())
                    .append('\n');
        }
        if (toInsert.length() == 0) return;

        if (!text.isEmpty() && !text.startsWith("\n")) {
            toInsert.append('\n');
        }

        document.insertString(0, toInsert.toString());
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
