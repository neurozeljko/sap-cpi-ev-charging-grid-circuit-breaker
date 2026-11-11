package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest

Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def jsonSlurper = new JsonSlurper()
    def payload = jsonSlurper.parse(reader)

    String sessionId = payload.chargingSessionId ?: "CHG-SESSION-UNKNOWN"
    String chargerId = payload.chargerId ?: "EVSE-DE-DEFAULT-01"
    double energyDeliveredKWh = (payload.energyDeliveredKWh ?: 0.0) as Double
    double tariffRateEUR = (payload.tariffRatePerKWh ?: 0.45) as Double
    String vehicleUUID = payload.vehicleAccountUUID ?: "VEH-UNKNOWN-USER"

    if (energyDeliveredKWh <= 0.0) {
        throw new IllegalArgumentException("EV_BILLING_INVALID: Energy delivered kWh must be strictly greater than zero.")
    }

    double totalAmountEUR = (energyDeliveredKWh * tariffRateEUR).round(2)

    String sessionFingerprint = "${sessionId}|${chargerId}|${energyDeliveredKWh}|${totalAmountEUR}"
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hashBytes = digest.digest(sessionFingerprint.getBytes("UTF-8"))
    StringBuilder hexString = new StringBuilder()
    for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b)
        if (hex.length() == 1) hexString.append('0')
        hexString.append(hex)
    }
    String sessionHash = hexString.toString()

    message.setProperty("Prop_Charging_Session_ID", sessionId)
    message.setProperty("Prop_Charger_ID", chargerId)
    message.setProperty("Prop_Energy_KWh", energyDeliveredKWh)
    message.setProperty("Prop_Total_Amount_EUR", totalAmountEUR)
    message.setProperty("Prop_Vehicle_UUID", vehicleUUID)
    message.setProperty("Prop_Session_Hash", sessionHash)

    def normalizedQueuePayload = [
        evChargingQueueEnvelope: [
            chargingSessionId: sessionId,
            chargerId: chargerId,
            vehicleAccountUUID: vehicleUUID,
            energyDeliveredKWh: energyDeliveredKWh,
            tariffRatePerKWh: tariffRateEUR,
            totalNetAmountEUR: totalAmountEUR,
            sessionHashSHA256: sessionHash,
            queuedTimestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setBody(JsonOutput.toJson(normalizedQueuePayload))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-JMS-Routing-Priority", "HIGH_THROUGHPUT_BILLING")
    return message
}
