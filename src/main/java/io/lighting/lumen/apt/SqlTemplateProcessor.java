package io.lighting.lumen.apt;

import io.lighting.lumen.annotations.SqlTemplate;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("io.lighting.lumen.annotations.SqlTemplate")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class SqlTemplateProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        Map<TypeElement, List<ExecutableElement>> methodsByType = new LinkedHashMap<>();
        boolean hasErrors = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(SqlTemplate.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                error(element, "@SqlTemplate can only be applied to methods");
                hasErrors = true;
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            SqlTemplate annotation = method.getAnnotation(SqlTemplate.class);
            String template = annotation.value();
            try {
                io.lighting.lumen.template.SqlTemplate.parse(template);
            } catch (RuntimeException ex) {
                error(method, "Invalid SQL template: " + ex.getMessage());
                hasErrors = true;
                continue;
            }
            TypeElement enclosing = (TypeElement) method.getEnclosingElement();
            methodsByType.computeIfAbsent(enclosing, key -> new ArrayList<>()).add(method);
        }
        if (hasErrors) {
            return true;
        }
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByType.entrySet()) {
            generateTemplateClass(entry.getKey(), entry.getValue());
        }
        return true;
    }

    private void generateTemplateClass(TypeElement type, List<ExecutableElement> methods) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString() + "_SqlTemplates";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, type);
            try (Writer writer = sourceFile.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("public final class " + simpleName + " {\n");
                writer.write("    private " + simpleName + "() {\n");
                writer.write("    }\n\n");
                Set<String> usedNames = new HashSet<>();
                for (ExecutableElement method : methods) {
                    SqlTemplate annotation = method.getAnnotation(SqlTemplate.class);
                    String template = annotation.value();
                    String baseName = toConstantName(method.getSimpleName().toString());
                    String constantName = uniqueConstantName(baseName, usedNames);
                    writer.write("    public static final String " + constantName + " = \""
                        + escapeJava(template) + "\";\n");
                    writer.write("    public static final io.lighting.lumen.template.SqlTemplate "
                        + constantName + "_TEMPLATE = io.lighting.lumen.template.SqlTemplate.parse("
                        + constantName + ");\n\n");
                }
                writer.write("}\n");
            }
        } catch (IOException ex) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "Failed to generate template class: " + ex.getMessage(), type);
        }
    }

    private String uniqueConstantName(String base, Set<String> used) {
        String candidate = base;
        int index = 2;
        while (used.contains(candidate)) {
            candidate = base + "_" + index;
            index++;
        }
        used.add(candidate);
        return candidate;
    }

    private String toConstantName(String name) {
        StringBuilder builder = new StringBuilder();
        char prev = '\0';
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch)) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
                prev = ch;
                continue;
            }
            if (Character.isUpperCase(ch) && i > 0
                && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                builder.append('_');
            }
            builder.append(Character.toUpperCase(ch));
            prev = ch;
        }
        return builder.toString();
    }

    private String escapeJava(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20 || ch > 0x7e) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
