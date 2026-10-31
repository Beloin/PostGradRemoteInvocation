// Serviço CAT (Compute Average Temperature): calcula a média das temperaturas
// da caldeira publicadas no broker MQTT
plugins {
    java
    application
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
    // Cliente MQTT sugerido pela atividade (Eclipse Paho), resolvido do Maven Central
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    // Serialização JSON encapsulada na classe Json do subprojeto shared
    implementation(project(":shared"))
}

application {
    mainClass.set("CATService")
}