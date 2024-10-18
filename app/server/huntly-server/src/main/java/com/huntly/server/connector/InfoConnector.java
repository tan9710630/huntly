package com.huntly.server.connector;

import com.huntly.interfaces.external.model.CapturePage;
import com.huntly.server.util.HttpUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * @author lcomplete
 */
public abstract class InfoConnector {

    protected HttpClient buildHttpClient(ConnectorProperties properties, Integer timeout) {
        return HttpUtils.buildHttpClient(properties.getProxySetting(), timeout);
    }

    public abstract List<CapturePage> fetchNewestPages();

    public abstract List<CapturePage> fetchAllPages();

    public abstract CapturePage fetchPageContent(CapturePage capturePage);
}
