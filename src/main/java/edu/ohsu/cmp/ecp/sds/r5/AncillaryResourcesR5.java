package edu.ohsu.cmp.ecp.sds.r5;

import ca.uhn.fhir.jpa.starter.annotations.OnR5Condition;
import edu.ohsu.cmp.ecp.sds.AncillaryResources;
import edu.ohsu.cmp.ecp.sds.base.BaseAncillaryResources;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Conditional(OnR5Condition.class)
public class AncillaryResourcesR5 extends BaseAncillaryResources implements AncillaryResources {
	@Override
	public List<Class<? extends IBaseResource>> getAncillaryResourceClasses() {
		return List.of(
			Binary.class,
			Device.class,
			Location.class,
			Medication.class,
			Organization.class,
			Practitioner.class,
			PractitionerRole.class
		);
	}
}
