package com.walletapp.backend;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura de la constitución (principio II): domain sin dependencias de framework,
 * y ningún bounded context accede al domain/infrastructure interno de otro contexto.
 *
 * Cuando exista un segundo bounded context (además de auth), generalizar la segunda y tercera regla
 * con la API de slices() de ArchUnit en vez de escribir una regla por contexto.
 */
@AnalyzeClasses(packages = "com.walletapp.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta.persistence..", "org.hibernate..", "io.jsonwebtoken..");

    @ArchTest
    static final ArchRule auth_infrastructure_is_private_to_auth =
            noClasses().that().resideOutsideOfPackage("com.walletapp.backend.auth..")
                    .should().dependOnClassesThat().resideInAPackage("com.walletapp.backend.auth.infrastructure..");

    @ArchTest
    static final ArchRule auth_domain_is_private_except_for_shared_security =
            noClasses().that().resideOutsideOfPackages("com.walletapp.backend.auth..", "com.walletapp.backend.shared..")
                    .should().dependOnClassesThat().resideInAPackage("com.walletapp.backend.auth.domain..");
}
