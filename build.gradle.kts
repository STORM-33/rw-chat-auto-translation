plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.ow2.asm:asm:9.5")
    implementation("com.google.cloud:google-cloud-translate:2.0.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}



tasks.test {
    useJUnitPlatform()
}