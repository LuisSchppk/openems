package io.openems.edge.controller.ess.hybridess;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.openems.edge.ess.api.ManagedSymmetricEss;
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
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.hybridess.CSVUtil.Row;
import io.openems.edge.ess.api.CalculateGridMode;
import io.openems.edge.ess.api.ManagedSymmetricEssHybrid;
import io.openems.edge.meter.api.SymmetricMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Symmetric.Hybrid", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class HybridControllerImpl extends AbstractOpenemsComponent implements HybridController, Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(HybridControllerImpl.class);
		
	private Config config = null;
	
	private File energyPrediction;
	private File powerPrediction;
	
	/**
	 * Minimal total Energy that should be stored by 
	 * ESSs to ensure EVs can be serviced.
	 */
	private int defaultMinimumEnergy;
	private int maxGridPower;

	// Percentage of redox's maximum power output, that redox should supply alone as netpower.
	private double netPowerThreshold = 0.8;



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
		this.energyPrediction = Path.of(config.energyPrediction()).toFile();
		this.powerPrediction = Path.of(config.powerPrediction()).toFile();
		this.defaultMinimumEnergy = config.defaultMinimumEnergy()/* kWh to Wh*/ * 1000;
		this.maxGridPower = config.maxGridPower() /* kW to W*/ * 1000;
		if(!Files.exists(energyPrediction.toPath())) {
			this.logInfo(log, String.format("Energy prediction at %s not found", energyPrediction.toPath()));
		}
		
		if(!Files.exists(powerPrediction.toPath())) {
			this.logInfo(log, String.format("Power prediction at %s not found", powerPrediction.toPath()));
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		
		/*
		 * ESSs as local variable to avoid Problems when updating config of a ESS during runtime.
		 * -> look unto updatefilter 
		 */
		ManagedSymmetricEssHybrid redox = this.componentManager.getComponent(this.config.redoxId());
		ManagedSymmetricEssHybrid liIon = this.componentManager.getComponent(this.config.liIonId());
		GridMode gridMode = CalculateGridMode.calculate(Arrays.asList(redox.getGridMode(), liIon.getGridMode()));
		
		switch(gridMode) {
		case UNDEFINED:
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		case ON_GRID:
			break;
		case OFF_GRID:
		default:
			return;
		}
		
		// TODO Handling of different operating status.
		if(sum.getConsumptionActivePower().orElse(0) > 0) {
			this.discharge(redox, liIon);
		} else {
			this.charge(redox, liIon);
		}
	}

	private void discharge(ManagedSymmetricEssHybrid redox, ManagedSymmetricEssHybrid liIon) throws OpenemsNamedException {

		// ConsumptionActivePower has to be defined at this point, as it is checked before calling discharge.
		int requiredPower = sum.getConsumptionActivePower().get();
		double powerSplit = 1;
		if(requiredPower >= netPowerThreshold*redox.getAllowedDischargePower().orElse(0)
			|| !SoCArea.getArea(redox, defaultMinimumEnergy).equals(SoCArea.GREEN)) {

			// TODO Implement for discharge. No numbers yet.
			powerSplit = chargePowerSplit(redox, liIon);
		}

		int redoxPower = redox.filterPower((int) (powerSplit * requiredPower));
		int liIonPower = liIon.filterPower(requiredPower - redoxPower);

		redox.setActivePowerEquals(redoxPower);
		liIon.setActivePowerEquals(liIonPower);

		redox.setReactivePowerEquals(0);
		liIon.setReactivePowerEquals(0);
	}
	
	private void charge(ManagedSymmetricEssHybrid redox, ManagedSymmetricEssHybrid liIon) throws OpenemsNamedException {
		int targetGridSetPoint;
		int minimalStoredEnergy = getMinimalStoredEnergy();

		if(minimalStoredEnergy >= this.getTotalStoredCapacity()) {
			targetGridSetPoint = -this.maxGridPower;
		} else {
			targetGridSetPoint = getPredictedPower();
		}
		
		if(targetGridSetPoint > 0) {
			
			/* 
			 * TODO In this case Prediction recommends discharging.
			 * In this case this branch should probably not even be entered.
			 * As interim solution: set to 0
			 */
			targetGridSetPoint = 0;
		}

		int chargePower = targetGridSetPoint - this.getTotalProductionEnergy();

		double powerSplit = chargePowerSplit(redox, liIon);

		int redoxPower = redox.filterPower((int) (powerSplit*chargePower));
		int liIonPower = liIon.filterPower(chargePower-redoxPower);
		
		redox.setActivePowerEquals(redoxPower);
		liIon.setActivePowerEquals(liIonPower);
		
		redox.setReactivePowerEquals(0);
		liIon.setReactivePowerEquals(0);
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
	
	private int getMinimalStoredEnergy() {
		int minimalEnergy = this.defaultMinimumEnergy;

		// TODO duplicate Code, maybe use aux. method.
		// TODO change to global timestamp of cycle?
		LocalDateTime now = LocalDateTime.now(componentManager.getClock());
		List<Row> csvRows = CSVUtil.parseEnergyPrediction(energyPrediction);


		Optional<Row> prediction = csvRows.stream()
				.filter(row->now.isAfter(row.getStart()) && now.isBefore(row.getEnd()) && row.getValue() >= 0)
				.findFirst();
		if(prediction.isPresent()) {
			minimalEnergy = prediction.get().getValue();
		}
		return minimalEnergy;
	}

	private int getPredictedPower() {
		int predictedPower = 0;

		// TODO duplicate Code, maybe use aux. method.
		// TODO change to global timestamp of cycle?
		LocalDateTime now = LocalDateTime.now(componentManager.getClock());
		List<Row> csvRows = CSVUtil.parseEnergyPrediction(powerPrediction);
		Optional<Row> prediction = csvRows.stream()
				.filter(row->now.isAfter(row.getStart()) && now.isBefore(row.getEnd()) && row.getValue() >= 0)
				.findFirst();

		if(prediction.isPresent()) {
			predictedPower = prediction.get().getValue();
		}

		return predictedPower;
	}

	private int getTotalStoredCapacity() {
		int totalCapacity = 0;
		if(sum.getEssCapacity().isDefined()) {
			totalCapacity = sum.getEssCapacity().get();
		} else {
			logInfo(log, "Could not calculate totalCapacity correctly. Capacity Channel undefinded.");
		}
		return totalCapacity;
	}
	
	private int getTotalProductionEnergy() {
		int totalProductionEnergy = 0;
		if(sum.getProductionActivePower().isDefined()) {
			totalProductionEnergy = sum.getProductionActivePower().get();
		} else {
			logInfo(log, "Could not calculate totalCapacity correctly. Capacity Channel undefinded.");
		}
		return totalProductionEnergy;
	}


	private double chargePowerSplit(ManagedSymmetricEssHybrid redox, ManagedSymmetricEssHybrid liIon) throws InvalidValueException {
		double powerSplit = 1;
		SoCArea redoxArea = SoCArea.getArea(redox, defaultMinimumEnergy);
		SoCArea liOnArea = SoCArea.getArea(liIon, defaultMinimumEnergy);
		powerSplit = SoCArea.getPowerSplit(redoxArea, liOnArea);
		return powerSplit;
	}

	private enum SoCArea {
		RED,
		ORANGE,
		GREEN;

		// powerSplit = CHARGE_TABLE(liON, redox)
		private final static double[][] CHARGE_TABLE = {{0.5, 0.3, 0}, // liON RED
													   {0.7, 0.5, 0.2}, // liON ORANGE
				                                       {1.0, 0.8, 0.5}}; // lion GREEN
		private final static double[][] DISCHARGE_TABLE = {{0.5, 0.8, 1.0}, // liON RED
				 										  {0.2, 0.5, 0.7}, // liON ORANGE
				                                          {0, 0.2, 0.5}}; // lion GREEN

		/*
		 * TODO do we need to separate methods for the ESSs? If so this should maybe a method of the ESS.
		 * But from my POV this should be controlled by controller.
		 */
		private static SoCArea getArea(ManagedSymmetricEss ess, int minimumEnergy) throws InvalidValueException {
			SoCArea SoCArea;
			int storedEnergy = (ess.getSoc().getOrError() * ess.getCapacity().getOrError());
			if (storedEnergy <= 0.5 * minimumEnergy) {
				SoCArea = HybridControllerImpl.SoCArea.RED;
			} else if( storedEnergy <= 0.5* ess.getCapacity().getOrError()) {
				SoCArea = HybridControllerImpl.SoCArea.ORANGE;
			} else {
				SoCArea= HybridControllerImpl.SoCArea.GREEN;
			}
			return SoCArea;
		}

		private static double getPowerSplit(SoCArea redoxArea, SoCArea liOnArea) {
			return CHARGE_TABLE[liOnArea.ordinal()][redoxArea.ordinal()];
		}
	}
}
