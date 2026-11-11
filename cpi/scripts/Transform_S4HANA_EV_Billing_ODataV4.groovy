package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

Message processData(Message message) {
    String sessionId = message.getProperty("Prop_Charging_Session_ID")
    String chargerId = message.getProperty("Prop_Charger_ID")
    String vehicleUUID = message.getProperty("Prop_Vehicle_UUID")
    double energyKWh = (message.getProperty("Prop_Energy_KWh") ?: 0.0) as Double
    double totalAmountEUR = (message.getProperty("Prop_Total_Amount_EUR") ?: 0.0) as Double
    String sessionHash = message.getProperty("Prop_Session_Hash")
    String correlationId = message.getProperty("Prop_Correlation_ID") ?: UUID.randomUUID().toString()

    def s4BillingDocument = [
        BillingDocumentType: "F2",
        SalesOrganization: "1010",
        DistributionChannel: "10",
        Division: "00",
        SoldToParty: "EV-CUSTOMER-ACCOUNT-01",
        PayerParty: "EV-FLEET-GLOBAL-PAYER",
        BillingDocumentDate: new Date().format("yyyy-MM-dd"),
        TransactionCurrency: "EUR",
        TotalNetAmount: totalAmountEUR,
        to_Item: [
            [
                BillingDocumentItem: "000010",
                Product: "ENERGY-KWH-HIGH-SPEED-DC",
                BillingQuantity: energyKWh.toString(),
                BillingQuantityUnit: "KWH",
                NetAmount: totalAmountEUR,
                TaxAmount: (totalAmountEUR * 0.19).round(2),
                ItemText: "EV Session ${sessionId.take(15)} at Charger ${chargerId.take(15)}"
            ]
        ],
        gridAuditContext: [
            circuitBreakerStateAtPost: "CLOSED_NOMINAL",
            chargingSessionId: sessionId,
            originatingChargerHardwareId: chargerId,
            vehicleContractUUID: vehicleUUID,
            idempotencySessionHashSHA256: sessionHash,
            mplCorrelationTrackingId: correlationId,
            settledTimestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(s4BillingDocument)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-CleanCore-Target", "S4HANA_BILLING_DOCUMENT_ODATA_V4")
    message.setHeader("X-Throttling-Consumer-SLA", "50_MSG_PER_SEC_COMPLIANT")
    return message
}
