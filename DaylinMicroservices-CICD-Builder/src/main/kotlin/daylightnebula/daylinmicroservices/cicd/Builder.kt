package daylightnebula.daylinmicroservices.cicd

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import daylightnebula.daylinmicroservices.filesysteminterface.HashUtils
import daylightnebula.daylinmicroservices.filesysteminterface.pushFile
import daylightnebula.daylinmicroservices.filesysteminterface.requestFile
import daylightnebula.daylinmicroservices.requests.request
import okhttp3.internal.wait
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.Thread.sleep
import java.security.MessageDigest
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread
import kotlin.random.Random

val endpoints = hashMapOf<String, (json: JSONObject) -> JSONObject>(
    "run_build" to { json ->
        TODO("Implement run build endpoint")
    }
)
lateinit var service: Microservice

// control variables
var buildConfig: JSONObject? = null
var buildUUID = UUID.randomUUID()
val startTime = System.currentTimeMillis()

fun main(inArgs: Array<String>) {
    // process input args
    val args = processArgs(inArgs)

    // check if local mode is enabled
    val localModeEnabled = args.containsKey("local")

    // only create service when not in local mode
    service = Microservice(
        MicroserviceConfig(
            name = "CICD-Builder",
            tags = listOf("cicd"),
        ),
        if (localModeEnabled) hashMapOf() else endpoints
    )
    service.start()
    sleep(5000)

    // if in local mode, load config given path
    if (localModeEnabled) {
        buildConfig = JSONObject(File(args["local"]!!).readText())
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
var buildUploadDebug = true
fun runBuild(config: JSONObject) {
    // create and clear build directory
    val buildFolder = File(System.getProperty("user.dir"), "build")
    buildFolder.mkdirs()

    if (!buildUploadDebug) {
        // delete old build
        buildFolder.listFiles()?.forEach { it.deleteRecursively() }

        // clone the repo
        if (!config.has("url")) throw IllegalArgumentException("URL must be specified for clone")
        val cloneResult = cloneGitRepo(buildFolder, config.getString("url"), config.optString("auth"))
        if (cloneResult != 0) throw Exception("Clone operation failed, URL: ${config.getString("url")}, exit code: $cloneResult")

        // run each command, verifying after each
        for (any in (config.optJSONArray("commands")
            ?: throw IllegalArgumentException("Commands to build must be specified"))) {
            val command = any as? String ?: throw IllegalArgumentException("Commands must be a string")
            val cmdResult = runCommand(buildFolder, command)
            if (cmdResult != 0) throw Exception("Command $command could not be run, exited with code $cmdResult")
        }
    }

    // for each resource, save it to the file system accordingly
    config.optJSONArray("resources")?.forEach {
        val resource = it as? String ?: throw IllegalArgumentException("Resource must be a string")
        val resourceFile = File(buildFolder, resource)
        if (!resourceFile.exists()) throw IllegalArgumentException("Resource $resource does not exist, searched at ${resourceFile.absolutePath}")
        saveResource(resourceFile)
    } ?: throw IllegalArgumentException("Resources must be specified")

    // catch errors from shutdown
    try { service.dispose() } catch (ex: Exception) {}
}

// function to log and save a resource to the file system
fun saveResource(resource: File) {
    // file paths
    val masterFolderPath = "/.builds/${resource.nameWithoutExtension}"
    val metadataFilePath = "${masterFolderPath}/metadata.json"
    val buildPath = "$masterFolderPath/$buildUUID"

    // request metadata file
    println("Request metadata file $metadataFilePath")
    service.requestFile(metadataFilePath).get().let { file ->
        // get json object from file
        val metadata = file?.let { JSONObject(it.readText()) }
            ?: JSONObject()

        // update json
        metadata.put(
            buildUUID.toString(),
            JSONObject()
                .put("id", buildUUID)
                .put("time", startTime)
        )

        // create temp file
        val tempFile = File("tmp-${Random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE)}.json")
        tempFile.writeText(metadata.toString(4))

        // push
        println("Push file $metadataFilePath")
        val pushResult = service.pushFile(metadataFilePath, tempFile).whenComplete { b, _ -> println("Success? $b") }.get()
        println("Push result $pushResult")
    }
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