plugins {
    id("sap.commerce.build") version("3.7.1")
    id("sap.commerce.build.ccv2") version("3.7.1")
}
import mpern.sap.commerce.build.tasks.HybrisAntTask
import org.apache.tools.ant.taskdefs.condition.Os

//***********************
//* Script configurations
//***********************
val jacocoLib by configurations.creating
val extensionPack by configurations.creating

val DEPENDENCY_FOLDER = "dependencies"
repositories {
    flatDir { dirs(DEPENDENCY_FOLDER) }
    mavenCentral()
}

dependencies {
    dbDriver("mysql:mysql-connector-java:8.0.31")
    jacocoLib("org.jacoco:org.jacoco.agent:0.8.8")
}

//********************************
//* Helper atributes and functions
//********************************
fun createWindowsPath(path: String): String {
    return path.toString().replace("[\\/]".toRegex(), "\\\\")
}

//*******************************
//* Setup Environments properties
//*******************************

val platformPath = file("hrybris/bin/platform")

val envsDirPath = "hybris/config/environments"
val envValue = if (project.hasProperty("environment")) project.property("environment") else "local"

val optionalConfigDirPath = "hybris/config/optional-config"
val optionalConfigDir = file("${optionalConfigDirPath}")
val optionalConfigs = mapOf(
    "10-local.properties" to file("${envsDirPath}/commons/common.properties"),
    "20-local.properties" to file("${envsDirPath}/${envValue}/local.properties")
)


//***************************
//* Set up Environment tasks
//***************************
val configureProperties = tasks.register("configureProperties") {
    doFirst {
        println("Generating Config SymLinks...")
    }
    
    dependsOn("validateEnvironment")
    mustRunAfter("bootstrapPlatform", "createConfigDir")
}
optionalConfigs.forEach{
    val optionalPropertySymlink = tasks.register<Exec>("symlink${it.key}") {
        doFirst {
            println("Generating SymLink for ${it.key}...")
        }
        val path = it.value.relativeTo(optionalConfigDir)
        if (Os.isFamily(Os.FAMILY_UNIX)) {
            commandLine("sh", "-c", "ln -sfn ${path} ${it.key}")
        } else {
            // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
            val windowsPath = path.toString().replace("[/]".toRegex(), "\\")
            commandLine("cmd", "/c", """mklink /d "${it.key}" "${windowsPath}" """)
        }
        workingDir(optionalConfigDir)
    }
    configureProperties.configure {
        dependsOn(optionalPropertySymlink)
    }
}

tasks.register("validateEnvironment") {
    doFirst {
        println("Validating environment...")
    }
    
    if (!file("${envsDirPath}/${envValue}/local.properties").exists()) {
        throw GradleException("Environment folder does not exist")
    }
}

tasks.register<Copy>("generateDeveloperProperties") {
    doFirst {
        println("Generating Developer properties...")
    }
    
    onlyIf {
        envValue == "local"
    }
    from("${envsDirPath}/local/sample-developer.properties")
    into("${optionalConfigDirPath}")
    rename { "99-local.properties" }

    dependsOn("validateEnvironment")
}

tasks.register("createConfigDir") {
    dependsOn("copyCommonConfigDir", "copyEnvConfigDir")
}

tasks.register<Copy>("copyCommonConfigDir") {
    doFirst {
        println("Copy commons config directory...")
    }

    // copy excluding local* files 
    from("${envsDirPath}/commons")
    into("hybris/config")
    exclude ("common.properties")
}

tasks.register<Copy>("copyEnvConfigDir") {
    doFirst {
        println("Copy ${envValue} config directory...")
    }

    // copy excluding local* files 
    from("${envsDirPath}/${envValue}")
    into("hybris/config")
    exclude ( "local.properties", "sample-developer.properties", "localextensions.xml" )

    mustRunAfter("copyCommonConfigDir")
}

tasks.register<WriteProperties>("generateLocalProperties") {
    doFirst {
        println("Generating Local properties...")
    }
    
    comment = "GENEREATED AT " + java.time.Instant.now()
    outputFile = project.file("hybris/config/local.properties")

    property("hybris.optional.config.dir", "\${HYBRIS_CONFIG_DIR}/optional-config")

    dependsOn("generateDeveloperProperties")
    mustRunAfter("configureProperties", "createConfigDir")
}

