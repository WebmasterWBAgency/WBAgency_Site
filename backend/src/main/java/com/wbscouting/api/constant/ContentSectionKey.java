package com.wbscouting.api.constant;

import java.util.Set;

public final class ContentSectionKey {

    private ContentSectionKey() {}

    public static final String HOME_HERO = "HOME_HERO";
    public static final String ABOUT_US = "ABOUT_US";
    public static final String ABOUT_MANIFESTO = "ABOUT_MANIFESTO";
    public static final String CONTACT_INFO = "CONTACT_INFO";
    public static final String SOCIAL_LINKS = "SOCIAL_LINKS";
    public static final String TERMS_PRIVACY = "TERMS_PRIVACY";
    public static final String TERMS_OF_USE = "TERMS_OF_USE";
    public static final String PRIVACY_POLICY = "PRIVACY_POLICY";
    public static final String TERMS = "TERMS";
    public static final String PRIVACY = "PRIVACY";
    public static final String APPLY_HOW_IT_WORKS = "APPLY_HOW_IT_WORKS";

    public static final Set<String> CANONICAL_KEYS = Set.of(
            HOME_HERO,
            ABOUT_US,
            ABOUT_MANIFESTO,
            CONTACT_INFO,
            SOCIAL_LINKS,
            TERMS_PRIVACY,
            TERMS_OF_USE,
            PRIVACY_POLICY,
            TERMS,
            PRIVACY,
            APPLY_HOW_IT_WORKS
    );
}
