pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mqttmonitor"

// Serviços da atividade MQTT (o Sensor entra depois como mais um subprojeto)
include(":shared", ":CATService", ":Alarms")