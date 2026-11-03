
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
