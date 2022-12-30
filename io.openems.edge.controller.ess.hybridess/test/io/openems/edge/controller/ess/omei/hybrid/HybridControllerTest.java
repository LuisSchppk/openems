package io.openems.edge.controller.ess.omei.hybrid;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.controller.ess.hybridess.prediction.PredictionCSV;
import io.openems.edge.controller.ess.hybridess.controller.HybridControllerImpl;
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
	private static final String MAIN_ID = "ess0";
	private static final String SUPPORT_ID ="ess1";
	
	private static final int MAIN_MAX_APPARENT_POWER = 100_000;
	private static final int SUPPORT_MAX_APPARENT_POWER = 276_000;

	private static final ChannelAddress MAIN_SOC = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress SUPPORT_SOC = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.SOC.id());
	private static final ChannelAddress MAIN_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(MAIN_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress SUPPORT_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(SUPPORT_ID,
			ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id());

	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(MAIN_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress MAIN_MAX_APPARENT_POWER_CHANNEL = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.MAX_APPARENT_POWER.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_CHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.UPPER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());
	private static final ChannelAddress SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT = new ChannelAddress(SUPPORT_ID, ManagedSymmetricEssHybrid.ChannelId.LOWER_POSSIBLE_DISCHARGE_POWER_LIMIT.id());

	private static final ChannelAddress MAIN_CAPACITY = new ChannelAddress(MAIN_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress SUPPORT_CAPACITY = new ChannelAddress(SUPPORT_ID, SymmetricEss.ChannelId.CAPACITY.id());
	private static final ChannelAddress MAIN_ACTIVE_POWER = new ChannelAddress(MAIN_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	private static final ChannelAddress SUPPORT_ACTIVE_POWER = new ChannelAddress(SUPPORT_ID,
			SymmetricEss.ChannelId.ACTIVE_POWER.id());
	
	private static final String METER_ID = "meter0";
	private static final ChannelAddress METER_ACTIVE_POWER = new ChannelAddress(METER_ID, SymmetricMeter.ChannelId.ACTIVE_POWER.id());

	private static final String SUM_ID = "_sum";
	private static final ChannelAddress PRODUCTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.PRODUCTION_ACTIVE_POWER.id());
	private static final ChannelAddress CONSUMPTION_POWER = new ChannelAddress(SUM_ID, Sum.ChannelId.CONSUMPTION_ACTIVE_POWER.id());
	
	private static final int MAX_GRID_POWER = 200_000; // W
	private static final int DEFAULT_MIN_ENERGY = 100_000; // Wh
	
	@Test
	public void chargeSplit() throws Exception {

		// Always below min to always use maxGridPower
		ControllerTest controllerTest = createControllerTest();


		controllerTest.next(new TestCase() // Both in red
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5) // Both start in red area 0,05 * 400_000Wh = 20_000 <= 50_000
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -20_000) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -20_000) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -180_000)
						)
				.next(new TestCase() // Both in red
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5) // Both start in red area 0,05 * 400_000Wh = 20_000 <= 50_000
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(-0.5*MAX_GRID_POWER)) // power should be split equally.
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(-0.5*MAX_GRID_POWER)))
				.next(new TestCase() // MAIN orange, support red
						.input(METER_ACTIVE_POWER,0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 20) // MAIN orange: 0.3 of chargePower
						.input(SUPPORT_SOC, 5) // SUPPORT red: 0.7 of chargePower
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(-0.3*MAX_GRID_POWER))
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(-0.7*MAX_GRID_POWER)))
				.next(new TestCase() // MAIN green, support red
						.input(METER_ACTIVE_POWER,0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 80) // MAIN orange: 0.3 of chargePower
						.input(SUPPORT_SOC, 5) // SUPPORT red: 0.7 of chargePower
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER));
	}

	@Test
	public void chargePowerPrediction() throws Exception {
		int predictedPower = -100_000;
		ControllerTest controllerTest = createControllerTest();
		addPrediction("2022-12-08T10:00","2022-12-08T12:00", predictedPower, powerPrediction);

		controllerTest.next(new TestCase() // both in green -> minEnergy satisfied.
						.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
				.input(METER_ACTIVE_POWER,0) // Set consumption to 0
				.input(MAIN_CAPACITY, 400_000)
				.input(SUPPORT_CAPACITY, 276_000)
				.input(MAIN_SOC, 70) // Green SoC area
				.input(SUPPORT_SOC, 70)  // Green SoC area
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(MAIN_SET_ACTIVE_POWER_EQUALS, predictedPower / 2)
				.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, predictedPower / 2)
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
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 70) // Green SoC area 280_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2))
				.next(new TestCase() // Above energy minimum set by prediction.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 100) // Green SoC area 400_000Wh
						.input(SUPPORT_SOC, 70)  // Green SoC area 193_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0));
	}

	@Test
	public void chargeUsesProduction() throws Exception {
		int productionPower = 400_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase()
				.timeleap(clock,1, ChronoUnit.HOURS) // Advance to time window with prediction.
				.input(METER_ACTIVE_POWER,0) // Set consumption to 0
				.input(PRODUCTION_POWER, productionPower)
				.input(MAIN_CAPACITY, 400_000)
				.input(SUPPORT_CAPACITY, 276_000)
				.input(MAIN_SOC, 70)
				.input(SUPPORT_SOC, 70)
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
				.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
				.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
				.output(MAIN_SET_ACTIVE_POWER_EQUALS, -productionPower / 2)
				.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -productionPower / 2));
	}

	@Test
	public void chargeMinEnergy() throws Exception {
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // Below energy minimum.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 10) // 40_000Wh
						.input(SUPPORT_SOC, 10)  // 27_600Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, -MAX_GRID_POWER / 2))
				.next(new TestCase() // Above energy minimum.
						.input(METER_ACTIVE_POWER,0) // Set consumption to 0
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 20) // 80_000Wh
						.input(SUPPORT_SOC, 20)  // 55_200Wh
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000) // targetPower within limit
						.input(MAIN_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -300_000)
						.input(SUPPORT_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0));
	}

	@Test
	public void dischargeSplit() throws Exception {
		int required_power  = 200_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase() // Both Red
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5)
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 20_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 20_000) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 180_000))
				.next(new TestCase() // Both Red
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5)
						.input(SUPPORT_SOC, 5)  // 0,05 * 276_000 = 13800
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, required_power/2) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, required_power/2)
				)
				.next(new TestCase() // main red, support orange
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5)
						.input(SUPPORT_SOC, 20)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.2)) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.8))
				)
				.next(new TestCase() // main red, support green
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5)
						.input(SUPPORT_SOC, 80)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0) // targetPower outside limit
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, (int)(required_power*0.0)) // power should be limited by filterPower
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, (int)(required_power*1.0))
				);
	}

	@Test
	public void dischargeNetLoad() throws Exception {
		int required_power  = 60_000;
		ControllerTest controllerTest = createControllerTest();
		controllerTest.next(new TestCase()
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 70)
						.input(SUPPORT_SOC, 70)
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, required_power)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, 0))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, 2*required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 70)
						.input(SUPPORT_SOC, 70)
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, required_power)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, required_power))
				.next(new TestCase()
						.input(CONSUMPTION_POWER, required_power)
						.input(METER_ACTIVE_POWER, 0)
						.input(MAIN_CAPACITY, 400_000)
						.input(SUPPORT_CAPACITY, 276_000)
						.input(MAIN_SOC, 5)
						.input(SUPPORT_SOC, 70)
						.input(MAIN_MAX_APPARENT_POWER_CHANNEL, MAIN_MAX_APPARENT_POWER)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(MAIN_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_LOWER_LIMIT, 0)
						.input(SUPPORT_GET_POSSIBLE_DISCHARGE_POWER_UPPER_LIMIT, 300_000)
						.output(MAIN_SET_ACTIVE_POWER_EQUALS, 0)
						.output(SUPPORT_SET_ACTIVE_POWER_EQUALS, required_power));
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
				.addComponent(setupESS(MAIN_ID, MAIN_MAX_APPARENT_POWER))
				.addComponent(setupESS(SUPPORT_ID, SUPPORT_MAX_APPARENT_POWER))//
				.addComponent(new DummySymmetricMeter(METER_ID)) //
				.activate(MyConfig.create()
						.setId(CTRL_ID)
						.setMainId(MAIN_ID)
						.setSupportId(SUPPORT_ID)//
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

		StringJoiner row = new StringJoiner(PredictionCSV.SEPARATOR);
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

		StringJoiner fieldNames = new StringJoiner(PredictionCSV.SEPARATOR);
		fieldNames.add("START").add("END").add("VALUE");
		Files.writeString(predictionCSV.toPath(), fieldNames + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		return predictionCSV.toPath();
	}

}
