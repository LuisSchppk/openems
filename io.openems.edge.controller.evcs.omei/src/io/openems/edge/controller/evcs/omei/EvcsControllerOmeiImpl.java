package io.openems.edge.controller.evcs.omei;

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
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.evcs.api.ManagedEvcs;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Evcs.Omei", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class EvcsControllerOmeiImpl extends AbstractOpenemsComponent implements EvcsControllerOmei, Controller, OpenemsComponent, ModbusSlave {

	private Config config;
	
	private final Logger log = LoggerFactory.getLogger(EvcsControllerOmeiImpl.class);
	
	@Reference
	protected ComponentManager componentManager;

	@Reference
	protected ConfigurationAdmin cm;

	@Reference
	protected Sum sum;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private ManagedEvcs evcs;

	public EvcsControllerOmeiImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(),//
				EvcsControllerOmei.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "evcs", config.evcs_id())) {
			return;
		}
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "mode", config.mode().name())) {
			return;
		}
	}
	
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		boolean isClustered = this.evcs.getIsClustered().orElse(false);
		int minPower = evcs.getMinimumHardwarePower().getOrError();
		int maxPower = evcs.getMaximumHardwarePower().getOrError();
		
		if(isClustered) {
			runClustered();
			return;
		}

		switch(config.mode()) {
		case CHARGING:
			charge(minPower, maxPower);
			break;
		case DISCHARGING:
			discharge(minPower, maxPower);
			break;
		case INACTIVE:
			this.evcs.setChargePowerLimit(0);
			break;
		default: break;
		}
		
		
	}
	
	private void charge(int minPower, int maxPower) throws OpenemsNamedException {
		
		if (evcs.getEnergySession().getOrError() <= config.energySessionLimit()) {
			int nextChargePower = config.targetChargePower();

			// Adapt to hardware limits.
			if(nextChargePower < minPower) {
				nextChargePower = minPower;
			}

			if(nextChargePower > maxPower) {
				nextChargePower = maxPower;
			}

			evcs.setChargePowerLimit(nextChargePower);
		} else {
			this.evcs.setChargePowerLimit(0);
		}
	}
	
	private void discharge(int minPower, int maxPower) throws InvalidValueException, OpenemsNamedException {
		
		if(evcs.getEnergySession().getOrError() >= -config.energySessionLimit()) {
			int nextDischargePower = config.targetChargePower();
			
			// Adapt to hardware limits.
			if(nextDischargePower < minPower) {
				nextDischargePower = minPower;
			}

			if(nextDischargePower > maxPower) {
				nextDischargePower = maxPower;
			}
			
			evcs.setChargePowerLimit(nextDischargePower);
		} else {
			this.evcs.setChargePowerLimit(0);
		}
	}
	
	private void runClustered() {
		throw new IllegalStateException("No behavior for clustered evcs yet.");
	};

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				Controller.getModbusSlaveNatureTable(accessMode));
	}
}
