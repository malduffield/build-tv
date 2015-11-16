package com.limpygnome.daemon.remote.service.thread;

import com.limpygnome.daemon.api.Controller;
import com.limpygnome.daemon.api.ControllerState;
import com.limpygnome.daemon.common.ExtendedThread;
import com.limpygnome.daemon.remote.service.HostInformationService;
import com.limpygnome.daemon.remote.service.StatsForwarderService;
import com.limpygnome.daemon.remote.service.InstanceIdentityService;
import com.limpygnome.daemon.remote.service.VersionService;
import com.limpygnome.daemon.remote.service.auth.AuthTokenProviderService;
import com.limpygnome.daemon.util.JsonUtil;
import com.limpygnome.daemon.util.RestClient;
import org.apache.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A thread responsible for initially sending information about the system to an endpoint. Periodic updates regarding
 * the system are sent to the same endpoint too.
 */
public class StatsForwarderThread extends ExtendedThread
{
    private static final Logger LOG = LogManager.getLogger(StatsForwarderThread.class);

    private static final String STATS_FORWARDER_USER_AGENT = "stats-forwarder";

    private Controller controller;
    private StatsForwarderService statsForwarderService;
    private InstanceIdentityService instanceIdentityService;
    private AuthTokenProviderService authProviderService;
    private VersionService versionService;
    private HostInformationService hostInformationService;

    private String ledDaemonPatternEndpointUrl;
    private String systemDaemonStatsEndpointUrl;
    private String systemDaemonScreenGetEndpointUrl;
    private String buildTvDaemonDashboardEndpointUrl;

    public StatsForwarderThread(Controller controller, StatsForwarderService statsForwarderService)
    {
        this.controller = controller;
        this.statsForwarderService = statsForwarderService;
        this.instanceIdentityService = (InstanceIdentityService) controller.getServiceByName(InstanceIdentityService.SERVICE_NAME);
        this.authProviderService = (AuthTokenProviderService) controller.getServiceByName(AuthTokenProviderService.SERVICE_NAME);
        this.versionService = (VersionService) controller.getServiceByName(VersionService.SERVICE_NAME);
        this.hostInformationService = (HostInformationService) controller.getServiceByName(HostInformationService.SERVICE_NAME);

        // Build endpoint URLs for available daemons
        // -- build-tv-daemon
        if (controller.isComponentEnabled("build-tv-daemon"))
        {
            long buildTvDaemonPort = controller.getSettings().getLong("local-ports/build-tv-daemon");
            buildTvDaemonDashboardEndpointUrl = "http://localhost:" + buildTvDaemonPort + "/build-tv-daemon/dashboard/get";
        }
        else
        {
            buildTvDaemonDashboardEndpointUrl = null;
        }

        // -- led-daemon
        if (controller.isComponentEnabled("led-daemon"))
        {
            long ledDaemonPort = controller.getSettings().getLong("local-ports/led-daemon");
            ledDaemonPatternEndpointUrl = "http://localhost:" + ledDaemonPort + "/led-daemon/leds/get";
        }
        else
        {
            ledDaemonPatternEndpointUrl = null;
        }

        // -- system-daemon
        if (controller.isComponentEnabled("system-daemon"))
        {
            long systemDaemonPort = controller.getSettings().getLong("local-ports/system-daemon");
            systemDaemonStatsEndpointUrl = "http://localhost:" + systemDaemonPort + "/system-daemon/stats";
            systemDaemonScreenGetEndpointUrl = "http://localhost:" + systemDaemonPort + "/system-daemon/screen/get";
        }
        else
        {
            systemDaemonStatsEndpointUrl = null;
            systemDaemonScreenGetEndpointUrl = null;
        }
    }

    @Override
    public void run()
    {
        LOG.debug("Waiting for controller to finish loading...");
        controller.waitForState(ControllerState.RUNNING);

        LOG.debug("Started...");

        long frequency = statsForwarderService.getFrequency();
        boolean infoSent = false;

        while (!isExit())
        {
            // Initially we will send our info to the endpoint, as a way to register with them...
            if (!infoSent)
            {
                infoSent = sendInfo();
            }
            else
            {
                // Send update to endpoint
                sendUpdate("online", true);
            }

            // Sleep...
            try
            {
                Thread.sleep(frequency);
            }
            catch (InterruptedException e) { }
        }

        // Send an update we're shutting down...
        LOG.debug("Sending shutdown update...");

        sendUpdate("shutdown", false);

        LOG.debug("Thread finished");
    }

