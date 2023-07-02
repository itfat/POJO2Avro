package com.example.pojo2avro;

import com.example.pojo2avro.exception.IllegalAvroSchemaConversionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.avro.Schema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ClickBasedAction extends AnAction {

    DTOToAvroConverter dtoToAvroConverter = new DTOToAvroConverter();

    @Override
    public void update(AnActionEvent e) {
        super.update(e);

        // Get the PSI file and find the enclosing PsiClass
        PsiFile psiFile = e.getData(PlatformDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiElement element = psiFile != null && editor != null ?
                psiFile.findElementAt(editor.getCaretModel().getOffset()) : null;
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

        if (psiClass == null || !isDtoClass(psiClass)) {
            // The right-clicked class is not a DTO class
            // Hide the action from the editor popup menu
            e.getPresentation().setVisible(false);
            e.getPresentation().setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            Editor editor = e.getData(PlatformDataKeys.EDITOR);
            Document document = editor != null ? editor.getDocument() : null;

            if (document != null) {
                // Get the caret position in the editor
                CaretModel caretModel = editor.getCaretModel();
                int caretOffset = caretModel.getOffset();

                // Get the PSI file and find the enclosing PsiClass
                PsiFile psiFile = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document);
                PsiElement element = psiFile.findElementAt(caretOffset);
                PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

                if (psiClass != null) {

                    // Generate the Avro schema
                    Schema avroSchema = dtoToAvroConverter.generateAvroSchema(psiClass);

                    // Create the Avro schema file path
                    File dtoFile = new File(psiFile.getVirtualFile().getPath());
                    String avroSchemaFilePath = psiFile.getProject().getBasePath() + "/" + dtoFile.getName().replace("Dto.java", "AvroDto.avsc");
                    File avroSchemaFile = new File(avroSchemaFilePath);
                    try (FileWriter writer = new FileWriter(avroSchemaFile)) {
                        writer.write(avroSchema.toString(true));
                    } catch (IOException exception) {
                        throw exception;
                    }

                    // Show a message or perform any additional actions as needed
                    System.out.println("Avro schema saved to: " + avroSchemaFile.getAbsolutePath());
                }
            }

        } catch (Exception exception) {
            throw new IllegalAvroSchemaConversionException("Unable to convert class to Avro schema");
        }
    }
    public boolean isDtoClass(PsiClass psiClass) {
        return psiClass.getName().endsWith("Dto");
    }

}
