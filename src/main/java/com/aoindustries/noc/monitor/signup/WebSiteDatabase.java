/*
 * Copyright 2000-2013, 2015, 2016, 2018, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.signup;

import com.aoindustries.dbc.Database;
import com.aoindustries.io.AOPool;
import com.aoindustries.util.PropertiesUtils;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Java interface to the master AO Industries database.
 *
 * @author  AO Industries, Inc.
 */
public class WebSiteDatabase extends Database {

	private static final Logger logger = Logger.getLogger(WebSiteDatabase.class.getName());

	/**
	 * Only one database accessor is made.
	 */
	private static WebSiteDatabase websiteDatabase;

	private static Properties props;

	private static String getProperty(String name) throws IOException {
		if (props == null) props = PropertiesUtils.loadFromResource(WebSiteDatabase.class, "WebSiteDatabase.properties");
		return props.getProperty(name);
	}

	private static String getDatabaseDriver() throws IOException {
		return getProperty("com.aoindustries.website.database.driver");
	}

	private static String getDatabaseURL() throws IOException {
		return getProperty("com.aoindustries.website.database.url");
	}

	private static String getDatabaseUsername() throws IOException {
		return getProperty("com.aoindustries.website.database.username");
	}

	private static String getDatabasePassword() throws IOException {
		return getProperty("com.aoindustries.website.database.password");
	}

	private static int getDatabasePoolSize() throws IOException {
		return Integer.parseInt(getProperty("com.aoindustries.website.database.pool.size"));
	}

	private static long getDatabaseMaxConnectionAge() throws IOException {
		String S=getProperty("com.aoindustries.website.database.max_connection_age");
		return S==null || S.length()==0 ? AOPool.DEFAULT_MAX_CONNECTION_AGE : Long.parseLong(S);
	}

	/**
	 * Make no instances.
	 */
	private WebSiteDatabase() throws IOException {
		super(
			getDatabaseDriver(),
			getDatabaseURL(),
			getDatabaseUsername(),
			getDatabasePassword(),
			getDatabasePoolSize(),
			getDatabaseMaxConnectionAge(),
			logger
		);
	}

	public static WebSiteDatabase getDatabase() throws IOException {
		synchronized(WebSiteDatabase.class) {
			if(websiteDatabase==null) websiteDatabase=new WebSiteDatabase();
			return websiteDatabase;
		}
	}
}
