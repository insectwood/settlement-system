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

rootProject.name = "settlement-system"

include(
    "common-event",
    "common-test",
    "service-order",
    "service-payment",
    "service-settlement",
)