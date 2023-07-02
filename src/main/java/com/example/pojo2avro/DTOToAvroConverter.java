package com.example.pojo2avro;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.PsiType.BOOLEAN;
import static com.intellij.psi.PsiType.BYTE;
import static com.intellij.psi.PsiType.CHAR;
import static com.intellij.psi.PsiType.DOUBLE;
import static com.intellij.psi.PsiType.FLOAT;
import static com.intellij.psi.PsiType.INT;
import static com.intellij.psi.PsiType.LONG;
import static com.intellij.psi.PsiType.SHORT;

public class DTOToAvroConverter {
    public Schema generateAvroSchema(PsiClass psiClass) {
        SchemaBuilder.FieldAssembler<Schema> fieldAssembler = SchemaBuilder.record(psiClass.getName())
                .namespace(psiClass.getQualifiedName()).fields();

        for (PsiField field : psiClass.getFields()) {
            String fieldName = field.getName();
            PsiType fieldType = field.getType();
            Schema fieldSchema = convertPsiTypeToAvroSchema(fieldType);

            fieldAssembler = fieldAssembler.name(fieldName).type(fieldSchema).noDefault();
        }

        return fieldAssembler.endRecord();
    }
    private Schema convertPsiTypeToAvroSchema(PsiType psiType) {
        if (psiType instanceof PsiPrimitiveType) {
            return convertPsiPrimitiveType((PsiPrimitiveType) psiType);
        } else if (psiType instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) psiType;
            PsiClass resolvedClass = classType.resolve();
            if (resolvedClass != null && resolvedClass.isEnum()) {
                // Handle enum types
                return convertPsiEnumType(resolvedClass);
            } else if (resolvedClass != null && resolvedClass.getQualifiedName().equals(String.class.getCanonicalName())) {
                // Handle String type
                return Schema.create(Schema.Type.STRING);
            } else if (resolvedClass != null && resolvedClass.getQualifiedName().equals(List.class.getCanonicalName())) {
                PsiType[] parameters = classType.getParameters();
                if (parameters.length == 1) {
                    // Get the element type of the list
                    PsiType elementType = parameters[0];
                    Schema elementSchema = convertPsiTypeToAvroSchema(elementType);
                    return SchemaBuilder.array().items(elementSchema);
                }
            }else if (resolvedClass != null && resolvedClass.getQualifiedName().equals(LocalDateTime.class.getCanonicalName())) {
                return SchemaBuilder.builder().longBuilder().prop("logicalType", "local-timestamp-millis").endLong();
            } else if (resolvedClass != null && isPrimitiveType(resolvedClass.getQualifiedName())) {
                PsiPrimitiveType primitiveType = fromQualifiedName(resolvedClass.getQualifiedName());
                return convertPsiPrimitiveType(primitiveType);

            } else if (resolvedClass != null && !(psiType instanceof PsiPrimitiveType)) {
                return convertPsiClassType(resolvedClass);
            }
        } else if (psiType instanceof PsiArrayType) {
            // Handle array types
            PsiArrayType arrayType = (PsiArrayType) psiType;
            PsiType componentType = arrayType.getComponentType();
            Schema elementTypeSchema = convertPsiTypeToAvroSchema(componentType);
            return SchemaBuilder.array().items(elementTypeSchema);
        } else {
            // Handle other class types
            PsiClassType classType = (PsiClassType) psiType;
            PsiClass resolvedClass = classType.resolve();
            if (resolvedClass != null) {
                return convertPsiClassType(resolvedClass);
            }
        }

