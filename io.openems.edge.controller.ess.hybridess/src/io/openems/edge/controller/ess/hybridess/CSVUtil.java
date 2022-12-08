package io.openems.edge.controller.ess.hybridess;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class to assist in reading and parsing the CSVFiles containing the
 * energy and power predictions required for {@link HybridController}.
 * Requires CSVFiles in the format of 'start, end, value' with {@code start} and {@code end}
 * being in the ISO-8061 format and {@code value} an integer value. 
 * 
 * @author Luis Schoppik
 *
 */
public class CSVUtil {
	
	public static final CharSequence SEPARATOR =",";
	
	/* 
	 * TODO Make sure this is an adequate implementation. Currently assumes 
	 * small CSVFiles (Day/Week) which are updated during runtime.
	 * Update by overwriting.
	 * 
	 * In case of multiple Files: Pass Path to folder as parameter and select 
	 * Files based on naming scheme. Maybe Date?
	 * 
	 */
	public static List<Row> parseEnergyPrediction(File energyPrediction) {
		List<Row> rows = new LinkedList<Row>();
		try(BufferedReader reader = Files.newBufferedReader(energyPrediction.toPath())) {
			String line = reader.readLine(); // FieldNames;
			while((line=reader.readLine())!=null) {
				rows.add(parseLine(line));
			}
		} catch (IOException e) {
			rows = Collections.emptyList();
		}
		return rows;
	}
	
	private static Row parseLine(String line) {
		String[] entries = line.split(SEPARATOR.toString());
		LocalDateTime start = LocalDateTime.parse(entries[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		LocalDateTime end = LocalDateTime.parse(entries[1], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		int value = Integer.valueOf(entries[2]);
		return new Row(start, end, value);
	}
	
	public static class Row{
		private LocalDateTime start;
		private LocalDateTime end;
		private int value;
		
		private Row(LocalDateTime start, LocalDateTime end, int value) {
			this.start = start;
			this.end = end;
			this.value = value;
		}
		
		public LocalDateTime getStart() {
			return start;
		}
		public void setStart(LocalDateTime start) {
			this.start = start;
		}
		public LocalDateTime getEnd() {
			return end;
		}
		public void setEnd(LocalDateTime end) {
			this.end = end;
		}
		public int getValue() {
			return value;
		} 
		public void setValue(int value) {
			this.value = value;
		}
	}
}
