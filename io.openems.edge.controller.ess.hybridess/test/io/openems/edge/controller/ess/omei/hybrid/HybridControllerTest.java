package io.openems.edge.controller.ess.omei.hybrid;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.StringJoiner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.controller.ess.hybridess.CSVUtil;
import io.openems.edge.controller.ess.hybridess.HybridControllerImpl;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.meter.test.DummySymmetricMeter;

public class HybridControllerTest {
	
	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
	private static final String FOLDER = "CSVUtils";
	private static final String ENERGY_PREDICTION ="energyPrediction.csv";
	private static final String POWER_PREDICTION ="powerPrediction.csv";
	private static File tempDir;

	private static final String CTRL_ID = "ctrl0";
	private static final String REDOX_ID = "ess0";
	private static final String LION_ID ="ess1";
	
	private static int REDOX_MAX_APPARENT_POWER = 100_000;
	private static int LION_MAX_APPARENT_POWER = 276_000;
	
	private static final ChannelAddress REDOX_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(REDOX_ID,
			"SetActivePowerEquals");
	private static final ChannelAddress REDOX_ACTIVE_POWER = new ChannelAddress(REDOX_ID,
			"ActivePower");
	
	private static final ChannelAddress LION_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(LION_ID,
			"SetActivePowerEquals");
	private static final ChannelAddress LION_ACTIVE_POWER = new ChannelAddress(LION_ID,
			"ActivePower");
	
	private static final String METER_ID = "meter0";
	private static final ChannelAddress METER_ACTIVE_POWER = new ChannelAddress(METER_ID, "ActivePower");
	
	private static ManagedSymmetricEssHybrid setupESS(String id, int maxApparentPower) {
		return new DummyHybridEss(id, new DummyPower(maxApparentPower));
	}
	
	private void addPrediction(String start, String end, int value, Path filepath) throws IOException {
		if(!Files.exists(tempDir.toPath()) || !Files.exists(filepath)) {
			fail(String.format("Could not find %s", filepath.toString()));
		}
		
		StringJoiner row = new StringJoiner(CSVUtil.SEPARATOR);
		row.add(start).add(end).add(String.valueOf(value));
		Files.writeString(filepath,String.format("%s%s",row.toString(), System.lineSeparator()), StandardOpenOption.APPEND);
	}
	
	private Path createPredictionFile(String filename) throws IOException {
		tempDir = tempFolder.newFolder(FOLDER);
		File predictionCSV = tempFolder.newFile(filename);
		
		StringJoiner fieldNames = new StringJoiner(CSVUtil.SEPARATOR);
		fieldNames.add("START").add("END").add("VALUE");
		Files.writeString(predictionCSV.toPath(), fieldNames.toString() + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		
		return predictionCSV.toPath();
	}

	@Test
	public void test() throws Exception {
		Path energyPrediction = createPredictionFile(ENERGY_PREDICTION);
		Path powerPrediction = createPredictionFile(POWER_PREDICTION);
		addPrediction("2022-12-08T12:00", "2022-12-08T15:00", 200_000, energyPrediction);
		addPrediction("2022-12-08T09:00","2022-12-08T12:00", 100_000, powerPrediction);
		
		new ControllerTest(new HybridControllerImpl()) //
		.addReference("componentManager", new DummyComponentManager()) //
		.addComponent(setupESS(REDOX_ID, LION_MAX_APPARENT_POWER))
		.addComponent(setupESS(LION_ID, LION_MAX_APPARENT_POWER))//
		.addComponent(new DummySymmetricMeter(METER_ID)) //
		.activate(MyConfig.create() //
				.setId(CTRL_ID) //
				.setRedoxId(REDOX_ID)
				.setLiIonId(LION_ID)//
				.setMeterId(METER_ID)
				.build());
	}

}
