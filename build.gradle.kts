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
            check(names.none { it.matches(Regex("assets/[^/]+/(molecules|alloys)/.*")) }) { "Bundled legacy definition fallback remains" }
            check(names.none { it.startsWith("com/rubenverg/moldraw/molecule/") || it.startsWith("com/rubenverg/moldraw/MoleculeManager") || it.startsWith("com/rubenverg/moldraw/AlloyManager") || it.startsWith("com/rubenverg/moldraw/util/FormulaParser") }) { "Legacy hardcoded material adapters remain in game JAR" }
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

val reviewPackZip by tasks.registering(Zip::class) {
    archiveFileName.set("gtmoldraw-data-pack-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("schema") { into("schema") }
    from("data-packs/migrated") { into("packs") }
    from("data-packs/runtime-species") { into("packs") }
    from("docs/FORMAT-V1.md")
    from("docs/TESTING-0.7-A.md") { rename { "README.md" } }
}
tasks.named("assemble") { dependsOn(reviewPackZip) }

tasks.named<ProcessResources>("processResources") {
    from("schema") { into("META-INF/gtmoldraw-schema") }
}
val toolLibraries = configurations.create("toolLibraries")
dependencies {
    add(toolLibraries.name, "com.google.code.gson:gson:2.2.4") { isTransitive = false }
    add(toolLibraries.name, "org.joml:joml:1.10.8") { isTransitive = false }
}
val packToolJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("classes"))
    archiveFileName.set("gtmoldraw-pack-tool-${project.version}.jar")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "com.rubenverg.moldraw.tool.PackTool") }
    from(sourceSets["main"].output) {
        include("com/rubenverg/moldraw/core/**", "com/rubenverg/moldraw/tool/**", "com/rubenverg/moldraw/util/Pair.class", "META-INF/gtmoldraw-schema/**")
    }
    from({ toolLibraries.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    from(listOf("LICENSE-UPSTREAM", "NOTICE.md")) { into("META-INF") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.named("assemble") { dependsOn(packToolJar) }

val verifyPackToolJar by tasks.registering {
    dependsOn(packToolJar)
    val artifact = packToolJar.flatMap { it.archiveFile }
    inputs.file(artifact)
    doLast {
        ZipFile(artifact.get().asFile).use { jar ->
            val forbidden = jar.entries().asSequence().map { it.name }.filter {
                it.startsWith("net/minecraft/") || it.startsWith("net/minecraftforge/") ||
                    it.startsWith("gregtech/") || it.startsWith("cpw/mods/fml/") ||
                    it.startsWith("codechicken/") || it.startsWith("org/lwjgl/") ||
                    it.endsWith(".dll") || it.endsWith(".so") || it.endsWith(".dylib")
            }.toList()
            check(forbidden.isEmpty()) { "Game/native dependency in offline tool: $forbidden" }
            println("Offline tool JAR boundary check passed")
        }
    }
}
tasks.named("check") { dependsOn(verifyPackToolJar) }

val packRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.PackRegression")
    args(layout.projectDirectory.asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(packRegression) }

val previewDirectory = layout.buildDirectory.dir("preview-stage-4")
val generatePackPreview by tasks.registering(JavaExec::class) {
    dependsOn(packToolJar)
    classpath = files(packToolJar.flatMap { it.archiveFile })
    mainClass.set("com.rubenverg.moldraw.tool.PackTool")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    args("preview", layout.projectDirectory.dir("data-packs/migrated").asFile.absolutePath, previewDirectory.get().asFile.absolutePath)
    val auditSnapshot = providers.gradleProperty("auditSnapshot")
    if (auditSnapshot.isPresent) { args("--audit", auditSnapshot.get()); inputs.file(auditSnapshot.get()) }
    inputs.dir("data-packs/migrated")
    outputs.dir(previewDirectory)
}
val previewZip by tasks.registering(Zip::class) {
    dependsOn(generatePackPreview)
    archiveFileName.set("gtmoldraw-${project.version}-preview.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    val currentGeneration = providers.fileContents(previewDirectory.map { it.file("report.json") }).asText.map { report ->
        Regex("\"path\"\\s*:\\s*\"(gallery-[^/\"]+)/").find(report)?.groupValues?.get(1) ?: ""
    }
    from(previewDirectory) {
        into("preview")
        exclude {
            val first = it.relativePath.segments.firstOrNull() ?: ""
            first.startsWith("gallery-") && first != currentGeneration.get()
        }
    }
    from(packToolJar) { into("tools") }
    from("tools/migration/legacy-bindings.json") { into("tools") }
    from("data-packs/migrated") { into("packs") }
    from("data-packs/runtime-species") { into("packs") }
    from("schema") { into("schema") }
    from("docs/FORMAT-V1.md")
    from("docs/TESTING-0.7-A.md") { rename { "README.md" } }
}
tasks.named("assemble") { dependsOn(previewZip) }

val migrationRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.MigrationRegression")
    args(layout.projectDirectory.asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(migrationRegression) }

// Legacy sources remain for reproducible migration/oracle tests; none can load in the game JAR.
tasks.withType<Jar>().configureEach {
    if (name == "jar" || name == "shadowJar") {
    exclude("assets/*/molecules/**", "assets/*/alloys/**")
    exclude("com/rubenverg/moldraw/molecule/**", "com/rubenverg/moldraw/MoleculeManager*", "com/rubenverg/moldraw/AlloyManager*", "com/rubenverg/moldraw/MoleculeSnapshots*", "com/rubenverg/moldraw/util/FormulaParser*")
    }
}
val runtimePackRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.RuntimePackRegression")
    args(layout.projectDirectory.asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(runtimePackRegression) }

val installZip by tasks.registering(Zip::class) {
    dependsOn(tasks.named("reobfJar"))
    archiveFileName.set("gtmoldraw-${project.version}-install.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = true
    isReproducibleFileOrder = true
    from("data-packs/runtime-species") { into("config/gtmoldraw/packs") }
    from(productionArtifact) { into("mods") }
    from("data-packs/migrated/official") { into("config/gtmoldraw/packs/official") }
}
tasks.named("assemble") { dependsOn(installZip) }

val auditRegression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.rubenverg.moldraw.core.AuditRegression")
    args(layout.projectDirectory.asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(auditRegression) }

val speciesRegression by tasks.registering(JavaExec::class) {
 dependsOn(tasks.named("testClasses")); classpath = sourceSets["test"].runtimeClasspath
 mainClass.set("com.rubenverg.moldraw.core.SpeciesRegression")
 args(layout.projectDirectory.asFile.absolutePath)
 javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.named("check") { dependsOn(speciesRegression) }
