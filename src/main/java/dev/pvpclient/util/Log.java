package dev.pvpclient.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared logger so every package logs under the same name. */
public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("PvpClient");

    private Log() {}
}
