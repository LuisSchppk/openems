package io.openems.edge.controller.ess.omei.hybrid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.StringJoiner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.JUnit4;

import io.openems.edge.controller.ess.hybridess.CSVUtil;
import io.openems.edge.controller.ess.hybridess.CSVUtil.Row;

public class CSVUtilTest {

	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
	
	private static final String FOLDER = "CSVUtils";
	private static final String ENERGY_PREDICTION ="energyPrediction.csv";
	
	@Test
	public void readCSV() throws IOException {
		int energyReq = 100_000;
		File tempDir = tempFolder.newFolder(FOLDER);
		File testCSV = tempFolder.newFile(ENERGY_PREDICTION);
		StringJoiner fieldNames = new StringJoiner(CSVUtil.SEPARATOR);
		StringJoiner sj = new StringJoiner(CSVUtil.SEPARATOR);
		
		fieldNames.add("START").add("END").add("ENERGY");
		sj.add("2022-12-08T12:00").add("2022-12-08T15:00").add(String.valueOf(energyReq));
		Files.writeString(testCSV.toPath(), fieldNames.toString() + System.lineSeparator() + sj.toString(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		LocalDateTime timeStamp = LocalDateTime.of(LocalDate.of(2022, Month.DECEMBER, 8), LocalTime.of(13, 23));
		Row firstRow = CSVUtil.parseEnergyPrediction(testCSV).get(0);
		assertTrue(firstRow.getStart().isBefore(timeStamp));
		assertTrue(firstRow.getEnd().isAfter(timeStamp));
		assertEquals(energyReq, firstRow.getValue());
	}
}
