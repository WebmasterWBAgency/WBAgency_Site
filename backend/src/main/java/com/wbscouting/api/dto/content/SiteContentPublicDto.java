package com.wbscouting.api.dto.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SiteContentPublicDto {

    private String sectionKey;
    private Map<String, Object> payload;
    private Map<String, Object> mediaUrls;
    private String lang;
    private Object content;

    public Object getContent() {
        if (content != null) {
            return content;
        }
        if (payload != null) {
            return payload;
        }
        return java.util.Collections.emptyMap();
    }

    public void setContent(Object content) {
        this.content = content;
    }
}
