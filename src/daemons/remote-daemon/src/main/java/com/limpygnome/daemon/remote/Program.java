package com.limpygnome.daemon.remote;

import com.limpygnome.daemon.api.Controller;
import com.limpygnome.daemon.api.imp.DefaultController;
import com.limpygnome.daemon.remote.service.HostInformationService;
import com.limpygnome.daemon.remote.service.InstanceIdentityService;
import com.limpygnome.daemon.remote.service.RestProxyService;
import com.limpygnome.daemon.remote.service.StatsForwarderService;
import com.limpygnome.daemon.remote.service.VersionService;
import com.limpygnome.daemon.remote.service.auth.RandomTokenAuthProviderService;
import com.limpygnome.daemon.remote.service.proxy.ProxyService;
import com.limpygnome.daemon.service.RestService;

/**
 * Entry point into the remote daemon.
 */
public class Program
{

    public static void main(String[] args)
    {
        Controller controller = new DefaultController("remote-daemon");

        // Add services
        controller.add(RandomTokenAuthProviderService.SERVICE_NAME, new RandomTokenAuthProviderService());
        controller.add(RestProxyService.SERVICE_NAME, new RestProxyService());
        controller.add(InstanceIdentityService.SERVICE_NAME, new InstanceIdentityService());
        controller.add(VersionService.SERVICE_NAME, new VersionService());
        controller.add(HostInformationService.SERVICE_NAME, new HostInformationService());
        controller.add(StatsForwarderService.SERVICE_NAME, new StatsForwarderService());
        controller.add(ProxyService.SERVICE_NAME, new ProxyService());

        // Attach REST handlers
        RestService.attachControllerRestHandlerServices(controller);

        // Start forever...
        controller.hookAndStartAndWaitForExit();
    }

}