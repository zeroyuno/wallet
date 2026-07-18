package com.walletapp.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura de la constitución (principio II): domain sin dependencias de framework,
 * y ningún bounded context accede al domain/infrastructure interno de otro contexto — salvo
 * `shared`, que cualquier contexto puede usar (ver plan.md de la feature 001).
 */
@AnalyzeClasses(packages = "com.walletapp.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // Agregar acá el nombre de paquete de cada bounded context nuevo (ej. "account").
    private static final List<String> BOUNDED_CONTEXTS = List.of("auth", "account");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta.persistence..", "org.hibernate..", "io.jsonwebtoken..");

    @ArchTest
    static void infrastructure_is_private_to_its_own_context(JavaClasses classes) {
        for (String context : BOUNDED_CONTEXTS) {
            noClasses().that().resideOutsideOfPackage("com.walletapp.backend." + context + "..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.walletapp.backend." + context + ".infrastructure..")
                    .check(classes);
        }
    }

    @ArchTest
    static void domain_is_private_to_its_own_context_except_shared(JavaClasses classes) {
        for (String context : BOUNDED_CONTEXTS) {
            noClasses().that().resideOutsideOfPackages(
                            "com.walletapp.backend." + context + "..", "com.walletapp.backend.shared..")
                    .should().dependOnClassesThat().resideInAPackage("com.walletapp.backend." + context + ".domain..")
                    .check(classes);
        }
    }
}
