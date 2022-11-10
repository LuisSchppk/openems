package io.openems.edge.controller.symmetric.balancingomei;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.filter.PidFilter;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.meter.api.SymmetricMeter;

/**
 * Balancing Controller handling two ESS.
 *
 */
@Designate(ocd = Config.class, factory = true)
@Component(name = "Controller.Symmetric.BalancingOmeiCluster", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class BalancingOmeiCluster extends AbstractOpenemsComponent implements Controller, OpenemsComponent {

	// Values for PIDFilter
	private final static double P = 0.3;
	private final static double I = 0.3;
	private final static double D = 0.1;
	
	/*
	 * Workaround for unexpected PidFilter behavior. 
	 * Both ESS use the same power object, which only has one pidFilter object.
	 * Therefore both activePowerValues are calculated with the same filter, which leads to faults,
	 * as the calculation is not memoryless.  
	 */
	private PidFilter redoxFilter = new PidFilter(P,I,D);
	private PidFilter litIonFilter = new PidFilter(P,I,D);
	
	@Reference
	protected ComponentManager componentManager;
	
	@Reference
	private Sum sum;

	private Config config;
	
	private final Logger log = LoggerFactory.getLogger(BalancingOmeiCluster.class);

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

	public BalancingOmeiCluster() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	
	@Override
	public void run() throws OpenemsNamedException {
		SymmetricMeter meter = this.componentManager.getComponent(this.config.meter_id());
		
		/*
		 * ESSs as local variable to avoid Problems when updating config of a ESS during runtime. 
		 */
		ManagedSymmetricEss redox = this.componentManager.getComponent(this.config.redox_id());
		ManagedSymmetricEss litIon = this.componentManager.getComponent(this.config.litIon_id());

		int litSoc = litIon.getSoc().getOrError();
		int redSoc = redox.getSoc().getOrError();
		
		/*
		 * OPENEMS calls this every cycle. Therefore I do the same. Maybe it would be more efficient
		 * to call it once during init?
		 */
		configurePid(redox, redoxFilter);
		configurePid(litIon, litIonFilter);
		
		if(!(isOnGrid(redox)&&isOnGrid(litIon))) {
			return;
		}
		
		var calculatedPower = calculatePower(meter, config.targetGridSetpoint());
		
		if(calculatedPower == 0) {
			/*
			 * Should this case have unique behavior?
			 */
		} else if(calculatedPower <  0) {
			handleCharge(calculatedPower, redox, litIon, redSoc, litSoc);
		} else {
			handleDischarge(calculatedPower, redox, litIon, redSoc, litSoc);
		}
	}
	
	/**
	 * Calculate the power needed to balance grid feed-in of {@code meter} to {@code targetGridSetpoint},
	 * using the sum of the active power of all currently active ESS.
	 *  
	 * @param meter the meter which should be balanced.
	 * @param targetGridSetpoint Value in [W] which should be feed-in(positive) 
	 * 	      or sold-off(negative) to grid.
	 * @return Value in [W] determining the amount of power the ESSs need to charge(negative) 
	 * 		   or discharge(positive) to balance the meter.
	 * @throws InvalidValueException if active Power of meter or the ESSs is {@code null}.
	 */
	private int calculatePower(SymmetricMeter meter, int targetGridSetpoint) throws InvalidValueException {
		return meter.getActivePower().getOrError() /* current buy-from/sell-to grid */
				+ sum.getEssActivePower().getOrError() /* current charge/discharge of ALL Ess */
				- targetGridSetpoint; /* the configured target setpoint */
	}
	
	private void handleCharge(int calculatedPower,ManagedSymmetricEss redox,
			ManagedSymmetricEss litIon, int redSoc, int litSoc) throws OpenemsNamedException {
		
		if(litSoc <= config.minSoc() || redSoc >= config.maxSoc()) {
			
			// If redox above max or litIon below min. -> Prioritize charging litIon.
			filterAndApplyPower(calculatedPower, litIon, litIonFilter);
			filterAndApplyPower(0, redox, redoxFilter);
		} else if(redSoc < config.maxSoc()) {
			
			// otherwise prioritize redox charging.
			filterAndApplyPower(calculatedPower, redox, redoxFilter);
			filterAndApplyPower(0, litIon, litIonFilter);	
		}
	}
	
	/**
	 * Managed which ESS to discharge. Prioritizes {@code litIon}. If either {@code redox}'s SoC is above
	 * its max. SoC or {@code litIon} is below its min. SoC, prioritize charging litIon.
	 * The limits are set in the config. 
	 * 
	 * @param calcultedPower Value in [W] to which the activePower of the ESS is to be set.
	 * @param redox	Redoxflow ESS, prioritized in charging.
	 * @param litIon Lithium-Ion ESS.
	 * @param redSoc SoC of {@code redox}.
	 * @param litSoc SoC of {@code litIon}.
	 * @throws OpenemsNamedException
	 */
	private void handleDischarge(int calcultedPower, ManagedSymmetricEss redox,
			ManagedSymmetricEss litIon, int redSoc, int litSoc ) throws OpenemsNamedException {
		if(redSoc >= config.maxSoc() || litSoc <= config.minSoc()) {
			
			/// If redox above max or litIon below min. -> Prioritize discharging redox.
			filterAndApplyPower(calcultedPower, redox, redoxFilter);
			filterAndApplyPower(0, litIon, litIonFilter);
		} else if(litSoc > config.minSoc()) {
			
			// otherwise prioritize LitIon discharging.
			filterAndApplyPower(calcultedPower, litIon, litIonFilter);
			filterAndApplyPower(0, redox, redoxFilter);
		}
	}
	
	/**
	 * Sets minimum and maximum charging power as the limits on the output value of the {@code pidFilter}.
	 * 
	 * @param ess
	 * @param pidFilter
	 */
	private void configurePid(ManagedSymmetricEss ess, PidFilter pidFilter) {
		var power = ess.getPower();
		var minPower = power.getMinPower(ess, Phase.ALL, Pwr.ACTIVE);
		var maxPower = power.getMaxPower(ess, Phase.ALL, Pwr.ACTIVE);
		if (maxPower < minPower) {
			maxPower = minPower; // avoid rounding error
		}
		pidFilter.setLimits(minPower, maxPower);
	}
	
	/**
	 * Filters the calculatedPower according to the {@code pidFilter} 
	 * and sets it as active power to the ess.
	 * 
	 * @param calculatedPower
	 * @param ess
	 * @param pidFilter
	 * @throws OpenemsNamedException
	 */
	private void filterAndApplyPower(int calculatedPower, ManagedSymmetricEss ess, PidFilter pidFilter) throws OpenemsNamedException {
			int currentActivePower = ess.getActivePower().orElse(0);
			var pidOutput = pidFilter.applyPidFilter(currentActivePower, calculatedPower);
			ess.setActivePowerEquals(pidOutput);
	}
	
	private boolean isOnGrid(ManagedSymmetricEss ess) {
		boolean isOnGrid = false;
		var gridMode = ess.getGridMode();
		if (gridMode.isUndefined()) {
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		}
		switch (gridMode) {
		case ON_GRID:
		case UNDEFINED:
			isOnGrid = true;
			break;
		case OFF_GRID:
		default: isOnGrid = false;
		}
		return isOnGrid;
	}
}
