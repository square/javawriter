package com.squareup.javapoet;

import com.google.testing.compile.CompilationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.Modifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

/**
 * define the annotation above a package
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.RUNTIME)
@interface PkgAnnotation {
}
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.RUNTIME)
@interface PkgAnnotation2 {
}

@RunWith(JUnit4.class)
public class PackageAnnotationTest {
    @Rule
    public final CompilationRule compilation = new CompilationRule();

    @Test public void annotatedPackage(){
        JavaFile source = JavaFile.builder("com.company",
                TypeSpec.classBuilder("PkgClass")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PUBLIC)
                                .build())
                        .build())
                .addPkgAnnotation(AnnotationSpec.builder(PkgAnnotation.class).build())
                .build();
        assertThat(source.toString()).isEqualTo(""
                + "@PkgAnnotation\n"
                + "package com.company;\n"
                + "\n"
                + "import com.squareup.javapoet.PkgAnnotation;\n"
                + "\n"
                + "class PkgClass {\n"
                + "  public void test() {\n"
                + "  }\n"
                + "}\n");
    }
    @Test public void package_infoTest(){
        JavaFile source = JavaFile.builder("com.company",
                TypeSpec.classBuilder("PkgConst")
                        .addField(int.class,"PACKAGE_CONST",Modifier.FINAL,Modifier.STATIC)
                        .build())
                .addPkgAnnotation(AnnotationSpec.builder(PkgAnnotation.class).build())
                .build();
        assertThat(source.toString()).isEqualTo("@PkgAnnotation\n" +
                "package com.company;\n" +
                "\n" +
                "import com.squareup.javapoet.PkgAnnotation;\n" +
                "\n" +
                "class PkgConst {\n" +
                "  static final int PACKAGE_CONST;\n" +
                "}\n");
    }
    @Test public void addAnnotationByClassName(){
        JavaFile source = JavaFile.builder("com.company",
                TypeSpec.classBuilder("PkgConst")
                        .addField(int.class,"PACKAGE_CONST",Modifier.FINAL,Modifier.STATIC)
                        .build())
                .addPkgAnnotation(ClassName.get("com","PkgAnnotation2"))
                .build();
        assertThat(source.toString()).isEqualTo("@PkgAnnotation2\n" +
                "package com.company;\n" +
                "\n" +
                "import com.PkgAnnotation2;\n" +
                "\n" +
                "class PkgConst {\n" +
                "  static final int PACKAGE_CONST;\n" +
                "}\n");
    }
    @Test public void addAnnotations(){
        JavaFile source = JavaFile.builder("com.company",
                TypeSpec.classBuilder("PkgConst")
                        .addField(int.class,"PACKAGE_CONST",Modifier.FINAL,Modifier.STATIC)
                        .build())
                .addPkgAnnotations(Arrays.asList(AnnotationSpec.builder(PkgAnnotation.class).build(),
                        AnnotationSpec.builder(PkgAnnotation2.class).build()))
                .build();
        assertThat(source.toString()).isEqualTo("@PkgAnnotation\n" +
                "@PkgAnnotation2\n" +
                "package com.company;\n" +
                "\n" +
                "import com.squareup.javapoet.PkgAnnotation;\n" +
                "import com.squareup.javapoet.PkgAnnotation2;\n" +
                "\n" +
                "class PkgConst {\n" +
                "  static final int PACKAGE_CONST;\n" +
                "}\n");
    }
    @Test public void PkgAnnotationByName(){
        JavaFile source = JavaFile.builder("com.company",
                TypeSpec.classBuilder("PkgClass")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PUBLIC)
                                .build())
                        .build())
                .addPkgAnnotation(PkgAnnotation.class)
                .build();
        assertThat(source.toString()).isEqualTo(""
                + "@PkgAnnotation\n"
                + "package com.company;\n"
                + "\n"
                + "import com.squareup.javapoet.PkgAnnotation;\n"
                + "\n"
                + "class PkgClass {\n"
                + "  public void test() {\n"
                + "  }\n"
                + "}\n");
    }


}
