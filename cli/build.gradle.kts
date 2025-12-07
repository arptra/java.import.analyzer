plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":report"))
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("com.example.importanalyzer.cli.AnalyzerCli")
}
