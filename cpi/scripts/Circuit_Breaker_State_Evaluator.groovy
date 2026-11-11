package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def jsonSlurper = new JsonSlurper()
    def payload = jsonSlurper.parse(reader)
    def sessionData = payload.evChargingQueueEnvelope ?: payload

    String circuitBreakerState = message.getProperty("Prop_Global_Circuit_State") ?: "CLOSED"
    long circuitOpenedTimestamp = (message.getProperty("Prop_Circuit_Opened_Epoch") ?: 0L) as Long
    long currentTimeEpoch = new Date().getTime()
    long coolDownPeriodMillis = 600000L

    if (circuitBreakerState == "OPEN") {
        if ((currentTimeEpoch - circuitOpenedTimestamp) > coolDownPeriodMillis) {
            circuitBreakerState = "HALF_OPEN"
        }
    }

    message.setProperty("Prop_Circuit_State", circuitBreakerState)
    message.setProperty("Prop_Charging_Session_ID", sessionData.chargingSessionId)
    message.setProperty("Prop_Charger_ID", sessionData.chargerId)
    message.setProperty("Prop_Energy_KWh", sessionData.energyDeliveredKWh)
    message.setProperty("Prop_Total_Amount_EUR", sessionData.totalNetAmountEUR)
    message.setProperty("Prop_Vehicle_UUID", sessionData.vehicleAccountUUID)
    message.setProperty("Prop_Session_Hash", sessionData.sessionHashSHA256)

    message.setHeader("X-Circuit-Breaker-Status", circuitBreakerState)
    return message
}
