package com.company.messenger.domain.profile;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Upload limits may be tightened by the intranet operator without rebuilding the application. */
@ConfigurationProperties(prefix = "app.profile")
public record ProfileImageProperties(@DefaultValue("5242880") long imageMaxBytes,
                                     @DefaultValue("4096") int imageMaxDimension) { }