    private boolean sendInfo()
    {
        LOG.debug("Building message...");

        JSONObject request;

        // Build info request
        try
        {
            // Fetch Jira dashboard
            Long jiraDashboard = fetchJiraDashboard(true);

            // Build info object
            request = new JSONObject();

            request.put("uuid", instanceIdentityService.getInstanceUuid().toString());
            request.put("title", instanceIdentityService.getTitle());
            request.put("hostname", hostInformationService.getHostname());
            request.put("port", hostInformationService.getRestPort());
            request.put("auth", authProviderService.getAuthToken());
            request.put("version", versionService.getVersion());
            request.put("dashboard", jiraDashboard);
        }
        catch (Exception e)
        {
            LOG.error("Failed to build info message", e);
            return false;
        }

        // Send info request
        String endpointUrlInfo = statsForwarderService.getEndpointUrlInfo();
        LOG.debug("Sending info... - url: {}", endpointUrlInfo);

        try
        {
            // Send to endpoint
            RestClient restClient = new RestClient(STATS_FORWARDER_USER_AGENT, -1);
            HttpResponse httpResponse = restClient.executePost(endpointUrlInfo, request);

            int statusCode = httpResponse.getStatusLine().getStatusCode();

            if (statusCode != 200)
            {
                LOG.warn("Failed to send info, unexpected HTTP status code - status code: {}, url: {}", statusCode, endpointUrlInfo);
                return false;
            }
            else
            {
                LOG.debug("Successfully sent info");
                return true;
            }
        }
        catch (Exception e)
        {
            LOG.error("Failed to send info to REST endpoint", e);
            return false;
        }
    }

    private void sendUpdate(String status, boolean fetchExternalData)
    {
        LOG.debug("Sending update...");

        try
        {
            // Fetch latest stats
            JSONArray jsonArrayStats = fetchStatistics(fetchExternalData);

            if (jsonArrayStats == null) {
                jsonArrayStats = new JSONArray();
            }

            // Fetch latest build indicator
            String buildIndicator = fetchBuildIndicator(fetchExternalData);

            if (buildIndicator == null) {
                buildIndicator = "shutdown";
            }

            // Fetch screen state
            Boolean screenState = fetchScreenState(fetchExternalData);

            if (screenState == null) {
                screenState = false;
            }

            // Build update packet object
            JSONObject response = new JSONObject();

            response.put("uuid", instanceIdentityService.getInstanceUuid().toString());
            response.put("status", status);
            response.put("buildIndicator", buildIndicator);
            response.put("metrics", jsonArrayStats);
            response.put("screen", screenState);

            // Send to endpoint
            RestClient restClient = new RestClient(STATS_FORWARDER_USER_AGENT, -1);
            restClient.executePost(statsForwarderService.getEndpointUrlUpdate(), response);

            LOG.debug("Successfully sent update");
        }
        catch (Exception e)
        {
            LOG.error("Failed to send system REST update", e);
        }
    }

    private String fetchBuildIndicator(boolean fetchExternalData)
    {
        String pattern = (String) fetchJsonObjectFromUrl(ledDaemonPatternEndpointUrl, new String[]{"current", "pattern"}, fetchExternalData);

        LOG.debug("Current build indicator retrieved - pattern: {}", pattern);

        return pattern;
    }

    private JSONArray fetchStatistics(boolean fetchExternalData)
    {
        JSONArray response = (JSONArray) fetchDataFromUrl(systemDaemonStatsEndpointUrl, fetchExternalData);
        LOG.debug("Retrieved statistics - metrics: {}", response != null ? response.size() : 0);

        return response;
    }

    private Long fetchJiraDashboard(boolean fetchExternalData)
    {
        Long response = (Long) fetchJsonObjectFromUrl(buildTvDaemonDashboardEndpointUrl, new String[]{"id"}, fetchExternalData);
        LOG.debug("Retrieved Jira dashboard - dashboard ID: {}", response);

        return response;
    }

    public Boolean fetchScreenState(boolean fetchExternalData)
    {
        Boolean screenState = (Boolean) fetchJsonObjectFromUrl(systemDaemonScreenGetEndpointUrl, new String[]{ "on" }, fetchExternalData);
        LOG.debug("Retrieved screen state - on: {}", screenState);

        return screenState;
    }

    private Object fetchJsonObjectFromUrl(String url, String[] path, boolean fetchExternalData)
    {
        Object response = fetchDataFromUrl(url, fetchExternalData);

        if (response != null)
        {
            // Check object is correct type
            if (!(response instanceof JSONObject))
            {
                LOG.warn("Incorrect type returned, expected JSONObject - url: {}, class: {}", url, response.getClass().getName());
                return null;
            }

            // Check key present
            if (path != null)
            {
                JSONObject jsonRoot = (JSONObject) response;
                Object value = JsonUtil.getNestedNode(jsonRoot, path);

                if (value == null)
                {
                    LOG.warn("Value not present in JSON response - url: {}, path: {}", url, path);
                }

                return value;
            }
            else
            {
                return response;
            }
        }
        else
        {
            return null;
        }
    }

    private Object fetchDataFromUrl(String url, boolean fetchExternalData)
    {
        // Check we're allowed to fetch external data
        if (!fetchExternalData)
        {
            LOG.debug("Ignored request to fetch data, external flag set is false - url: {}", url);
            return null;
        }
        // Check daemon is available
        else if (url == null)
        {
            LOG.debug("Ignored request to fetch data, daemon unavailable");
            return null;
        }

        // Attempt to fetch data
        try
        {
            LOG.debug("Fetching data - url: {}", url);

            RestClient restClient = new RestClient(STATS_FORWARDER_USER_AGENT, -1);
            return restClient.executeGet(url);
        }
        catch (Exception e)
        {
            LOG.error("Failed to retrieve data - url: " + url, e);
            return null;
        }
    }

}
