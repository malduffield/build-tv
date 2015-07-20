package com.limpygnome.daemon.buildtv.service;

import com.limpygnome.daemon.api.Controller;
import com.limpygnome.daemon.api.Service;
import com.limpygnome.daemon.buildtv.led.LedTimeThread;
import com.limpygnome.daemon.buildtv.led.PatternSource;

/**
 * Responsible for controlling the LED requests sent to the LED daemon.
 */
public class LedTimeService implements Service
{
    private LedTimeThread ledTimeThread;

    @Override
    public synchronized void start(Controller controller)
    {
        String ledDaemonUrl = controller.getSetting("ws281x.led.rest.url");
        String screenDaemonUrl = controller.getSetting("screen-daemon.rest.screen.url");

        ledTimeThread = new LedTimeThread(
                ledDaemonUrl,
                screenDaemonUrl
        );
        ledTimeThread.start();
    }

    @Override
    public synchronized void stop(Controller controller)
    {
        ledTimeThread.kill();
    }

    public synchronized void addPatternSource(PatternSource patternSource)
    {
        ledTimeThread.addPatternSource(patternSource);
    }

    public synchronized void removePatternSource(PatternSource patternSource)
    {
        ledTimeThread.removePatternSource(patternSource);
    }
}