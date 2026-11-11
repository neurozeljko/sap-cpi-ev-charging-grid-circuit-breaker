package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

Message processData(Message message) {
    String sessionId = message.getProperty("Prop_Charging_Session_ID")
    String chargerId = message.getProperty("Prop_Charger_ID")
    String circuitState = message.getProperty("Prop_Circuit_State")

    def suspensionPayload = [
        circuitBreakerSuspension: [
            status: "BILLING_DISPATCH_PAUSED",
            circuitState: circuitState,
            chargingSessionId: sessionId,
            chargerId: chargerId,
            action: "HOLD_IN_JMS_QUEUE_FOR_COOL_DOWN",
            timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(suspensionPayload)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-Circuit-Breaker-Status", "HOLD_JMS_RETENTION")
    return message
}
