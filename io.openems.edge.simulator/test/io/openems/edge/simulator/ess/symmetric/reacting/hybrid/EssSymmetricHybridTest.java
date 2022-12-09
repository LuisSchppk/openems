package io.openems.edge.simulator.ess.symmetric.reacting.hybrid;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.TimeLeapClock;
import io.openems.edge.ess.test.DummyPower;


public class EssSymmetricHybridTest {
	
	private static final String ESS_ID = "ess0";
	private static final int CAPACITY = 400_000;
	private static final int MAX_APPARENT_POWER = 100_000;
	private static final int SOC = 50;
	private static final int RAMP_RATE = 40_000;
	private static final int RESPONSE_TIME = 2000;
	private static final int CHARGE_POWER = -100_000;
	private static final int DISCHARGE_POWER = 100_000;

	private static final ChannelAddress ESS_SOC = new ChannelAddress(ESS_ID, "Soc");
	private static final ChannelAddress ESS_SET_ACTIVE_POWER_EQUALS = new ChannelAddress(ESS_ID,
			"SetActivePowerEquals");
	
	private static final ChannelAddress ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT = new ChannelAddress(ESS_ID, "PossibleChargePowerUpperLimit");
	private static final ChannelAddress ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT = new ChannelAddress(ESS_ID, "PossibleChargePowerLowerLimit");
	
	final TimeLeapClock clock = new TimeLeapClock(Instant.now(), ZoneOffset.UTC);
	
	
	private ManagedSymmetricEssHybridTest setup(String ess_id, int capacity, int maxApparentPower, int SoC, GridMode gridMode,
			int rampRate, int responseTime, int chargePower, int dischargePower) throws OpenemsException, Exception {
		return new ManagedSymmetricEssHybridTest(new EssSymmetricHybrid()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("power", new DummyPower()) //
				.activate(MyConfig.create() //
						.setId(ess_id) //
						.setCapacity(capacity) //
						.setMaxApparentPower(maxApparentPower) //
						.setInitialSoc(SoC) //
						.setGridMode(gridMode) //
						.setRampRate(rampRate)
						.setResponseTime(responseTime)
						.setAllowedChargePower(chargePower)
						.setAllowedDischargePower(dischargePower)
						.build());
	}
	
	private ManagedSymmetricEssHybridTest setup() throws OpenemsException, Exception {
		return this.setup(ESS_ID, CAPACITY, MAX_APPARENT_POWER, SOC, GridMode.ON_GRID, RAMP_RATE, RESPONSE_TIME, CHARGE_POWER, DISCHARGE_POWER);
	}
	
	/*
	 * Test if logic for no response time works.
	 * ESS should be able to charge right away. 
	 */
	@Test
	public void noResponseTime() throws OpenemsException, Exception {
		ManagedSymmetricEssHybridTest testEss = setup(ESS_ID,
				CAPACITY,
				MAX_APPARENT_POWER,
				SOC, GridMode.ON_GRID,
				RAMP_RATE,
				0, // RESPONSE TIME = 15min
				CHARGE_POWER,
				DISCHARGE_POWER);
		testEss.next(new TestCase()
				.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -RAMP_RATE)
				.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0));
	}
	
	@Test
	public void responseTime() throws OpenemsException, Exception {
		
		ManagedSymmetricEssHybridTest testEss = setup(ESS_ID,
				CAPACITY,
				MAX_APPARENT_POWER,
				SOC, GridMode.ON_GRID,
				RAMP_RATE,
				1000*60*15, // RESPONSE TIME = 15min
				CHARGE_POWER,
				DISCHARGE_POWER);
		testEss.next(new TestCase() // Response time not elapsed.
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0)
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, 0))
				.next(new TestCase() // Response time elapsed
					.timeleap(clock, 15, ChronoUnit.MINUTES) // wait for response time to elapse.
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_LOWER_LIMIT, -RAMP_RATE)
					.output(ESS_GET_POSSIBLE_CHARGE_POWER_UPPER_LIMIT, 0));
	}
	
	@Test
	public void ramping() {
		// Specify ramping behavior and common timestamp first.
	}
	
	public void maxPowerInput() throws OpenemsException, Exception {
		ManagedSymmetricEssHybridTest testEss = setup(ESS_ID,
				CAPACITY,
				MAX_APPARENT_POWER,
				SOC, GridMode.ON_GRID,
				RAMP_RATE,
				0, // RESPONSE TIME = 0
				CHARGE_POWER,
				DISCHARGE_POWER);
	}
}
