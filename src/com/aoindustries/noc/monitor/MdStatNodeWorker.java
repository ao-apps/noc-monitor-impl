/*
 * Copyright 2008-2013, 2014, 2016 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.util.StringUtility;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workers for watching /proc/mdstat.
 *
 * @author  AO Industries, Inc.
 */
class MdStatNodeWorker extends SingleResultNodeWorker {

	private enum RaidLevel {
		LINEAR,
		RAID1,
		RAID5,
		RAID6
	}

	/**
	 * One unique worker is made per persistence file (and should match the aoServer exactly)
	 */
	private static final Map<String, MdStatNodeWorker> workerCache = new HashMap<>();
	static MdStatNodeWorker getWorker(File persistenceFile, AOServer aoServer) throws IOException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			MdStatNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new MdStatNodeWorker(persistenceFile, aoServer);
				workerCache.put(path, worker);
			} else {
				if(!worker.aoServer.equals(aoServer)) throw new AssertionError("worker.aoServer!=aoServer: "+worker.aoServer+"!="+aoServer);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private AOServer aoServer;

	MdStatNodeWorker(File persistenceFile, AOServer aoServer) {
		super(persistenceFile);
		this.aoServer = aoServer;
	}

	@Override
	protected String getReport() throws IOException, SQLException {
		return aoServer.getMdStatReport();
	}

	/**
	 * Determines the alert level and message for the provided result.
	 *
	 * raid1:
	 *      one up + zero down: medium
	 *      zero down: none
	 *      three+ up: low
	 *      two up...: medium
	 *      one up...: high
	 *      zero up..: critical
	 * raid5:
	 *      one down.: high
	 *      two+ down: critical
	 * raid6:
	 *      one down.: medium
	 *      two down.: high
	 *      two+ down: critical
	 */
	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, SingleResult result) {
		if(result.getError()!=null) {
			return new AlertLevelAndMessage(
				AlertLevel.CRITICAL,
				accessor.getMessage(
					//locale,
					"MdStatNode.alertMessage.error",
					result.getError()
				)
			);
		}
		String report = result.getReport();
		List<String> lines = StringUtility.splitLines(report);
		RaidLevel lastRaidLevel = null;
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		String highestAlertMessage = "";
		for(String line : lines) {
			if(
				!line.startsWith("Personalities :")
				&& !line.startsWith("unused devices:")
				&& !(
					line.startsWith("      bitmap: ")
					&& line.endsWith(" chunk")
				)
			) {
				if(line.indexOf(':')!=-1) {
					// Must contain raid type
					if(line.contains(" linear ")) lastRaidLevel = RaidLevel.LINEAR;
					else if(line.contains(" raid1 ")) lastRaidLevel = RaidLevel.RAID1;
					else if(line.contains(" raid5 ")) lastRaidLevel = RaidLevel.RAID5;
					else if(line.contains(" raid6 ")) lastRaidLevel = RaidLevel.RAID6;
					else {
						return new AlertLevelAndMessage(
							AlertLevel.CRITICAL,
							accessor.getMessage(
								//locale,
								"MdStatNode.alertMessage.noRaidType",
								line
							)
						);
					}
				} else {
					// Resync is low priority
					if(
						(
							line.contains("resync")
							|| line.contains("recovery")
						)
						&& line.contains("finish")
						&& line.contains("speed")
					) {
						if(AlertLevel.LOW.compareTo(highestAlertLevel)>0) {
							highestAlertLevel = AlertLevel.LOW;
							highestAlertMessage = accessor.getMessage(
								//locale,
								"MdStatNode.alertMessage.resync",
								line.trim()
							);
						}
					} else {
						int pos1 = line.indexOf('[');
						if(pos1!=-1) {
							int pos2=line.indexOf(']', pos1+1);
							if(pos2!=-1) {
								pos1 = line.indexOf('[', pos2+1);
								if(pos1!=-1) {
									pos2=line.indexOf(']', pos1+1);
									if(pos2!=-1) {
										// Count the down and up between the brackets
										int upCount = 0;
										int downCount = 0;
										for(int pos=pos1+1;pos<pos2;pos++) {
											char ch = line.charAt(pos);
											if(ch=='U') upCount++;
											else if(ch=='_') downCount++;
											else {
												return new AlertLevelAndMessage(
													AlertLevel.CRITICAL,
													accessor.getMessage(
														//locale,
														"MdStatNode.alertMessage.invalidCharacter",
														ch
													)
												);
											}
										}
										// Get the current alert level
										final AlertLevel alertLevel;
										final String alertMessage;
										if(lastRaidLevel==RaidLevel.RAID1) {
											if(upCount==1 && downCount==0) {
												// xen917-4.fc.aoindustries.com has a bad drive we don't fix, this is normal for it
												/*if(aoServer.getHostname().toString().equals("xen917-4.fc.aoindustries.com")) alertLevel = AlertLevel.NONE;
												else*/ alertLevel = AlertLevel.MEDIUM;
											}
											else if(downCount==0) alertLevel = AlertLevel.NONE;
											else if(upCount>=3) alertLevel = AlertLevel.LOW;
											else if(upCount==2) alertLevel = AlertLevel.MEDIUM;
											else if(upCount==1) alertLevel = AlertLevel.HIGH;
											else if(upCount==0) alertLevel = AlertLevel.CRITICAL;
											else throw new AssertionError("upCount should have already matched");
											alertMessage = accessor.getMessage(
												//locale,
												"MdStatNode.alertMessage.raid1",
												upCount,
												downCount
											);
										} else if(lastRaidLevel==RaidLevel.RAID5) {
											if(downCount==0) alertLevel = AlertLevel.NONE;
											else if(downCount==1) alertLevel = AlertLevel.HIGH;
											else if(downCount>=2) alertLevel = AlertLevel.CRITICAL;
											else throw new AssertionError("downCount should have already matched");
											alertMessage = accessor.getMessage(
												//locale,
												"MdStatNode.alertMessage.raid5",
												upCount,
												downCount
											);
										} else if(lastRaidLevel==RaidLevel.RAID6) {
											if(downCount==0) alertLevel = AlertLevel.NONE;
											else if(downCount==1) alertLevel = AlertLevel.MEDIUM;
											else if(downCount==2) alertLevel = AlertLevel.HIGH;
											else if(downCount>=3) alertLevel = AlertLevel.CRITICAL;
											else throw new AssertionError("downCount should have already matched");
											alertMessage = accessor.getMessage(
												//locale,
												"MdStatNode.alertMessage.raid6",
												upCount,
												downCount
											);
										} else {
											return new AlertLevelAndMessage(
												AlertLevel.CRITICAL,
												accessor.getMessage(
													//locale,
													"MdStatNode.alertMessage.unexpectedRaidLevel",
													lastRaidLevel
												)
											);
										}
										if(alertLevel.compareTo(highestAlertLevel)>0) {
											highestAlertLevel = alertLevel;
											highestAlertMessage = alertMessage;
										}
									} else {
										return new AlertLevelAndMessage(
											AlertLevel.CRITICAL,
											accessor.getMessage(
												//locale,
												"MdStatNode.alertMessage.unableToFindCharacter",
												']',
												line
											)
										);
									}
								} else {
									return new AlertLevelAndMessage(
										AlertLevel.CRITICAL,
										accessor.getMessage(
											//locale,
											"MdStatNode.alertMessage.unableToFindCharacter",
											'[',
											line
										)
									);
								}
							} else {
								return new AlertLevelAndMessage(
									AlertLevel.CRITICAL,
									accessor.getMessage(
										//locale,
										"MdStatNode.alertMessage.unableToFindCharacter",
										']',
										line
									)
								);
							}
						}
					}
				}
			}
		}
		return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
	}
}
