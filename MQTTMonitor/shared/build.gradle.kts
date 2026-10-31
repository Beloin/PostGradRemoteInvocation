// Classes compartilhadas entre os serviços (por enquanto só a serialização JSON)
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // Gson fica encapsulada na classe Json; api porque os serviços usam a classe
    api("com.google.code.gson:gson:2.11.0")
}