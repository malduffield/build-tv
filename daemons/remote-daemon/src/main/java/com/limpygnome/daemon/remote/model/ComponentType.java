package com.limpygnome.daemon.remote.model;

/**
 * The type of local component (daemon or client).
 *
 * Used for matching the first path/directory of an incoming request to a local component port for forwarding/proxying.
 */
public enum ComponentType
{
    /**
     * The LED daemon - LED strip etc.
     */
    LED_DAEMON("led-daemon", "local-ports/led-daemon"),

    /**
     * The system daemon - statistics, screen, power etc.
     */
    SYSTEM_DAEMON("system-daemon", "local-ports/system-daemon"),

    /**
     * The build TV daemon - Jenkins etc.
     */
    BUILD_TV_DAEMON("build-tv-daemon", "local-ports/build-tv-daemon"),

    /**
     * The interval daemon - timed patterns/notifications etc.
     */
    INTERVAL_DAEMON("interval-daemon", "local-ports/interval-daemon")
    ;

    public final String COMPONENT_NAME;
    public final String TOP_LEVEL_PATH;
    public final String SETTING_KEY_PORT;

    ComponentType(String COMPONENT_NAME, String SETTING_KEY_PORT)
    {
        this.COMPONENT_NAME = COMPONENT_NAME;
        this.TOP_LEVEL_PATH = COMPONENT_NAME;
        this.SETTING_KEY_PORT = SETTING_KEY_PORT;
    }

}
