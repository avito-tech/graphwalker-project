package org.graphwalker.java.source;

/*
 * #%L
 * GraphWalker Java
 * %%
 * Original work Copyright (c) 2005 - 2018 GraphWalker
 * Modified work Copyright (c) 2018 - 2019 Avito
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.javaparser.ast.Modifier.PUBLIC;
import static java.lang.Character.isLowerCase;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.EnumSet.noneOf;
import static java.util.EnumSet.of;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.graphwalker.java.source.CodeGenerator.toValidMethodOrClassName;

/**
 * @author Ivan Bonkin
 */
public final class FactoryCodeGenerator {

  private static final Logger logger = LoggerFactory.getLogger(FactoryCodeGenerator.class);
  private static FactoryCodeGenerator generator = new FactoryCodeGenerator();

  public static void writeFactorySource(List<Path> linkedFiles, SourceFile file) {
    try {
      String source = generator.generate(file, linkedFiles);
      Files.createDirectories(file.getOutputPath().getParent());
      Files.write(file.getOutputPath(), source.getBytes(UTF_8), CREATE, TRUNCATE_EXISTING);
    } catch (Throwable t) {
      logger.error(t.getMessage());
      throw new CodeGeneratorException(t);
    }
  }

  public String generate(SourceFile sourceFile, List<Path> linkedFiles) {
    CompilationUnit compilationUnit = getCompilationUnit(sourceFile);
    generateMethods(sourceFile, compilationUnit, linkedFiles);
    return compilationUnit.toString();
  }

  private CompilationUnit getCompilationUnit(SourceFile sourceFile) {
    CompilationUnit compilationUnit = new CompilationUnit();
    compilationUnit.setComment(new LineComment(" Generated by GraphWalker (http://www.graphwalker.org)"));
    if (!"".equals(sourceFile.getPackageName())) {
      PackageDeclaration packageDeclaration = new PackageDeclaration(new Name(sourceFile.getPackageName()));
      compilationUnit.setPackageDeclaration(packageDeclaration);
    }

    ClassOrInterfaceDeclaration interfaceDeclaration = new ClassOrInterfaceDeclaration(of(PUBLIC), false, sourceFile.getClassName().toString());
    interfaceDeclaration.setInterface(true);
    compilationUnit.addType(interfaceDeclaration);

    return compilationUnit;
  }

  private void generateMethods(SourceFile sourceFile, CompilationUnit compilationUnit, List<Path> linkedFiles) {
    ClassOrInterfaceDeclaration body = (ClassOrInterfaceDeclaration) compilationUnit.getTypes().get(0);

    for (Path filePath : linkedFiles) {
      MethodDeclaration methodDeclaration = getMethodDeclaration(sourceFile, filePath);
      if (null != methodDeclaration) {
        body.addMember(methodDeclaration);
      }
    }
  }

  static MethodDeclaration getMethodDeclaration(SourceFile sourceFile, Path filePath) {
    String filename = filePath.getFileName().toString();
    String className = toValidMethodOrClassName(removeExtension(filename));
    if (!className.isEmpty()) {
      String methodName = isLowerCase(className.charAt(0)) || className.contains("_")
        ? "get_" + className
        : "get" + className;
      Type type;
      Path parentDir = sourceFile.getBasePath().relativize(filePath).getParent();
      if (parentDir != null) {
        String packageName = parentDir.toString().replace(File.separator, ".").replaceAll(" ", "_");
        type = JavaParser.parseClassOrInterfaceType(packageName + "." + className);
      } else {
        type = JavaParser.parseClassOrInterfaceType(className);
      }
      return new MethodDeclaration(noneOf(Modifier.class), type, methodName)
        .setJavadocComment(new JavadocComment(
          "Implementation of " + filename + " model file.\n\n@return partial model implementation"))
        .setBody(null);
    }
    return null;
  }

}
