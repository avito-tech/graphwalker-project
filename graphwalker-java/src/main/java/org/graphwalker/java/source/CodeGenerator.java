package org.graphwalker.java.source;

/*
 * #%L
 * GraphWalker Java
 * %%
 * Copyright (C) 2005 - 2014 GraphWalker
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

import org.apache.commons.collections4.set.ListOrderedSet;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.model.Edge.RuntimeEdge;
import org.graphwalker.core.model.Vertex.RuntimeVertex;
import org.graphwalker.io.factory.ContextFactory;
import org.graphwalker.io.factory.ContextFactoryScanner;
import org.graphwalker.io.factory.yed.YEdContextFactory;
import org.graphwalker.java.source.cache.Cache;
import org.graphwalker.java.source.cache.CacheEntry;
import org.graphwalker.java.source.cache.SimpleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import japa.parser.ASTHelper;
import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.PackageDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.comments.LineComment;
import japa.parser.ast.expr.AnnotationExpr;
import japa.parser.ast.expr.MemberValuePair;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.NormalAnnotationExpr;
import japa.parser.ast.expr.StringLiteralExpr;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Collections.singletonList;
import static org.graphwalker.core.model.Model.RuntimeModel;

//import org.apache.commons.collections4.set.ListOrderedSet;

/**
 * @author Nils Olsson
 */
public final class CodeGenerator extends VoidVisitorAdapter<ChangeContext> {

  private static final Logger logger = LoggerFactory.getLogger(CodeGenerator.class);
  private static CodeGenerator generator = new CodeGenerator();

  private static class SearchForLinkedFileVisitor extends SimpleFileVisitor<Path> {

    private final Path input, output;
    private final SimpleCache cache;
    private final ListOrderedSet<Path> linkedFiles = new ListOrderedSet<>();

    public SearchForLinkedFileVisitor(Path input, Path output) {
      this.input = input;
      this.output = output;
      this.cache = new SimpleCache(output.resolve("link"));
    }

