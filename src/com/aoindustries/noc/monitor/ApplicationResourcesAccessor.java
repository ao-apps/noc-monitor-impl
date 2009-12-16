/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import java.util.Locale;

/**
 * Provides a simplified interface for obtaining localized values from the ApplicationResources.properties files.
 *
 * @version  1.0
 *
 * @author  AO Industries, Inc.
 */
final class ApplicationResourcesAccessor {

    /**
     * Make no instances.
     */
    private ApplicationResourcesAccessor() {
    }

    private static final com.aoindustries.util.i18n.ApplicationResourcesAccessor accessor = new com.aoindustries.util.i18n.ApplicationResourcesAccessor("com.aoindustries.noc.monitor.ApplicationResources");

    public static String getMessage(Locale locale, String key) {
        return accessor.getMessage(locale, key);
    }
    
    public static String getMessage(Locale locale, String key, Object... args) {
        return accessor.getMessage(locale, key, args);
    }
}
