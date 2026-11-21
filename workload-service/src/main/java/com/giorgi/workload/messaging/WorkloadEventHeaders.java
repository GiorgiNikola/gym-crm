package com.giorgi.workload.messaging;

import org.springframework.jms.support.JmsHeaders;

public final class WorkloadEventHeaders {

    public static final String TYPE_ID = "eventType";
    public static final String TYPE = "workloadChange";
    public static final String EVENT_VERSION = "eventVersion";
    public static final String SUPPORTED_VERSION = "1";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String AUTHORIZATION = "authorization";
    public static final String GROUP_ID = "JMSXGroupID";
    public static final String DELIVERY_COUNT = "JMSXDeliveryCount";
    public static final String SENT_AT = JmsHeaders.TIMESTAMP;

    public static final String DLQ_REASON = "dlqReason";
    public static final String DLQ_DETAIL = "dlqDetail";
    public static final String ORIGINAL_QUEUE = "originalQueue";
    public static final String EVENT_ID = "eventId";

    private WorkloadEventHeaders() {
    }
}
