package org.example;

import org.objectweb.asm.*;
import java.io.*;
import java.util.jar.*;

/**
 * A utility class for modifying JAR files, specifically designed to inject translation functionality
 * into a game library by modifying its chat handling class.
 */
public class JarModifier {
    public static void main(String[] args) {
        String inputJarPath = "src/main/resources/game-lib.jar";
        String outputJarPath = "build/game-lib-modified.jar";
        modifyJar(inputJarPath, outputJarPath);
    }

    /**
     * Modifies a JAR file by:
     * 1. Reading the original JAR file
     * 2. Modifying the chat handling class
     * 3. Adding new translation helper classes
     * 4. Creating a new modified JAR file
     *
     * @param inputJarPath Path to the original JAR file
     * @param outputJarPath Path where the modified JAR will be saved
     */
    public static void modifyJar(String inputJarPath, String outputJarPath) {
        try {
            // Create build directory if it doesn't exist
            new File("build").mkdirs();

            // Open the input JAR file and create output JAR stream
            JarFile jarFile = new JarFile(inputJarPath);
            JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJarPath));

            // Process each entry in the original JAR
            jarFile.stream().forEach(entry -> {
                try {
                    String entryName = entry.getName();
                    JarEntry newEntry = new JarEntry(entryName);
                    jos.putNextEntry(newEntry);

                    // Check if this is the chat class we want to modify
                    if (entryName.equals("com/corrodinggames/rts/gameFramework/f/m.class")) {
                        System.out.println("Modifying chat class: " + entryName);
                        byte[] modifiedClass = modifyChatClass(jarFile.getInputStream(entry));
                        jos.write(modifiedClass);
                    } else {
                        // Copy other files as-is
                        jarFile.getInputStream(entry).transferTo(jos);
                    }
                    jos.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // Add the Google Translate helper class to the JAR
            JarEntry translatorEntry = new JarEntry("org/example/GoogleTranslateHelper.class");
            jos.putNextEntry(translatorEntry);
            try (FileInputStream fis = new FileInputStream(
                    "build/classes/java/main/org/example/GoogleTranslateHelper.class")) {
                fis.transferTo(jos);
            }
            jos.closeEntry();

            // Add the TranslationResult inner class to the JAR
            JarEntry resultEntry = new JarEntry("org/example/GoogleTranslateHelper$TranslationResult.class");
            jos.putNextEntry(resultEntry);
            try (FileInputStream fis = new FileInputStream(
                    "build/classes/java/main/org/example/GoogleTranslateHelper$TranslationResult.class")) {
                fis.transferTo(jos);
            }
            jos.closeEntry();

            // Close all streams
            jos.close();
            jarFile.close();
            System.out.println("JAR modification completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Modifies the chat handling class to inject translation functionality.
     * Uses ASM library to modify the bytecode of the class.
     *
     * @param classFile Input stream of the original class file
     * @return Modified class file as byte array
     */
    private static byte[] modifyChatClass(InputStream classFile) throws IOException {
        // Create ClassReader to read the original class
        ClassReader cr = new ClassReader(classFile);
        // Create ClassWriter to write the modified class
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        // Create ClassVisitor to modify the class
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // Check if this is the chat message handling method we want to modify
                if (name.equals("a") &&
                        descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)Lcom/corrodinggames/rts/gameFramework/f/n;")) {

                    // Create a MethodVisitor to inject translation code
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();

                            // Load the second parameter (message text)
                            mv.visitVarInsn(Opcodes.ALOAD, 2);

                            // Call translation helper method
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "org/example/GoogleTranslateHelper",
                                    "processMessage",
                                    "(Ljava/lang/String;)Ljava/lang/String;",
                                    false);

                            // Store the translated text back in the local variable
                            mv.visitVarInsn(Opcodes.ASTORE, 2);
                        }
                    };
                }
                return mv;
            }
        };

        // Perform the class modification
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
