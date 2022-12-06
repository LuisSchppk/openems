package io.openems.edge.controller.ess.hybridess;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller HybridController", //
		description = "Controller for Hybrid ESS consisting of one Redox-ESS and one LiOn-ESS")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlHybridController0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";
	
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

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	String webconsole_configurationFactory_nameHint() default "Controller HybridController [{id}]";

}