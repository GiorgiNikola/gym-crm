package com.giorgi.gymcrm.workload;

public final class WorkloadMessageHeaders {

    public static final String TYPE_ID = "eventType";
    public static final String TYPE = "workloadChange";
    public static final String EVENT_VERSION = "eventVersion";
    public static final int VERSION = 1;
    public static final String TRANSACTION_ID = "transactionId";
    public static final String AUTHORIZATION = "authorization";
    public static final String GROUP_ID = "JMSXGroupID";

    private WorkloadMessageHeaders() {
    }
}
