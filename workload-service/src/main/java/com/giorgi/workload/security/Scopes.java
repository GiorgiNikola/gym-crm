package com.giorgi.workload.security;

public final class Scopes {

    public static final String WRITE = "workload:write";
    public static final String READ = "workload:read";
    public static final String WRITE_AUTHORITY = "SCOPE_" + WRITE;
    public static final String READ_AUTHORITY = "SCOPE_" + READ;

    private Scopes() {
    }
}
