package daylightnebula.daylinmicroservices.cicd

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import daylightnebula.daylinmicroservices.filesysteminterface.HashUtils
import daylightnebula.daylinmicroservices.filesysteminterface.pushFile
import daylightnebula.daylinmicroservices.filesysteminterface.requestFile
import daylightnebula.daylinmicroservices.requests.broadcastRequestByTag
import daylightnebula.daylinmicroservices.requests.request
import mu.KotlinLogging
import okhttp3.internal.wait
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.Thread.sleep
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.HashMap
import kotlin.concurrent.thread
import kotlin.random.Random

// control variables
var buildConfig: JSONObject? = null
var buildUUID = UUID.randomUUID()
val startTime = System.currentTimeMillis()

val endpoints = hashMapOf<String, (json: JSONObject) -> JSONObject>(
    "run_build" to { json ->
        if (buildConfig == null) {
            buildConfig = json.getJSONObject("config")
            buildUUID = UUID.fromString(json.getString("buildID"))
            JSONObject().put("build_accepted", true)
        } else JSONObject().put("build_accepted", false)
    }
)
val logger = KotlinLogging.logger("CICD-Builder")
val config = MicroserviceConfig(
    name = "CICD-Builder",
    tags = listOf("cicd"),
    logger = logger,
    consulUrl = "http://host.docker.internal:8500/"
)
lateinit var service: Microservice

fun main(inArgs: Array<String>) {
    // process input args
    val args = processArgs(inArgs)

    // check if local mode is enabled
    val localModeEnabled = args.containsKey("local")

    // only create service when not in local mode
    service = Microservice(config, endpoints)
    service.start()

    // if in local mode, load config given path
    if (localModeEnabled) {
        buildConfig = JSONObject(File(args["local"]!!).readText())
    } else {
        service.broadcastRequestByTag("cicd", "builder_ready", JSONObject().put("service_id", config.uuid.toString()))
    }

    // keep alive for 10 seconds
    while (System.currentTimeMillis() - startTime < 10000) {
        // if a build is given, run
        if (buildConfig != null) {
            runBuild(buildConfig!!)
            break // break the loop so that run build is not called multiple times
        }
    }
}

// function that runs the build
var buildUploadDebug = false
fun runBuild(config: JSONObject) {
    // create and clear build directory
    val buildFolder = File(System.getProperty("user.dir"), "build")
    buildFolder.mkdirs()
    var success = true

    // delete old build
    if (!buildUploadDebug) buildFolder.listFiles()?.forEach { it.deleteRecursively() }

    if (!buildUploadDebug) {
        // clone the repo
        if (!config.has("url")) throw IllegalArgumentException("URL must be specified for clone")
        val url = config.getString("url")
        val cloneResult = cloneGitRepo(buildFolder, config.getString("url"), config.optString("auth"))
        if (cloneResult != 0) {
            println("Clone operation failed, URL: ${config.getString("url")}, exit code: $cloneResult")
            success = false
        }
    }

    if (success && !buildUploadDebug) {
        // run each command, verifying after each
        for (any in (config.optJSONArray("commands")
            ?: throw IllegalArgumentException("Commands to build must be specified"))) {
            val command = any as? String ?: throw IllegalArgumentException("Commands must be a string")
            val cmdResult = runCommand(buildFolder, command)
            if (cmdResult != 0) {
                println("Command $command could not be run, exited with code $cmdResult")
                success = false
            }
        }
    }

    // for each resource, save to zip file
    val output = ByteArrayOutputStream()
    if (success) {
        try {
            ZipOutputStream(output).use { zos ->
                config.optJSONArray("resources")?.forEach {
                    val resourceFile = File(
                        buildFolder,
                        it as? String ?: throw IllegalArgumentException("Resource must be a string")
                    )
                    if (!resourceFile.exists() || resourceFile.isDirectory)
                        throw IllegalArgumentException("Resource ${resourceFile.name} does not exist or is a directory, searched at ${resourceFile.absolutePath}")

                    zos.putNextEntry(ZipEntry(resourceFile.name))
                    zos.write(resourceFile.readBytes())
                }
            }
        } catch (ex: Exception) { success = false }
    }

    // assemble json object
    val json = JSONObject().put("success", success).put("buildID", buildUUID)

    if (success) {
        val bytes = output.toByteArray()
        File("tmp.zip").writeBytes(bytes)
        json.put("result", Base64.getEncoder().encode(bytes))
            .put("hash", HashUtils.getChecksumFromBytes(MessageDigest.getInstance("MD5"), bytes))
    }

    // broadcast build
    service.broadcastRequestByTag("cicd", "build_result", json)
    logger.info("Finished build with result ID: ${json["buildID"]} Success: ${json["success"]}")

    // force shutdown
    System.exit(0)
}

// function that runs the given command
fun runCommand(target: File, command: String) =
    ProcessBuilder("cmd.exe", "/c", command)
        .directory(target)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start().waitFor()

// function that clones the given git repo with teh url and auth given // todo implement auth
fun cloneGitRepo(target: File, url: String, auth: String?) =
    ProcessBuilder("git", "clone", url, target.absolutePath)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start().waitFor()

// function that takes in the input array of arguments and splits them into a key value map on their first equals sign
fun processArgs(args: Array<String>): HashMap<String, String> {
    val output = hashMapOf<String, String>()
    args.forEach { str ->
        val tokens = str.split("=", limit = 2)
        output[tokens.first()] = tokens.last()
    }
    return output
}