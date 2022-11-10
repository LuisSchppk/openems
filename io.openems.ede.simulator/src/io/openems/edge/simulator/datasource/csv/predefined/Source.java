package io.openems.edge.simulator.datasource.csv.predefined;

public enum Source {
	ZERO("zero.csv"), //
	OMEI_DATA_GB_SOLARPARK_MUEHLAECKER("omei-data-gb-solarpark-muehlaecker.csv"), //
	H0_HOUSEHOLD_SUMMER_WEEKDAY_STANDARD_LOAD_PROFILE("h0-summer-weekday-standard-load-profile.csv"), //
	H0_HOUSEHOLD_SUMMER_WEEKDAY_PV_PRODUCTION("h0-summer-weekday-pv-production.csv"), //
	H0_HOUSEHOLD_SUMMER_WEEKDAY_NON_REGULATED_CONSUMPTION("h0-summer-weekday-non-regulated-consumption.csv"), //
	H0_HOUSEHOLD_SUMMER_WEEKDAY_PV_PRODUCTION2("h0-summer-weekday-pv-production2.csv");

	public final String filename;

	private Source(String filename) {
		this.filename = filename;
	}
}
