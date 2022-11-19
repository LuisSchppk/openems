package io.openems.edge.simulator.ess.symmetric.reacting.omei;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssOmei;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.simulator.ess.symmetric.reacting.EssSymmetric;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Simulator.EssSymmetric.Reacting.Omei", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
})
public class EssSymmetricOmei extends AbstractOpenemsComponent 
	implements ManagedSymmetricEssOmei, ManagedSymmetricEss, SymmetricEss, OpenemsComponent, TimedataProvider, EventHandler, StartStoppable, ModbusSlave {
	
	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;
		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
	
	private Config config;
	
	/**
	 * Maximum amount of power change from last step to current.
	 */
	private int rampRate;
	
	/**
	 * Time from power Requested to first power supplied in [s].
	 */
	private long responseTime;
	
	private Instant startRequested;
	
	private boolean powerRequested = false;
	
	private boolean ready;
	
	private int minSoc;
	
	private int maxSoc;
	
	private OperatingStatus operatingStatus;
	
	/**
	 * Current Energy in the battery [Wms], based on SoC.
	 */
	private long energy = 0;
	
	private Instant lastTimestamp = null;

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);
	
	private final static int POWER_PRECISION = 1;
	
	@Reference
	private Power power;

	@Reference
	protected ConfigurationAdmin cm;

	@Reference
	protected ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;
	
	public EssSymmetricOmei(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				ChannelId.values() //
		);
	}
	
	@Activate
	void activate(ComponentContext context, Config config) throws IOException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.energy = (long) ((double) config.capacity() /* [Wh] */ * 3600 /* [Wsec] */ * 1000 /* [Wmsec] */
				/ 100 * this.config.initialSoc() /* [current SoC] */);
		this._setSoc(config.initialSoc());
		this._setMaxApparentPower(config.maxApparentPower());
		this._setAllowedChargePower(config.capacity() * -1);
		this._setAllowedDischargePower(config.capacity());
		this._setGridMode(config.gridMode());
		this._setCapacity(config.capacity());
		this.rampRate = config.rampRate();
		this.responseTime = Duration.of(config.responseTime(), ChronoUnit.MILLIS).toSeconds();
		this.ready = responseTime == 0;
		this.operatingStatus = OperatingStatus.STANDBY;
	}
	
	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}
	
	@Override
	public void handleEvent(Event event) {
		Integer activePower = this.getActivePower().orElse(0);
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculateEnergy();
			
			if(startRequested != null && Duration.between(startRequested, Instant.now()).toSeconds() >= responseTime) {
				
				// Ess is ready to provide power if its start has been requested and if the response time has elapsed.
				this.ready = true;
				
				// reset response timer
				this.startRequested = null;
			} else if(activePower != 0) {
				if(activePower > 0) {
					this.operatingStatus = OperatingStatus.CHARGING;
				} else {
					this.operatingStatus = OperatingStatus.DISCHARGING;
				}
			} else {
				this.ready = this.responseTime == 0;
				this.operatingStatus = OperatingStatus.STANDBY;
			}
			
			break;
		}
	}
	
	/*
	 * Gets called by Solver.java after power constraints have been solved. 
	 */
	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		
		/*
		 * calculate State of charge
		 */
		Instant now = Instant.now(this.componentManager.getClock());
		final int soc = calculateSoc(now);
		this._setSoc(soc);
		this.lastTimestamp = now;
		
		/*
		 * Apply Active/Reactive power to simulated channels
		 */
		if ((soc == 0 && activePower > 0) 
				|| (soc == 100 && activePower < 0)) {
			activePower = 0;
		}
		this._setActivePower(activePower);
		
		if ((soc == 0 && reactivePower > 0) 
				|| (soc == 100 && reactivePower < 0) ) {
			reactivePower = 0;
		}
		this._setReactivePower(reactivePower);
		
		/*
		 * Set AllowedCharge / Discharge based on SoC
		 */
		if (soc == 100) {
			this._setAllowedChargePower(0);
		} else {
			this._setAllowedChargePower(this.config.capacity() * -1);
		}
		if (soc == 0) {
			this._setAllowedDischargePower(0);
		} else {
			this._setAllowedDischargePower(this.config.capacity());
		}
	}
	
	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				SymmetricEss.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode), //
				StartStoppable.getModbusSlaveNatureTable(accessMode), //
				ModbusSlaveNatureTable.of(EssSymmetric.class, accessMode, 100) //
				.build());
	}

	@Override
	public void setStartStop(StartStop value) throws OpenemsNamedException {
		this.setStartStop(value);
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	@Override
	public int getPowerPrecision() {
		return POWER_PRECISION;
	}
	
	@Override
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|L:" + this.getActivePower().asString() //
				+ "|Allowed:" + this.getAllowedChargePower().asStringWithoutUnit() + ";"
				+ this.getAllowedDischargePower().asString();
	}
	
	@Override
	public int getPowerStep() {
		return rampRate;
	}
	
	@Override
	public boolean isReady() {
		return ready;
	}
	
	@Override
	public void start() {
		if(startRequested == null) {
			startRequested = Instant.now();
		}
	}
	
	/**
	 * Calculate the Energy values from ActivePower.
	 */
	private void calculateEnergy() {
		// Calculate Energy
		Integer activePower = this.getActivePower().get();
		if (activePower == null) {
			// Not available
			this.calculateChargeEnergy.update(null);
			this.calculateDischargeEnergy.update(null);
		} else if (activePower > 0) {
			// Buy-From-Grid
			this.calculateChargeEnergy.update(0);
			this.calculateDischargeEnergy.update(activePower);
		} else {
			// Sell-To-Grid
			this.calculateChargeEnergy.update(activePower * -1);
			this.calculateDischargeEnergy.update(0);
		}
	}
	
	private int calculateSoc(Instant now) {
		int soc = this.config.initialSoc();
		
		// Check if this is not the inital run
		if (this.lastTimestamp != null) {
			// calculate duration since last value
			long duration /* [msec] */ = Duration.between(this.lastTimestamp, now).toMillis();

			// calculate energy since last run in [Wh]
			long energy /* [Wmsec] */ = this.getActivePower().orElse(0) /* [W] */ * duration /* [msec] */;

			// Adding the energy to the initial energy.
			this.energy -= energy;

			double calculatedSoc = this.energy //
					/ (this.config.capacity() * 3600. /* [Wsec] */ * 1000 /* [Wmsec] */) //
					* 100 /* [SoC] */;

			if (calculatedSoc > 100) {
				soc = 100;
			} else if (calculatedSoc < 0) {
				soc = 0;
			} else {
				soc = (int) Math.round(calculatedSoc);
			}
		}
		return soc;
	}

	@Override
	public int getMinSoc() {
		return minSoc;
	}

	@Override
	public int getMaxSoc() {
		return maxSoc;
	}
}
