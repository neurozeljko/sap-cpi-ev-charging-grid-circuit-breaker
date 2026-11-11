package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

Message processData(Message message) {
    def exception = message.getProperty("CamelExceptionCaught")
    String sessionId = message.getProperty("Prop_Charging_Session_ID") ?: "UNKNOWN_SESSION"
    String chargerId = message.getProperty("Prop_Charger_ID") ?: "UNKNOWN_CHARGER"
    String correlationId = message.getHeader("SAP_MplCorrelationId", String) ?: UUID.randomUUID().toString()
    String errorDetails = exception ? exception.getMessage() : "HTTP 503 Backend Service Unavailable / HANA DB Lock Contention"

    long tripTimestamp = new Date().getTime()

    message.setProperty("Prop_Global_Circuit_State", "OPEN")
    message.setProperty("Prop_Circuit_Opened_Epoch", tripTimestamp)

    def teamsIncidentCard = [
        "@type": "MessageCard",
        "themeColor": "D9381E",
        "summary": "CRITICAL: S/4HANA Database Overload - Circuit Breaker Tripped",
        "sections": [[
            "activityTitle": "⚡ EV Charging Grid Circuit Breaker: OPEN (10-Minute Cooldown)",
            "facts": [
                ["name": "Fault Root Cause:", "value": "S/4HANA 503 DB Lock Contention / Thread Starvation"],
                ["name": "Action Taken:", "value": "Consumer throttler suspended for 10 minutes"],
                ["name": "Buffer Safety:", "value": "100% of EV billing events retained safely in JMS Queue"],
                ["name": "Session Affected:", "value": sessionId],
                ["name": "Correlation ID:", "value": correlationId]
            ]
        ]]
    ]

    def fallbackEnvelope = [
        circuitBreakerTripEvent: [
            state: "CIRCUIT_BREAKER_OPEN",
            cooldownSeconds: 600,
            activeSessionId: sessionId,
            originatingCharger: chargerId,
            incidentId: correlationId,
            cause: errorDetails,
            jmsPreservationPolicy: "RETAIN_IN_QUEUE_DO_NOT_ACKNOWLEDGE",
            timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setProperty("Prop_Teams_Incident_Payload", JsonOutput.toJson(teamsIncidentCard))
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(fallbackEnvelope)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-Circuit-Breaker-Alert", "TRIPPED_OPEN_S4_PROTECTED")
    return message
}
