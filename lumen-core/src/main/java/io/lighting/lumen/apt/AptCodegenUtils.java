package io.lighting.lumen.apt;

import java.io.IOException;
import java.io.Writer;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * APT 代码生成的通用工具。
 * <p>
 * 只包含纯工具方法，避免在处理器中堆叠重复逻辑。
 */
final class AptCodegenUtils {
    private AptCodegenUtils() {
    }

    /**
     * 生成 package 声明行，空包名返回空字符串。
     */
    static String packageLine(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        return "package " + packageName + ";\n\n";
    }

    /**
     * 写入源码文件，并在失败时输出编译期错误。
     */
    static void writeSourceFile(
        Filer filer,
        Messager messager,
        Element originatingElement,
        String qualifiedName,
        String content,
        String errorPrefix
    ) {
        try {
            JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, originatingElement);
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(content);
            }
        } catch (IOException ex) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                errorPrefix + ex.getMessage(),
                originatingElement
            );
        }
    }

    /**
     * 将驼峰/混合命名转换为常量风格（UPPER_SNAKE）。
     */
    static String toConstantName(String name) {
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

    /**
     * 对字符串进行 Java 字面量转义。
     */
    static String escapeJava(String value) {
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
}