    /**
     * @return unmodifiable view of files to linked in a single context.
     */
    public List<Path> getLinkedFiles() {
      return linkedFiles.asList();
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
      if (!cache.contains(file) || isModified(file, cache)) {
        try {
          ContextFactory factory = ContextFactoryScanner.get(file);
          List<Context> contexts = factory.create(file);

          for (Context context : contexts) {
            for (RuntimeVertex vertex : context.getModel().getVertices()) {
              if (vertex.getIndegrees().isEmpty() && vertex.getOutdegrees().isEmpty()) {
                continue;
              }
              // if file contains INDEGREE/OUTDEGREE, mark it
              if (context.getNextElement() != null) {
                linkedFiles.add(0, file);
              } else {
                linkedFiles.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          }
          // otherwise remove previously marked file
          linkedFiles.remove(file);
          cache.add(file, new CacheEntry(file.toFile().lastModified(), true));

        } catch (Throwable t) {
          logger.error(t.getMessage());
          cache.add(file, new CacheEntry(file.toFile().lastModified(), false));
        }
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
      cache.add(file, new CacheEntry(file.toFile().lastModified(), false));
      return FileVisitResult.CONTINUE;
    }
  }

  private static class MergeFileVisitor extends SimpleFileVisitor<Path> {

    private final Path input, output;
    private final List<Path> linkedFiles;
    private final SimpleCache cache;

    public MergeFileVisitor(Path input, Path output, List<Path> linkedFiles) {
      this.input = input;
      this.output = output;
      this.linkedFiles = linkedFiles;
      this.cache = new SimpleCache(output);
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      Objects.requireNonNull(dir);
      Objects.requireNonNull(attrs);

      if (!linkedFiles.isEmpty()) {
        Path entry = linkedFiles.get(0);
        try {
          ContextFactory factory = ContextFactoryScanner.get(entry);
          Context context = ((YEdContextFactory) factory).read(linkedFiles);

          SourceFile sourceFile = new SourceFile(context.getModel().getName(), entry, input, output);
          write(context, sourceFile);
          cache.add(entry, new CacheEntry(entry.toFile().lastModified(), true));
        } catch (Throwable t) {
          logger.error(t.getMessage());
          cache.add(entry, new CacheEntry(entry.toFile().lastModified(), false));
        }
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
      if ((!cache.contains(file) || isModified(file, cache)) && !linkedFiles.contains(file)) {
        try {
          ContextFactory factory = ContextFactoryScanner.get(file);
          List<Context> contexts = factory.create(file);
          for (Context context : contexts) {
            SourceFile sourceFile = new SourceFile(context.getModel().getName(), file, input, output);
            write(context, sourceFile);
            cache.add(file, new CacheEntry(file.toFile().lastModified(), true));
          }
        } catch (Throwable t) {
          logger.error(t.getMessage());
          cache.add(file, new CacheEntry(file.toFile().lastModified(), false));
        }
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
      cache.add(file, new CacheEntry(file.toFile().lastModified(), false));
      return FileVisitResult.CONTINUE;
    }
  }

  private static boolean isModified(Path file, Cache<Path, CacheEntry> cache) throws IOException {
    return !Files.getLastModifiedTime(file).equals(cache.get(file).getLastModifiedTime());
  }

  public static void generate(final Path input, final Path output) {
    SearchForLinkedFileVisitor searchForLinkedFileVisitor = new SearchForLinkedFileVisitor(input, output);
    try {
      Files.walkFileTree(input, searchForLinkedFileVisitor);
      Files.walkFileTree(input, new MergeFileVisitor(input, output, searchForLinkedFileVisitor.getLinkedFiles()));
    } catch (IOException e) {
      logger.error(e.getMessage());
      throw new CodeGeneratorException(e);
    }
  }

  private static void write(Context context, SourceFile file) throws IOException {
    try {
      RuntimeModel model = context.getModel();
      String source = generator.generate(file, model);
      Files.createDirectories(file.getOutputPath().getParent());
      Files.write(file.getOutputPath(), source.getBytes(Charset.forName("UTF-8")), CREATE, TRUNCATE_EXISTING);
    } catch (Throwable t) {
      logger.error(t.getMessage());
      throw new CodeGeneratorException(t);
    }
  }

  public List<String> generate(String file) throws IOException {
    return generate(Paths.get(file));
  }

  public List<String> generate(File file) throws IOException {
    return generate(file.toPath());
  }

  public List<String> generate(Path path) throws IOException {
    ContextFactory factory = ContextFactoryScanner.get(path);
    List<Context> contexts = factory.create(path);
    List<String> sources = new ArrayList<>();
    for (Context context : contexts) {
      String sourceStr = "";
      SourceFile sourceFile = new SourceFile(context.getModel().getName(), path);
      sourceStr += generate(sourceFile, context.getModel());
      sources.add(sourceStr);
    }
    return sources;
  }

  public String generate(SourceFile sourceFile, RuntimeModel model) {
    CompilationUnit compilationUnit = getCompilationUnit(sourceFile);
    ChangeContext changeContext = new ChangeContext(model);
    visit(compilationUnit, changeContext);
    removeMethods(compilationUnit, changeContext);
    generateMethods(compilationUnit, changeContext);
    return compilationUnit.toString();
  }

  private CompilationUnit getCompilationUnit(SourceFile sourceFile) {
    CompilationUnit compilationUnit;
    if (Files.exists(sourceFile.getOutputPath())) {
      try {
        compilationUnit = JavaParser.parse(sourceFile.getOutputPath().toFile());
      } catch (Throwable t) {
        logger.error(t.getMessage());
        throw new RuntimeException(t);
      }
    } else {
      compilationUnit = new CompilationUnit();
      compilationUnit.setComment(new LineComment(" Generated by GraphWalker (http://www.graphwalker.org)"));
      if (!"".equals(sourceFile.getPackageName())) {
        compilationUnit.setPackage(createPackageDeclaration(sourceFile));
      }
      compilationUnit.setImports(Arrays.asList(
        new ImportDeclaration(new NameExpr("org.graphwalker.java.annotation.Model"), false, false),
        new ImportDeclaration(new NameExpr("org.graphwalker.java.annotation.Vertex"), false, false),
        new ImportDeclaration(new NameExpr("org.graphwalker.java.annotation.Edge"), false, false)
      ));
      ASTHelper.addTypeDeclaration(compilationUnit, getInterfaceName(sourceFile));
    }
    return compilationUnit;
  }

  private void removeMethods(CompilationUnit compilationUnit, ChangeContext changeContext) {
    if (0 < changeContext.getMethodDeclarations().size()) {
      ClassOrInterfaceDeclaration body = (ClassOrInterfaceDeclaration) compilationUnit.getTypes().get(0);
      body.getMembers().removeAll(changeContext.getMethodDeclarations());
    }
  }

  private void generateMethods(CompilationUnit compilationUnit, ChangeContext changeContext) {
    ClassOrInterfaceDeclaration body = (ClassOrInterfaceDeclaration) compilationUnit.getTypes().get(0);
    for (String methodName : changeContext.getMethodNames()) {
      if (isValidName(methodName)) {
        MethodDeclaration method = new MethodDeclaration(Modifier.INTERFACE, ASTHelper.VOID_TYPE, methodName);
        List<AnnotationExpr> annotations = new ArrayList<>();
        List<RuntimeVertex> vertices = changeContext.getModel().findVertices(methodName);
        if (vertices != null) {
          String description = vertices.isEmpty() ? "" : vertices.iterator().next().getDescription();
          List<MemberValuePair> memberValuePairs = singletonList(new MemberValuePair("value", new StringLiteralExpr(description)));
          annotations.add(new NormalAnnotationExpr(ASTHelper.createNameExpr("Vertex"), memberValuePairs));
        } else {
          List<RuntimeEdge> edges = changeContext.getModel().findEdges(methodName);
          if (edges != null) {
            String description = edges.isEmpty() ? "" : edges.iterator().next().getDescription();
            List<MemberValuePair> memberValuePairs = singletonList(new MemberValuePair("value", new StringLiteralExpr(description)));
            annotations.add(new NormalAnnotationExpr(ASTHelper.createNameExpr("Edge"), memberValuePairs));
          } else {
            throw new IllegalStateException("No vertices or edges were found for method: \"" + methodName + "\"");
          }
        }
        method.setAnnotations(annotations);
        ASTHelper.addMember(body, method);
      }
    }
  }

  private boolean isValidName(String name) {
    if (null == name || name.isEmpty()) {
      return false;
    }
    boolean valid = true;
    for (int i = 0; i < name.length(); i++) {
      if (0 == i) {
        valid &= Character.isJavaIdentifierStart(name.charAt(i));
      } else {
        valid &= Character.isJavaIdentifierPart(name.charAt(i));
      }
    }
    return valid;
  }

  public void visit(MethodDeclaration methodDeclaration, ChangeContext changeContext) {
    if (changeContext.getMethodNames().contains(methodDeclaration.getName())) {
      changeContext.getMethodNames().remove(methodDeclaration.getName());
    } else {
      changeContext.addMethodDeclaration(methodDeclaration);
    }
  }

  private PackageDeclaration createPackageDeclaration(SourceFile sourceFile) {
    return new PackageDeclaration(ASTHelper.createNameExpr(sourceFile.getPackageName()));
  }

  private ClassOrInterfaceDeclaration getInterfaceName(SourceFile sourceFile) {
    ClassOrInterfaceDeclaration classOrInterfaceDeclaration = new ClassOrInterfaceDeclaration(ModifierSet.PUBLIC, false, sourceFile.getClassName());
    List<MemberValuePair> memberValuePairs = new ArrayList<>();
    memberValuePairs.add(new MemberValuePair("file", new StringLiteralExpr(sourceFile.getRelativePath().toString().replace(File.separator, "/"))));
    List<AnnotationExpr> annotations = new ArrayList<>();
    annotations.add(new NormalAnnotationExpr(ASTHelper.createNameExpr("Model"), memberValuePairs));
    classOrInterfaceDeclaration.setAnnotations(annotations);
    classOrInterfaceDeclaration.setInterface(true);
    return classOrInterfaceDeclaration;
  }
}
