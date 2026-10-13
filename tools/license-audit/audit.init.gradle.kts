// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Auditoria de licenças (só no CI e quando você pede): gera o SBOM CycloneDX e a lista das versões
// efetivamente resolvidas em cada classpath, SEM mexer no build do app. O build normal não usa este
// arquivo, então nada muda na compilação nem no APK.
//
// Uso:
//   ./gradlew --init-script tools/license-audit/audit.init.gradle.kts :app:cyclonedxDirectBom :app:kalResolvedDependencies
//   python3 tools/license-audit/check_licenses.py
//
// Saídas:
//   app/build/reports/cyclonedx-direct/bom.json      SBOM CycloneDX 1.6 (componentes, versões, licenças dos POMs)
//   app/build/reports/license-audit/resolved.json    grupo:nome:versão resolvidos por classpath
// O workflow copia o SBOM para build/reports/sbom.json.

import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.gradle.CyclonedxPlugin
import org.cyclonedx.model.Component
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        // CycloneDX Gradle Plugin (Apache-2.0), ferramenta de build: não entra no APK.
        classpath("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.5.1")
    }
}

// Classpaths auditados e o escopo de cada um (o script Python usa os mesmos nomes).
val auditedConfigs = listOf(
    "releaseRuntimeClasspath",             // APK de release
    "coreLibraryDesugaring",               // java.time para Android 7: o R8/L8 copia este código para o APK
    "debugRuntimeClasspath",               // APK de teste (debug)
    "debugUnitTestRuntimeClasspath",       // testes na JVM: não distribuído
    "debugAndroidTestRuntimeClasspath",    // testes no aparelho: não distribuído
)

rootProject {
    apply<CyclonedxPlugin>()
}

allprojects {
    if (path == ":app") {
        afterEvaluate {
            tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
                projectType.set(Component.Type.APPLICATION)
                componentName.set("kalendrium")
                includeConfigs.set(auditedConfigs)
                // marcado como "test" só o que aparece apenas nos classpaths de teste
                testConfigs.set(listOf(".*UnitTest.*", ".*AndroidTest.*"))
                includeLicenseText.set(false)
            }

            tasks.register("kalResolvedDependencies") {
                description = "Lista grupo:nome:versão efetivamente resolvidos em cada classpath auditado."
                val out = layout.buildDirectory.file("reports/license-audit/resolved.json")
                outputs.file(out)
                outputs.upToDateWhen { false }
                doLast {
                    val json = auditedConfigs.joinToString(",\n", "{\n", "\n}\n") { name ->
                        val ids = project.configurations.getByName(name).incoming.resolutionResult.allComponents
                            .mapNotNull { it.id as? ModuleComponentIdentifier }
                            .map { "${it.group}:${it.module}:${it.version}" }
                            .distinct().sorted()
                        "  \"$name\": [\n" + ids.joinToString(",\n") { "    \"$it\"" } + "\n  ]"
                    }
                    val f = out.get().asFile
                    f.parentFile.mkdirs()
                    f.writeText(json)
                    println("Dependências resolvidas: $f")
                }
            }
        }
    }
}
