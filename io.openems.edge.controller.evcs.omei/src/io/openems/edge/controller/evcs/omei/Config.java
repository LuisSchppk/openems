package io.openems.edge.controller.evcs.omei;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "EVCS Controller OMEI", //
		description = "")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlEvcsOmei0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name = "Evcs-ID", description = "ID of Evcs device (Has to be managed).", required = true)
	String evcs_id() default "evcs0";
	
	@AttributeDefinition(name = "Ess-ID", description = "ID of Ess device.")
	String ess_id() default "ess0";
	
	@AttributeDefinition(name = "Target-charge power [W]", description = "Set the Target for the default charge mode in Watt.", min = "0")
	int targetChargePower() default 30000;
	
	@AttributeDefinition(name = "Energy limit in this session in [Wh]", description = "Set the Energylimit in this Session in Wh. The charging station will only charge till this limit; '0' is no limit.", min = "0")
	int energySessionLimit() default 40000;
	
	@AttributeDefinition(name = "Mode", description = "Determines whether to charge vehicle or to use V2X.")
	Mode mode() default Mode.CHARGING;
	
	@AttributeDefinition(name = "Evcs target filter", description = "This is auto-generated by 'Evcs-ID'.")
	String evcs_target() default "(enabled=true)";

	String webconsole_configurationFactory_nameHint() default "Controller Electric Vehicle Charging Station OMEI [{id}]";
}