tasks.register<Copy>("copyJacocoLibs") {
    from(jacocoLib)
    into("hybris/bin/platform/lib")

    mustRunAfter("bootstrapPlatform")
}

tasks.register("generateEnvironment") {
    dependsOn("createConfigDir", "copyJacocoLibs", "configureProperties", "generateLocalProperties")
    mustRunAfter("bootstrapPlatformExt")
}

val unpackManifestExtensionPacks = tasks.register("unpackManifestExtensionPacks") {
    doLast {
        println("Unpacking Extension Packs...")
        file("hybris/bin/modules/.lastupdate").createNewFile();
    }
}

tasks.register<Copy>("boostrapExtensionPacks") {
    onlyIf {
        CCV2.manifest.extensionPacks != null || !CCV2.manifest.extensionPacks.isEmpty()
    }
    dependsOn("unpackManifestExtensionPacks")
    mustRunAfter("bootstrapPlatform")
}

tasks.register("bootstrapPlatformExt") {
    dependsOn("bootstrapPlatform", "boostrapExtensionPacks")
}

tasks.named("installManifestAddons") {
    mustRunAfter("generateLocalProperties")
}

//***************************
//* Main Setup task
//***************************
tasks.register("setupEnvironment") {
    group = "SAP Commerce"
    description = "Setup local development"

    dependsOn("bootstrapPlatformExt", "generateEnvironment","installManifestAddons")
}

//***************************
// * Server Start/Stop tasks
//***************************
tasks.register<HybrisAntTask>("updateSystemWithConfig") {
    description = "Updates System with config"
    
    args("updatesystem")

    // arguments
    val configPath = file("defaultUpdateConfigFile.json").relativeTo(platformPath)
    antProperty("configFile", "${configPath}")
}

tasks.register("yhybrisserver") {
    description = "Hybris server service. Options available: start, stop"
    val arg = if (project.hasProperty("server")) project.property("server") else ""
    
    onlyIf {
        file("hybris/bin/platform").exists() && (arg == "start" || arg == "stop")
    }

    doLast {
        val arg = if (project.hasProperty("server")) project.property("server") else ""
        exec {
            workingDir("hybris/bin/platform")
            if (Os.isFamily(Os.FAMILY_UNIX)) {
                commandLine("sh", "-c", "./hybrisserver.sh ${arg}")
            } else {
                // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
                commandLine("cmd", "/c", """hybrisserver.bat "${arg}" """)
            }
        }
    }
}


//**************************
//* Solr Setup Configuration
//**************************
tasks.register<HybrisAntTask>("startSolr") {
    args("startSolrServers")
}
tasks.register<HybrisAntTask>("stopSolr") {
    args("stopSolrServers")
    mustRunAfter("startSolr")
}
tasks.register("startStopSolr") {
    dependsOn("startSolr", "stopSolr")
}
tasks.register("configureSolrConfig") {
    dependsOn("symlinkSolrConfig")
    group = "Setup"
    description = "Prepare Solr configuration"

    mustRunAfter("createConfigDir")
}
tasks.register("clearDefaultSolrConfig") {
    dependsOn("startStopSolr")
    doLast {
        val configSetsDir = file("hybris/config/solr/instances/default/configsets");
        if (configSetsDir.exists()) {
            delete(configSetsDir)
        }
    }
}
tasks.register<Exec>("symlinkSolrConfig") {
    dependsOn("clearDefaultSolrConfig")

    val solrPath = "../../../../../solr/server/solr/configsets"
    if (Os.isFamily(Os.FAMILY_UNIX)) {
        commandLine("sh", "-c", "ln -sfn ${solrPath} configsets")
    } else {
        // https://blogs.windows.com/windowsdeveloper/2016/12/02/symlinks-windows-10/
        val windowsPath = createWindowsPath(solrPath)
        commandLine("cmd", "/c", """mklink /d "configsets" "${windowsPath}" """)
    }
    workingDir("hybris/config/solr/instances/default")
}