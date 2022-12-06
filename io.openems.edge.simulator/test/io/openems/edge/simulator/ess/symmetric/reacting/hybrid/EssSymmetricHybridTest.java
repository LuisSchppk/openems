package io.openems.edge.simulator.ess.symmetric.reacting.hybrid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import org.junit.Test;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;

import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.common.event.EventBuilder;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.cycle.Cycle;
import io.openems.edge.common.event.EdgeEventConstants;
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
	
	private ManagedSymmetricEssHybridTest setup(String ess_id, int capacity, int maxApparentPower, int SoC, GridMode gridMode,
			int rampRate, int responseTime, int chargePower, int dischargePower) throws OpenemsException, Exception {
		return new ManagedSymmetricEssHybridTest(new EssSymmetricHybrid()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
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
	
	@Test
	public void responseTimeNotReady() throws OpenemsException, Exception {
		ManagedSymmetricEssHybridTest testEss = setup();
		int[] possibleChargePower = testEss.getSut().calculatePossibleChargePower();
		assertEquals(0, possibleChargePower[0]);
		assertEquals(0, possibleChargePower[1]);
	}
	
	
	// Not working as values do not get written to channel. I have to use the openems test framework
	@Test
	public void responseTimeReady() throws OpenemsException, Exception {
		ManagedSymmetricEssHybridTest testEss = setup();
		int[] possibleChargePower = testEss.getSut().calculatePossibleChargePower();
		sleep(2000); // sleep for at least response time
		testEss.getSut().handleEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE, Collections.emptyMap()));
		possibleChargePower = testEss.getSut().calculatePossibleChargePower();
		
		// WARNING: ActivePower Channel not defined. Gets caught and assigned to 0 due to defensive programming. Need test for actual ramping.
		assertEquals(-RAMP_RATE, possibleChargePower[0]);
		assertEquals(0, possibleChargePower[1]);
	}

	private void sleep(int i) {
		// TODO Auto-generated method stub
		
	}
	
}
