package daylightnebula.daylinmicroservices.cicd

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import org.json.JSONObject

val endpoints = hashMapOf<String, (json: JSONObject) -> JSONObject>(
    "run_build" to { json ->
        TODO("Implement run build endpoint")
    }
)
val service = Microservice(
    MicroserviceConfig(
        name = "CICD-Builder",
        tags = listOf("cicd"),
    ),
    endpoints
)

fun main() {
    service.start()
    while(true) {}
}