plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

val ktorVersion = "3.0.0"

dependencies {
    // Ktor Server (Netty) + Negociação de Conteúdo JSON
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Ktor Client com a engine Java (conforme a documentação anexada)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Logback (necessário para logs do Ktor sem warnings)
    implementation("ch.qos.logback:logback-classic:1.5.6")
}
application {
    // Define o Servidor como execução padrão do comando :run
    mainClass.set("com.rpc.ServerKt")
}

// Tarefa para rodar o Cliente via linha de comando com suporte a leitura do teclado
tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Executa o cliente interativo da calculadora RPC"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rpc.ClientKt")
    standardInput = System.`in`
}

// Tarefa dedicada para rodar o Servidor
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Executa o servidor Ktor RPC"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rpc.ServerKt")
}