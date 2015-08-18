package com.limpygnome.daemon.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * The facade/controller for a daemon, which acts as the bridge between all the internal services/components.
 */
public class Controller
{
    private static final Logger LOG = LogManager.getLogger(Controller.class);

    private String controllerName;
    private boolean running;
    private HashMap<String, Service> services;
    private Settings settings;

    public Controller(String controllerName)
    {
        this.controllerName = controllerName;
        this.services = new HashMap<>();
        this.settings = new Settings(this);
    }

    public synchronized void add(String serviceName, Service service)
    {
        if (running)
        {
            throw new RuntimeException("Cannot add service whilst running - name: " + serviceName +
                    ", class: " + service.getClass().getName()
            );
        }

        services.put(serviceName, service);
    }

    public synchronized void start()
    {
        LOG.info("Starting controller...");

        // Reload settings
        settings.reload();

        // Start all services
        for (Map.Entry<String, Service> kv : services.entrySet())
        {
            LOG.debug("Starting service - name: {}", kv.getKey());

            kv.getValue().start(this);

            LOG.debug("Started service successfully - name: {}", kv.getKey());
        }

        setRunning(true);

        LOG.info("Controller started successfully");
    }

    public synchronized void stop()
    {
        LOG.info("Controller stopping...");

        // Stop all services
        for (Map.Entry<String, Service> kv : services.entrySet())
        {
            LOG.debug("Stopping service - name: {}", kv.getKey());

            kv.getValue().stop(this);

            LOG.debug("Stopped service - name: {}", kv.getKey());
        }

        setRunning(false);

        LOG.info("Controller has stopped");
    }

    public synchronized void waitForExit()
    {
        // Block until we exit...
        while (!running)
        {
            try
            {
                wait();
            }
            catch (InterruptedException e)
            {
            }
        }
    }

    public synchronized void hookShutdown()
    {
        // This will hook an event to stop the controller when the JVM is shutting down
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run()
            {
                Controller.this.stop();
            }
        });

        LOG.debug("Hooked runtime shutdown event");
    }

    public synchronized void hookAndStartAndWaitForExit()
    {
        hookShutdown();
        start();
        waitForExit();
    }

    public String getControllerName()
    {
        return controllerName;
    }

    public synchronized Service getServiceByName(String serviceName)
    {
        Service service = services.get(serviceName);

        if (service == null)
        {
            throw new RuntimeException("Service '" + serviceName + "' missing");
        }

        return service;
    }

    private void setRunning(boolean value)
    {
        this.running = value;
        notifyAll();

        LOG.debug("Changed running state of controller - running: {}", this.running);
    }

    public Settings getSettings()
    {
        return settings;
    }

}
