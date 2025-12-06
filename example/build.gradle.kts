plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":report"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("com.example.importanalyzer.example.DemoMain")
}

tasks.withType<JavaCompile> {
    options.isFailOnError = false
}
