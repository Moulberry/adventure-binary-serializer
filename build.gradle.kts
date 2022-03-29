plugins {
	id("java")
	id("java-library")
    id("maven-publish")
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

allprojects {
	group = "net.gauntletmc"
	version = "1.0.0"
}

repositories {
	mavenCentral()
}

dependencies {
	compileOnlyApi("org.jetbrains:annotations:23.0.0")
	implementation("net.kyori:adventure-api:4.10.1")

	testImplementation("net.kyori:adventure-nbt:4.10.1")
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}
}
