package io.openems.edge.controller.asymmetric.balancingcosphi;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Controller Balancing Cos-Phi Asymmetric", //
		description = "Keeps the Grid meter on a defined Cos-Phi.")
@interface Config {
	String service_pid();

	String id() default "ctrlBalancingCosPhi0";

	boolean enabled() default true;

	@AttributeDefinition(name = "Ess-ID", description = "ID of Ess device.")
	String ess_id();

	@AttributeDefinition(name = "Grid-Meter-ID", description = "ID of the Grid-Meter.")
	String meter_id();

	@AttributeDefinition(name = "Cos-Phi", description = "Cosinus Phi (e.g. '1' or '0.95').")
	double cosPhi() default CosPhi.DEFAULT_COS_PHI;

	@AttributeDefinition(name = "Inductive/Capacitive", description = "Inductive or Capacitive cos phi.")
	CosPhiDirection direction() default CosPhiDirection.CAPACITIVE;

	String webconsole_configurationFactory_nameHint() default "Controller Balancing Cos-Phi Asymmetric [{id}]";
}