        // Default to string schema if the type is not supported
        return Schema.create(Schema.Type.STRING);
    }

    private Schema convertPsiPrimitiveType(PsiPrimitiveType psiType) {
        PsiType primitiveType = psiType;
        if (BOOLEAN.equals(primitiveType)) {
            return Schema.create(Schema.Type.BOOLEAN);
        } else if (BYTE.equals(primitiveType)) {
            return Schema.create(Schema.Type.BYTES);
        } else if (SHORT.equals(primitiveType) || INT.equals(primitiveType) || LONG.equals(primitiveType)) {
            return Schema.create(Schema.Type.LONG);
        } else if (FLOAT.equals(primitiveType)) {
            return Schema.create(Schema.Type.FLOAT);
        } else if (DOUBLE.equals(primitiveType)) {
            return Schema.create(Schema.Type.DOUBLE);
        } else if (CHAR.equals(primitiveType)) {
            return Schema.create(Schema.Type.STRING);
        }
        return Schema.create(Schema.Type.STRING);
    }

    private Schema convertPsiEnumType(PsiClass enumClass) {
        SchemaBuilder.EnumBuilder<Schema> enumBuilder = SchemaBuilder.enumeration(enumClass.getName())
                .namespace(enumClass.getQualifiedName());

        List<String> symbols = new ArrayList<>();
        for (PsiField field : enumClass.getFields()) {
            if (field instanceof PsiEnumConstant) {
                String enumSymbol = field.getName();
                symbols.add(enumSymbol);
            }
        }

        Schema schema = enumBuilder.symbols(symbols.toArray(new String[0]));

        return schema;
    }

    private Schema convertPsiClassType(PsiClass psiClass) {
        if (psiClass.isEnum()) {
            return convertPsiEnumType(psiClass);
        } else if (psiClass.getQualifiedName().equals(String.class.getCanonicalName())) {
            return Schema.create(Schema.Type.STRING);
        } else if (psiClass.getQualifiedName().equals(List.class.getCanonicalName())) {
            // Handle List type
            PsiType[] typeParameters = psiClass.getTypeParameters()[0].getSuperTypes();
            if (typeParameters.length == 1) {
                PsiType elementType = typeParameters[0];
                Schema elementSchema = convertPsiTypeToAvroSchema(elementType);
                return SchemaBuilder.array().items(elementSchema);
            }
        } else {
            SchemaBuilder.FieldAssembler<Schema> fieldAssembler = SchemaBuilder.record(psiClass.getName())
                    .namespace(psiClass.getQualifiedName()).fields();

            for (PsiField field : psiClass.getFields()) {
                String fieldName = field.getName();
                PsiType fieldType = field.getType();
                Schema fieldSchema = convertPsiTypeToAvroSchema(fieldType);

                fieldAssembler = fieldAssembler.name(fieldName).type(fieldSchema).noDefault();
            }

            return fieldAssembler.endRecord();
        }

        // Default to string schema if the type is not supported
        return Schema.create(Schema.Type.STRING);
    }

    private boolean isPrimitiveType(String qualifiedName) {
        return qualifiedName.equals(Boolean.class.getCanonicalName())
                || qualifiedName.equals(Byte.class.getCanonicalName())
                || qualifiedName.equals(Character.class.getCanonicalName())
                || qualifiedName.equals(Double.class.getCanonicalName())
                || qualifiedName.equals(Float.class.getCanonicalName())
                || qualifiedName.equals(Integer.class.getCanonicalName())
                || qualifiedName.equals(Long.class.getCanonicalName())
                || qualifiedName.equals(Short.class.getCanonicalName());
    }

    private PsiPrimitiveType fromQualifiedName(String qualifiedName) {
        if (qualifiedName.equals(Boolean.class.getCanonicalName())) {
            return PsiType.BOOLEAN;
        } else if (qualifiedName.equals(Byte.class.getCanonicalName())) {
            return PsiType.BYTE;
        } else if (qualifiedName.equals(Character.class.getCanonicalName())) {
            return PsiType.CHAR;
        } else if (qualifiedName.equals(Double.class.getCanonicalName())) {
            return PsiType.DOUBLE;
        } else if (qualifiedName.equals(Float.class.getCanonicalName())) {
            return PsiType.FLOAT;
        } else if (qualifiedName.equals(Integer.class.getCanonicalName())) {
            return PsiType.INT;
        } else if (qualifiedName.equals(Long.class.getCanonicalName())) {
            return PsiType.LONG;
        } else if (qualifiedName.equals(Short.class.getCanonicalName())) {
            return PsiType.SHORT;
        }
        return null;
    }
}
