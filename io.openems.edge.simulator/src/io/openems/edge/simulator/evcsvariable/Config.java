package io.openems.edge.simulator.evcsvariable;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.evcs.api.ChargingType;

@ObjectClassDefinition(//
		name = "Simulator EVCS Variable", //
		description = "This simulates a Electric Vehicle Charging Station with more customization than the standard implementation.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "evcs0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name ="Maximum Hardware Power", description = "Maximum power with which this"
			+ " EVCS can charge limited by the Hardware.")
	int MaximumHardwarePower() default 22080;
	
	@AttributeDefinition(name ="Minimum Hardware Power", description = "Minimum power with which this"
			+ " EVCS can charge limited by the Hardware.")
	int MinimumHardwarePower() default 4200;
	
	@AttributeDefinition(name ="Charging Type", description =  "")
	ChargingType chargingType();

	String webconsole_configurationFactory_nameHint() default "Simulator EVCS Variable [{id}]";

}