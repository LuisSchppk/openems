package io.openems.edge.controller.ess.omei.hybrid;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.controller.ess.hybridess.CSVUtil;
import io.openems.edge.controller.ess.hybridess.HybridControllerImpl;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyPower;
import io.openems.edge.meter.api.SymmetricMeter;
import io.openems.edge.meter.test.DummySymmetricMeter;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.StringJoiner;

import static org.junit.Assert.fail;

public class HybridControllerTest {

	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
	private static final String FOLDER = "CSVUtils";
	private Path energyPrediction;
	private Path powerPrediction;
	private static File tempDir;

	private TimeLeapClock clock;

	private static final String CTRL_ID = "ctrl0";
	private static final String REDOX_ID = "ess0";
	private static final String LION_ID ="ess1";
	
	private static final int REDOX_MAX_APPARENT_POWER = 100_000;
	private static final int LION_MAX_APPARENT_POWER = 276_000;

	private static final ChannelAddress REDOX_SOC = new ChannelAddress(REDOX_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress LION_SOC = new ChannelAddress(LION_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress REDOX_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(REDOX_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress LION_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(LION_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(REDOX_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(REDOX_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress REDOX_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(REDOX_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress REDOX_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(REDOX_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(LION_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(LION_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress LION_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(LION_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress LION_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(LION_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress REDOX_CAPACITY = new ChannelAddress(REDOX_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress LION_CAPACITY = new ChannelAddress(LION_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress REDOX_ACTIVE_POWER = new ChannelAddress(REDOX_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	private static final ChannelAddress LION_ACTIVE_POWER = new ChannelAddress(LION_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	
	private static final String METER_ID = "meter0";
	private static final ChannelAddress METER_ACTIVE_POWER = new ChannelAddress(METER_ID, SymmetricMeter.ChannelId.ACTIVE_POWER.id());

	private static final String SUM_ID = "_sum";
	private static final ChannelAddress PRODUCTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.PRODUCTION_ACTIVE_POWER.id());;
	
	private static final int MAX_GRID_POWER = 200_000; // W
	private static final int DEFAULT_MIN_ENERGY = 100_000; // Wh

	@Test
	public void chargeSplit() throws Exception {

		// Always below min to always use maxGridPower
		ControllerTest controllerTest = createControllerTest();


		controllerTest.next(new TestCase() // Both in red
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 5) // Both start in red area 0,05 * 400_000Wh = 20_000 <= 50_000
						.input(LION_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -20_000) // targetPower outside limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, -20_000) // power should be limited by filterPower
						.output(LION_SET_ACTIVE_POWER_EQUALS, -180_000)
						)
				.next(new TestCase() // Both in red
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 5) // Both start in red area 0,05 * 400_000Wh = 20_000 <= 50_000
						.input(LION_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, (int)(-0.5*MAX_GRID_POWER)) // power should be split equally.
						.output(LION_SET_ACTIVE_POWER_EQUALS, (int)(-0.5*MAX_GRID_POWER)))
				.next(new TestCase() // redox orange, liIOn red
						.input(METER_ACTIVE_POWER,0)
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 20) // redox orange: 0.3 of chargePower
						.input(LION_SOC, 5) // liOn red: 0.7 of chargePower
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, (int)(-0.3*MAX_GRID_POWER))
						.output(LION_SET_ACTIVE_POWER_EQUALS, (int)(-0.7*MAX_GRID_POWER)))
				.next(new TestCase() // redox green, liIon red
						.input(METER_ACTIVE_POWER,0)
						.input(PRODUCTION_POWER, MAX_GRID_POWER) // minEnergy satisfied -> no more power from grid.
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 80) // redox orange: 0.3 of chargePower
						.input(LION_SOC, 5) // liOn red: 0.7 of chargePower
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, 0)
						.output(LION_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER));
	}

	@Test
	public void chargePowerPrediction() throws Exception {
		int predictedPower = -100_000;
		ControllerTest controllerTest = createControllerTest();
		addPrediction("2022-12-08T10:00","2022-12-08T12:00", predictedPower, powerPrediction);

		controllerTest.next(new TestCase() // both in green -> minEnergy satisfied.
						.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
				.input(METER_ACTIVE_POWER,0) // Set consumption to 0
				.input(REDOX_CAPACITY, 400_000)
				.input(LION_CAPACITY, 276_000)
				.input(REDOX_SOC, 70) // Green SoC area
				.input(LION_SOC, 70)  // Green SoC area
				.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
				.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(REDOX_SET_ACTIVE_POWER_EQUALS, predictedPower / 2)
				.output(LION_SET_ACTIVE_POWER_EQUALS, predictedPower / 2)
		);
	}

	@Test
	public void chargeEnergyPrediction() throws Exception {
		int predictedEnergy = 500_000;
		ControllerTest controllerTest = createControllerTest();
		addPrediction("2022-12-08T10:00","2022-12-08T12:00", predictedEnergy, energyPrediction);
		controllerTest.next(new TestCase() // Below energy minimum set by prediction.
						.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 70) // Green SoC area 280_000Wh
						.input(LION_SOC, 70)  // Green SoC area 193_200Wh
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2)
						.output(LION_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2))
				.next(new TestCase() // Above energy minimum set by prediction.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 100) // Green SoC area 400_000Wh
						.input(LION_SOC, 70)  // Green SoC area 193_200Wh
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, 0)
						.output(LION_SET_ACTIVE_POWER_EQUALS, 0));
	}

	@Test
	public void chargeUsesProduction() throws Exception {
		int productionPower = 400_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase()
				.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
				.input(METER_ACTIVE_POWER,0) // Set consumption to 0
				.input(PRODUCTION_POWER, productionPower)
				.input(REDOX_CAPACITY, 400_000)
				.input(LION_CAPACITY, 276_000)
				.input(REDOX_SOC, 70)
				.input(LION_SOC, 70)
				.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
				.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(REDOX_SET_ACTIVE_POWER_EQUALS, -productionPower / 2)
				.output(LION_SET_ACTIVE_POWER_EQUALS, -productionPower / 2));
	}

	@Test
	public void chargeMinEnergy() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // Below energy minimum set by prediction.
						.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 10) // 40_000Wh
						.input(LION_SOC, 10)  // 27_600Wh
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2)
						.output(LION_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2))
				.next(new TestCase() // Above energy minimum set by prediction.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(REDOX_CAPACITY, 400_000)
						.input(LION_CAPACITY, 276_000)
						.input(REDOX_SOC, 20) // 80_000Wh
						.input(LION_SOC, 10)  // 27_600Wh | TODO Still in Red area -> needs to be considered!
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(REDOX_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(LION_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(REDOX_SET_ACTIVE_POWER_EQUALS, 0)
						.output(LION_SET_ACTIVE_POWER_EQUALS, 0));
	}

	private ControllerTest createControllerTest() throws Exception {
		return createControllerTest(DEFAULT_MIN_ENERGY);
	}
	private ControllerTest createControllerTest(int defaultMinimumGridPower) throws Exception {
		LocalDateTime begin = LocalDateTime.parse("2022-12-08T09:00", DateTimeFormatter.ISO_DATE_TIME);
		clock = new TimeLeapClock(Instant.ofEpochSecond(begin.toEpochSecond(ZoneOffset.UTC)), ZoneOffset.UTC);

		energyPrediction = createPredictionFile("energyPrediction.csv");
		powerPrediction = createPredictionFile("powerPrediction.csv");

		return new ControllerTest(new HybridControllerImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock))
				.addReference("sum", new DummySum())
				.addComponent(setupESS(REDOX_ID, REDOX_MAX_APPARENT_POWER))
				.addComponent(setupESS(LION_ID, LION_MAX_APPARENT_POWER))//
				.addComponent(new DummySymmetricMeter(METER_ID)) //
				.activate(MyConfig.create()
						.setId(CTRL_ID)
						.setRedoxId(REDOX_ID)
						.setLiIonId(LION_ID)//
						.setMeterId(METER_ID)
						.setEnergyPrediction(energyPrediction.toString())
						.setPowerPrediction(powerPrediction.toString())
						.setMaxGridPower(MAX_GRID_POWER)
						.setDefaultMinimumEnergy(defaultMinimumGridPower)
						.build());
	}

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
		try {
			tempDir = tempFolder.newFolder(FOLDER);
		} catch (IOException e) {
			if(!Files.exists(tempDir.toPath())) {
				throw e;
			}
			// Else Folder already exists -> ignore exception.
		}

		File predictionCSV = tempFolder.newFile(filename);

		StringJoiner fieldNames = new StringJoiner(CSVUtil.SEPARATOR);
		fieldNames.add("START").add("END").add("VALUE");
		Files.writeString(predictionCSV.toPath(), fieldNames + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		return predictionCSV.toPath();
	}

}
