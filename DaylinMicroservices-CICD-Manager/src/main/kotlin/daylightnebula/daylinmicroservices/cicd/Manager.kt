package daylightnebula.daylinmicroservices.cicd

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import daylightnebula.daylinmicroservices.loopingThread
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.lang.IllegalArgumentException
import java.lang.Thread.sleep
import java.util.*
import kotlin.collections.HashMap

// builds
val builds = hashMapOf<String, Build>()
data class Build(val name: String, val config: JSONObject)

// build queue
const val QUEUE_DROP_TIME = 60000
lateinit var runCommand: String
val buildQueue = mutableListOf<QueuedBuild>()
data class QueuedBuild(val id: UUID, val build: Build, val createdTime: Long)

// setup microservice necessities
val endpoints = hashMapOf<String, (json: JSONObject) -> JSONObject>(
    "get_builds" to {
            JSONObject().put("builds", JSONArray().putAll(builds.values.map { it.name }))
    },
    "run_build" to { json ->
        if (json.has("build") && builds.containsKey(json.getString("build"))) {
            val build = builds[json.getString("build")]!!
            buildQueue.add(
                QueuedBuild(
                    UUID.randomUUID(),
                    build,
                    System.currentTimeMillis()
                )
            )
            ProcessBuilder(runCommand).start()

            JSONObject().put("success", true)
        } else JSONObject().put("success", false)
    },
    "builder_ready" to {
        TODO("Builder Ready Endpoint")
    }
)
val service = Microservice(
    MicroserviceConfig(
        name = "CICD-Manager",
        tags = listOf("cicd"),
    ),
    endpoints
)

fun main(inArgs: Array<String>) {
    // load args
    val args = processArgs(inArgs)

    // load configs
    val configFile = File(
        args["config"]
            ?: "config.json"
    )
    if (!configFile.exists()) {
        service.dispose() // something major went wrong, we need to crash the service
    }
    val configJSON = JSONObject(configFile.readText())
    runCommand = configJSON.getString("builder_start_command")
    builds.putAll(
        configJSON.getJSONArray("builds")
            .mapNotNull { json ->
                if (json is JSONObject) {
                    val name = json.getString("name")
                    name to Build(
                        name,
                        json.getJSONObject("build_config")
                    )
                } else null
            }
    )

    // run service
    service.start()
    while (true) {
        synchronized(buildQueue) {
            buildQueue.removeAll { System.currentTimeMillis() - it.createdTime > QUEUE_DROP_TIME }
        }

        sleep(1000)
    }
}

// function that takes in the input array of arguments and splits them into a key value map on their first equals sign
fun processArgs(args: Array<String>): HashMap<String, String> {
    val output = hashMapOf<String, String>()
    args.forEach { str ->
        val tokens = str.split("=", limit = 2)
        output[tokens.first()] = tokens.last()
    }
    return output
}