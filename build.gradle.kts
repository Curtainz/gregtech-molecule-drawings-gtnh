import java.util.zip.ZipFile

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val formulaRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.ChemicalFormulaRegression")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.withType<Test>().configureEach { failOnNoDiscoveredTests = false }
tasks.named("check") { dependsOn(formulaRegression) }

val moleculeCollectionsRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.molecule.MoleculeCollectionsRegression")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(moleculeCollectionsRegression) }

val pixelRasterRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.PixelRasterRegression")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(pixelRasterRegression) }

val productionArtifact = layout.buildDirectory.file("libs/gtmoldraw-${project.version}.jar")
val verifyProductionJar by tasks.registering {
    dependsOn(tasks.named("reobfJar"))
    val artifact = productionArtifact
    inputs.file(artifact)
    doLast {
        ZipFile(artifact.get().asFile).use { jar ->
            val names = jar.entries().asSequence().map { it.name }.toList()
            val forbidden = names.filter {
                it.startsWith("org/lwjgl/") || it.startsWith("net/minecraft/") ||
                    it.startsWith("codechicken/") || it.contains("fastutil") ||
                    it.endsWith(".dll") || it.endsWith(".so") || it.endsWith(".dylib") ||
                    it.endsWith("Regression.class")
            }
            check(forbidden.isEmpty()) { "Unexpected bundled runtime classes/native libraries: $forbidden" }
            jar.entries().asSequence().filter { it.name.startsWith("com/rubenverg/moldraw/core/") && it.name.endsWith(".class") }.forEach { entry ->
                val constants = jar.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                val forbiddenCore = listOf("net/minecraft/", "net/minecraftforge/", "gregtech/", "cpw/mods/fml/", "org/lwjgl/")
                check(forbiddenCore.none { constants.contains(it) }) { "Client/runtime dependency leaked into core: ${entry.name}" }
            }
            check(names.contains("assets/gtmoldraw/lang/zh_CN.lang")) { "Missing GTMolDraw resources" }
            check(names.contains("com/rubenverg/moldraw/core/PixelBatch.class")) { "Missing raster core" }
            println("Production JAR boundary check passed")
        }
    }
}
tasks.named("check") { dependsOn(verifyProductionJar) }

val architectureRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.ArchitectureRegression")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(architectureRegression) }
