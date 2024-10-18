package com.huntly.server.service;

import com.huntly.common.exceptions.NoSuchDataException;
import com.huntly.interfaces.external.dto.PreviewFeedsInfo;
import com.huntly.interfaces.external.model.FeedsSetting;
import com.huntly.server.config.HuntlyProperties;
import com.huntly.server.connector.ConnectorType;
import com.huntly.server.connector.rss.FeedUtils;
import com.huntly.server.domain.entity.Connector;
import com.huntly.server.repository.ConnectorRepository;
import com.huntly.server.repository.PageRepository;
import com.huntly.server.util.HttpUtils;
import com.huntly.server.util.SiteUtils;
import com.rometools.rome.feed.synd.SyndFeed;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * @author lcomplete
 */
@Service
public class FeedsService {
    private final HuntlyProperties huntlyProperties;

    private final ConnectorService connectorService;

    private final ConnectorFetchService connectorFetchService;

    private final ConnectorRepository connectorRepository;

    private final GlobalSettingService globalSettingService;

    private final PageRepository pageRepository;

    public FeedsService(HuntlyProperties huntlyProperties, ConnectorService connectorService, ConnectorFetchService connectorFetchService, ConnectorRepository connectorRepository, GlobalSettingService globalSettingService, PageRepository pageRepository) {
        this.huntlyProperties = huntlyProperties;
        this.connectorService = connectorService;
        this.connectorFetchService = connectorFetchService;
        this.connectorRepository = connectorRepository;
        this.globalSettingService = globalSettingService;
        this.pageRepository = pageRepository;
    }

    public Connector followFeed(String subscribeUrl) {
        PreviewFeedsInfo feedsInfo = previewFeeds(subscribeUrl);
        Connector connector = connectorService.getBySubscribeUrl(subscribeUrl, ConnectorType.RSS);
        boolean isNew = false;
        if (connector != null) {
            connector.setName(feedsInfo.getTitle());
            connector.setIconUrl(feedsInfo.getSiteFaviconUrl());
        } else {
            connector = new Connector();
            connector.setSubscribeUrl(subscribeUrl);
            connector.setType(ConnectorType.RSS.getCode());
            connector.setCrawlFullContent(false);
            connector.setEnabled(true);
            connector.setDisplaySequence(1);
            connector.setName(feedsInfo.getTitle());
            connector.setInboxCount(0);
            connector.setIconUrl(feedsInfo.getSiteFaviconUrl());
            connector.setCreatedAt(Instant.now());
            isNew = true;
        }
        connector = connectorRepository.save(connector);
        if (isNew) {
            connectorFetchService.fetchPagesImmediately(connector.getId());
        }
        return connector;
    }

    public PreviewFeedsInfo previewFeeds(String subscribeUrl) {
        PreviewFeedsInfo feedsInfo = new PreviewFeedsInfo();
        feedsInfo.setFeedUrl(subscribeUrl);
        var proxySetting = globalSettingService.getProxySetting();
        var httpClient = HttpUtils.buildHttpClient(proxySetting, huntlyProperties.getFeedFetchTimeoutSeconds());
        var feedClient = HttpUtils.buildFeedOkHttpClient(proxySetting, huntlyProperties.getFeedFetchTimeoutSeconds());
        SyndFeed syndFeed = FeedUtils.parseFeedUrl(subscribeUrl, feedClient);
        if (syndFeed != null) {
            feedsInfo.setSiteLink(syndFeed.getLink());
            feedsInfo.setTitle(syndFeed.getTitle());
            feedsInfo.setDescription(syndFeed.getDescription());
        }
        Connector connector = connectorService.getBySubscribeUrl(subscribeUrl, ConnectorType.RSS);
        if (connector != null) {
            feedsInfo.setSubscribed(true);
            feedsInfo.setTitle(connector.getName());
        }
        if (StringUtils.isBlank(feedsInfo.getSiteFaviconUrl())) {
            SiteUtils.Favicon favicon = SiteUtils.getFaviconFromHome(feedsInfo.getSiteLink(), httpClient);
            if (favicon != null) {
                feedsInfo.setSiteFaviconUrl(favicon.getIconUrl());
            }
        }
        return feedsInfo;
    }

    private Connector requireOneFeedConnector(Integer connectorId) {
        Connector connector = connectorRepository.findById(connectorId).orElse(null);
        if (connector == null || !ConnectorType.RSS.getCode().equals(connector.getType())) {
            throw new NoSuchDataException("connector not found by id: " + connectorId);
        }
        return connector;
    }

    public Connector updateFeedsSetting(FeedsSetting feedsSetting) {
        Connector connector = requireOneFeedConnector(feedsSetting.getConnectorId());
        Integer rawFolderId = connector.getFolderId();
        connector.setCrawlFullContent(feedsSetting.getCrawlFullContent());
        connector.setName(feedsSetting.getName());
        connector.setEnabled(feedsSetting.getEnabled());
        connector.setSubscribeUrl(feedsSetting.getSubscribeUrl());
        connector.setFolderId(feedsSetting.getFolderId() == null || feedsSetting.getFolderId().equals(0) ? null : feedsSetting.getFolderId());
        connector.setFetchIntervalSeconds(feedsSetting.getFetchIntervalMinutes() * 60);
        var result = connectorRepository.save(connector);
        if (!Objects.equals(rawFolderId, connector.getFolderId())) {
            pageRepository.updateFolderIdByConnectorId(connector.getId(), connector.getFolderId());
        }
        return result;
    }

    public void delete(Integer connectorId) {
        Connector connector = requireOneFeedConnector(connectorId);
        connectorRepository.delete(connector);
        pageRepository.deleteConnectorId(connectorId);
    }

    public FeedsSetting getFeedsSetting(Integer connectorId) {
        Connector connector = requireOneFeedConnector(connectorId);
        FeedsSetting feedsSetting = new FeedsSetting();
        feedsSetting.setConnectorId(connector.getId());
        feedsSetting.setCrawlFullContent(connector.getCrawlFullContent());
        feedsSetting.setName(connector.getName());
        feedsSetting.setEnabled(connector.getEnabled());
        feedsSetting.setFolderId(connector.getFolderId());
        feedsSetting.setSubscribeUrl(connector.getSubscribeUrl());
        int fetchIntervalMinutes = huntlyProperties.getDefaultFeedFetchIntervalSeconds() / 60;
        if (connector.getFetchIntervalSeconds() != null) {
            fetchIntervalMinutes = connector.getFetchIntervalSeconds() / 60;
        }
        feedsSetting.setFetchIntervalMinutes(fetchIntervalMinutes);
        return feedsSetting;
    }
}
