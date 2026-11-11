# ADR-024: Global EV Charging Grid (High-Throughput JMS Throttling & Circuit Breaker)

## Status
Accepted

## Context
A network of 500,000 EV charging stations across Europe sends simultaneous billing events during peak windows. Direct synchronous ERP ingestion causes SAP HANA database lock contention, thread starvation, and OOM crashes.

## Decision
1. Decouple edge ingestion using an asynchronous JMS Queue (`queue:EV_Charging_Billing_Ingest`), returning HTTP 202 Accepted to chargers within 20 milliseconds.
2. Implement a dedicated worker consumer process throttling delivery to an exact rate of 50 messages per second.
3. Deploy an autonomous Circuit Breaker pattern: upon encountering ERP HTTP 503/429 responses, transition state to OPEN, suspend delivery for 10 minutes, and preserve 100% of payloads inside the JMS queue.
4. Execute financial billing postings in SAP S/4HANA Cloud utilizing standard Clean Core OData V4 APIs.

## Consequences
- Protects S/4HANA core database from peak concurrency spikes and OOM failures.
- Guarantees zero data loss during cloud ERP maintenance windows.
- Provides sub-20ms edge acknowledgment for 500,000 simultaneous charging stations.
