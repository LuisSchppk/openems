package io.openems.edge.controller.ess.omei.hybrid;

import java.time.Instant;
import java.util.Arrays;

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
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.filter.PidFilter;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.CalculateGridMode;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEssOmei;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.meter.api.SymmetricMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Symmetric.Hybrid", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class HybridControllerImpl extends AbstractOpenemsComponent implements HybridController, Controller, OpenemsComponent {
	
	// Values for PIDFilter
	private final static double P = 0.3;
	private final static double I = 0.3;
	private final static double D = 0.1;

	private final Logger log = LoggerFactory.getLogger(HybridControllerImpl.class);
		
	/*
	 * Workaround for unexpected PidFilter behavior. 
	 * Both ESS use the same power object, which only has one pidFilter object.
	 * Therefore both activePowerValues are calculated with the same filter, which leads to faults,
	 * as the calculation is not memoryless.  
	 */
	private PidFilter redoxFilter = new PidFilter(P,I,D);
	private PidFilter liIonFilter = new PidFilter(P,I,D);
	private Config config = null;
	
	private Instant redoxPowerRequested;
	
	private int lastPower;

	@Reference
	private ComponentManager componentManager;
	
	@Reference 
	private Sum sum;

	public HybridControllerImpl(){
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				HybridController.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		lastPower = 0;
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		SymmetricMeter meter = this.componentManager.getComponent(this.config.meter_id());
		
		/*
		 * ESSs as local variable to avoid Problems when updating config of a ESS during runtime.
		 * -> look unto updatefilter 
		 */
		ManagedSymmetricEssOmei redox = this.componentManager.getComponent(this.config.redox_id());
		ManagedSymmetricEssOmei liIon = this.componentManager.getComponent(this.config.litIon_id());
		GridMode gridMode = CalculateGridMode.calculate(Arrays.asList(redox.getGridMode(), liIon.getGridMode()));
		
		
		/*
		 * OPENEMS calls this every cycle. Therefore I do the same. Maybe it would be more efficient
		 * to call it once during init?
		 */
//		configurePid(redox, redoxFilter);
//		configurePid(liIon, liIonFilter);
		
		switch(gridMode) {
		case UNDEFINED:
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		case ON_GRID:
			break;
		case OFF_GRID:
		default:
			return;
		}
		
		// Power the ESSs needs to charge/ discharge to meet the required GridSetpoint
		int calculatedPower = calculatePower(meter, calculateGridSetpoint());
		double splitModifier = calculateSplitModifierResponseTime(redox, liIon, calculatedPower);
		
		int splitPower = (int) (splitModifier * calculatedPower);
		redox.setActivePowerEquals(splitPower);
		liIon.setActivePowerEquals(calculatedPower-splitPower);
		redox.setReactivePowerEquals(0);
		liIon.setReactivePowerEquals(0);
//		filterAndApplyPower(splitPower, redox,redoxFilter);
//		filterAndApplyPower(calculatedPower - splitPower, liIon, liIonFilter);
	}
	
	/**
	 * Determines amount of power that should be feed into or sold to grid, based on predicted supply and demand
	 * and current Energy Prices. 
	 * 
	 * @return Value in [W] which should be feed-in(positive) 
	 * 	      or sold-off(negative) to grid.
	 */
	private int calculateGridSetpoint() {
		// Consider Prediction
		// Consider Energy Prices
		return 0;
	}
	
	/**
	 * Determines ratio of available active power each ESS should receive, based on
	 * SoC, available capacity, C-Rate and  Allowed Dis-/Charge Power.
	 * 1 redox receives all power.
	 * 0 liIon receives all power
	 * 
	 * @return Value in [0...1]
	 * @throws InvalidValueException when ESS SoC could not be read.
	 */
	private double calculateSplitModifierPriority(ManagedSymmetricEssOmei redox, ManagedSymmetricEssOmei liOn, int calculatedPower) throws InvalidValueException {
		double splitModifier = 0;
		double emergencyCharge = 0.25;		// TODO implement as parameter via config
		double fastCharge = 0.5; 			// TODO implement as parameter via config
		
		int litSoc = liOn.getSoc().getOrError();
		int redSoc = redox.getSoc().getOrError();
		
		if(calculatedPower <= 0) {
			splitModifier = 1.0 - redSoc/100.0; 
			
			// Supply min. Power if LiOn is below certain limit.
			if(litSoc <= liOn.getMinSoc()) {		// TODO maybe min/maxSoc as parameter for Controller?
				splitModifier = Math.min(splitModifier, emergencyCharge);
			} else if (litSoc <= liOn.getMinSoc() * 2) {
				splitModifier = Math.min(splitModifier, fastCharge);
			}
			
		} else {
			splitModifier = 1.0 - litSoc/ 100.0;
		}
		
		if(splitModifier < 0 || splitModifier > 1) {
			throw new IllegalArgumentException("Invalid Split.");
		}
		return splitModifier;
	}
	
	private double calculateSplitModifierResponseTime(ManagedSymmetricEssOmei redox, ManagedSymmetricEssOmei liOn, int calculatedPower) throws InvalidValueException {
		double splitModifier = 0;
		
		if(redox.isReady()) {
			splitModifier = calculateSplitModifierPriority(redox, liOn, calculatedPower);
		} else {
			redox.start();
			splitModifier = 0;
		}
		
		if(splitModifier < 0 || splitModifier > 1) {
			throw new IllegalArgumentException("Invalid Split.");
		}
		return splitModifier;
	}
	
	// Here problems with the simple split idea...
	private double calculateSplitModifierPowerStep(ManagedSymmetricEssOmei redox, ManagedSymmetricEssOmei liOn, int calculatedPower) throws InvalidValueException {
		double splitModifier = 0;
		int deltaPower = calculatedPower - lastPower;
		int powerStep = redox.getPowerStep();
		
		if(deltaPower <= 0) {
			if(-deltaPower <= redox.getPowerStep()) {
				
				// redox can reduce power output sufficiently
				splitModifier = 1;
			} else if(calculatedPower <= 0) {

			} else if( calculatedPower > 0) {
				
			}
		} else {
			
		}

		if(splitModifier < 0 || splitModifier > 1) {
			throw new IllegalArgumentException("Invalid Split.");
		}
		return splitModifier;
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
