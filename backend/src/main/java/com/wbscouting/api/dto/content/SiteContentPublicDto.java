package com.wbscouting.api.dto.content;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiteContentPublicDto {

    private String sectionKey;
    private Map<String, Object> payload;
    private Map<String, Object> mediaUrls;
    private String lang;
    private Object content;

    public SiteContentPublicDto() {
    }

    public SiteContentPublicDto(String sectionKey, Map<String, Object> payload, Map<String, Object> mediaUrls, String lang, Object content) {
        this.sectionKey = sectionKey;
        this.payload = payload;
        this.mediaUrls = mediaUrls;
        this.lang = lang;
        this.content = content;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSectionKey() {
        return sectionKey;
    }

    public void setSectionKey(String sectionKey) {
        this.sectionKey = sectionKey;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(Map<String, Object> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public Object getContent() {
        if (content != null) {
            return content;
        }
        if (payload != null && payload.containsKey("content")) {
            return payload.get("content");
        }
        if (payload != null) {
            return payload;
        }
        return Collections.emptyMap();
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public static class Builder {
        private String sectionKey;
        private Map<String, Object> payload;
        private Map<String, Object> mediaUrls;
        private String lang;
        private Object content;

        public Builder sectionKey(String sectionKey) {
            this.sectionKey = sectionKey;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder mediaUrls(Map<String, Object> mediaUrls) {
            this.mediaUrls = mediaUrls;
            return this;
        }

        public Builder lang(String lang) {
            this.lang = lang;
            return this;
        }

        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        public SiteContentPublicDto build() {
            return new SiteContentPublicDto(sectionKey, payload, mediaUrls, lang, content);
        }
    }
}
