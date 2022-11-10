package io.openems.edge.controller.symmetric.balancingomei;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller Balancing Symmetric Omei", //
		description = "Optimizes the self-consumption by keeping the grid meter on zero.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlBalancing0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;
	
	@AttributeDefinition(name = "Redox", description = "ID of Redox-Ess.")
	String redox_id();
	
	@AttributeDefinition(name = "Lithium-Ion", description = "ID of Lithium-Ion-Ess.")
	String litIon_id();

	@AttributeDefinition(name = "Grid-Meter-ID", description = "ID of the Grid-Meter.")
	String meter_id();
	
	@AttributeDefinition(name ="Min. Soc",
			description = "SoC until which Lithium-Ion will prioritize discharge.", min ="0", max = "100")
	int minSoc() default 20;
	
	@AttributeDefinition(name ="Max. Soc",
			description = "SoC until which Redox will prioritize charging.", min ="0", max = "100")
	int maxSoc() default 90;

	@AttributeDefinition(name = "Target Grid Setpoint", description = "The target setpoint for grid. Positive for buy-from-grid; negative for sell-to-grid.")
	int targetGridSetpoint() default 0;

	String webconsole_configurationFactory_nameHint() default "Controller Balancing Symmetric [{id}]";

}