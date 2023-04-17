package daylightnebula.daylinmicroservices.cicd

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.Thread.sleep

val endpoints = hashMapOf<String, (json: JSONObject) -> JSONObject>(
    "run_build" to { json ->
        TODO("Implement run build endpoint")
    }
)
lateinit var service: Microservice

// control variables
var buildConfig: JSONObject? = null
val startTime = System.currentTimeMillis()

fun main(inArgs: Array<String>) {
    // process input args
    val args = processArgs(inArgs)

    // check if local mode is enabled
    val localModeEnabled = args.containsKey("local")

    // only create service when not in local mode
    if (!localModeEnabled) {
        service = Microservice(
            MicroserviceConfig(
                name = "CICD-Builder",
                tags = listOf("cicd"),
            ),
            endpoints
        )
        service.start()
    }

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
fun runBuild(config: JSONObject) {
    // create and clear build directory
    val buildFolder = File(System.getProperty("user.dir"), "build")
    buildFolder.mkdirs()
    buildFolder.listFiles()?.forEach { it.deleteRecursively() }

    // clone the repo
    if (!config.has("url")) throw IllegalArgumentException("URL must be specified for clone")
    val cloneResult = cloneGitRepo(buildFolder, config.getString("url"), config.optString("auth"))
    if (cloneResult != 0) throw Exception("Clone operation failed, URL: ${config.getString("url")}, exit code: $cloneResult")

    // run each command, verifying after each
    for (any in (config.optJSONArray("commands") ?: throw IllegalArgumentException("Commands to build must be specified"))) {
        val command = any as? String ?: throw IllegalArgumentException("Commands must be a string")
        val cmdResult = runCommand(buildFolder, command)
        if (cmdResult != 0) throw Exception("Command $command could not be run, exited with code $cmdResult")
    }

    // catch errors from shutdown
    try { service.dispose() } catch (ex: Exception) {}
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